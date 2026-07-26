use embassy_rp::{gpio::Output, pwm::{Pwm, SetDutyCycle}};

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

    pub fn set_speed(&mut self, percent: i8) {
        let percent = percent.clamp(-100, 100);

        if percent == 0 {
            // Power-efficient stop: both pins Low
            self.direction_pin.set_low();
            self.speed_pin.set_duty_cycle_fully_off().unwrap();
        } else if percent > 0 {
            self.direction_pin.set_low();
            self.speed_pin.set_duty_cycle_percent(percent as u8).unwrap();
        } else {
            self.direction_pin.set_high();
            let speed_percent = percent.unsigned_abs();
            self.speed_pin.set_duty_cycle_percent(100 - speed_percent).unwrap();
        }
    }
}
