package com.koenichiwa.tricycletops.domain.model

sealed interface VehicleConnectionState {
    data object Disconnected : VehicleConnectionState

    data class Connecting(
        val deviceAddress: String,
    ) : VehicleConnectionState

    data class Connected(
        val vehicle: DiscoveredVehicle,
        val dutyLimits: VehicleDutyLimits? = null,
    ) : VehicleConnectionState

    data class Disconnecting(
        val deviceAddress: String,
    ) : VehicleConnectionState

    data class Error(
        val message: String,
    ) : VehicleConnectionState
}
