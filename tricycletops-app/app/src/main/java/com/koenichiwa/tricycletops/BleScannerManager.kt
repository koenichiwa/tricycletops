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
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
class BleScannerManager(private val context: Context) {

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
    private val SCAN_PERIOD: Long = 10000 // Scan for 10 seconds

    init {
        checkBluetoothState()
    }

    fun checkBluetoothState() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _isBluetoothEnabled.value = false
            _errorMessage.value = "Bluetooth is not supported on this device"
        } else {
            _isBluetoothEnabled.value = adapter.isEnabled
            if (!adapter.isEnabled) {
                _errorMessage.value = "Bluetooth is turned off"
            } else {
                _errorMessage.value = null
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                addScanResult(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val message = when (errorCode) {
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
        val device = result.device
        val address = device.address
        val name = result.scanRecord?.deviceName ?: device.name
        val rssi = result.rssi

        // Check if this is our Tricycletops peripheral
        val serviceUuids = result.scanRecord?.serviceUuids ?: emptyList()
        val targetUuid = ParcelUuid.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

        val matchesUuid = serviceUuids.contains(targetUuid)
        val matchesName = name?.contains("Tricycletops", ignoreCase = true) == true
        val isTricycletops = matchesUuid || matchesName

        val currentList = _discoveredDevices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.address == address }

        val bleDevice = BleDevice(
            address = address,
            name = name,
            rssi = rssi,
            isTricycletops = isTricycletops,
            rawDevice = device
        )

        if (existingIndex >= 0) {
            currentList[existingIndex] = bleDevice
        } else {
            currentList.add(bleDevice)
        }

        // Sort: Tricycletops first, then by RSSI (strongest signal first)
        _discoveredDevices.value = currentList.sortedWith(
            compareByDescending<BleDevice> { it.isTricycletops }
                .thenByDescending { it.rssi }
        )
    }

    fun startScanning() {
        if (!hasPermissions()) {
            _errorMessage.value = "Bluetooth permissions are missing"
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
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
        val settings = ScanSettings.Builder()
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

    fun stopScanning() {
        val adapter = bluetoothAdapter
        if (adapter != null && adapter.isEnabled) {
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

    fun hasPermissions(): Boolean {
        val scanGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

        val connectGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        return scanGranted && connectGranted
    }
}
