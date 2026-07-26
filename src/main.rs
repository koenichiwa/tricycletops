#![no_std]
#![no_main]

use embassy_executor::Spawner;
use embassy_rp::gpio::{Level, Output};
use embassy_time::Timer;

// defmt Logging
use defmt_rtt as _;
use defmt::info;

use panic_probe as _;

#[embassy_executor::main]
async fn main(_spawner: Spawner) {
    let p = embassy_rp::init(Default::default());
    
    info!("Initializing the program");
    let mut motor_a_speed = Output::new(p.PIN_1, Level::Low);
    let mut motor_a_direction = Output::new(p.PIN_0, Level::Low);

    let mut motor_b_speed = Output::new(p.PIN_3, Level::Low);
    let mut motor_b_direction = Output::new(p.PIN_2, Level::Low);

    loop {
        info!("Toggle led pin");
        motor_a_speed.set_high();

        Timer::after_secs(1).await;

        motor_a_speed.set_low();

        Timer::after_secs(1).await;

        motor_b_speed.set_high();

        Timer::after_secs(1).await;

        motor_b_speed.set_low();

        Timer::after_secs(1).await;
    }
}
