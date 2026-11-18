#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_rp::{
    gpio::Output,
    pwm::{Pwm, PwmError, SetDutyCycle},
};

#[derive(Copy, Clone, Debug, Default, PartialEq, Eq, defmt::Format)]
pub enum Direction {
    #[default]
    Forward,
    Reverse,
}

pub trait Motor {
    type Error: core::fmt::Debug;

    fn max_duty(&self) -> u16;
    fn set_duty(&mut self, duty: u16, direction: Direction) -> Result<(), Self::Error>;
    fn stop(&mut self) -> Result<(), Self::Error>;
}

#[cfg(any(test, not(all(target_arch = "arm", target_os = "none"))))]
#[derive(Default, Debug, Clone, PartialEq, Eq)]
pub struct MockMotor {
    pub max_duty: u16,
    pub current_duty: u16,
    pub current_direction: Direction,
    pub stopped: bool,
    pub fail_set_duty: bool,
    pub fail_stop: bool,
}

#[cfg(any(test, not(all(target_arch = "arm", target_os = "none"))))]
impl MockMotor {
    pub const fn new(max_duty: u16) -> Self {
        Self {
            max_duty,
            current_duty: 0,
            current_direction: Direction::Forward,
            stopped: true,
            fail_set_duty: false,
            fail_stop: false,
        }
    }
}

#[cfg(any(test, not(all(target_arch = "arm", target_os = "none"))))]
impl Motor for MockMotor {
    type Error = &'static str;

    fn max_duty(&self) -> u16 {
        self.max_duty
    }

    fn set_duty(&mut self, duty: u16, direction: Direction) -> Result<(), Self::Error> {
        if self.fail_set_duty {
            return Err("mock motor set_duty failed");
        }
        self.current_duty = duty;
        self.current_direction = direction;
        self.stopped = false;
        Ok(())
    }

    fn stop(&mut self) -> Result<(), Self::Error> {
        if self.fail_stop {
            return Err("mock motor stop failed");
        }
        self.current_duty = 0;
        self.stopped = true;
        Ok(())
    }
}

#[cfg(all(target_arch = "arm", target_os = "none"))]
pub struct MotorControl<'a> {
    speed_pin: Pwm<'a>,
    direction_pin: Output<'a>,
}

#[cfg(all(target_arch = "arm", target_os = "none"))]
impl<'a> MotorControl<'a> {
    pub const fn new(speed_pin: Pwm<'a>, direction_pin: Output<'a>) -> Self {
        Self {
            speed_pin,
            direction_pin,
        }
    }

    pub fn max_duty(&self) -> u16 {
        <Self as Motor>::max_duty(self)
    }

    pub fn set_duty(&mut self, duty: u16, direction: Direction) -> Result<(), PwmError> {
        <Self as Motor>::set_duty(self, duty, direction)
    }

    pub fn stop(&mut self) -> Result<(), PwmError> {
        <Self as Motor>::stop(self)
    }
}

#[cfg(all(target_arch = "arm", target_os = "none"))]
impl<'a> Motor for MotorControl<'a> {
    type Error = PwmError;

    fn max_duty(&self) -> u16 {
        self.speed_pin.max_duty_cycle()
    }

    fn set_duty(&mut self, duty: u16, direction: Direction) -> Result<(), Self::Error> {
        let max_duty = self.max_duty();
        let duty = duty.min(max_duty);

        if duty == 0 {
            return self.stop();
        }

        match direction {
            Direction::Forward => {
                self.speed_pin.set_duty_cycle(duty)?;
                self.direction_pin.set_low();
            }
            Direction::Reverse => {
                self.speed_pin.set_duty_cycle(max_duty - duty)?;
                self.direction_pin.set_high();
            }
        }

        Ok(())
    }

    fn stop(&mut self) -> Result<(), Self::Error> {
        self.speed_pin.set_duty_cycle_fully_off()?;
        self.direction_pin.set_low();
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mock_motor_default_and_new() {
        let motor = MockMotor::new(2500);
        assert_eq!(motor.max_duty(), 2500);
        assert_eq!(motor.current_duty, 0);
        assert_eq!(motor.current_direction, Direction::Forward);
        assert!(motor.stopped);
    }

    #[test]
    fn test_mock_motor_set_duty_forward_and_reverse() {
        let mut motor = MockMotor::new(2500);

        assert!(motor.set_duty(1000, Direction::Forward).is_ok());
        assert_eq!(motor.current_duty, 1000);
        assert_eq!(motor.current_direction, Direction::Forward);
        assert!(!motor.stopped);

        assert!(motor.set_duty(2000, Direction::Reverse).is_ok());
        assert_eq!(motor.current_duty, 2000);
        assert_eq!(motor.current_direction, Direction::Reverse);
        assert!(!motor.stopped);
    }

    #[test]
    fn test_mock_motor_stop() {
        let mut motor = MockMotor::new(2500);
        assert!(motor.set_duty(1500, Direction::Forward).is_ok());
        assert!(!motor.stopped);

        assert!(motor.stop().is_ok());
        assert_eq!(motor.current_duty, 0);
        assert!(motor.stopped);
    }

    #[test]
    fn test_mock_motor_error_injection() {
        let mut motor = MockMotor::new(2500);
        motor.fail_set_duty = true;
        assert!(motor.set_duty(1000, Direction::Forward).is_err());

        motor.fail_set_duty = false;
        motor.fail_stop = true;
        assert!(motor.stop().is_err());
    }
}
