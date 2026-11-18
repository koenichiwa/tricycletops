package com.koenichiwa.tricycletops.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle
import com.koenichiwa.tricycletops.domain.model.VehicleConnectionState
import com.koenichiwa.tricycletops.domain.usecase.ConnectVehicleUseCase
import com.koenichiwa.tricycletops.domain.usecase.DiscoverVehiclesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeviceScannerViewModel(
    private val discoverVehiclesUseCase: DiscoverVehiclesUseCase,
    private val connectVehicleUseCase: ConnectVehicleUseCase,
) : ViewModel() {
    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    val connectionState: StateFlow<VehicleConnectionState> = connectVehicleUseCase.connectionState

    val uiState: StateFlow<ScannerUiState> =
        combine(
            _hasPermissions,
            discoverVehiclesUseCase.isBluetoothEnabled,
            discoverVehiclesUseCase.isScanning,
            discoverVehiclesUseCase.vehicles,
            connectVehicleUseCase.connectionState,
        ) { hasPerms, isBtEnabled, scanning, vehicles, connState ->
            when {
                !hasPerms -> ScannerUiState.MissingPermissions
                !isBtEnabled -> ScannerUiState.BluetoothDisabled
                connState is VehicleConnectionState.Connecting -> ScannerUiState.Connecting(connState.deviceAddress)
                scanning -> ScannerUiState.Scanning(vehicles)
                else -> ScannerUiState.Idle(vehicles)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ScannerUiState.MissingPermissions,
        )

    init {
        viewModelScope.launch {
            combine(_hasPermissions, discoverVehiclesUseCase.isBluetoothEnabled) { hasPerms, isBtEnabled ->
                hasPerms && isBtEnabled
            }.collect { canScan ->
                if (canScan &&
                    !discoverVehiclesUseCase.isScanning.value &&
                    (connectVehicleUseCase.connectionState.value is VehicleConnectionState.Disconnected ||
                        connectVehicleUseCase.connectionState.value is VehicleConnectionState.Error)
                ) {
                    discoverVehiclesUseCase.startScan()
                }
            }
        }
    }

    fun onPermissionStatusChanged(isGranted: Boolean) {
        _hasPermissions.value = isGranted
    }

    fun toggleScan() {
        if (_hasPermissions.value && discoverVehiclesUseCase.isBluetoothEnabled.value) {
            if (discoverVehiclesUseCase.isScanning.value) {
                discoverVehiclesUseCase.stopScan()
            } else {
                discoverVehiclesUseCase.startScan()
            }
        }
    }

    fun startScan() {
        if (_hasPermissions.value && discoverVehiclesUseCase.isBluetoothEnabled.value) {
            discoverVehiclesUseCase.startScan()
        }
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
