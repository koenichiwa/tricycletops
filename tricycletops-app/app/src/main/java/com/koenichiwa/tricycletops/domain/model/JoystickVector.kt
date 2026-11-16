package com.koenichiwa.tricycletops.domain.model

data class JoystickVector(
    val x: Float,
    val y: Float,
) {
    val isZero: Boolean
        get() = x == 0f && y == 0f

    companion object {
        val ZERO = JoystickVector(x = 0f, y = 0f)
    }
}
