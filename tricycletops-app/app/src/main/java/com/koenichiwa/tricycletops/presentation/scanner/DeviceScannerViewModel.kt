package com.koenichiwa.tricycletops.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle
import com.koenichiwa.tricycletops.domain.model.VehicleConnectionState
import com.koenichiwa.tricycletops.domain.usecase.ConnectVehicleUseCase
import com.koenichiwa.tricycletops.domain.usecase.DiscoverVehiclesUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DeviceScannerViewModel(
    private val discoverVehiclesUseCase: DiscoverVehiclesUseCase,
    private val connectVehicleUseCase: ConnectVehicleUseCase,
) : ViewModel() {
    val discoveredDevices: StateFlow<List<DiscoveredVehicle>> = discoverVehiclesUseCase.vehicles
    val isScanning: StateFlow<Boolean> = discoverVehiclesUseCase.isScanning
    val connectionState: StateFlow<VehicleConnectionState> = connectVehicleUseCase.connectionState

    fun toggleScan() {
        if (isScanning.value) {
            discoverVehiclesUseCase.stopScan()
        } else {
            discoverVehiclesUseCase.startScan()
        }
    }

    fun startScan() {
        discoverVehiclesUseCase.startScan()
    }

    fun stopScan() {
        discoverVehiclesUseCase.stopScan()
    }

    fun connectToVehicle(vehicle: DiscoveredVehicle) {
        stopScan()
        viewModelScope.launch {
            connectVehicleUseCase(vehicle)
        }
    }

    companion object {
        fun provideFactory(
            discoverVehiclesUseCase: DiscoverVehiclesUseCase,
            connectVehicleUseCase: ConnectVehicleUseCase,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DeviceScannerViewModel(discoverVehiclesUseCase, connectVehicleUseCase) as T
                }
            }
    }
}
