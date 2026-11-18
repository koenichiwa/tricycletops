package com.koenichiwa.tricycletops.presentation.scanner

import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle
import com.koenichiwa.tricycletops.domain.model.JoystickVector
import com.koenichiwa.tricycletops.domain.model.VehicleConnectionState
import com.koenichiwa.tricycletops.domain.repository.VehicleControlRepository
import com.koenichiwa.tricycletops.domain.usecase.ConnectVehicleUseCase
import com.koenichiwa.tricycletops.domain.usecase.DiscoverVehiclesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceScannerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeRepository: FakeVehicleControlRepository
    private lateinit var discoverVehiclesUseCase: DiscoverVehiclesUseCase
    private lateinit var connectVehicleUseCase: ConnectVehicleUseCase
    private lateinit var viewModel: DeviceScannerViewModel

    class FakeVehicleControlRepository : VehicleControlRepository {
        val vehiclesFlow = MutableStateFlow<List<DiscoveredVehicle>>(emptyList())
        override val discoveredVehicles: StateFlow<List<DiscoveredVehicle>> = vehiclesFlow

        val scanningFlow = MutableStateFlow(false)
        override val isScanning: StateFlow<Boolean> = scanningFlow

        val bluetoothEnabledFlow = MutableStateFlow(true)
        override val isBluetoothEnabled: StateFlow<Boolean> = bluetoothEnabledFlow

        val connectionStateFlow = MutableStateFlow<VehicleConnectionState>(VehicleConnectionState.Disconnected)
        override val connectionState: StateFlow<VehicleConnectionState> = connectionStateFlow

        var startScanCalled = false
        var stopScanCalled = false
        var connectedVehicle: DiscoveredVehicle? = null

        override fun startScan() {
            startScanCalled = true
            scanningFlow.value = true
        }

        override fun stopScan() {
            stopScanCalled = true
            scanningFlow.value = false
        }

        override suspend fun connect(vehicle: DiscoveredVehicle) {
            connectedVehicle = vehicle
            connectionStateFlow.value = VehicleConnectionState.Connecting(vehicle.address)
        }

        override suspend fun disconnect() {
            connectionStateFlow.value = VehicleConnectionState.Disconnected
        }

        override suspend fun drive(joystick: JoystickVector) = Unit
        override suspend fun stopImmediate() = Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeVehicleControlRepository()
        discoverVehiclesUseCase = DiscoverVehiclesUseCase(fakeRepository)
        connectVehicleUseCase = ConnectVehicleUseCase(fakeRepository)
        viewModel = DeviceScannerViewModel(discoverVehiclesUseCase, connectVehicleUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is MissingPermissions when permissions are not granted`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(ScannerUiState.MissingPermissions, viewModel.uiState.value)
        collectJob.cancel()
    }

    @Test
    fun `when permissions granted but Bluetooth disabled, uiState is BluetoothDisabled`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        fakeRepository.bluetoothEnabledFlow.value = false
        viewModel.onPermissionStatusChanged(true)
        advanceUntilIdle()

        assertEquals(ScannerUiState.BluetoothDisabled, viewModel.uiState.value)
        collectJob.cancel()
    }

    @Test
    fun `when permissions granted and Bluetooth enabled, automatically starts scanning and uiState is Scanning`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        fakeRepository.bluetoothEnabledFlow.value = true
        viewModel.onPermissionStatusChanged(true)
        advanceUntilIdle()

        assertTrue(fakeRepository.startScanCalled)
        assertTrue(viewModel.uiState.value is ScannerUiState.Scanning)
        collectJob.cancel()
    }

    @Test
    fun `when scanning is stopped manually, uiState becomes Idle with discovered devices`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        val testDevice = DiscoveredVehicle(name = "Tricycletops", address = "AA:BB:CC:DD:EE:FF", rssi = -60)
        fakeRepository.vehiclesFlow.value = listOf(testDevice)

        viewModel.onPermissionStatusChanged(true)
        advanceUntilIdle()

        viewModel.stopScan()
        advanceUntilIdle()

        assertEquals(ScannerUiState.Idle(listOf(testDevice)), viewModel.uiState.value)
        collectJob.cancel()
    }

    @Test
    fun `when Bluetooth is disabled while scanning, uiState transitions to BluetoothDisabled`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        viewModel.onPermissionStatusChanged(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ScannerUiState.Scanning)

        fakeRepository.bluetoothEnabledFlow.value = false
        advanceUntilIdle()

        assertEquals(ScannerUiState.BluetoothDisabled, viewModel.uiState.value)
        collectJob.cancel()
    }

    @Test
    fun `when connection begins, uiState becomes Connecting`() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        viewModel.onPermissionStatusChanged(true)
        advanceUntilIdle()

        val testDevice = DiscoveredVehicle(name = "Tricycletops", address = "AA:BB:CC:DD:EE:FF", rssi = -60)
        viewModel.connectToVehicle(testDevice)
        advanceUntilIdle()

        assertEquals(ScannerUiState.Connecting("AA:BB:CC:DD:EE:FF"), viewModel.uiState.value)
        assertEquals(testDevice, fakeRepository.connectedVehicle)
        collectJob.cancel()
    }
}
