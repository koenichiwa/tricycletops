package com.koenichiwa.tricycletops

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel

class ConnectedViewModel(application: Application) : AndroidViewModel(application) {
    private val bleGattClient = BleGattClient(application.applicationContext)

    val connectionState = bleGattClient.connectionState
    val connectedDevice = bleGattClient.connectedDevice
    val leftMaxDuty = bleGattClient.leftMaxDuty
    val rightMaxDuty = bleGattClient.rightMaxDuty

    fun connectToDevice(address: String, name: String?) {
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null) {
            Log.e("ConnectedViewModel", "BluetoothAdapter is null - cannot connect")
            return
        }

        try {
            val rawDevice = bluetoothAdapter.getRemoteDevice(address)
            val bleDevice = BleDevice(
                address = address,
                name = name ?: "TricycleTops",
                rssi = 0,
                rawDevice = rawDevice
            )
            bleGattClient.connect(bleDevice)
        } catch (e: Exception) {
            Log.e("ConnectedViewModel", "Error creating BleDevice or connecting: ${e.localizedMessage}")
        }
    }

    fun sendDriveCommand(leftMotorSpeed: Int, rightMotorSpeed: Int) {
        bleGattClient.sendDriveCommand(leftMotorSpeed, rightMotorSpeed)
    }

    fun disconnect() {
        bleGattClient.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        bleGattClient.disconnect()
    }
}
