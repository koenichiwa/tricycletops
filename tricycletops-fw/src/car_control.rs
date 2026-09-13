use defmt::info;
use embassy_rp::{
    Peri,
    gpio::{Level, Output, Pin},
    pwm::{ChannelBPin, Config, Pwm, PwmError, Slice},
};
use embassy_sync::{blocking_mutex::raw::ThreadModeRawMutex, signal::Signal};

use crate::motor_control::MotorControl;

#[derive(Clone, Copy, defmt::Format)]
pub struct DriveCommand {
    pub left: i32,
    pub right: i32,
}

pub struct CarControl<'a> {
    left_motor: MotorControl<'a>,
    right_motor: MotorControl<'a>,
}

impl CarControl<'_> {
    pub fn new<SliceLeft, SpeedLeft, SliceRight, SpeedRight>(
        motor_left_pwm_slice: Peri<'static, SliceLeft>,
        motor_left_speed: Peri<'static, SpeedLeft>,
        motor_left_direction: Peri<'static, impl Pin>,
        motor_right_pwm_slice: Peri<'static, SliceRight>,
        motor_right_speed: Peri<'static, SpeedRight>,
        motor_right_direction: Peri<'static, impl Pin>,
    ) -> Self
    where
        SliceLeft: Slice,
        SpeedLeft: ChannelBPin<SliceLeft>,
        SliceRight: Slice,
        SpeedRight: ChannelBPin<SliceRight>,
    {
        let config = Config::default();

        let motor_left_speed =
            Pwm::new_output_b(motor_left_pwm_slice, motor_left_speed, config.clone());
        let motor_left_direction = Output::new(motor_left_direction, Level::Low);
        let left_motor = MotorControl::new(motor_left_speed, motor_left_direction);

        let motor_right_speed = Pwm::new_output_b(motor_right_pwm_slice, motor_right_speed, config);
        let motor_right_direction = Output::new(motor_right_direction, Level::Low);
        let right_motor = MotorControl::new(motor_right_speed, motor_right_direction);

        Self {
            left_motor,
            right_motor,
        }
    }

    pub fn max_duties(&self) -> (u16, u16) {
        (self.left_motor.max_duty(), self.right_motor.max_duty())
    }

    /// Set individual speeds for left and right motors.
    /// Inputs should be integers between -max_duty and max_duty.
    pub fn set_speeds(&mut self, left_speed: i32, right_speed: i32) -> Result<(), PwmError> {
        self.left_motor.set_speed(left_speed)?;
        self.right_motor.set_speed(right_speed)?;

        Ok(())
    }

    pub async fn run(
        &mut self,
        drive_command_signal: &'static Signal<ThreadModeRawMutex, DriveCommand>,
    ) -> Result<(), PwmError> {
        loop {
            let DriveCommand { left, right } = drive_command_signal.wait().await;
            info!(
                "Car control task: setting speeds left={}, right={}",
                left, right
            );
            if let Err(error) = self.set_speeds(left, right) {
                let _ = self.set_speeds(0, 0);
                return Err(error);
            }
        }
    }
}
