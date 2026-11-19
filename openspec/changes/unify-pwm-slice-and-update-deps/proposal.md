# Proposal

## Why

The Tricycletops firmware currently allocates two separate RP2040 PWM hardware slices (`PWM_SLICE0` on GP1 for Left, and `PWM_SLICE1` on GP3 for Right), dedicating only one channel on each slice to speed control and consuming two full PWM slices. By consolidating both motor speed channels onto a single hardware PWM slice (`PWM_SLICE0` Channels A and B on GP0 and GP1), the firmware synchronizes the PWM carrier frequency across both drive wheels, frees an entire RP2040 PWM slice for future peripherals, and simplifies hardware resource allocation. Simultaneously, several key embedded dependencies in `tricycletops-fw/Cargo.toml` (`cortex-m`, `cortex-m-rt`, `static_cell`, `portable-atomic`, `defmt`, `defmt-rtt`) are behind their latest minor releases and can be safely updated in incremental tiers verified through interactive hardware testing cards.

## What Changes

- **Single PWM Slice Motor Actuation**: Reconfigure motor PWM actuation to drive Left Motor speed from Channel A (GP0) and Right Motor speed from Channel B (GP1) of `PWM_SLICE0`, while assigning Direction pins to GP2 (Left) and GP3 (Right).
- **Driver Abstraction Update**: Adapt `MotorControl` to accept `PwmOutput<'static>` (or generic `SetDutyCycle`) so both independent channels of a single slice can be controlled cleanly via `embassy_rp::pwm::Pwm::new_output_ab` and `.split()`.
- **Hardware Wiring Diagram**: Update `wiring_diagram.mmd` and documentation in `GEMINI.md` to reflect the updated pin routing.
- **Incremental Dependency Updates**: Update firmware dependencies in `tricycletops-fw/Cargo.toml` across staged risk tiers:
  - Tier 1: `cortex-m` (0.7.10), `cortex-m-rt` (0.7.7), `static_cell` (2.1.1), `portable-atomic` (1.15.0).
  - Tier 2: `defmt` (1.2.0), `defmt-rtt` (1.3.1).
- **Interactive Flashing & Verification Protocol**: Provide explicit hardware test cards for each incremental stage (wiring instructions, flash commands, expected RTT logs, and mobile app drive maneuvers) allowing the user to flash and verify the physical car collaboratively.

## Capabilities

### New Capabilities

*(None)*

### Modified Capabilities

- `remote-car-control`: Update `Wheel Drive Hardware Actuation` requirement to specify that both wheel speed channels are driven from channels A and B of a single shared RP2040 PWM slice with synchronized base frequency and independent channel duty cycles.

## Impact

- **Firmware (`tricycletops-fw`)**:
  - `src/board.rs`: Instantiates `PWM_SLICE0` in dual-output mode (`new_output_ab`), splits it into two `PwmOutput` instances, and remaps GPIO pins.
  - `src/motor_control.rs`: Updates `MotorControl` struct to hold `PwmOutput<'a>` instead of full `Pwm<'a>`.
  - `Cargo.toml` & `Cargo.lock`: Updated version numbers for core cortex and defmt crates.
- **Hardware & Wiring**:
  - Requires physical rewiring of the 4 jumper wires connecting Pico W GPIO headers to motor driver inputs (GP0=Left PWM, GP1=Right PWM, GP2=Left Dir, GP3=Right Dir).
- **Specifications & Documentation**:
  - `wiring_diagram.mmd` updated.
  - `openspec/specs/remote-car-control/spec.md` updated with single-slice hardware actuation.
  - Android application (`tricycletops-app`) is completely unaffected (GATT service UUIDs and packet protocols remain identical).
