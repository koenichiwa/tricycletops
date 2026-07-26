#![no_std]
#![no_main]

mod motor_control;
mod car_control;

use embassy_executor::Spawner;
use embassy_rp::gpio::{Level, Output};
use embassy_rp::pwm::{Config, Pwm};
use embassy_sync::blocking_mutex::raw::ThreadModeRawMutex;
use embassy_sync::channel::{Channel, Receiver};
use embassy_time::Timer;
use motor_control::MotorControl;
use car_control::CarControl;

// defmt Logging
use defmt_rtt as _;
use defmt::info;

use panic_probe as _;

#[derive(Clone, Copy, defmt::Format)]
pub struct DriveCommand {
    left: f32, 
    right: f32,
}

// Bounded MPMC channel with a capacity of 4 messages
static CAR_COMMAND_CHANNEL: Channel<ThreadModeRawMutex, DriveCommand, 4> = Channel::new();

#[embassy_executor::task]
async fn car_control_task(
    mut car: CarControl<'static>,
    receiver: Receiver<'static, ThreadModeRawMutex, DriveCommand, 4>,
) {
    loop {
        let DriveCommand { left, right } = receiver.receive().await;
        info!("Car control task: setting speeds left={}, right={}", left, right);
        if let Err(_error) = car.set_speeds(left, right) {
            break;
        }
    }

    let _ = car.set_speeds(0.0, 0.0);
}

#[embassy_executor::main]
async fn main(spawner: Spawner) {
    let p = embassy_rp::init(Default::default());
    
    info!("Initializing the program");

    // PWM configuration: top sets the PWM period/resolution
    let config = Config::default();

    // Motor A (Left): PIN_1 is Slice 0 Channel B
    let motor_a_speed = Pwm::new_output_b(p.PWM_SLICE0, p.PIN_1, config.clone());
    let motor_a_direction = Output::new(p.PIN_0, Level::Low);
    let motor_a = MotorControl::new(motor_a_speed, motor_a_direction);

    // Motor B (Right): PIN_3 is Slice 1 Channel B
    let motor_b_speed = Pwm::new_output_b(p.PWM_SLICE1, p.PIN_3, config);
    let motor_b_direction = Output::new(p.PIN_2, Level::Low);
    let motor_b = MotorControl::new(motor_b_speed, motor_b_direction);

    let car = CarControl::new(motor_a, motor_b);

    // Get the receiver handle and spawn the control task
    let receiver = CAR_COMMAND_CHANNEL.receiver();
    spawner.spawn(car_control_task(car, receiver)).unwrap();

    // Get the sender handle for sending commands
    let sender = CAR_COMMAND_CHANNEL.sender();

    // Test script. Here we should fetch data from a source
    loop {
        info!("Sending Drive command (0.7, -0.7)");
        sender.send(DriveCommand { left: 0.7, right: -0.7 }).await;

        Timer::after_secs(3).await;

        info!("Sending Drive command (0.4, -0.4) - Spin");
        sender.send(DriveCommand { left: 0.9, right: -0.9 }).await;

        Timer::after_secs(2).await;

        info!("Sending Stop command");
        sender.send(DriveCommand {left: 0.0, right: 0.0}).await;

        Timer::after_secs(2).await;
    }
}
