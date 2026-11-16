package com.koenichiwa.tricycletops.data.ble

import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle
import kotlinx.coroutines.flow.StateFlow

interface BleScannerDataSource {
    val discoveredDevices: StateFlow<List<DiscoveredVehicle>>
    val isScanning: StateFlow<Boolean>

    fun startScan(): Boolean

    fun stopScan()
}
