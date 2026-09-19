package com.koenichiwa.tricycletops.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import androidx.annotation.RequiresPermission
import com.koenichiwa.tricycletops.domain.Device
import com.koenichiwa.tricycletops.domain.DevicePublisher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BleDevicePublisher(
    private val adapter: BluetoothAdapter,
) : DevicePublisher {
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun observe(): Flow<Device> =
        callbackFlow {
            val scanner = adapter.bluetoothLeScanner ?: return@callbackFlow

            val callback =
                object : ScanCallback() {
                    override fun onScanResult(
                        callbackType: Int,
                        result: ScanResult,
                    ) {
                        val device = BleDevice(result.device.address)
                        trySend(device)
                    }

                    override fun onBatchScanResults(results: List<ScanResult?>?) {
                        results?.filterNotNull()?.forEach { result ->
                            val device = BleDevice(result.device.address)
                            trySend(device)
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        close(BleScanException(errorCode))
                    }
                }
            scanner.startScan(callback)

            awaitClose {
                scanner.stopScan(callback)
            }
        }
}

data class BleDevice(
    override val id: String,
) : Device
