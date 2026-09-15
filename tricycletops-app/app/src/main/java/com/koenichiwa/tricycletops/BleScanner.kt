package com.koenichiwa.tricycletops

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val SCAN_PERIOD: Long = 10000 // Scan for 10 seconds

class BleScanner(
    private val bluetoothAdapter: BluetoothAdapter,
) {
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())

    private val scanCallback =
        object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                addScanResult(result)
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (result in results) {
                    addScanResult(result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BleScanner", "BLE Scan failed with error code: $errorCode")
                _isScanning.value = false
            }
        }

    private fun addScanResult(result: ScanResult) {
        val device = result.device
        val address = device.address
        val name = result.scanRecord?.deviceName

        val rssi = result.rssi

        val startsWithName = name?.startsWith("Tricycletops", ignoreCase = true) ?: false
        if (!startsWithName) {
            return
        }

        val currentList = _discoveredDevices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.address == address }

        val bleDevice =
            BleDevice(
                address = address,
                name = name,
                rssi = rssi,
                rawDevice = device,
            )

        if (existingIndex >= 0) {
            currentList[existingIndex] = bleDevice
        } else {
            currentList.add(bleDevice)
        }

        // Sort: RSSI (strongest signal first)
        _discoveredDevices.value = currentList.sortedByDescending { it.rssi }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning() {
        if (isScanning.value) return

        val scanner = bluetoothLeScanner
        if (scanner == null || !bluetoothAdapter.isEnabled) {
            _isScanning.value = false
            return
        }

        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        val filters = mutableListOf<ScanFilter>()
        val settings =
            ScanSettings
                .Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        handler.postDelayed({
            if (_isScanning.value) {
                stopScanning()
            }
        }, SCAN_PERIOD)

        scanner.startScan(filters, settings, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        if (!isScanning.value) {
            return
        }
        _isScanning.value = false
        if (bluetoothAdapter.isEnabled) {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.e("BleScanner", "Failed to stop BLE scan safely", e)
            }
        }
    }

    fun clearDevices() {
        _discoveredDevices.value = emptyList()
    }
}
