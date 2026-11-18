use embassy_rp::{
    gpio::Output,
    pwm::{Pwm, PwmError, SetDutyCycle},
};

#[derive(Copy, Clone, Debug, PartialEq, Eq, defmt::Format)]
pub enum Direction {
    Forward,
    Reverse,
}

pub struct MotorControl<'a> {
    speed_pin: Pwm<'a>,
    direction_pin: Output<'a>,
}

impl<'a> MotorControl<'a> {
    pub const fn new(speed_pin: Pwm<'a>, direction_pin: Output<'a>) -> Self {
        Self {
            speed_pin,
            direction_pin,
        }
    }

    pub fn max_duty(&self) -> u16 {
        self.speed_pin.max_duty_cycle()
    }

    pub fn set_duty(&mut self, duty: u16, direction: Direction) -> Result<(), PwmError> {
        let max_duty = self.max_duty();
        let duty = duty.min(max_duty);

        if duty == 0 {
            return self.stop();
        }

        match direction {
            Direction::Forward => {
                self.direction_pin.set_low();
                self.speed_pin.set_duty_cycle(duty)?;
            }
            Direction::Reverse => {
                self.direction_pin.set_high();
                self.speed_pin.set_duty_cycle(max_duty - duty)?;
            }
        }

        Ok(())
    }

    pub fn stop(&mut self) -> Result<(), PwmError> {
        self.direction_pin.set_low();
        self.speed_pin.set_duty_cycle_fully_off()?;
        Ok(())
    }
}
