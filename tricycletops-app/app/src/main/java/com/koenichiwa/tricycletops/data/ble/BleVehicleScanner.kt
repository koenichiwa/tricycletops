package com.koenichiwa.tricycletops.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BleVehicleScanner(
    private val context: Context,
) : BleScannerDataSource {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter get() = bluetoothManager?.adapter

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredVehicle>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredVehicle>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(adapter?.isEnabled == true)
    override val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val bluetoothReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val isEnabled = state == BluetoothAdapter.STATE_ON
                    _isBluetoothEnabled.value = isEnabled
                    if (!isEnabled) {
                        _isScanning.value = false
                    }
                }
            }
        }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context.applicationContext,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private val scanCallback =
        object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult?,
            ) {
                result?.let { handleScanResult(it) }
            }

            @SuppressLint("MissingPermission")
            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                _isScanning.value = false
            }
        }

    fun hasPermissions(): Boolean {
        val hasScan = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN,
        ) == PackageManager.PERMISSION_GRANTED
        val hasConnect = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        return hasScan && hasConnect
    }

    fun isBluetoothEnabled(): Boolean {
        return _isBluetoothEnabled.value
    }

    @SuppressLint("MissingPermission")
    override fun startScan(): Boolean {
        if (!hasPermissions() || !isBluetoothEnabled()) {
            _isScanning.value = false
            return false
        }

        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _isScanning.value = false
            return false
        }

        if (_isScanning.value) {
            return true
        }

        val settings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        return try {
            scanner.startScan(null, settings, scanCallback)
            _isScanning.value = true
            true
        } catch (_: SecurityException) {
            _isScanning.value = false
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        if (!_isScanning.value) return

        if (hasPermissions()) {
            try {
                adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (_: SecurityException) {
                // Ignore security exceptions on stop
            }
        }
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val scanRecord = result.scanRecord
        val rawName =
            try {
                device.name ?: scanRecord?.deviceName
            } catch (_: SecurityException) {
                scanRecord?.deviceName
            }

        val matchesName = rawName?.contains(BleGattConstants.DEVICE_NAME_PREFIX, ignoreCase = true) == true
        val matchesService =
            scanRecord?.serviceUuids?.contains(
                ParcelUuid(BleGattConstants.SERVICE_UUID),
            ) == true

        if (!matchesName && !matchesService) {
            return
        }

        val displayName = rawName ?: BleGattConstants.DEVICE_NAME_PREFIX
        val address = device.address ?: return
        val rssi = result.rssi

        _discoveredDevices.update { currentList ->
            val existingIndex = currentList.indexOfFirst { it.address == address }
            if (existingIndex >= 0) {
                currentList.mapIndexed { index, existing ->
                    if (index == existingIndex) {
                        existing.copy(rssi = rssi, name = existing.name ?: displayName)
                    } else {
                        existing
                    }
                }
            } else {
                currentList + DiscoveredVehicle(name = displayName, address = address, rssi = rssi)
            }
        }
    }
}
