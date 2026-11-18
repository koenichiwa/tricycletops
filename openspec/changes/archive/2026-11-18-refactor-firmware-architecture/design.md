# Design: Firmware Architecture Refactoring & Safety Watchdog

## Context

The current `tricycletops-fw` codebase on the Raspberry Pi Pico W (RP2040) is implemented across `main.rs`, `ble_peripheral.rs`, `car_control.rs`, and `motor_control.rs`. Currently:
- `main.rs` mixes CYW43 SPI/PIO/DMA initialization, pin mapping, motor instantiation, and peripheral startup into a single file.
- `CarControl::new` requires 6 generic types tied to RP2040 hardware PWM slices and pins.
- If the BLE link drops while moving, motors run indefinitely at the last commanded duty cycle.
- If `CarControl::run` encounters a `PwmError`, the task exits permanently and the car becomes unresponsive.
- The status LED blinks at an arbitrary 1Hz rate inside `ble_peripheral.rs`, joined directly to the BLE stack.
- The codebase has 0% unit test coverage because Cortex-M assembly and RP2040 peripheral crates prevent `cargo test` from running on the developer host machine.

See `proposal.md` for overall motivation and goals.

## Goals / Non-Goals

**Goals:**
- **Pin Preservation**: Maintain 100% compatibility with existing hardware wiring:
  - Left Motor: PIN_1 (Speed / PWM 0B), PIN_0 (Direction)
  - Right Motor: PIN_3 (Speed / PWM 1B), PIN_2 (Direction)
  - Status LED: PIN_15
  - CYW43: PIN_23 (PWR), PIN_25 (CS), PIN_24 (DIO), PIN_29 (CLK), PIO0, DMA_CH0, DMA_CH1
- **BLE Contract Preservation**: Maintain exact GATT layout (Service `fff0`, Drive `fff1`, Duty Limits `fff2`).
- **Autonomous Safety Failsafe**: Ensure motors stop within 500ms if BLE communication is interrupted or central disconnects.
- **Modular Architecture**: Separate hardware initialization (BSP), protocol serialization, motor kinematics, and BLE peripheral handling into distinct modules.
- **Task Resilience**: Ensure motor control task survives transient driver errors.
- **Host Unit Testability**: Introduce a testable `lib.rs` inside the same crate, allowing `cargo test` to execute protocol and kinematics unit tests on host machines without requiring target hardware.

**Non-Goals:**
- Splitting the repository into a multi-crate Cargo workspace.
- Changing pin mappings or microcontroller platform.
- Modifying the Android application codebase.
- Changing BLE advertising names or GATT UUIDs.
- Dynamic memory allocation (`no_std` and static allocation remain strictly enforced).

## Decisions

### 1. Module Structure and Testable Library Layout

We will structure `tricycletops-fw` with both a library target (`src/lib.rs`) and a binary target (`src/main.rs`) in the same package:

```
src/
├── lib.rs             # Library root exposing testable core modules (protocol, kinematics)
├── main.rs            # Binary entry point: board init, task spawning, orchestration
├── board.rs           # BSP: pin assignments, CYW43 setup, motor driver instantiation
├── car_control.rs     # Vehicle kinematics, deadband handling, watchdog-protected control task
├── motor_control.rs   # Low-level H-bridge direction & PWM speed driver
├── protocol.rs        # Pure byte codecs for GATT characteristics (fff1, fff2)
├── indicator.rs       # Status LED driver reflecting peripheral connection states
└── ble_peripheral.rs  # trouble-host peripheral stack, advertisement, GATT server
```

In `Cargo.toml`:
```toml
[lib]
name = "tricycletops_fw"
path = "src/lib.rs"

[[bin]]
name = "tricycletops-fw"
path = "src/main.rs"
test = false
```

Target-specific dependencies (such as `cortex-m`, `cortex-m-rt`, and platform-specific features of `embassy-executor` / `embassy-rp`) will be target-gated under `[target.'cfg(all(target_arch = "arm", target_os = "none"))'.dependencies]`. Setting `test = false` on `[[bin]]` ensures `cargo test` runs against `lib.rs` without attempting to compile the ARM Cortex-M `#![no_main]` binary on the host.

*Rationale*: Enables instantaneous host-based unit tests via `cargo test` without the overhead of creating, publishing, or linking multiple repository crates.

*Alternatives considered*:
- *Separate workspace crate (`tricycletops-core`)*: Rejected by user preference to keep everything in a single clean crate.
- *Testing only via hardware emulation (QEMU/probe-rs)*: Rejected due to high setup complexity and slower CI feedback loops compared to native host unit tests.

### 2. Motor Control Watchdog and Resilient Task Loop

In `car_control.rs`, `CarControl::run` will be wrapped in `embassy_time::with_timeout`:

```rust
pub async fn run(
    &mut self,
    drive_command_signal: &'static Signal<ThreadModeRawMutex, DriveCommand>,
) -> ! {
    const WATCHDOG_TIMEOUT: Duration = Duration::from_millis(500);

    loop {
        match with_timeout(WATCHDOG_TIMEOUT, drive_command_signal.wait()).await {
            Ok(cmd) => {
                if let Err(err) = self.set_speeds(cmd.left, cmd.right) {
                    warn!("PWM error setting speeds: {:?}", defmt::Debug2Format(&err));
                    let _ = self.stop();
                }
            }
            Err(_) => {
                // Timeout elapsed without receiving a new command -> safety stop
                let _ = self.stop();
            }
        }
    }
}
```

In `ble_peripheral.rs`, when `GattConnectionEvent::Disconnected` is encountered, the GATT task will also immediately signal `DriveCommand { left: 0, right: 0 }` to ensure zero latency stopping upon intentional or abrupt disconnection.

*Rationale*: A 500ms timeout provides ample room for the Android app's 25Hz (40ms) update rate while halting the vehicle within half a second if packets cease or link drops.

### 3. Dependency Injection in `CarControl`

Instead of `CarControl::new` taking generic slices and pin types, `board.rs` will initialize the two `MotorControl` instances and pass them into `CarControl::new`:

```rust
pub struct CarControl<'a> {
    left_motor: MotorControl<'a>,
    right_motor: MotorControl<'a>,
}

impl<'a> CarControl<'a> {
    pub fn new(left_motor: MotorControl<'a>, right_motor: MotorControl<'a>) -> Self { ... }
}
```

Kinematics calculation (`split_speed`) will be an independent, pure function that can be unit-tested directly on host without requiring hardware mocks.

*Rationale*: Removes complex trait bounds (`Slice`, `ChannelBPin`) from `CarControl`, making vehicle kinematics clean and decoupled from low-level pin typing.

### 4. Protocol Extraction (`protocol.rs`)

Move `decode_drive_command`, `encode_drive_command`, and `encode_duty_limits` to `protocol.rs`:
- `decode_drive_command(data: &[u8]) -> Option<DriveCommand>`
- `encode_drive_command(cmd: DriveCommand) -> [u8; 8]`
- `encode_duty_limits(limits: DutyLimits) -> [u8; 8]`

*Rationale*: Pure functions without dependencies on Embassy or trouble-host; easily validated and tested.

### 5. Status Indication Decoupling (`indicator.rs`)

Extract LED blinking into an `indicator_task` listening to a `Signal<ThreadModeRawMutex, IndicatorState>` or shared state:
- `Advertising`: Blinking pattern (e.g. 500ms on, 500ms off)
- `Connected`: Solid on
- `Disconnected` / `Failsafe`: Rapid blink or return to advertising pattern

*Rationale*: Visual hardware feedback informs the user whether the Pico W is ready for pairing, actively connected, or disconnected.

## Test Strategy & Coverage Matrix

### Host Unit Testing (`cargo test`)
Unit tests will be implemented directly in the modules using standard Rust `#[cfg(test)]`:

| Module | Test Target | Scenarios Verified |
|:---|:---|:---|
| `protocol` | `decode_drive_command` | - Valid 8-byte LE payload decoding<br>- Rejection of short payloads (< 8 bytes)<br>- Rejection of long payloads (> 8 bytes)<br>- Extreme boundary values (`i32::MIN`, `i32::MAX`, `0`) |
| `protocol` | `encode_drive_command` | - Little-endian 8-byte serialization matches specification<br>- Round-trip equality: `decode(encode(c)) == Some(c)` |
| `protocol` | `encode_duty_limits` | - Correct packing of four 16-bit LE integers (`left_min`, `left_max`, `right_min`, `right_max`) into 8 bytes |
| `car_control` | `split_speed` | - Positive values map to `Direction::Forward`<br>- Negative values map to `Direction::Reverse` with absolute duty<br>- Clamping to `[-max_duty, max_duty]`<br>- Zero speed maps to zero duty |

### Firmware Compilation & Hardware Verification
- **Compilation Check**: `cargo check --target thumbv6m-none-eabi` to verify Cortex-M target compilation.
- **Release Build**: `cargo build --release --target thumbv6m-none-eabi` to verify flash budget and absence of compiler warnings.
- **Diagnostics**: `defmt` logging over RTT (`defmt-rtt`) to verify task startup, peripheral connection events, and watchdog timeouts on real hardware.

## Risks / Trade-offs

- **[Risk] Watchdog falsely stopping during erratic BLE traffic**
  → *Mitigation*: The app sends commands at 25Hz (every 40ms). A 500ms timeout tolerates up to 12 consecutively dropped packets before stopping, which is robust yet responsive enough to prevent runaway crashes.
- **[Risk] Target gating complexity in Cargo.toml**
  → *Mitigation*: Gating `cortex-m` and `cortex-m-rt` to `cfg(all(target_arch = "arm", target_os = "none"))` is the standard embedded Rust idiom for dual-mode crates.
- **[Risk] Signal overwrites or race conditions**
  → *Mitigation*: `Signal` represents the most recent desired command state, which is ideal for flight/drive controllers where stale commands must be discarded.
