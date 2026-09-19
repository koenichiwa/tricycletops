package com.koenichiwa.tricycletops.domain

import kotlinx.coroutines.flow.Flow

interface Device {
    val id: String
}

interface DevicePublisher {
    fun observe(): Flow<Device>
}
