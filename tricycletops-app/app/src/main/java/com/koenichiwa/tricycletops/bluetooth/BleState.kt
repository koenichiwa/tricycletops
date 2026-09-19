package com.koenichiwa.tricycletops.bluetooth

sealed interface BleState {
    data object Ready : BleState

    data object NoPermission : BleState

    data object Scanning : BleState

    data object Off : BleState

    data class Error(
        val cause: Throwable,
    ) : BleState
}
