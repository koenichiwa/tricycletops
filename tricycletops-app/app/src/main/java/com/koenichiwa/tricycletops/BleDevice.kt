package com.koenichiwa.tricycletops

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val isTricycletops: Boolean,
    val lastSeen: Long = System.currentTimeMillis(),
    val rawDevice: BluetoothDevice,
)
