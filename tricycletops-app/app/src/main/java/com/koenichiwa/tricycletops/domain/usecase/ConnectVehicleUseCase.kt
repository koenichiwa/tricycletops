package com.koenichiwa.tricycletops.domain.usecase

import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle
import com.koenichiwa.tricycletops.domain.model.VehicleConnectionState
import com.koenichiwa.tricycletops.domain.repository.VehicleControlRepository
import kotlinx.coroutines.flow.StateFlow

class ConnectVehicleUseCase(
    private val repository: VehicleControlRepository,
) {
    val connectionState: StateFlow<VehicleConnectionState>
        get() = repository.connectionState

    suspend operator fun invoke(vehicle: DiscoveredVehicle) {
        repository.connect(vehicle)
    }

    suspend fun disconnect() {
        repository.disconnect()
    }
}
