package com.koenichiwa.tricycletops.domain.usecase

import com.koenichiwa.tricycletops.domain.model.JoystickVector
import com.koenichiwa.tricycletops.domain.model.MotorDriveCommand
import com.koenichiwa.tricycletops.domain.model.VehicleDutyLimits
import com.koenichiwa.tricycletops.domain.model.WheelDutyLimits
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

class ComputeDifferentialDriveUseCase {
    operator fun invoke(
        joystick: JoystickVector,
        limits: VehicleDutyLimits = VehicleDutyLimits.DEFAULT,
    ): MotorDriveCommand {
        if (joystick.isZero) {
            return MotorDriveCommand.STOP
        }

        val clampedX = joystick.x.coerceIn(-1.0f, 1.0f)
        val clampedY = joystick.y.coerceIn(-1.0f, 1.0f)

        val v = clampedY
        val omega = clampedX

        val rawLeft = v + omega
        val rawRight = v - omega

        val maxMag = max(1.0f, max(abs(rawLeft), abs(rawRight)))
        val leftSpeed = rawLeft / maxMag
        val rightSpeed = rawRight / maxMag

        val leftDuty = mapSpeedToDuty(leftSpeed, limits.left)
        val rightDuty = mapSpeedToDuty(rightSpeed, limits.right)

        return MotorDriveCommand(leftDuty = leftDuty, rightDuty = rightDuty)
    }

    private fun mapSpeedToDuty(
        speed: Float,
        wheelLimits: WheelDutyLimits,
    ): Int {
        if (speed == 0.0f) return 0
        val magnitude = abs(speed)
        val dutyMagnitude = wheelLimits.minDuty + magnitude * (wheelLimits.maxDuty - wheelLimits.minDuty)
        val signedDuty = sign(speed) * dutyMagnitude.roundToInt()
        return signedDuty.toInt()
    }
}
