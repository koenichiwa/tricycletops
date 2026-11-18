#![no_std]
#![no_main]

use defmt::info;
use defmt_rtt as _;
use embassy_executor::Spawner;
use embassy_sync::blocking_mutex::raw::ThreadModeRawMutex;
use embassy_sync::signal::Signal;
use panic_probe as _;

use tricycletops_fw::ble_peripheral;
use tricycletops_fw::board::{self, Board};
use tricycletops_fw::car_control::{CarControl, DriveCommand};

// Bounded MPMC channel with a capacity of 1 message
static CAR_COMMAND_SIGNAL: Signal<ThreadModeRawMutex, DriveCommand> = Signal::new();

#[embassy_executor::task]
async fn car_control_task(mut car: CarControl<'static>) -> ! {
    car.run(&CAR_COMMAND_SIGNAL).await
}

#[embassy_executor::main]
async fn main(spawner: Spawner) {
    info!("Initializing board...");
    let board = Board::init().await;

    spawner.spawn(board::cyw43_task(board.cyw43_runner).unwrap());

    let car = CarControl::new(board.left_motor, board.right_motor);
    let duty_limits = car.duty_limits();
    spawner.spawn(car_control_task(car).unwrap());

    info!("Running BLE peripheral...");
    ble_peripheral::run(
        board.ble_controller,
        &CAR_COMMAND_SIGNAL,
        board.led_pin,
        duty_limits,
    )
    .await;
}
