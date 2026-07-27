#![no_std]
#![no_main]

mod car_control;
mod motor_control;

use car_control::CarControl;
use cyw43::bluetooth::BtDriver;
use cyw43::{Cyw43439, aligned_bytes};
use cyw43_pio::PioSpi;
use embassy_executor::Spawner;
use embassy_rp::gpio::{Level, Output, Pin};
use embassy_rp::peripherals::{DMA_CH0, DMA_CH1, PIO0};
use embassy_rp::pio::{InterruptHandler as PioInterruptHandler, Pio, PioPin};
use embassy_rp::pwm::{ChannelBPin, Config, Pwm, Slice};
use embassy_rp::{
    Peri, bind_interrupts, dma::Channel as DmaChannel, dma::InterruptHandler as DmaInterruptHandler,
};
use embassy_sync::blocking_mutex::raw::ThreadModeRawMutex;
use embassy_sync::signal::Signal;
use embassy_time::Timer;
use motor_control::MotorControl;

// defmt Logging
use defmt::info;
use defmt_rtt as _;

use panic_probe as _;
use static_cell::StaticCell;
use trouble_host::prelude::*;

use crate::car_control::DriveCommand;

// Bounded MPMC channel with a capacity of 1 message
static CAR_COMMAND_SIGNAL: Signal<ThreadModeRawMutex, DriveCommand> = Signal::new();

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

    static STATE: StaticCell<cyw43::State> = StaticCell::new();
    let state = STATE.init(cyw43::State::new());
    let (_net_device, bt_device, mut control, runner) =
        cyw43::new_with_bluetooth(state, pwr, spi, fw, btfw, nvram).await;
    spawner.spawn(cyw43_task(runner).unwrap());
    control.init(clm).await;

    ExternalController::new(bt_device)
}

#[embassy_executor::task]
async fn car_control_task(
    mut car: CarControl<'static>,
    drive_command_signal: &'static Signal<ThreadModeRawMutex, DriveCommand>,
) {
    loop {
        let DriveCommand { left, right } = drive_command_signal.wait().await;
        info!(
            "Car control task: setting speeds left={}, right={}",
            left, right
        );
        if let Err(_error) = car.set_speeds(left, right) {
            break;
        }
    }

    let _ = car.set_speeds(0.0, 0.0);
}

fn run_car_control<SliceLeft, SpeedLeft, SliceRight, SpeedRight>(
    spawner: &Spawner,
    motor_left_pwm_slice: Peri<'static, SliceLeft>,
    motor_left_speed: Peri<'static, SpeedLeft>,
    motor_left_direction: Peri<'static, impl Pin>,
    motor_right_pwm_slice: Peri<'static, SliceRight>,
    motor_right_speed: Peri<'static, SpeedRight>,
    motor_right_direction: Peri<'static, impl Pin>,
) where
    SliceLeft: Slice,
    SpeedLeft: ChannelBPin<SliceLeft>,
    SliceRight: Slice,
    SpeedRight: ChannelBPin<SliceRight>,
{
    // PWM configuration: top sets the PWM period/resolution
    let config = Config::default();

    // Motor A (Left): PIN_1 is Slice 0 Channel B
    let motor_left_speed =
        Pwm::new_output_b(motor_left_pwm_slice, motor_left_speed, config.clone());
    let motor_left_direction = Output::new(motor_left_direction, Level::Low);
    let motor_left = MotorControl::new(motor_left_speed, motor_left_direction);

    // Motor B (Right): PIN_3 is Slice 1 Channel B
    let motor_right_speed = Pwm::new_output_b(motor_right_pwm_slice, motor_right_speed, config);
    let motor_right_direction = Output::new(motor_right_direction, Level::Low);
    let motor_right = MotorControl::new(motor_right_speed, motor_right_direction);

    let car = CarControl::new(motor_left, motor_right);
    spawner.spawn(car_control_task(car, &CAR_COMMAND_SIGNAL).unwrap());
}

#[embassy_executor::main]
async fn main(spawner: Spawner) {
    let p = embassy_rp::init(Default::default());

    info!("Initializing the program");
    run_car_control(
        &spawner,
        p.PWM_SLICE0,
        p.PIN_1,
        p.PIN_0,
        p.PWM_SLICE1,
        p.PIN_3,
        p.PIN_2,
    );

    let _controller: ExternalController<_, 10> = create_ble_controller(
        &spawner, p.PIN_23, p.PIN_25, p.PIO0, p.PIN_24, p.PIN_29, p.DMA_CH0, p.DMA_CH1,
    )
    .await;
    // Test script. Here we should fetch data from a source
    loop {
        info!("Sending Drive command (0.7, -0.7)");
        CAR_COMMAND_SIGNAL.signal(DriveCommand {
            left: 0.7,
            right: -0.7,
        });

        Timer::after_secs(3).await;

        info!("Sending Drive command (0.4, -0.4) - Spin");
        CAR_COMMAND_SIGNAL.signal(DriveCommand {
            left: 0.9,
            right: -0.9,
        });

        Timer::after_secs(2).await;

        info!("Sending Stop command");
        CAR_COMMAND_SIGNAL.signal(DriveCommand {
            left: 0.0,
            right: 0.0,
        });

        Timer::after_secs(2).await;
    }
}
