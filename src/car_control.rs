use crate::motor_control::MotorControl;

pub struct CarControl<'a> {
    left_motor: MotorControl<'a>,
    right_motor: MotorControl<'a>,
}

impl<'a> CarControl<'a> {
    pub const fn new(left_motor: MotorControl<'a>, right_motor: MotorControl<'a>) -> Self {
        Self {
            left_motor,
            right_motor,
        }
    }

    /// Set individual speeds for left and right motors.
    pub fn set_speeds(&mut self, left_speed: f32, right_speed: f32) {
        let left_percent =  (left_speed * 100f32) as i8;
        let right_percent = (right_speed * 100f32) as i8;

        self.left_motor.set_speed(left_percent);
        self.right_motor.set_speed(right_percent);
    }

    /// Stop the car immediately (both motors set to 0% speed).
    pub fn stop(&mut self) {
        self.set_speeds(0f32, 0f32);
    }
}
