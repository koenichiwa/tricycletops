#![no_std]
#![no_main]

mod ble_peripheral;
mod car_control;
mod motor_control;

use car_control::{CarControl, DriveCommand};
use cyw43::bluetooth::BtDriver;
use cyw43::{Cyw43439, aligned_bytes};
use cyw43_pio::PioSpi;
use embassy_executor::Spawner;
use embassy_rp::config::Config;
use embassy_rp::gpio::{Level, Output, Pin};
use embassy_rp::peripherals::{DMA_CH0, DMA_CH1, PIO0};
use embassy_rp::pio::{InterruptHandler as PioInterruptHandler, Pio, PioPin};
use embassy_rp::{
    Peri, bind_interrupts, dma::Channel as DmaChannel, dma::InterruptHandler as DmaInterruptHandler,
};
use embassy_sync::blocking_mutex::raw::ThreadModeRawMutex;
use embassy_sync::signal::Signal;

// defmt Logging
use defmt::info;
use defmt_rtt as _;

use panic_probe as _;
use static_cell::StaticCell;
use trouble_host::prelude::*;

// Bounded MPMC channel with a capacity of 1 message
static CAR_COMMAND_SIGNAL: Signal<ThreadModeRawMutex, DriveCommand> = Signal::new();

// State for the CYW43439 wifi+bluetooth driver
static STATE: StaticCell<cyw43::State> = StaticCell::new();

bind_interrupts!(struct Irqs {
    PIO0_IRQ_0 => PioInterruptHandler<PIO0>;
    DMA_IRQ_0 => DmaInterruptHandler<DMA_CH0>, DmaInterruptHandler<DMA_CH1>;
});

#[embassy_executor::task]
async fn cyw43_task(
    runner: cyw43::Runner<
        'static,
        cyw43::SpiBus<Output<'static>, PioSpi<'static, PIO0, 0>>,
        Cyw43439,
    >,
) -> ! {
    runner.run().await
}

#[allow(clippy::too_many_arguments)]
async fn create_ble_controller(
    spawner: &Spawner,
    pwr_pin: Peri<'static, impl Pin>,
    cs_pin: Peri<'static, impl Pin>,
    pio_instance: Peri<'static, PIO0>,
    dio_pin: Peri<'static, impl PioPin>,
    clk_pin: Peri<'static, impl PioPin>,
    dma_tx: Peri<'static, DMA_CH0>,
    dma_rx: Peri<'static, DMA_CH1>,
) -> ExternalController<BtDriver<'static>, 10> {
    let fw = aligned_bytes!("../target/cyw43-firmware/43439A0.bin");
    let clm = aligned_bytes!("../target/cyw43-firmware/43439A0_clm.bin");
    let btfw = aligned_bytes!("../target/cyw43-firmware/43439A0_btfw.bin");
    let nvram = aligned_bytes!("../target/cyw43-firmware/nvram_rp2040.bin");

    let pwr = Output::new(pwr_pin, Level::Low);
    let cs = Output::new(cs_pin, Level::High);
    let mut pio = Pio::new(pio_instance, Irqs);
    let spi = PioSpi::new(
        &mut pio.common,
        pio.sm0,
        cyw43_pio::DEFAULT_CLOCK_DIVIDER,
        pio.irq0,
        cs,
        dio_pin,
        clk_pin,
        DmaChannel::new(dma_tx, Irqs),
        DmaChannel::new(dma_rx, Irqs),
    );

    let state = STATE.init(cyw43::State::new());
    let (_net_device, bt_device, mut control, runner) =
        cyw43::new_with_bluetooth(state, pwr, spi, fw, btfw, nvram).await;
    spawner.spawn(cyw43_task(runner).unwrap());
    control.init(clm).await;

    ExternalController::new(bt_device)
}

#[embassy_executor::task]
async fn car_control_task(mut car: CarControl<'static>) {
    if let Err(error) = car.run(&CAR_COMMAND_SIGNAL).await {
        let error = defmt::Debug2Format(&error);
        info!("[car_control_task] error: {:?}", error);
    }
}

#[embassy_executor::main]
async fn main(spawner: Spawner) {
    let p = embassy_rp::init(Config::default());

    info!("Initializing the program");

    let car = CarControl::new(
        p.PWM_SLICE0,
        p.PIN_1,
        p.PIN_0,
        p.PWM_SLICE1,
        p.PIN_3,
        p.PIN_2,
    );
    let duty_limits = car.duty_limits();
    spawner.spawn(car_control_task(car).unwrap());

    let mut led_pin = Output::new(p.PIN_15, Level::High);
    let ble_controller: ExternalController<_, 10> = create_ble_controller(
        &spawner, p.PIN_23, p.PIN_25, p.PIO0, p.PIN_24, p.PIN_29, p.DMA_CH0, p.DMA_CH1,
    )
    .await;
    led_pin.toggle();

    info!("Running BLE peripheral...");
    ble_peripheral::run(ble_controller, &CAR_COMMAND_SIGNAL, led_pin, duty_limits).await;
}
