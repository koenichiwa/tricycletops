package com.koenichiwa.tricycletops

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ScannerState {
    data object Initializing : ScannerState

    data object PermissionRequired : ScannerState

    data object BluetoothDisabled : ScannerState

    data object Ready : ScannerState

    data object Scanning : ScannerState

    data class Error(
        val message: String,
    ) : ScannerState
}

class ScannerViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val bleScanner = bluetoothAdapter?.let { BleScanner(it) }
    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()
    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()
    val isScanning = bleScanner?.isScanning ?: MutableStateFlow(false).asStateFlow()
    val discoveredDevices = bleScanner?.discoveredDevices ?: MutableStateFlow<List<BleDevice>>(emptyList()).asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val bluetoothReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val action = intent.action
                if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_ON -> {
                            if (bluetoothAdapter?.bluetoothLeScanner == null) {
                                return
                            }
                            _errorMessage.value = null

                            _isBluetoothEnabled.value = true
                        }

                        BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                            _isBluetoothEnabled.value = false
                            _errorMessage.value = "Bluetooth is turned off"
                            // Auto-stop scanning if Bluetooth is turned off
                            stopScanning()
                        }
                    }
                }
            }
        }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        application.registerReceiver(bluetoothReceiver, filter)
        checkBluetoothState()
        updatePermissionsState()
    }

    private fun checkBluetoothState() {
        if (bluetoothAdapter == null) {
            _isBluetoothEnabled.value = false
            _errorMessage.value = "Bluetooth is not supported on this device"
            return
        }

        val scanner: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            _isBluetoothEnabled.value = false
            _errorMessage.value = "Bluetooth LE Scanner not available"
            return
        }

        val enabled = bluetoothAdapter.isEnabled
        _isBluetoothEnabled.value = enabled
        if (!enabled) {
            _errorMessage.value = "Bluetooth is turned off"
        }
    }

    fun updatePermissionsState() {
        val scanGranted =
            ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        Log.i(javaClass.name, scanGranted.toString())
        _hasPermissions.value = scanGranted
    }

    fun startScanning() {
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            _hasPermissions.value = false
            return
        }
        bleScanner?.startScanning()
    }

    fun stopScanning() {
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            _hasPermissions.value = false
            return
        }
        bleScanner?.stopScanning()
    }

    fun clearDevices() {
        bleScanner?.clearDevices()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            // Ignore if receiver was not registered
        }
        try {
            bleScanner?.stopScanning()
        } catch (e: SecurityException) {
            // Ignore missing permission during teardown
        }
    }
}
