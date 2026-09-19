package com.koenichiwa.tricycletops

import android.Manifest
import android.annotation.SuppressLint
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koenichiwa.tricycletops.bluetooth.BleState
import com.koenichiwa.tricycletops.bluetooth.BluetoothStatePublisher
import com.koenichiwa.tricycletops.domain.Device
import com.koenichiwa.tricycletops.domain.DevicePublisher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

class ScannerViewModel(
    val statePublisher: BluetoothStatePublisher,
    private val devicePublisher: DevicePublisher,
) : ViewModel() {
    class Factory(
        private val statePublisher: BluetoothStatePublisher,
        private val devicePublisher: DevicePublisher,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ScannerViewModel(statePublisher, devicePublisher) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun observeDevices() =
        devicePublisher
            .observe()
            .scan(emptyMap<String, Device>()) { devices, device ->
                devices + (device.id to device)
            }.map {
                it.values.toSortedSet { left, right -> left.id.compareTo(right.id) }
            }.onStart {
                _bleState.value = BleState.Scanning
            }.catch { error ->
                _bleState.value = BleState.Error(error)
            }

    val enabled = statePublisher.observeEnabled()
    val permitted = statePublisher.observePermissions()

    @OptIn(ExperimentalCoroutinesApi::class)
    val deviceState: StateFlow<Set<Device>> =
        enabled
            .combine(permitted) { enabled, permitted ->
                enabled to permitted
            }.flatMapLatest { (enabled, permitted) ->
                if (!enabled) {
                    _bleState.value = BleState.Off
                    flowOf(emptySet())
                } else if (!permitted) {
                    _bleState.value = BleState.NoPermission
                    flowOf(emptySet())
                } else {
                    @SuppressLint("MissingPermission")
                    observeDevices()
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = emptySet(),
            )

    fun updatePermissions() = statePublisher.triggerPermissionUpdate()

    private val _bleState = MutableStateFlow<BleState>(BleState.Off)
    val bleState = _bleState.asStateFlow()
}
