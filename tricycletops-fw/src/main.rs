#![no_std]
#![no_main]

use defmt_rtt as _;
use embassy_executor::Spawner;
use embassy_sync::blocking_mutex::raw::ThreadModeRawMutex;
use embassy_sync::signal::Signal;
use log::info;
use panic_probe as _;

use tricycletops_fw::ble_peripheral;
use tricycletops_fw::board::{self, Board};
use tricycletops_fw::car_control::{CarCommand, CarControl, TargetCarControl};
use tricycletops_fw::indicator::{self, IndicatorState};

// Signals
static CAR_COMMAND_SIGNAL: Signal<ThreadModeRawMutex, CarCommand> = Signal::new();
static INDICATOR_SIGNAL: Signal<ThreadModeRawMutex, IndicatorState> = Signal::new();

#[embassy_executor::task]
async fn car_control_task(mut car: TargetCarControl) -> ! {
    car.run(&CAR_COMMAND_SIGNAL, &INDICATOR_SIGNAL).await
}

#[embassy_executor::task]
async fn logger_task(driver: board::UsbDriver) {
    embassy_usb_logger::run!(1024, log::LevelFilter::Info, driver);
}

#[embassy_executor::main]
async fn main(spawner: Spawner) {
    let board = Board::init().await;

    spawner.spawn(logger_task(board.usb_driver).unwrap());
    info!("Board initialized, USB logging active");

    spawner.spawn(board::cyw43_task(board.cyw43_runner).unwrap());
    spawner.spawn(indicator::indicator_task(board.led_pin, &INDICATOR_SIGNAL).unwrap());

    let car = CarControl::new(board.left_motor, board.right_motor);
    let duty_limits = car.duty_limits();
    spawner.spawn(car_control_task(car).unwrap());

    info!("Running BLE peripheral...");
    ble_peripheral::run(
        board.ble_controller,
        &CAR_COMMAND_SIGNAL,
        &INDICATOR_SIGNAL,
        duty_limits,
    )
    .await;
}
