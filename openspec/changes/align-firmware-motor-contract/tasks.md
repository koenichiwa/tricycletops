# Tasks

## 1. Low-Level Motor Control & Actuation Refactoring

- [x] 1.1 Introduce `Direction` enum (`Forward`, `Reverse`) and update `MotorControl` in `tricycletops-fw/src/motor_control.rs` to implement `set_duty(duty: u16, direction: Direction)` and `stop()`. Verify compilation with `cargo check --target thumbv6m-none-eabi`.
- [x] 1.2 Refactor `CarControl` in `tricycletops-fw/src/car_control.rs` to decompose signed `i32` drive commands into raw `(u16, Direction)` tuples via `split_speed()` and actuate wheels via `set_duty`. Verify compilation with `cargo check --target thumbv6m-none-eabi`.

## 2. BLE Characteristic `fff2` 8-Byte Alignment & Deadband Configuration

- [x] 2.1 Define configurable deadband constants (`LEFT_MOTOR_MIN_DUTY`, `RIGHT_MOTOR_MIN_DUTY`) and update `CarControl::duty_limits()` to return four bounds: `((left_min, left_max), (right_min, right_max))`. Verify compilation with `cargo check --target thumbv6m-none-eabi`.
- [x] 2.2 Update characteristic `fff2` in `tricycletops-fw/src/ble_peripheral.rs` to 8 bytes (`[u8; 8]`) and pack `left_min`, `left_max`, `right_min`, `right_max` as little-endian `u16` values. Verify compilation with `cargo check --target thumbv6m-none-eabi`.
- [ ] 2.3 Update `tricycletops-fw/src/main.rs` to pass `duty_limits` to `ble_peripheral::run()`. Verify compilation with `cargo check --target thumbv6m-none-eabi`.

## 3. End-to-End Build Verification & Protocol Documentation

- [ ] 3.1 Compile `tricycletops-fw` with release profile (`cargo build --release --target thumbv6m-none-eabi`) to ensure zero compiler errors or warnings.
- [ ] 3.2 Run local unit tests in `tricycletops-app` (`./gradlew test`) to verify compatibility with 8-byte duty limit parsing and differential drive calculation.
- [ ] 3.3 Update `GEMINI.md` specification to document the 8-byte `fff2` characteristic format and firmware `MotorControl` design.
