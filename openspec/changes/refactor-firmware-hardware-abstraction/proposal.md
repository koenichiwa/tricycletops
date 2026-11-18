# Proposal: Firmware Hardware Abstraction & Resilient State Indication

## Why

While `tricycletops-fw` was recently modularized, `CarControl`, `MotorControl`, and `indicator_task` remain tightly coupled to RP2040-specific hardware types (`embassy_rp::pwm::Pwm` and `embassy_rp::gpio::Output`), forcing them to be target-gated. As a result, core vehicle control logic, safety stops, and status patterns cannot be unit-tested on host machines (`cargo test`). Additionally, `indicator_task` contains complex nested `select` logic with an unused `Failsafe` state, and the BLE advertising loop crashes the MCU via `panic!` on transient radio errors. 

Introducing a clean hardware abstraction layer (`Motor` trait), declarative blink patterns with active watchdog failsafe feedback, and a zero-panic BLE retry loop will make the firmware robust, maintainable, and 100% testable on developer hosts without altering the BLE GATT contract with the Android app.

## What Changes

- **Hardware Abstraction Layer (`Motor` trait)**: Introduce a decoupled `Motor` trait for setting duty cycles and stopping. Make `CarControl` generic over `Motor`, allowing pure `#![no_std]` domain logic and comprehensive host unit testing via `MockMotor`.
- **Target-Agnostic `MotorControl`**: Implement the `Motor` trait on a generic H-bridge controller that drives any PWM and digital output pins, confining RP2040-specific pin bindings entirely to `board.rs`.
- **Declarative Indicator State Machine**: Replace nested `select` branching in `indicator.rs` with declarative `BlinkPattern` timings (500ms on/off for Advertising, Solid ON for Connected, 100ms on/off for Failsafe) and an interruptible phase stepper.
- **Active Failsafe Diagnostics**: Wire the `CarControl` 500ms watchdog timeout and motor actuator errors to signal `IndicatorState::Failsafe`, providing immediate physical LED feedback when communication stalls.
- **Zero-Panic BLE Peripheral Lifecycle**: Replace `panic!` calls in `ble_peripheral.rs` during advertisement or connection failures with structured logging and async retry backoff.
- **Comprehensive Host Unit Test Suite**: Add host unit tests for `CarControl` kinematics, deadband handling, watchdog transitions, and motor fault tolerance using `cargo test --lib`.

## Capabilities

### New Capabilities
*(None)*

### Modified Capabilities
- `remote-car-control`: Formally specify the peripheral's autonomous 500ms command timeout watchdog and the three-state visual indicator behavior (Advertising, Connected, Failsafe) on the status LED.

## Impact

- **Firmware (`tricycletops-fw`)**:
  - `src/motor_control.rs`: Exposes `Motor` trait, generic H-bridge implementation, and `MockMotor`.
  - `src/car_control.rs`: Decoupled from RP2040 types; implements portable domain control and watchdog failsafe signaling.
  - `src/indicator.rs`: Replaced with declarative `BlinkPattern` state machine.
  - `src/ble_peripheral.rs`: Hardened against transient advertising and disconnect errors without panicking.
  - `src/board.rs`: Instantiates concrete RP2040 `MotorControl` instances for `CarControl`.
  - `src/lib.rs` & `src/main.rs`: Updates task signatures and dependency wiring.
- **Android App (`tricycletops-app`)**: No impact. The GATT service UUID (`fff0`), drive characteristic (`fff1`), duty limits characteristic (`fff2`), and little-endian wire protocols remain 100% unchanged.
