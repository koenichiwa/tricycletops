# Proposal: Refactor Firmware Architecture and Add Safety Watchdog

## Why

The RP2040 firmware (`tricycletops-fw`) currently functions as a monolithic prototype with tight coupling across pin configuration, peripheral initialization, and GATT message handling. More critically, the firmware lacks peripheral-side safety failsafes: if the BLE central disconnects unexpectedly or communications drop while driving, the motors remain energized indefinitely at the last received duty cycle, and any runtime PWM driver error terminates the motor control task permanently. In addition, the codebase has 0% unit test coverage because architecture-specific dependencies prevent running tests on host developer machines. Refactoring into a modular, decoupled architecture introduces vital safety watchdogs, task resilience, a testable library structure for host unit testing, and clear hardware abstractions while keeping the physical pin layout and BLE contract intact.

## What Changes

- **Peripheral Command Watchdog & Disconnection Failsafe**: Introduce an autonomous 500ms command timeout in the motor control loop and an explicit motor cutoff on BLE disconnection to prevent runaway vehicle behavior.
- **Task Resilience & Error Recovery**: Refactor the motor control task into a resilient loop that catches driver errors, safely halts motors, and continues listening for subsequent commands instead of crashing or exiting the task.
- **Testable Library & Host Unit Testing**: Configure a dual `lib.rs` / `main.rs` architecture with target-gated dependencies within the existing crate, enabling fast host-based unit tests via `cargo test` for protocol codecs, kinematics, and boundary conditions without requiring hardware or a separate crate.
- **Board Support Package (BSP) Layer**: Centralize RP2040 GPIO pins, PWM slices, DMA channels, and PIO peripherals into a dedicated `board` module, removing hardware configuration clutter from application orchestration in `main.rs`.
- **Motor Driver Abstraction & Dependency Injection**: Initialize motor drivers in the board/hardware setup and inject them into `CarControl`, decoupling vehicle kinematics from RP2040 pin and slice generics.
- **Protocol Codec Extraction**: Move GATT drive command byte decoding (`fff1`) and duty limits serialization (`fff2`) out of GATT event handling into an isolated, pure-function `protocol` module.
- **State-Driven Observability**: Replace the arbitrary 1-second LED blinking with a state-driven indicator task that reflects peripheral status (e.g. advertising blink, connected solid, failsafe alert).

## Capabilities

### Modified Capabilities
- `remote-car-control`: Adds requirements for firmware-side autonomous command watchdog timeout (auto-stop within 500ms of lost commands) and mandatory motor shutdown upon BLE disconnection.

## Impact

- **Firmware (`tricycletops-fw`)**: Refactored module structure (`lib.rs`, `board`, `protocol`, `indicator`, `car_control`, `motor_control`, `ble_peripheral`, and `main.rs`).
- **Developer Tooling & CI**: Developers can now run `cargo test --lib --target <host>` to execute unit tests locally and in CI without target hardware.
- **Hardware & Pins**: Pin mappings remain completely unchanged (GPIO 0, 1, 2, 3 for motors; GPIO 15 for status LED; GPIO 23, 24, 25, 29, PIO0, DMA0/1 for CYW43).
- **BLE Contract**: GATT Service UUID `fff0` and Characteristics `fff1`, `fff2` maintain identical byte layout and semantics; no breaking changes to the Android app.
