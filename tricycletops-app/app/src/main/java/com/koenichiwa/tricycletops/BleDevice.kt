package com.koenichiwa.tricycletops

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val rawDevice: BluetoothDevice,
)
