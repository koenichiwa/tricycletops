package com.koenichiwa.tricycletops.domain.repository

import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle
import com.koenichiwa.tricycletops.domain.model.JoystickVector
import com.koenichiwa.tricycletops.domain.model.VehicleConnectionState
import kotlinx.coroutines.flow.StateFlow

interface VehicleControlRepository {
    val discoveredVehicles: StateFlow<List<DiscoveredVehicle>>
    val isScanning: StateFlow<Boolean>
    val isBluetoothEnabled: StateFlow<Boolean>
    val connectionState: StateFlow<VehicleConnectionState>

    fun startScan()

    fun stopScan()

    suspend fun connect(vehicle: DiscoveredVehicle)

    suspend fun disconnect()

    suspend fun drive(joystick: JoystickVector)

    suspend fun stopImmediate()
}
