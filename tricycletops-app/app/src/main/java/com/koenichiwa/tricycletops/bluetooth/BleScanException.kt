package com.koenichiwa.tricycletops.bluetooth

class BleScanException(
    errorCode: Int,
) : RuntimeException("BLE scan failed: $errorCode")
