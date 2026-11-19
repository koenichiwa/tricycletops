#![no_std]
#![no_main]

use defmt::info;
use defmt_embassy_usbserial as _;
use embassy_executor::Spawner;
use embassy_sync::blocking_mutex::raw::ThreadModeRawMutex;
use embassy_sync::signal::Signal;
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
    let mut config = embassy_usb::Config::new(0xc0de, 0xcafe);
    config.manufacturer = Some("Tricycletops");
    config.product = Some("Tricycletops Defmt Logger");
    config.serial_number = Some("12345678");
    config.max_packet_size_0 = 64;

    // Required CDC settings for composite/IAD compliance
    config.composite_with_iads = true;
    config.device_class = 0xEF;
    config.device_sub_class = 0x02;
    config.device_protocol = 0x01;

    defmt_embassy_usbserial::run(driver, config).await;
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
