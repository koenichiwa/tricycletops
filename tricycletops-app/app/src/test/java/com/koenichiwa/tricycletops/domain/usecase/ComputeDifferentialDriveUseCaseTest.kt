package com.koenichiwa.tricycletops.domain.usecase

import com.koenichiwa.tricycletops.domain.model.JoystickVector
import com.koenichiwa.tricycletops.domain.model.VehicleDutyLimits
import com.koenichiwa.tricycletops.domain.model.WheelDutyLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ComputeDifferentialDriveUseCaseTest {

    private lateinit var useCase: ComputeDifferentialDriveUseCase

    private val defaultLimits =
        VehicleDutyLimits(
            left = WheelDutyLimits(minDuty = 0, maxDuty = 1000),
            right = WheelDutyLimits(minDuty = 0, maxDuty = 1000),
        )

    private val deadbandLimits =
        VehicleDutyLimits(
            left = WheelDutyLimits(minDuty = 200, maxDuty = 1000),
            right = WheelDutyLimits(minDuty = 200, maxDuty = 1000),
        )

    @Before
    fun setUp() {
        useCase = ComputeDifferentialDriveUseCase()
    }

    @Test
    fun `neutral joystick position returns stop command with zero duties`() {
        val result = useCase(JoystickVector.ZERO, defaultLimits)

        assertEquals(0, result.leftDuty)
        assertEquals(0, result.rightDuty)
        assertTrue(result.isStop)
    }

    @Test
    fun `neutral position returns zero even with min-duty deadband limits`() {
        val result = useCase(JoystickVector(0f, 0f), deadbandLimits)

        assertEquals(0, result.leftDuty)
        assertEquals(0, result.rightDuty)
        assertTrue(result.isStop)
    }

    @Test
    fun `full forward driving sets both wheels to positive max duty`() {
        val result = useCase(JoystickVector(x = 0f, y = 1f), defaultLimits)

        assertEquals(1000, result.leftDuty)
        assertEquals(1000, result.rightDuty)
    }

    @Test
    fun `full reverse driving sets both wheels to negative max duty`() {
        val result = useCase(JoystickVector(x = 0f, y = -1f), defaultLimits)

        assertEquals(-1000, result.leftDuty)
        assertEquals(-1000, result.rightDuty)
    }

    @Test
    fun `full pivot right turn rotates wheels in opposite directions`() {
        val result = useCase(JoystickVector(x = 1f, y = 0f), defaultLimits)

        assertEquals(1000, result.leftDuty)
        assertEquals(-1000, result.rightDuty)
    }

    @Test
    fun `full pivot left turn rotates wheels in opposite directions`() {
        val result = useCase(JoystickVector(x = -1f, y = 0f), defaultLimits)

        assertEquals(-1000, result.leftDuty)
        assertEquals(1000, result.rightDuty)
    }

    @Test
    fun `desaturation scaling on forward right preserves turn without exceeding max duty`() {
        val result = useCase(JoystickVector(x = 1f, y = 1f), defaultLimits)

        assertEquals(1000, result.leftDuty)
        assertEquals(0, result.rightDuty)
    }

    @Test
    fun `desaturation scaling on forward left preserves turn without exceeding max duty`() {
        val result = useCase(JoystickVector(x = -1f, y = 1f), defaultLimits)

        assertEquals(0, result.leftDuty)
        assertEquals(1000, result.rightDuty)
    }

    @Test
    fun `desaturation scaling on reverse right preserves turn without exceeding max duty`() {
        val result = useCase(JoystickVector(x = 1f, y = -1f), defaultLimits)

        assertEquals(0, result.leftDuty)
        assertEquals(-1000, result.rightDuty)
    }

    @Test
    fun `desaturation scaling on reverse left preserves turn without exceeding max duty`() {
        val result = useCase(JoystickVector(x = -1f, y = -1f), defaultLimits)

        assertEquals(-1000, result.leftDuty)
        assertEquals(0, result.rightDuty)
    }

    @Test
    fun `zero-speed wheel in turn produces exact zero duty even with deadband offset`() {
        val result = useCase(JoystickVector(x = 1f, y = 1f), deadbandLimits)

        assertEquals(1000, result.leftDuty)
        assertEquals(0, result.rightDuty)
    }

    @Test
    fun `deadband offset scales half-speed above minimum duty threshold`() {
        val result = useCase(JoystickVector(x = 0f, y = 0.5f), deadbandLimits)

        // minDuty(200) + 0.5 * (1000 - 200) = 600
        assertEquals(600, result.leftDuty)
        assertEquals(600, result.rightDuty)
    }

    @Test
    fun `deadband offset scales reverse speed above minimum duty threshold`() {
        val result = useCase(JoystickVector(x = 0f, y = -1f), deadbandLimits)

        assertEquals(-1000, result.leftDuty)
        assertEquals(-1000, result.rightDuty)
    }

    @Test
    fun `input values beyond unit circle are safely clamped`() {
        val result = useCase(JoystickVector(x = 2.5f, y = 0f), defaultLimits)

        assertEquals(1000, result.leftDuty)
        assertEquals(-1000, result.rightDuty)
    }
}
