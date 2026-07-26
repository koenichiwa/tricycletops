#![no_std]
#![no_main]

use embassy_executor::Spawner;
use embassy_rp::gpio::{Level, Output};
use embassy_rp::pwm::{Config, Pwm, SetDutyCycle};
use embassy_time::Timer;

// defmt Logging
use defmt_rtt as _;
use defmt::info;

use panic_probe as _;

#[embassy_executor::main]
async fn main(_spawner: Spawner) {
    let p = embassy_rp::init(Default::default());
    
    info!("Initializing the program");

    // PWM configuration: top sets the PWM period/resolution
    let mut config = Config::default();
    config.top = 32768;
    config.compare_b = 0; // Start with 0% duty cycle (stopped)

    // Motor A: PIN_1 is Slice 0 Channel B
    let mut motor_a_speed = Pwm::new_output_b(p.PWM_SLICE0, p.PIN_1, config.clone());
    let mut motor_a_direction = Output::new(p.PIN_0, Level::Low);

    // Motor B: PIN_3 is Slice 1 Channel B
    let mut motor_b_speed = Pwm::new_output_b(p.PWM_SLICE1, p.PIN_3, config.clone());
    let mut motor_b_direction = Output::new(p.PIN_2, Level::Low);

    loop {
        info!("Running Motor A at 75% speed");
        motor_a_direction.set_low();
        motor_a_speed.set_duty_cycle_percent(75).unwrap();

        Timer::after_secs(2).await;

        info!("Stopping Motor A");
        motor_a_speed.set_duty_cycle_percent(0).unwrap();

        Timer::after_secs(1).await;

        info!("Running Motor B at 50% speed");
        motor_b_direction.set_low();
        motor_b_speed.set_duty_cycle_percent(50).unwrap();

        Timer::after_secs(2).await;

        info!("Stopping Motor B");
        motor_b_speed.set_duty_cycle_percent(0).unwrap();

        Timer::after_secs(1).await;
    }
}
