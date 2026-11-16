package com.koenichiwa.tricycletops.presentation.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koenichiwa.tricycletops.domain.model.JoystickVector
import com.koenichiwa.tricycletops.domain.model.VehicleConnectionState
import com.koenichiwa.tricycletops.domain.usecase.ConnectVehicleUseCase
import com.koenichiwa.tricycletops.domain.usecase.DriveVehicleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VehicleControlViewModel(
    private val connectVehicleUseCase: ConnectVehicleUseCase,
    private val driveVehicleUseCase: DriveVehicleUseCase,
) : ViewModel() {
    val connectionState: StateFlow<VehicleConnectionState> = connectVehicleUseCase.connectionState

    private val _joystickVector = MutableStateFlow(JoystickVector.ZERO)
    val joystickVector: StateFlow<JoystickVector> = _joystickVector.asStateFlow()

    fun onJoystickMoved(vector: JoystickVector) {
        _joystickVector.value = vector
        viewModelScope.launch {
            driveVehicleUseCase(vector)
        }
    }

    fun failsafeStopAndDisconnect() {
        _joystickVector.value = JoystickVector.ZERO
        viewModelScope.launch {
            driveVehicleUseCase.stopImmediate()
            connectVehicleUseCase.disconnect()
        }
    }

    fun disconnect() {
        failsafeStopAndDisconnect()
    }

    companion object {
        fun provideFactory(
            connectVehicleUseCase: ConnectVehicleUseCase,
            driveVehicleUseCase: DriveVehicleUseCase,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return VehicleControlViewModel(connectVehicleUseCase, driveVehicleUseCase) as T
                }
            }
    }
}
