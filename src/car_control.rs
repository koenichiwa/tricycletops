use embassy_rp::pwm::PwmError;

use crate::motor_control::MotorControl;

#[derive(Clone, Copy, defmt::Format)]
pub struct DriveCommand {
    pub left: f32,
    pub right: f32,
}

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
    /// Inputs should be floats between -1.0 and 1.0.
    pub fn set_speeds(&mut self, left_speed: f32, right_speed: f32) -> Result<(), PwmError> {
        let left_speed = left_speed.clamp(-1.0, 1.0);
        let right_speed = right_speed.clamp(-1.0, 1.0);

        self.left_motor.set_speed(left_speed)?;
        self.right_motor.set_speed(right_speed)?;

        Ok(())
    }
}
