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
    let mut led_pin = Output::new(p.PIN_16, Level::Low);
    loop {
        info!("Toggle led pin");
        led_pin.toggle();
        Timer::after_secs(1).await;
    }
}
