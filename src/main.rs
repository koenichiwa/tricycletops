#![no_std]
#![no_main]

mod motor_control;
mod car_control;

use embassy_executor::Spawner;
use embassy_rp::config::Config as RpConfig;
use embassy_rp::gpio::{Level, Output};
use embassy_rp::pwm::{Config  as PwmConfig, Pwm};
use embassy_time::Timer;
use motor_control::MotorControl;
use car_control::CarControl;

// defmt Logging
use defmt_rtt as _;
use defmt::info;

use panic_probe as _;

#[embassy_executor::main]
async fn main(_spawner: Spawner) {
    let p = embassy_rp::init(RpConfig::default());
    
    info!("Initializing the program");

    // PWM configuration: top sets the PWM period/resolution
    let config = PwmConfig::default();

    // Motor A (Left): PIN_1 is Slice 0 Channel B
    let motor_a_speed = Pwm::new_output_b(p.PWM_SLICE0, p.PIN_1, config.clone());
    let motor_a_direction = Output::new(p.PIN_0, Level::Low);
    let motor_a = MotorControl::new(motor_a_speed, motor_a_direction);

    // Motor B (Right): PIN_3 is Slice 1 Channel B
    let motor_b_speed = Pwm::new_output_b(p.PWM_SLICE1, p.PIN_3, config);
    let motor_b_direction = Output::new(p.PIN_2, Level::Low);
    let motor_b = MotorControl::new(motor_b_speed, motor_b_direction);

    let mut car = CarControl::new(motor_a, motor_b);

    loop {
        info!("Driving car forward at 50% speed");
        car.set_speeds(70.0, -70.0);

        Timer::after_secs(3).await;

        info!("Spinning car in place (differential turn)");
        car.set_speeds(40.0, -40.0);

        Timer::after_secs(2).await;

        info!("Stopping car");
        car.stop();

        Timer::after_secs(2).await;
    }
}
