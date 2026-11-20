#![no_std]
#![no_main]

use defmt::info;
use embassy_executor::Spawner;
use embassy_sync::blocking_mutex::raw::ThreadModeRawMutex;
use embassy_sync::signal::Signal;
use embassy_usb::Builder;
use embassy_usb::Config;
use embassy_usb::UsbDevice;
use embassy_usb::class::cdc_acm::{CdcAcmClass, Sender, State};
use panic_probe as _;
use static_cell::StaticCell;

use tricycletops_fw::ble_peripheral;
use tricycletops_fw::board::{self, Board};
use tricycletops_fw::car_control::{CarCommand, CarControl, TargetCarControl};
use tricycletops_fw::indicator::{self, IndicatorState};
use tricycletops_fw::usb_logger;

// Signals
static CAR_COMMAND_SIGNAL: Signal<ThreadModeRawMutex, CarCommand> = Signal::new();
static INDICATOR_SIGNAL: Signal<ThreadModeRawMutex, IndicatorState> = Signal::new();

#[embassy_executor::task]
async fn car_control_task(mut car: TargetCarControl) -> ! {
    car.run(&CAR_COMMAND_SIGNAL, &INDICATOR_SIGNAL).await
}

#[embassy_executor::task]
async fn usb_device_task(mut device: UsbDevice<'static, board::UsbDriver>) -> ! {
    device.run().await
}

#[embassy_executor::task]
async fn usb_logger_task(sender: Sender<'static, board::UsbDriver>) -> ! {
    usb_logger::cdc_task(sender).await
}

#[embassy_executor::main]
async fn main(spawner: Spawner) {
    let board = Board::init().await;

    // Configure USB device builder
    let mut config = Config::new(0xc0de, 0xcafe);
    config.manufacturer = Some("Tricycletops");
    config.product = Some("Tricycletops Defmt Logger");
    config.serial_number = Some("defmt");
    config.max_power = 100;
    config.max_packet_size_0 = 64;

    static CONFIG_DESCRIPTOR: StaticCell<[u8; 256]> = StaticCell::new();
    static BOS_DESCRIPTOR: StaticCell<[u8; 256]> = StaticCell::new();
    static MSOS_DESCRIPTOR: StaticCell<[u8; 256]> = StaticCell::new();
    static CONTROL_BUF: StaticCell<[u8; 64]> = StaticCell::new();
    static STATE: StaticCell<State> = StaticCell::new();

    let mut builder = Builder::new(
        board.usb_driver,
        config,
        CONFIG_DESCRIPTOR.init([0; 256]),
        BOS_DESCRIPTOR.init([0; 256]),
        MSOS_DESCRIPTOR.init([0; 256]),
        CONTROL_BUF.init([0; 64]),
    );

    let state = STATE.init(State::new());
    let class = CdcAcmClass::new(&mut builder, state, 64);
    let (sender, _) = class.split();
    let usb = builder.build();

    spawner.spawn(usb_device_task(usb).unwrap());
    spawner.spawn(usb_logger_task(sender).unwrap());
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
