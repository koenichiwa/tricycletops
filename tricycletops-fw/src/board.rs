#![cfg(all(target_arch = "arm", target_os = "none"))]

//! Board Support Package (BSP) for RP2040 and Tricycletops.

use cyw43::Cyw43439;
use cyw43_pio::PioSpi;
use embassy_rp::gpio::{Level, Output};
use embassy_rp::peripherals::{DMA_CH0, DMA_CH1, PIO0, USB};
use embassy_rp::pio::{InterruptHandler as PioInterruptHandler, Pio};
use embassy_rp::pwm::{Config as PwmConfig, Pwm};
use embassy_rp::usb::InterruptHandler as UsbInterruptHandler;
use embassy_rp::{
    bind_interrupts, dma::Channel as DmaChannel, dma::InterruptHandler as DmaInterruptHandler,
};

use crate::motor_control::MotorControl;

bind_interrupts!(struct Irqs {
    PIO0_IRQ_0 => PioInterruptHandler<PIO0>;
    DMA_IRQ_0 => DmaInterruptHandler<DMA_CH0>, DmaInterruptHandler<DMA_CH1>;
    USBCTRL_IRQ => UsbInterruptHandler<USB>;
});

pub type Cyw43Spi = PioSpi<'static, PIO0, 0>;
pub type Cyw43Runner = cyw43::Runner<'static, cyw43::SpiBus<Output<'static>, Cyw43Spi>, Cyw43439>;
pub type UsbDriver = embassy_rp::usb::Driver<'static, USB>;

pub struct Board {
    pub left_motor: MotorControl<'static>,
    pub right_motor: MotorControl<'static>,
    pub led_pin: Output<'static>,
    pub cyw43_pwr: Output<'static>,
    pub cyw43_spi: Cyw43Spi,
    pub usb_driver: UsbDriver,
}

impl Board {
    pub fn init() -> Self {
        let p = embassy_rp::init(embassy_rp::config::Config::default());

        // Status LED on PIN_15
        let led_pin = Output::new(p.PIN_15, Level::High);

        // Motor drivers:
        // Left: Speed on PIN_1 (PWM 0B), Direction on PIN_0
        // Right: Speed on PIN_3 (PWM 1B), Direction on PIN_2
        let pwm_config = PwmConfig::default();
        let left_speed = Pwm::new_output_b(p.PWM_SLICE0, p.PIN_1, pwm_config.clone());
        let left_dir = Output::new(p.PIN_0, Level::Low);
        let left_motor = MotorControl::new(left_speed, left_dir);

        let right_speed = Pwm::new_output_b(p.PWM_SLICE1, p.PIN_3, pwm_config);
        let right_dir = Output::new(p.PIN_2, Level::Low);
        let right_motor = MotorControl::new(right_speed, right_dir);

        // CYW43 Wi-Fi / Bluetooth pins and SPI bus
        let cyw43_pwr = Output::new(p.PIN_23, Level::Low);
        let cs = Output::new(p.PIN_25, Level::High);
        let mut pio = Pio::new(p.PIO0, Irqs);
        let cyw43_spi = PioSpi::new(
            &mut pio.common,
            pio.sm0,
            cyw43_pio::DEFAULT_CLOCK_DIVIDER,
            pio.irq0,
            cs,
            p.PIN_24,
            p.PIN_29,
            DmaChannel::new(p.DMA_CH0, Irqs),
            DmaChannel::new(p.DMA_CH1, Irqs),
        );

        let usb_driver = UsbDriver::new(p.USB, Irqs);

        Self {
            left_motor,
            right_motor,
            led_pin,
            cyw43_pwr,
            cyw43_spi,
            usb_driver,
        }
    }
}
