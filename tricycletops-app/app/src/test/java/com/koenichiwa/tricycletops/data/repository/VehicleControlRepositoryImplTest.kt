package com.koenichiwa.tricycletops.data.repository

import com.koenichiwa.tricycletops.data.ble.BleGattDataSource
import com.koenichiwa.tricycletops.data.ble.BleScannerDataSource
import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle
import com.koenichiwa.tricycletops.domain.model.JoystickVector
import com.koenichiwa.tricycletops.domain.model.MotorDriveCommand
import com.koenichiwa.tricycletops.domain.model.VehicleConnectionState
import com.koenichiwa.tricycletops.domain.model.VehicleDutyLimits
import com.koenichiwa.tricycletops.domain.model.WheelDutyLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VehicleControlRepositoryImplTest {

    private lateinit var fakeScanner: FakeBleScanner
    private lateinit var fakeGattClient: FakeBleGattClient
    private lateinit var repository: VehicleControlRepositoryImpl

    private var virtualTime = 1000L

    class FakeBleScanner : BleScannerDataSource {
        val devicesFlow = MutableStateFlow<List<DiscoveredVehicle>>(emptyList())
        override val discoveredDevices: StateFlow<List<DiscoveredVehicle>> = devicesFlow

        val scanningFlow = MutableStateFlow(false)
        override val isScanning: StateFlow<Boolean> = scanningFlow

        val bluetoothEnabledFlow = MutableStateFlow(true)
        override val isBluetoothEnabled: StateFlow<Boolean> = bluetoothEnabledFlow

        var startScanCalled = false
        var stopScanCalled = false

        override fun startScan(): Boolean {
            startScanCalled = true
            scanningFlow.value = true
            return true
        }

        override fun stopScan() {
            stopScanCalled = true
            scanningFlow.value = false
        }
    }

    class FakeBleGattClient : BleGattDataSource {
        val stateFlow = MutableStateFlow<VehicleConnectionState>(VehicleConnectionState.Disconnected)
        override val connectionState: StateFlow<VehicleConnectionState> = stateFlow

        var connectedVehicle: DiscoveredVehicle? = null
        var disconnectCalled = false
        val writtenCommands = mutableListOf<MotorDriveCommand>()

        override fun connect(vehicle: DiscoveredVehicle) {
            connectedVehicle = vehicle
            stateFlow.value = VehicleConnectionState.Connected(vehicle)
        }

        override fun disconnect() {
            disconnectCalled = true
            stateFlow.value = VehicleConnectionState.Disconnected
        }

        override fun writeDriveCommand(command: MotorDriveCommand): Boolean {
            writtenCommands.add(command)
            return true
        }
    }

    @Before
    fun setUp() {
        virtualTime = 1000L
        fakeScanner = FakeBleScanner()
        fakeGattClient = FakeBleGattClient()
        repository =
            VehicleControlRepositoryImpl(
                scanner = fakeScanner,
                gattClient = fakeGattClient,
                scope = CoroutineScope(Dispatchers.Default),
                throttleIntervalMs = 40L,
                timeProvider = { virtualTime },
            )
    }

    @Test
    fun `isBluetoothEnabled reflects scanner status`() {
        assertTrue(repository.isBluetoothEnabled.value)

        fakeScanner.bluetoothEnabledFlow.value = false
        assertEquals(false, repository.isBluetoothEnabled.value)
    }

    @Test
    fun `startScan and stopScan delegate to scanner`() {
        repository.startScan()
        assertTrue(fakeScanner.startScanCalled)

        repository.stopScan()
        assertTrue(fakeScanner.stopScanCalled)
    }

    @Test
    fun `connect delegates to gattClient`() = runBlocking {
        val vehicle = DiscoveredVehicle(name = "Tricycletops", address = "AA:BB:CC:DD:EE:FF", rssi = -50)
        repository.connect(vehicle)

        assertEquals(vehicle, fakeGattClient.connectedVehicle)
    }

    @Test
    fun `disconnect sends stop command and terminates GATT connection`() = runBlocking {
        val vehicle = DiscoveredVehicle(name = "Tricycletops", address = "AA:BB:CC:DD:EE:FF", rssi = -50)
        repository.connect(vehicle)

        repository.disconnect()

        assertTrue(fakeGattClient.disconnectCalled)
        assertTrue(fakeGattClient.writtenCommands.any { it.isStop })
    }

    @Test
    fun `drive with non-zero vector transmits command immediately on initial call`() = runBlocking {
        repository.drive(JoystickVector(x = 0f, y = 1f))

        assertEquals(1, fakeGattClient.writtenCommands.size)
        assertEquals(1000, fakeGattClient.writtenCommands.first().leftDuty)
        assertEquals(1000, fakeGattClient.writtenCommands.first().rightDuty)
    }

    @Test
    fun `drive throttles high-frequency active drag updates and sends freshest position after delay`() = runBlocking {
        repository.drive(JoystickVector(x = 0f, y = 0.5f))
        assertEquals(1, fakeGattClient.writtenCommands.size)

        // Advance virtual time by 10ms (< 40ms interval) and drag further
        virtualTime += 10L
        repository.drive(JoystickVector(x = 0f, y = 0.8f))
        // Should not have sent immediate command
        assertEquals(1, fakeGattClient.writtenCommands.size)

        // Drag to full forward at 20ms
        virtualTime += 10L
        repository.drive(JoystickVector(x = 0f, y = 1.0f))
        assertEquals(1, fakeGattClient.writtenCommands.size)

        // Wait for throttled delay (throttle interval is 40ms)
        Thread.sleep(60)

        // Now the freshest command (full forward 1000, 1000) should have been dispatched
        assertEquals(2, fakeGattClient.writtenCommands.size)
        assertEquals(1000, fakeGattClient.writtenCommands.last().leftDuty)
        assertEquals(1000, fakeGattClient.writtenCommands.last().rightDuty)
    }

    @Test
    fun `releasing joystick transmits stop command immediately with zero latency`() = runBlocking {
        // Start dragging
        repository.drive(JoystickVector(x = 0f, y = 1.0f))
        assertEquals(1, fakeGattClient.writtenCommands.size)

        // User releases joystick 5ms later
        virtualTime += 5L
        repository.drive(JoystickVector.ZERO)

        // Must immediately transmit stop command without waiting for 40ms interval
        assertEquals(2, fakeGattClient.writtenCommands.size)
        assertTrue(fakeGattClient.writtenCommands.last().isStop)
    }

    @Test
    fun `drive scales using custom duty limits from connected vehicle state`() = runBlocking {
        val vehicle = DiscoveredVehicle(name = "Tricycletops", address = "AA:BB:CC:DD:EE:FF", rssi = -50)
        val customLimits =
            VehicleDutyLimits(
                left = WheelDutyLimits(minDuty = 100, maxDuty = 500),
                right = WheelDutyLimits(minDuty = 100, maxDuty = 500),
            )
        fakeGattClient.stateFlow.value = VehicleConnectionState.Connected(vehicle, customLimits)

        repository.drive(JoystickVector(x = 0f, y = 1.0f))

        assertEquals(1, fakeGattClient.writtenCommands.size)
        assertEquals(500, fakeGattClient.writtenCommands.first().leftDuty)
        assertEquals(500, fakeGattClient.writtenCommands.first().rightDuty)
    }
}
