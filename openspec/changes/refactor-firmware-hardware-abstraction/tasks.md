# Tasks: Firmware Hardware Abstraction & Resilient State Indication

## 1. Hardware Abstraction & Motor Driver

- [x] 1.1 Define the `Motor` trait and `Direction` enum in `src/motor_control.rs`, provide `MockMotor` for tests, and verify unit tests pass on host (`cargo test --lib --target x86_64-unknown-linux-gnu`).
- [x] 1.2 Implement `Motor` for RP2040 `MotorControl` using `embassy_rp` PWM and GPIO output pins, and verify target compilation (`cargo check --target thumbv6m-none-eabi`).

## 2. Decoupled CarControl Domain Logic & Kinematics

- [x] 2.1 Refactor `CarControl<L: Motor, R: Motor>` in `src/car_control.rs` to remove RP2040-specific imports, enabling pure `#![no_std]` compilation on host.
- [x] 2.2 Add `IndicatorState` signaling in `CarControl::run` on 500ms watchdog timeout and actuator errors, and verify state transitions and motor stop logic using `MockMotor` in host unit tests (`cargo test --lib`).

## 3. Declarative Indicator State Machine

- [x] 3.1 Implement `IndicatorState` (Advertising, Connected, Failsafe) and declarative `BlinkPattern` in `src/indicator.rs`, and verify pattern definitions in host unit tests (`cargo test --lib`).
- [x] 3.2 Implement the flattened, interruptible `step_phase` blink runner for `indicator_task`, and verify target compilation (`cargo check --target thumbv6m-none-eabi`).

## 4. BLE Peripheral Resilience & System Wiring

- [x] 4.1 Harden `src/ble_peripheral.rs` by replacing `panic!` on advertisement or connection error with warning logs and an async retry backoff loop.
- [x] 4.2 Wire concrete `MotorControl` instances in `src/board.rs` and update task signatures and signals in `src/main.rs`, verifying target release build (`cargo build --release --target thumbv6m-none-eabi`).

## 5. Full Validation & Verification

- [x] 5.1 Execute all host unit tests (`cargo test --lib --target host-tuple`) to ensure 100% test pass rate across protocol, kinematics, and indicator modules.
- [x] 5.2 Run firmware formatting and clippy (`cargo fmt --check` and `cargo clippy --target thumbv6m-none-eabi`), verifying zero errors or warnings.

## 6. Disconnection Watchdog Disarming & Failsafe Resilience

- [x] 6.1 Introduce `CarCommand` enum (`Drive(DriveCommand)`, `Disconnected`) and `handle_disconnect` in `src/car_control.rs`, disarming the 500ms command timeout watchdog and clearing failsafe state on disconnect without indicator clobbering, verified by host unit tests (`cargo test --lib --target host-tuple`).
- [x] 6.2 Update `src/ble_peripheral.rs` to signal `CarCommand::Disconnected` on link termination, and update signal types in `src/main.rs`, verifying target compilation (`cargo check --target thumbv6m-none-eabi`).
- [ ] 6.3 Execute full host unit test suite, target release build, and workspace pre-commit checks (`cargo test --lib --target host-tuple`, `cargo build --release --target thumbv6m-none-eabi`, and `pre-commit run --all-files`).
