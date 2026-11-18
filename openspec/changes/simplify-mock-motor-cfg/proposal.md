# Proposal: Simplify MockMotor Cfg Attributes

## Why

In `tricycletops-fw/src/motor_control.rs`, `MockMotor` is annotated with the complex conditional `#[cfg(any(test, not(all(target_arch = "arm", target_os = "none"))))]`. This defensive check was introduced to permit non-target compilation, but in practice the firmware has only one physical non-test target (the RP2040 Raspberry Pi Pico W) and `MockMotor` is purely a unit test double consumed in test modules (`car_control::tests` and `motor_control::tests`).

Replacing this verbose expression with `#[cfg(test)]` clarifies intent, reduces cognitive overhead, and aligns with idiomatic Rust embedded test design.

## What Changes

- Replace `#[cfg(any(test, not(all(target_arch = "arm", target_os = "none"))))]` on `MockMotor`, its constructor `impl MockMotor`, and `impl Motor for MockMotor` with `#[cfg(test)]` in `tricycletops-fw/src/motor_control.rs`.
- Verify that host unit tests (`cargo test-host`) and bare-metal target builds (`cargo check --target thumbv6m-none-eabi`) continue to compile and pass with zero warnings.

## Capabilities

### New Capabilities
*(None)*

### Modified Capabilities
*(None)*

## Impact

- **Firmware (`tricycletops-fw`)**:
  - `src/motor_control.rs`: Simplified conditional compilation attributes for `MockMotor`.
- **Android App (`tricycletops-app`)**: No impact.
- **Protocol / Hardware**: No impact.
