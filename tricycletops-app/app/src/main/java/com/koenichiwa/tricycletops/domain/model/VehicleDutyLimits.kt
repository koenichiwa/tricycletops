package com.koenichiwa.tricycletops.domain.model

data class VehicleDutyLimits(
    val left: WheelDutyLimits,
    val right: WheelDutyLimits,
) {
    companion object {
        val DEFAULT =
            VehicleDutyLimits(
                left = WheelDutyLimits(minDuty = 0, maxDuty = 1000),
                right = WheelDutyLimits(minDuty = 0, maxDuty = 1000),
            )
    }
}
