# Tasks

## 1. Testable Library Architecture & Gating

- [x] 1.1 Configure dual `[lib]` and `[[bin]]` layout with target-gated Cortex-M dependencies in `tricycletops-fw/Cargo.toml` and verify host test runner with `cargo test --lib --target host-tuple`
- [x] 1.2 Create `src/lib.rs` exporting testable modules (`protocol`, `car_control`, `motor_control`, `indicator`, `board`, `ble_peripheral`)

## 2. Protocol & Kinematics with Unit Tests

- [x] 2.1 Implement `src/protocol.rs` with byte codecs (`decode_drive_command`, `encode_drive_command`, `encode_duty_limits`) and verify unit tests pass on host via `cargo test --lib -- protocol`
- [x] 2.2 Refactor speed splitting and clamping in `src/car_control.rs` into testable pure functions and verify unit tests pass on host via `cargo test --lib -- car_control`
- [x] 2.3 Implement `src/indicator.rs` defining `IndicatorState` and `indicator_task` with state-specific LED patterns (advertising blink, connected solid, failsafe flash) and verify compilation

## 3. Board Support Package & Driver Decoupling

- [x] 3.1 Create `src/board.rs` encapsulating RP2040 pin configurations, PIO/DMA interrupt bindings, CYW43 wireless initialization, and motor driver instantiation, verifying compilation
- [x] 3.2 Refactor `CarControl` in `src/car_control.rs` to accept pre-configured `MotorControl` instances via dependency injection rather than raw hardware generic pins and slices, verifying compilation

## 4. Safety Watchdog & Failsafe Implementation

- [x] 4.1 Implement autonomous 500ms command timeout watchdog and non-terminating error recovery loop in `CarControl::run` in `src/car_control.rs`
- [x] 4.2 Update `src/ble_peripheral.rs` to use `protocol.rs`, dispatch explicit zero-speed stop commands upon GATT disconnection, and publish `IndicatorState` updates to the indicator

## 5. System Orchestration & Final Verification

- [x] 5.1 Refactor `src/main.rs` to cleanly initialize the board and spawn `cyw43_task`, `car_control_task`, `indicator_task`, and `ble_peripheral::run`
- [x] 5.2 Run complete unit test suite on host (`cargo test --lib --target host-tuple`) and verify 100% test pass
- [x] 5.3 Verify full firmware build passes syntax and release compilation using `cargo check --target thumbv6m-none-eabi` and `cargo build --release --target thumbv6m-none-eabi`
- [x] 5.4 Update `GEMINI.md` documentation to document the refactored module structure, test execution commands, watchdog behavior, and indicator states
