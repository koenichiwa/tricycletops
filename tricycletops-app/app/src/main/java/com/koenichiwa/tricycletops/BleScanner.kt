package com.koenichiwa.tricycletops

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val SCAN_PERIOD: Long = 10000 // Scan for 10 seconds

class BleScanner(
    private val context: Context,
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    init {
        checkBluetoothState()
    }

    fun checkBluetoothState() {
        val enabled = context.isBleEnabled()
        _isBluetoothEnabled.value = enabled
        if (bluetoothAdapter == null) {
            _errorMessage.value = "Bluetooth is not supported on this device"
        } else if (!enabled) {
            _errorMessage.value = "Bluetooth is turned off"
        } else {
            _errorMessage.value = null
        }
    }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                addScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (result in results) {
                    addScanResult(result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val message =
                    when (errorCode) {
                        SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                        SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application registration failed"
                        SCAN_FAILED_INTERNAL_ERROR -> "Internal Bluetooth error"
                        SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE Scan feature unsupported"
                        else -> "Scan failed with error code $errorCode"
                    }
                _errorMessage.value = message
                _isScanning.value = false
            }
        }

    private fun addScanResult(result: ScanResult) {
        // Safe check for permissions before reading device details
        if (!context.hasBlePermissions()) return

        val device = result.device
        val address = device.address
        val name =
            try {
                result.scanRecord?.deviceName ?: device.name
            } catch (e: SecurityException) {
                null
            }
        val rssi = result.rssi

        // Filter: only show devices with a name starting with "Tricycletops"
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

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!context.hasBlePermissions()) {
            _errorMessage.value = "Bluetooth permissions are missing"
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !context.isBleEnabled()) {
            _errorMessage.value = "Bluetooth is not enabled"
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _errorMessage.value = "Bluetooth LE Scanner not available"
            return
        }

        if (_isScanning.value) return

        _errorMessage.value = null
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

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            _errorMessage.value = "Failed to start scanning: ${e.localizedMessage}"
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        val adapter = bluetoothAdapter
        if (adapter != null && context.isBleEnabled()) {
            val scanner = adapter.bluetoothLeScanner
            if (scanner != null && _isScanning.value) {
                try {
                    scanner.stopScan(scanCallback)
                } catch (e: Exception) {
                    // Ignore or log
                }
            }
        }
        _isScanning.value = false
    }

    fun clearDevices() {
        _discoveredDevices.value = emptyList()
    }
}
