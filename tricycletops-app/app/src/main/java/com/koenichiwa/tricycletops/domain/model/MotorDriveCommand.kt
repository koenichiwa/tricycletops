package com.koenichiwa.tricycletops.domain.model

data class MotorDriveCommand(
    val leftDuty: Int,
    val rightDuty: Int,
) {
    val isStop: Boolean
        get() = leftDuty == 0 && rightDuty == 0

    companion object {
        val STOP = MotorDriveCommand(leftDuty = 0, rightDuty = 0)
    }
}
