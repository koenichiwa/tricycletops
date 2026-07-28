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

    pub fn set_speed(&mut self, speed: f32) -> Result<(), PwmError> {
        let speed = speed.clamp(-1.0, 1.0);

        let max_duty = self.speed_pin.max_duty_cycle();

        let set_duty = (speed * f32::from(max_duty)) as i32;

        debug_assert!(
            set_duty.unsigned_abs() <= max_duty as u32,
            "duty {} exceeds max duty {}",
            set_duty,
            max_duty
        );

        match set_duty.cmp(&0) {
            core::cmp::Ordering::Less => {
                self.direction_pin.set_high();
                let set_duty = set_duty.unsigned_abs() as u16;
                self.speed_pin.set_duty_cycle(max_duty - set_duty)?;
            }
            core::cmp::Ordering::Equal => {
                self.direction_pin.set_low();
                self.speed_pin.set_duty_cycle_fully_off()?;
            }
            core::cmp::Ordering::Greater => {
                self.direction_pin.set_low();
                self.speed_pin.set_duty_cycle(set_duty as u16)?;
            }
        }

        Ok(())
    }
}
