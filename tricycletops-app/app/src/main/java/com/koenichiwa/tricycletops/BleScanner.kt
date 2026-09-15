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
                when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> TODO()
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> TODO()
                    SCAN_FAILED_INTERNAL_ERROR -> TODO()
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> TODO()
                    else -> TODO()
                }
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

        if (!bluetoothAdapter.isEnabled) {
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

        bluetoothLeScanner?.startScan(filters, settings, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        if (!isScanning.value) {
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            _isScanning.value = false
            return
        }
        bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
    }

    fun clearDevices() {
        _discoveredDevices.value = emptyList()
    }
}
