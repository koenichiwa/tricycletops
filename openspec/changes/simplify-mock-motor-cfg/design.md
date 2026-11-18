# Design: Simplify MockMotor Cfg Attributes

## Context

See `proposal.md` for motivation.

`tricycletops-fw` employs a dual library/binary architecture:
1. Bare-metal target (`thumbv6m-none-eabi` on RP2040 Pico W): Compiles the executable binary (`main.rs`) and target-specific drivers (`board.rs`, `ble_peripheral.rs`, and `MotorControl`).
2. Host developer target (`x86_64-unknown-linux-gnu` or macOS): Compiles the library in test mode (`cargo test-host`) with standard library support (`#![cfg_attr(not(test), no_std)]`).

Currently, `MockMotor` in `src/motor_control.rs` is guarded by:
```rust
#[cfg(any(test, not(all(target_arch = "arm", target_os = "none"))))]
```
Because `MockMotor` is only ever consumed inside `#[cfg(test)] mod tests` in `motor_control.rs` and `car_control.rs`, it is exclusively a test artifact.

## Goals / Non-Goals

**Goals:**
- Replace `#[cfg(any(test, not(all(target_arch = "arm", target_os = "none"))))]` with `#[cfg(test)]` on `MockMotor` and its associated `impl` blocks in `src/motor_control.rs`.
- Ensure clean compilation for all standard developer workflows: host unit tests (`cargo test-host`), target type checking (`cargo check --target thumbv6m-none-eabi`), and target release builds (`cargo build --release --target thumbv6m-none-eabi`).

**Non-Goals:**
- Changing `#[cfg(all(target_arch = "arm", target_os = "none"))]` on hardware-bound drivers (`board.rs`, `ble_peripheral.rs`, `MotorControl`, `TargetCarControl`, or `indicator_task`). These must remain target-gated so that non-test host checks (`cargo check --lib --target <host>`) do not attempt to compile Cortex-M and RP2040 peripherals.
- Modifying Cargo target-specific dependency configurations in `Cargo.toml`.

## Decisions

### Decision 1: Use `#[cfg(test)]` directly on `MockMotor`

Annotate `MockMotor`, its constructor block `impl MockMotor`, and its trait implementation `impl Motor for MockMotor` with `#[cfg(test)]`.

- **Rationale**: Expresses the true role of `MockMotor` as a unit test double. When compiling for tests (`cargo test-host`), `cfg(test)` is active, allowing `tests` modules across `motor_control` and `car_control` to import `crate::motor_control::MockMotor`. When compiling for target or host library checks, test code is stripped.
- **Alternatives Considered**:
  - *Keep the existing `any(test, not(all(target_arch = ...)))`*: Retains ~80 characters of unnecessary boolean logic per annotation.
  - *Move `MockMotor` into `src/motor_control.rs` `mod tests`*: Hinders access from `car_control::tests`, which relies on `crate::motor_control::MockMotor`.

### Decision 2: Keep target guards on hardware drivers and Cargo.toml

Maintain `#[cfg(all(target_arch = "arm", target_os = "none"))]` on hardware drivers and keep `[target.'cfg(...)'.dependencies]` in `Cargo.toml`.

- **Rationale**: Target-gating in `Cargo.toml` is required by Cargo syntax (Cargo does not support `cfg(test)` target dependency tables). In Rust source files, replacing target checks with `cfg(not(test))` would erroneously pull RP2040 hardware dependencies into host builds whenever `test` is not set (such as `cargo check --lib --target <host>`).

## Risks / Trade-offs

- **[Risk]** Non-test host code unexpectedly referencing `MockMotor` would fail to compile under `#[cfg(test)]`.
  - **Mitigation**: Codebase audit confirmed that `MockMotor` is only referenced within `#[cfg(test)]` blocks in `src/car_control.rs` and `src/motor_control.rs`.
