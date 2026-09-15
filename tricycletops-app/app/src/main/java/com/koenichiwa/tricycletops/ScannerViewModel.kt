package com.koenichiwa.tricycletops

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val bleScanner = BleScanner(application.applicationContext)

    val isScanning = bleScanner.isScanning
    val discoveredDevices = bleScanner.discoveredDevices
    val errorMessage = bleScanner.errorMessage

    fun checkBluetoothState() {
        bleScanner.checkBluetoothState()
    }

    fun startScanning() {
        bleScanner.startScanning()
    }

    fun stopScanning() {
        bleScanner.stopScanning()
    }

    fun clearDevices() {
        bleScanner.clearDevices()
    }

    override fun onCleared() {
        super.onCleared()
        bleScanner.stopScanning()
    }
}
