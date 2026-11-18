#![cfg_attr(not(test), no_std)]

pub mod car_control;
pub mod indicator;
pub mod motor_control;
pub mod protocol;

#[cfg(all(target_arch = "arm", target_os = "none"))]
pub mod ble_peripheral;
#[cfg(all(target_arch = "arm", target_os = "none"))]
pub mod board;
