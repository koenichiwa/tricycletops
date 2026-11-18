use defmt::{info, warn};
use embassy_sync::{blocking_mutex::raw::RawMutex, signal::Signal};
use embassy_time::{Duration, with_timeout};

use crate::motor_control::{Direction, Motor};

pub const LEFT_MOTOR_MIN_DUTY: u16 = 200;
pub const RIGHT_MOTOR_MIN_DUTY: u16 = 200;

#[derive(Clone, Copy, Debug, PartialEq, Eq, defmt::Format)]
pub struct DriveCommand {
    pub left: i32,
    pub right: i32,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum CarControlError<L, R> {
    LeftMotor(L),
    RightMotor(R),
}

pub struct CarControl<L, R> {
    left_motor: L,
    right_motor: R,
}

#[cfg(all(target_arch = "arm", target_os = "none"))]
pub type TargetCarControl = CarControl<
    crate::motor_control::MotorControl<'static>,
    crate::motor_control::MotorControl<'static>,
>;

impl<L: Motor, R: Motor> CarControl<L, R> {
    pub const fn new(left_motor: L, right_motor: R) -> Self {
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
    pub fn set_speeds(
        &mut self,
        left_speed: i32,
        right_speed: i32,
    ) -> Result<(), CarControlError<L::Error, R::Error>> {
        let (left_duty, left_dir) = split_speed(left_speed, self.left_motor.max_duty());
        let (right_duty, right_dir) = split_speed(right_speed, self.right_motor.max_duty());

        self.left_motor
            .set_duty(left_duty, left_dir)
            .map_err(CarControlError::LeftMotor)?;
        self.right_motor
            .set_duty(right_duty, right_dir)
            .map_err(CarControlError::RightMotor)?;

        Ok(())
    }

    pub fn stop(&mut self) -> Result<(), CarControlError<L::Error, R::Error>> {
        let left_res = self.left_motor.stop().map_err(CarControlError::LeftMotor);
        let right_res = self.right_motor.stop().map_err(CarControlError::RightMotor);
        left_res?;
        right_res?;
        Ok(())
    }

    pub fn left_motor(&self) -> &L {
        &self.left_motor
    }

    pub fn right_motor(&self) -> &R {
        &self.right_motor
    }

    pub fn left_motor_mut(&mut self) -> &mut L {
        &mut self.left_motor
    }

    pub fn right_motor_mut(&mut self) -> &mut R {
        &mut self.right_motor
    }

    pub async fn run<M: RawMutex>(
        &mut self,
        drive_command_signal: &'static Signal<M, DriveCommand>,
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
                        warn!(
                            "Motor error setting speeds: {:?}",
                            defmt::Debug2Format(&err)
                        );
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

    use crate::motor_control::MockMotor;

    #[test]
    fn test_car_control_duty_limits() {
        let left = MockMotor::new(2500);
        let right = MockMotor::new(3000);
        let car = CarControl::new(left, right);
        let limits = car.duty_limits();
        assert_eq!(limits.left_min, 200);
        assert_eq!(limits.left_max, 2500);
        assert_eq!(limits.right_min, 200);
        assert_eq!(limits.right_max, 3000);
    }

    #[test]
    fn test_car_control_set_speeds() {
        let left = MockMotor::new(2500);
        let right = MockMotor::new(2500);
        let mut car = CarControl::new(left, right);

        assert!(car.set_speeds(1000, -1500).is_ok());
        assert_eq!(car.left_motor().current_duty, 1000);
        assert_eq!(car.left_motor().current_direction, Direction::Forward);
        assert!(!car.left_motor().stopped);

        assert_eq!(car.right_motor().current_duty, 1500);
        assert_eq!(car.right_motor().current_direction, Direction::Reverse);
        assert!(!car.right_motor().stopped);
    }

    #[test]
    fn test_car_control_stop() {
        let left = MockMotor::new(2500);
        let right = MockMotor::new(2500);
        let mut car = CarControl::new(left, right);

        assert!(car.set_speeds(1000, 1000).is_ok());
        assert!(car.stop().is_ok());
        assert!(car.left_motor().stopped);
        assert_eq!(car.left_motor().current_duty, 0);
        assert!(car.right_motor().stopped);
        assert_eq!(car.right_motor().current_duty, 0);
    }

    #[test]
    fn test_car_control_motor_error() {
        let left = MockMotor::new(2500);
        let mut right = MockMotor::new(2500);
        right.fail_set_duty = true;
        let mut car = CarControl::new(left, right);

        let res = car.set_speeds(1000, 1000);
        assert_eq!(
            res,
            Err(CarControlError::RightMotor("mock motor set_duty failed"))
        );
    }
}
