package com.koenichiwa.tricycletops.data.repository

import com.koenichiwa.tricycletops.data.ble.BleGattDataSource
import com.koenichiwa.tricycletops.data.ble.BleScannerDataSource
import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle
import com.koenichiwa.tricycletops.domain.model.JoystickVector
import com.koenichiwa.tricycletops.domain.model.MotorDriveCommand
import com.koenichiwa.tricycletops.domain.model.VehicleConnectionState
import com.koenichiwa.tricycletops.domain.model.VehicleDutyLimits
import com.koenichiwa.tricycletops.domain.repository.VehicleControlRepository
import com.koenichiwa.tricycletops.domain.usecase.ComputeDifferentialDriveUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VehicleControlRepositoryImpl(
    private val scanner: BleScannerDataSource,
    private val gattClient: BleGattDataSource,
    private val computeDriveUseCase: ComputeDifferentialDriveUseCase = ComputeDifferentialDriveUseCase(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val throttleIntervalMs: Long = 40L,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) : VehicleControlRepository {
    override val discoveredVehicles: StateFlow<List<DiscoveredVehicle>> = scanner.discoveredDevices
    override val isScanning: StateFlow<Boolean> = scanner.isScanning
    override val isBluetoothEnabled: StateFlow<Boolean> = scanner.isBluetoothEnabled
    override val connectionState: StateFlow<VehicleConnectionState> = gattClient.connectionState

    private var lastSentTime = 0L
    private var activeVector: JoystickVector? = null
    private var streamJob: Job? = null
    private val throttleMutex = Mutex()

    override fun startScan() {
        scanner.startScan()
    }

    override fun stopScan() {
        scanner.stopScan()
    }

    override suspend fun connect(vehicle: DiscoveredVehicle) {
        gattClient.connect(vehicle)
    }

    override suspend fun disconnect() {
        stopImmediate()
        gattClient.disconnect()
    }

    override suspend fun drive(joystick: JoystickVector) {
        if (joystick.isZero) {
            stopImmediate()
            return
        }

        throttleMutex.withLock {
            activeVector = joystick
            if (streamJob == null || !streamJob!!.isActive) {
                sendDriveCommand(joystick)
                lastSentTime = timeProvider()
                streamJob =
                    scope.launch {
                        while (isActive) {
                            delay(throttleIntervalMs)
                            throttleMutex.withLock {
                                val vectorToSend = activeVector
                                if (isActive && vectorToSend != null && !vectorToSend.isZero) {
                                    sendDriveCommand(vectorToSend)
                                    lastSentTime = timeProvider()
                                }
                            }
                        }
                    }
            }
        }
    }

    override suspend fun stopImmediate() {
        throttleMutex.withLock {
            streamJob?.cancel()
            streamJob = null
            activeVector = null
            gattClient.writeDriveCommand(MotorDriveCommand.STOP)
            lastSentTime = timeProvider()
        }
    }

    private fun sendDriveCommand(joystick: JoystickVector) {
        val limits =
            (connectionState.value as? VehicleConnectionState.Connected)?.dutyLimits
                ?: VehicleDutyLimits.DEFAULT
        val command = computeDriveUseCase(joystick, limits)
        gattClient.writeDriveCommand(command)
    }
}
