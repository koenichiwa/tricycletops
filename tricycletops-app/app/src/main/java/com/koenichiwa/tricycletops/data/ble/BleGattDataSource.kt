package com.koenichiwa.tricycletops.data.ble

import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle
import com.koenichiwa.tricycletops.domain.model.MotorDriveCommand
import com.koenichiwa.tricycletops.domain.model.VehicleConnectionState
import kotlinx.coroutines.flow.StateFlow

interface BleGattDataSource {
    val connectionState: StateFlow<VehicleConnectionState>

    fun connect(vehicle: DiscoveredVehicle)

    fun disconnect()

    fun writeDriveCommand(command: MotorDriveCommand): Boolean
}
