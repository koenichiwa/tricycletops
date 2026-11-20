# Proposal

## Why

The Tricycletops firmware currently allocates two separate RP2040 PWM hardware slices (`PWM_SLICE0` on GP1 for Left, and `PWM_SLICE1` on GP3 for Right), dedicating only one channel on each slice to speed control and consuming two full PWM slices. By consolidating both motor speed channels onto a single hardware PWM slice (`PWM_SLICE0` Channels A and B on GP0 and GP1), the firmware synchronizes the PWM carrier frequency across both drive wheels, frees an entire RP2040 PWM slice for future peripherals, and simplifies hardware resource allocation.

Simultaneously, the firmware's core embedded dependencies in `tricycletops-fw/Cargo.toml` (`cortex-m`, `cortex-m-rt`, `static_cell`, `portable-atomic`, `reqwest`, `defmt`) and the pinned Embassy git patches (`embassy-*`, `cyw43*`) from May 2026 (`1d3c3de`) are behind upstream. Upstream Embassy has transitioned `cyw43` Bluetooth to `bt-hci-transport`, requiring an upgrade to `trouble-host 0.8.0` and `bt-hci 0.10.0`. By structuring these updates into four isolated risk tiers separated by interactive on-hardware flashing test cards, each software stack upgrade and the physical motor rewiring are independently verified on the physical car to isolate any potential regressions.

## What Changes

- **Staged Dependency & Stack Upgrades**: Update firmware dependencies and git patches across four isolated tiers:
  - **Tier 1 (Core Primitives)**: `cortex-m` (0.7.10), `cortex-m-rt` (0.7.7), `static_cell` (2.1.1), `portable-atomic` (1.15.0), `reqwest` (0.13.5), and align `embassy-time` specifier to 0.5.1.
  - **Tier 2 (Logging & Diagnostics)**: `defmt` (1.2.0), verifying target compilation and host buffer unit tests (`cargo test-host`) for the in-tree USB CDC-ACM logger (`src/usb_logger.rs`), with live log streaming verified via `defmt-print` on `/dev/ttyACM0`. Obsolete `defmt-rtt` references are dropped.
  - **Tier 3 (HAL & BLE Host Migration)**: Update Embassy git patches (`embassy-sync`, `embassy-executor`, `embassy-rp`, `embassy-time`, `embassy-usb`, `cyw43`, `cyw43-pio`) to upstream `main`. Upgrade `trouble-host` to 0.8.0 and `bt-hci` to 0.10.0. Adapt `src/board.rs` to new `cyw43` transport/DMA types and refactor `src/ble_peripheral.rs` to trouble-host 0.8 GATT server macros and runner architectures.
  - **Tier 4 (Hardware PWM Slice Consolidation)**: Reconfigure motor PWM actuation to drive Left Motor speed from Channel A (GP0) and Right Motor speed from Channel B (GP1) of `PWM_SLICE0`, assigning Direction pins to GP2 (Left) and GP3 (Right). Adapt `MotorControl` to accept `PwmOutput<'static>` via `embassy_rp::pwm::Pwm::new_output_ab` and `.split()`.
- **Hardware Wiring Diagram**: Update `wiring_diagram.mmd` and documentation in `GEMINI.md` to reflect the updated pin routing.
- **Interactive Flashing & Verification Protocol**: Provide explicit hardware test cards for each incremental tier (wiring instructions, flash commands, expected USB serial logs via `defmt-print`, and mobile app drive maneuvers) allowing the user to flash and verify the physical robot car collaboratively at each step.

## Capabilities

### New Capabilities

*(None)*

### Modified Capabilities

- `remote-car-control`: Update `Wheel Drive Hardware Actuation` requirement to specify that both wheel speed channels are driven from channels A and B of a single shared RP2040 PWM slice with synchronized base frequency and independent channel duty cycles.

## Impact

- **Firmware (`tricycletops-fw`)**:
  - `src/board.rs`: Adapts CYW43/DMA initialization for upstream Embassy `main`, preserves `usb_driver` and `USBCTRL_IRQ`, and instantiates `PWM_SLICE0` in dual-output mode (`new_output_ab`).
  - `src/ble_peripheral.rs`: Migrates GATT server definition, `HostResources`, and stack runners to `trouble-host 0.8.0`.
  - `src/usb_logger.rs`: Validates in-tree `#[defmt::global_logger]` and buffer tests under `defmt 1.2.0`.
  - `src/motor_control.rs`: Updates `MotorControl` struct to hold `PwmOutput<'a>` instead of full `Pwm<'a>`.
  - `Cargo.toml` & `Cargo.lock`: Updated version numbers and git patch revisions.
- **Hardware & Wiring**:
  - Requires physical rewiring of the 4 jumper wires connecting Pico W GPIO headers to motor driver inputs (GP0=Left PWM, GP1=Right PWM, GP2=Left Dir, GP3=Right Dir) during Tier 4.
- **Specifications & Documentation**:
  - `wiring_diagram.mmd` updated.
  - `openspec/specs/remote-car-control/spec.md` updated with single-slice hardware actuation.
  - Android application (`tricycletops-app`) is completely unaffected (GATT service UUIDs and packet protocols remain identical).
