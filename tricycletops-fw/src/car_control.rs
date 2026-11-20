use embassy_sync::{blocking_mutex::raw::RawMutex, signal::Signal};
use embassy_time::{Duration, with_timeout};
use log::{info, warn};

use crate::motor_control::{Direction, Motor};

pub const LEFT_MOTOR_MIN_DUTY: u16 = 200;
pub const RIGHT_MOTOR_MIN_DUTY: u16 = 200;

#[derive(Clone, Copy, Debug, PartialEq, Eq, defmt::Format)]
pub struct DriveCommand {
    pub left: i32,
    pub right: i32,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, defmt::Format)]
pub enum CarCommand {
    Drive(DriveCommand),
    Disconnected,
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

    pub fn handle_command<M: RawMutex>(
        &mut self,
        cmd: DriveCommand,
        indicator_signal: &Signal<M, crate::indicator::IndicatorState>,
        in_failsafe: &mut bool,
    ) -> Result<(), CarControlError<L::Error, R::Error>> {
        match self.set_speeds(cmd.left, cmd.right) {
            Ok(()) => {
                if *in_failsafe {
                    *in_failsafe = false;
                    indicator_signal.signal(crate::indicator::IndicatorState::Connected);
                }
                Ok(())
            }
            Err(err) => {
                let _ = self.stop();
                *in_failsafe = true;
                indicator_signal.signal(crate::indicator::IndicatorState::Failsafe);
                Err(err)
            }
        }
    }

    pub fn handle_watchdog_timeout<M: RawMutex>(
        &mut self,
        indicator_signal: &Signal<M, crate::indicator::IndicatorState>,
        in_failsafe: &mut bool,
    ) {
        let _ = self.stop();
        *in_failsafe = true;
        indicator_signal.signal(crate::indicator::IndicatorState::Failsafe);
    }

    pub fn handle_disconnect(
        &mut self,
        in_failsafe: &mut bool,
    ) -> Result<(), CarControlError<L::Error, R::Error>> {
        *in_failsafe = false;
        self.stop()
    }

    pub async fn run<M: RawMutex>(
        &mut self,
        car_command_signal: &'static Signal<M, CarCommand>,
        indicator_signal: &'static Signal<M, crate::indicator::IndicatorState>,
    ) -> ! {
        const WATCHDOG_TIMEOUT: Duration = Duration::from_millis(500);

        let mut has_received_command = false;
        let mut in_failsafe = false;

        loop {
            if !has_received_command {
                match car_command_signal.wait().await {
                    CarCommand::Drive(cmd) => {
                        has_received_command = true;
                        info!(
                            "Car control task: setting initial speeds left={}, right={}",
                            cmd.left, cmd.right
                        );
                        if let Err(err) =
                            self.handle_command(cmd, indicator_signal, &mut in_failsafe)
                        {
                            warn!("Motor error setting speeds: {:?}", err);
                        }
                    }
                    CarCommand::Disconnected => {
                        if let Err(err) = self.handle_disconnect(&mut in_failsafe) {
                            warn!("Motor error during disconnect: {:?}", err);
                        }
                    }
                }
            } else {
                match with_timeout(WATCHDOG_TIMEOUT, car_command_signal.wait()).await {
                    Ok(CarCommand::Drive(cmd)) => {
                        info!(
                            "Car control task: setting speeds left={}, right={}",
                            cmd.left, cmd.right
                        );
                        if let Err(err) =
                            self.handle_command(cmd, indicator_signal, &mut in_failsafe)
                        {
                            warn!("Motor error setting speeds: {:?}", err);
                        }
                    }
                    Ok(CarCommand::Disconnected) => {
                        has_received_command = false;
                        if let Err(err) = self.handle_disconnect(&mut in_failsafe) {
                            warn!("Motor error during disconnect: {:?}", err);
                        }
                    }
                    Err(_) => {
                        // Watchdog timeout elapsed without receiving a new drive command -> safety halt
                        self.handle_watchdog_timeout(indicator_signal, &mut in_failsafe);
                    }
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

    use crate::indicator::IndicatorState;
    use embassy_sync::blocking_mutex::raw::CriticalSectionRawMutex;

    #[test]
    fn test_car_control_failsafe_on_motor_error() {
        let left = MockMotor::new(2500);
        let mut right = MockMotor::new(2500);
        right.fail_set_duty = true;
        let mut car = CarControl::new(left, right);

        let indicator_signal: Signal<CriticalSectionRawMutex, IndicatorState> = Signal::new();
        let mut in_failsafe = false;

        let cmd = DriveCommand {
            left: 1000,
            right: 1000,
        };
        let res = car.handle_command(cmd, &indicator_signal, &mut in_failsafe);

        assert!(res.is_err());
        assert!(in_failsafe);
        assert!(indicator_signal.signaled());
        assert_eq!(indicator_signal.try_take(), Some(IndicatorState::Failsafe));
        assert!(car.left_motor().stopped);
        assert!(car.right_motor().stopped);
    }

    #[test]
    fn test_car_control_watchdog_timeout() {
        let left = MockMotor::new(2500);
        let right = MockMotor::new(2500);
        let mut car = CarControl::new(left, right);

        let indicator_signal: Signal<CriticalSectionRawMutex, IndicatorState> = Signal::new();
        let mut in_failsafe = false;

        // Start motors
        assert!(car.set_speeds(1000, 1000).is_ok());
        assert!(!car.left_motor().stopped);

        // Watchdog timeout occurs
        car.handle_watchdog_timeout(&indicator_signal, &mut in_failsafe);

        assert!(in_failsafe);
        assert!(indicator_signal.signaled());
        assert_eq!(indicator_signal.try_take(), Some(IndicatorState::Failsafe));
        assert!(car.left_motor().stopped);
        assert_eq!(car.left_motor().current_duty, 0);
        assert!(car.right_motor().stopped);
        assert_eq!(car.right_motor().current_duty, 0);
    }

    #[test]
    fn test_car_control_handle_disconnect() {
        let left = MockMotor::new(2500);
        let right = MockMotor::new(2500);
        let mut car = CarControl::new(left, right);

        let mut in_failsafe = true;
        let res = car.handle_disconnect(&mut in_failsafe);

        assert!(res.is_ok());
        assert!(!in_failsafe);
        assert!(car.left_motor().stopped);
        assert!(car.right_motor().stopped);
    }

    #[test]
    fn test_car_control_recovery_from_failsafe() {
        let left = MockMotor::new(2500);
        let right = MockMotor::new(2500);
        let mut car = CarControl::new(left, right);

        let indicator_signal: Signal<CriticalSectionRawMutex, IndicatorState> = Signal::new();
        let mut in_failsafe = true;

        let cmd = DriveCommand {
            left: 1200,
            right: -1200,
        };
        let res = car.handle_command(cmd, &indicator_signal, &mut in_failsafe);

        assert!(res.is_ok());
        assert!(!in_failsafe);
        assert!(indicator_signal.signaled());
        assert_eq!(indicator_signal.try_take(), Some(IndicatorState::Connected));
        assert_eq!(car.left_motor().current_duty, 1200);
        assert_eq!(car.left_motor().current_direction, Direction::Forward);
        assert_eq!(car.right_motor().current_duty, 1200);
        assert_eq!(car.right_motor().current_direction, Direction::Reverse);
    }
}
