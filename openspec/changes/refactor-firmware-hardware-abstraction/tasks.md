# Tasks: Firmware Hardware Abstraction & Resilient State Indication

## 1. Hardware Abstraction & Motor Driver

- [x] 1.1 Define the `Motor` trait and `Direction` enum in `src/motor_control.rs`, provide `MockMotor` for tests, and verify unit tests pass on host (`cargo test --lib --target x86_64-unknown-linux-gnu`).
- [x] 1.2 Implement `Motor` for RP2040 `MotorControl` using `embassy_rp` PWM and GPIO output pins, and verify target compilation (`cargo check --target thumbv6m-none-eabi`).

## 2. Decoupled CarControl Domain Logic & Kinematics

- [ ] 2.1 Refactor `CarControl<L: Motor, R: Motor>` in `src/car_control.rs` to remove RP2040-specific imports, enabling pure `#![no_std]` compilation on host.
- [ ] 2.2 Add `IndicatorState` signaling in `CarControl::run` on 500ms watchdog timeout and actuator errors, and verify state transitions and motor stop logic using `MockMotor` in host unit tests (`cargo test --lib`).

## 3. Declarative Indicator State Machine

- [ ] 3.1 Implement `IndicatorState` (Advertising, Connected, Failsafe) and declarative `BlinkPattern` in `src/indicator.rs`, and verify pattern definitions in host unit tests (`cargo test --lib`).
- [ ] 3.2 Implement the flattened, interruptible `step_phase` blink runner for `indicator_task`, and verify target compilation (`cargo check --target thumbv6m-none-eabi`).

## 4. BLE Peripheral Resilience & System Wiring

- [ ] 4.1 Harden `src/ble_peripheral.rs` by replacing `panic!` on advertisement or connection error with warning logs and an async retry backoff loop.
- [ ] 4.2 Wire concrete `MotorControl` instances in `src/board.rs` and update task signatures and signals in `src/main.rs`, verifying target release build (`cargo build --release --target thumbv6m-none-eabi`).

## 5. Full Validation & Verification

- [ ] 5.1 Execute all host unit tests (`cargo test --lib --target host-tuple`) to ensure 100% test pass rate across protocol, kinematics, and indicator modules.
- [ ] 5.2 Run firmware formatting and clippy (`cargo fmt --check` and `cargo clippy --target thumbv6m-none-eabi`), verifying zero errors or warnings.
