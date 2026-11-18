#![cfg_attr(not(test), no_std)]

pub mod protocol;
pub mod car_control;
pub mod motor_control;
pub mod indicator;

#[cfg(all(target_arch = "arm", target_os = "none"))]
pub mod board;
#[cfg(all(target_arch = "arm", target_os = "none"))]
pub mod ble_peripheral;
