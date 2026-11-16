package com.koenichiwa.tricycletops.data.ble

import java.util.UUID

object BleGattConstants {
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val DRIVE_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    val DUTY_LIMITS_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

    const val DEVICE_NAME_PREFIX = "Tricycletops"
}
