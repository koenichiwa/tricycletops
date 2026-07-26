use embassy_rp::{
    gpio::Output,
    pwm::{Pwm, PwmError, SetDutyCycle},
};

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

    pub fn set_speed(&mut self, percent: i8) -> Result<(), PwmError> {
        let percent = percent.clamp(-100, 100);
        let _max_duty = self.speed_pin.max_duty_cycle();

        match percent.cmp(&0) {
            core::cmp::Ordering::Less => {
                self.direction_pin.set_high();
                let speed_percent = percent.unsigned_abs();
                self.speed_pin.set_duty_cycle_percent(100 - speed_percent)?;
            }
            core::cmp::Ordering::Equal => {
                self.direction_pin.set_low();
                self.speed_pin.set_duty_cycle_fully_off()?;
            }
            core::cmp::Ordering::Greater => {
                self.direction_pin.set_low();
                self.speed_pin
                    .set_duty_cycle_percent(percent.unsigned_abs())?;
            }
        }

        Ok(())
    }
}
