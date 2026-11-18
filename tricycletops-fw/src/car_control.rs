#[cfg(all(target_arch = "arm", target_os = "none"))]
use defmt::{info, warn};
#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_rp::pwm::PwmError;
#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_sync::{blocking_mutex::raw::ThreadModeRawMutex, signal::Signal};
#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_time::{Duration, with_timeout};

use crate::motor_control::Direction;
#[cfg(all(target_arch = "arm", target_os = "none"))]
use crate::motor_control::MotorControl;

pub const LEFT_MOTOR_MIN_DUTY: u16 = 200;
pub const RIGHT_MOTOR_MIN_DUTY: u16 = 200;

#[derive(Clone, Copy, Debug, PartialEq, Eq, defmt::Format)]
pub struct DriveCommand {
    pub left: i32,
    pub right: i32,
}

#[cfg(all(target_arch = "arm", target_os = "none"))]
pub struct CarControl<'a> {
    left_motor: MotorControl<'a>,
    right_motor: MotorControl<'a>,
}

#[cfg(all(target_arch = "arm", target_os = "none"))]
impl<'a> CarControl<'a> {
    pub const fn new(left_motor: MotorControl<'a>, right_motor: MotorControl<'a>) -> Self {
        Self {
            left_motor,
            right_motor,
        }
    }

    pub fn duty_limits(&self) -> crate::protocol::DutyLimits {
        crate::protocol::DutyLimits {
            left_min: LEFT_MOTOR_MIN_DUTY,
            left_max: self.left_motor.max_duty(),
            right_min: RIGHT_MOTOR_MIN_DUTY,
            right_max: self.right_motor.max_duty(),
        }
    }

    /// Set individual speeds for left and right motors.
    /// Inputs should be integers between -max_duty and max_duty.
    pub fn set_speeds(&mut self, left_speed: i32, right_speed: i32) -> Result<(), PwmError> {
        let (left_duty, left_dir) = split_speed(left_speed, self.left_motor.max_duty());
        let (right_duty, right_dir) = split_speed(right_speed, self.right_motor.max_duty());

        self.left_motor.set_duty(left_duty, left_dir)?;
        self.right_motor.set_duty(right_duty, right_dir)?;

        Ok(())
    }

    pub fn stop(&mut self) -> Result<(), PwmError> {
        self.left_motor.stop()?;
        self.right_motor.stop()?;
        Ok(())
    }

    pub async fn run(
        &mut self,
        drive_command_signal: &'static Signal<ThreadModeRawMutex, DriveCommand>,
    ) -> ! {
        const WATCHDOG_TIMEOUT: Duration = Duration::from_millis(500);

        loop {
            match with_timeout(WATCHDOG_TIMEOUT, drive_command_signal.wait()).await {
                Ok(cmd) => {
                    info!(
                        "Car control task: setting speeds left={}, right={}",
                        cmd.left, cmd.right
                    );
                    if let Err(err) = self.set_speeds(cmd.left, cmd.right) {
                        warn!("PWM error setting speeds: {:?}", defmt::Debug2Format(&err));
                        let _ = self.stop();
                    }
                }
                Err(_) => {
                    // Watchdog timeout elapsed without receiving a new drive command -> safety halt
                    let _ = self.stop();
                }
            }
        }
    }
}

pub fn split_speed(speed: i32, max_duty: u16) -> (u16, Direction) {
    let clamped = speed.clamp(-(max_duty as i32), max_duty as i32);
    if clamped >= 0 {
        (clamped as u16, Direction::Forward)
    } else {
        (clamped.unsigned_abs() as u16, Direction::Reverse)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_split_speed_zero() {
        let (duty, dir) = split_speed(0, 2500);
        assert_eq!(duty, 0);
        assert_eq!(dir, Direction::Forward);
    }

    #[test]
    fn test_split_speed_forward() {
        let (duty, dir) = split_speed(1500, 2500);
        assert_eq!(duty, 1500);
        assert_eq!(dir, Direction::Forward);
    }

    #[test]
    fn test_split_speed_reverse() {
        let (duty, dir) = split_speed(-1500, 2500);
        assert_eq!(duty, 1500);
        assert_eq!(dir, Direction::Reverse);
    }

    #[test]
    fn test_split_speed_clamping_positive() {
        let (duty, dir) = split_speed(5000, 2500);
        assert_eq!(duty, 2500);
        assert_eq!(dir, Direction::Forward);

        let (duty, dir) = split_speed(i32::MAX, 2500);
        assert_eq!(duty, 2500);
        assert_eq!(dir, Direction::Forward);
    }

    #[test]
    fn test_split_speed_clamping_negative() {
        let (duty, dir) = split_speed(-5000, 2500);
        assert_eq!(duty, 2500);
        assert_eq!(dir, Direction::Reverse);

        let (duty, dir) = split_speed(i32::MIN, 2500);
        assert_eq!(duty, 2500);
        assert_eq!(dir, Direction::Reverse);
    }

    #[test]
    fn test_split_speed_boundary_exact_max() {
        let (duty, dir) = split_speed(2500, 2500);
        assert_eq!(duty, 2500);
        assert_eq!(dir, Direction::Forward);

        let (duty, dir) = split_speed(-2500, 2500);
        assert_eq!(duty, 2500);
        assert_eq!(dir, Direction::Reverse);
    }
}
