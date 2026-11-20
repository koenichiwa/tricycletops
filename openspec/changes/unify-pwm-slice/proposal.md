# Proposal

## Why

The Tricycletops firmware currently allocates two separate RP2040 PWM hardware slices (`PWM_SLICE0` on GP1 for Left Motor, and `PWM_SLICE1` on GP3 for Right Motor), dedicating only one channel on each slice to speed control and consuming two full PWM slices. By consolidating both motor speed channels onto a single hardware PWM slice (`PWM_SLICE0` Channels A and B on GP0 and GP1), the firmware synchronizes the PWM carrier frequency across both drive wheels, frees an entire RP2040 PWM slice for future peripherals or sensors, and simplifies hardware resource allocation.

## What Changes

- **Hardware PWM Slice Consolidation**: Reconfigure motor speed actuation to drive Left Motor speed from Channel A (GP0) and Right Motor speed from Channel B (GP1) of `PWM_SLICE0`, assigning Direction pins to GP2 (Left) and GP3 (Right).
- **Motor Control Driver Abstraction**: Refactor `MotorControl` in `tricycletops-fw/src/motor_control.rs` to hold `PwmOutput<'a>` (obtained via `embassy_rp::pwm::Pwm::new_output_ab` and `.split()`) instead of a full `Pwm<'a>` instance, retaining the existing `Motor` trait contract.
- **Board Support Package Refactoring**: Update `Board::init` in `tricycletops-fw/src/board.rs` to initialize `PWM_SLICE0` with dual outputs (`new_output_ab`), split it into independent channel outputs, and wire GP2 and GP3 as direction GPIO outputs.
- **Interactive Flashing & Verification Protocol**: Provide an explicit hardware Test Card detailing physical jumper rewiring instructions, flash commands, and drive verification steps (forward, reverse, steering, and watchdog halt) for the user to validate on the physical robot car.
- **Hardware Documentation**: Update `GEMINI.md` and repository wiring documentation to reflect the unified `PWM_SLICE0` architecture and pin routing.

## Capabilities

### New Capabilities

*(None)*

### Modified Capabilities

- `remote-car-control`: Update `Wheel Drive Hardware Actuation` requirement to specify that both wheel speed channels are driven from channels A and B of a single shared RP2040 PWM hardware slice, ensuring synchronized base frequency while maintaining independent channel duty cycles.

## Impact

- **Firmware (`tricycletops-fw`)**:
  - `src/board.rs`: Updates `PWM_SLICE0` configuration and pin assignments.
  - `src/motor_control.rs`: Updates `MotorControl` struct to hold `PwmOutput<'a>`.
- **Hardware & Wiring**:
  - Requires physical rewiring of the 4 jumper wires connecting the Pico W GPIO headers to the motor driver inputs:
    - GP0 (Header Pin 1): Left Motor PWM (Slice 0 Channel A)
    - GP1 (Header Pin 2): Right Motor PWM (Slice 0 Channel B)
    - GP2 (Header Pin 4): Left Motor Direction (GPIO Output)
    - GP3 (Header Pin 5): Right Motor Direction (GPIO Output)
- **Specifications & Documentation**:
  - `openspec/specs/remote-car-control/spec.md` updated with single-slice hardware actuation.
  - `GEMINI.md` updated with new pin routing and hardware conventions.
  - Android application (`tricycletops-app`) is completely unaffected (GATT service UUIDs and packet protocols remain identical).
