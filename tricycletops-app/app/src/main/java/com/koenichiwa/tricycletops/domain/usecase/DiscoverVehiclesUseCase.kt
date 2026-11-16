package com.koenichiwa.tricycletops.domain.usecase

import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle
import com.koenichiwa.tricycletops.domain.repository.VehicleControlRepository
import kotlinx.coroutines.flow.StateFlow

class DiscoverVehiclesUseCase(
    private val repository: VehicleControlRepository,
) {
    val vehicles: StateFlow<List<DiscoveredVehicle>>
        get() = repository.discoveredVehicles

    val isScanning: StateFlow<Boolean>
        get() = repository.isScanning

    fun startScan() {
        repository.startScan()
    }

    fun stopScan() {
        repository.stopScan()
    }
}
