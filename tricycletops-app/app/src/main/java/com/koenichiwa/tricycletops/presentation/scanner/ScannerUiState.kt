package com.koenichiwa.tricycletops.presentation.scanner

import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle

sealed interface ScannerUiState {
    data object MissingPermissions : ScannerUiState

    data object BluetoothDisabled : ScannerUiState

    data class Idle(
        val devices: List<DiscoveredVehicle> = emptyList(),
    ) : ScannerUiState

    data class Scanning(
        val devices: List<DiscoveredVehicle> = emptyList(),
    ) : ScannerUiState

    data class Connecting(
        val vehicleAddress: String,
    ) : ScannerUiState
}
