package com.koenichiwa.tricycletops

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Disconnecting,
}

@SuppressLint("MissingPermission")
class BleScannerManager(
    private val context: Context,
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
    val connectedDevice: StateFlow<BleDevice?> = _connectedDevice.asStateFlow()

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
            return
        }

        _isBluetoothEnabled.value = adapter.isEnabled
        if (!adapter.isEnabled) {
            _errorMessage.value = "Bluetooth is turned off"
            return
        }
        _errorMessage.value = null
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
        val device = result.device
        val address = device.address
        val name = result.scanRecord?.deviceName ?: device.name
        val rssi = result.rssi

        // Filter: only show devices with a name starting with "Tricycletops"
        val startsWithName = name?.startsWith("Tricycletops", ignoreCase = true) ?: false
        if (!startsWithName) {
            return
        }

        // Check if this is our Tricycletops peripheral (we know it starts with Tricycletops, so it matches)
        val isTricycletops = true

        val currentList = _discoveredDevices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.address == address }

        val bleDevice =
            BleDevice(
                address = address,
                name = name,
                rssi = rssi,
                isTricycletops = isTricycletops,
                rawDevice = device,
            )

        if (existingIndex >= 0) {
            currentList[existingIndex] = bleDevice
        } else {
            currentList.add(bleDevice)
        }

        // Sort: Tricycletops first, then by RSSI (strongest signal first)
        _discoveredDevices.value =
            currentList.sortedWith(
                compareByDescending<BleDevice> { it.isTricycletops }
                    .thenByDescending { it.rssi },
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
        val scanGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED

        val connectGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED

        return scanGranted && connectGranted
    }

    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _connectionState.value = ConnectionState.Disconnected
                    _connectedDevice.value = null
                    _errorMessage.value = "GATT connection failed (status: $status)"
                    gatt.close()
                    bluetoothGatt = null
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _connectionState.value = ConnectionState.Connected
                        gatt.discoverServices()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        _connectionState.value = ConnectionState.Disconnected
                        _connectedDevice.value = null
                        gatt.close()
                        bluetoothGatt = null
                    }
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(java.util.UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"))
                    if (service != null) {
                        val characteristic = service.getCharacteristic(java.util.UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"))
                        if (characteristic == null) {
                            _errorMessage.value = "Tricycletops drive characteristic not found"
                        }
                    } else {
                        _errorMessage.value = "Tricycletops service not found"
                    }
                } else {
                    _errorMessage.value = "Failed to discover GATT services: $status"
                }
            }
        }

    fun connect(device: BleDevice) {
        if (!hasPermissions()) {
            _errorMessage.value = "Permissions missing for BLE connection"
            return
        }

        stopScanning()

        _connectedDevice.value = device
        _connectionState.value = ConnectionState.Connecting

        try {
            bluetoothGatt = device.rawDevice.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            _errorMessage.value = "Connection failed: ${e.localizedMessage}"
            _connectionState.value = ConnectionState.Disconnected
            _connectedDevice.value = null
        }
    }

    fun disconnect() {
        if (!hasPermissions()) return

        _connectionState.value = ConnectionState.Disconnecting
        bluetoothGatt?.disconnect()
    }

    fun sendDriveCommand(
        left: Float,
        right: Float,
    ) {
        if (!hasPermissions() || connectionState.value != ConnectionState.Connected) {
            return
        }

        val gatt = bluetoothGatt ?: return
        val serviceUuid = java.util.UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val charUuid = java.util.UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

        val service = gatt.getService(serviceUuid)
        val characteristic = service?.getCharacteristic(charUuid)

        if (characteristic != null) {
            val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putFloat(left)
            buffer.putFloat(right)
            val payload = buffer.array()

            try {
                gatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                )
            } catch (e: Exception) {
                android.util.Log.e("BleScannerManager", "Failed to write characteristic: ${e.localizedMessage}")
            }
        }
    }
}
