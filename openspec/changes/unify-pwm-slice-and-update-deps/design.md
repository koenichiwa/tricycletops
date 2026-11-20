# Design: Unify PWM Slice and Update Dependencies

## Context

The Tricycletops RP2040 firmware currently configures motor speed actuation across two distinct hardware PWM slices:
- Left Motor: Speed on GP1 (`PWM_SLICE0`, Channel B), Direction on GP0.
- Right Motor: Speed on GP3 (`PWM_SLICE1`, Channel B), Direction on GP2.

The RP2040 hardware PWM peripheral contains 8 dual-channel slices (16 PWM outputs total: 8 `A` channels and 8 `B` channels). Each slice has a shared counter and wrap register (`top`), but separate compare registers (`cc.a` and `cc.b`). Driving two DC gearmotors requires identical PWM frequency and duty scaling, making a shared slice optimal.

Additionally, several core dependencies in `tricycletops-fw/Cargo.toml` (`cortex-m`, `cortex-m-rt`, `static_cell`, `portable-atomic`, `reqwest`, `defmt`) and the pinned Embassy git patches (`embassy-*`, `cyw43*` from May 2026, `1d3c3de`) are behind upstream. Upstream Embassy `main` has transitioned `cyw43` Bluetooth to `bt-hci-transport`, which requires migrating `trouble-host` to 0.8.0 and `bt-hci` to 0.10.0. Diagnostic logging was recently migrated to an in-tree USB CDC-ACM transport (`src/usb_logger.rs`), removing `defmt-rtt`.

To ensure system stability, all updates are applied across staged risk tiers separated by interactive on-hardware flashing Test Cards.

See `proposal.md` for motivation and `specs/remote-car-control/spec.md` for behavioral requirements.

## Goals / Non-Goals

**Goals:**
- Consolidate Left and Right motor speed actuation onto `PWM_SLICE0` Channels A and B.
- Refactor `Board::init` to initialize `PWM_SLICE0` via `Pwm::new_output_ab` and separate channels using `pwm.split()`, while preserving `usb_driver` and `USBCTRL_IRQ`.
- Update `MotorControl` to hold `PwmOutput<'a>` implementing `SetDutyCycle`.
- Update `wiring_diagram.mmd` and repo documentation.
- Upgrade Tier 1 dependencies (`cortex-m`, `cortex-m-rt`, `static_cell`, `portable-atomic`, `reqwest`, `embassy-time` specifier).
- Upgrade Tier 2 logging dependencies (`defmt` 1.2.0), verifying in-tree `src/usb_logger.rs` via host unit tests (`cargo test-host`) and target checks, dropping obsolete `defmt-rtt` references.
- Upgrade Tier 3 HAL & BLE stack: update Embassy git patches (`embassy-*`, `cyw43*`) to upstream `main`, upgrade `trouble-host` to 0.8.0 and `bt-hci` to 0.10.0, and adapt `src/board.rs` and `src/ble_peripheral.rs`.
- Establish an interactive execution flow where each tier provides a structured hardware Test Card for user flashing and physical validation.

**Non-Goals:**
- Modifying central BLE protocols, GATT UUIDs, packet encodings, or the Android application (`tricycletops-app`).
- Changing motor control kinematics, deadbands, or differential drive calculations.

## Decisions

### Decision 1: Pin Assignment on RP2040

We assign the motor control pins as follows:
- **GP0 (Pin 1)**: Left Motor Speed (`PWM_SLICE0`, Channel A)
- **GP1 (Pin 2)**: Right Motor Speed (`PWM_SLICE0`, Channel B)
- **GND (Pin 3)**: Common Ground Bus
- **GP2 (Pin 4)**: Left Motor Direction (GPIO Output)
- **GP3 (Pin 5)**: Right Motor Direction (GPIO Output)

*Rationale*:
- Uses contiguous pins (GP0–GP3) on header pins 1–5 of the Raspberry Pi Pico W.
- Grouping speed signals on GP0/GP1 naturally maps to Slice 0 channels A and B.
- Grouping direction signals on GP2/GP3 allows identical GPIO output handling.
- Does not conflict with CYW43 control pins (GP23, GP24, GP25, GP29), onboard LED (GP15), or USB.

### Decision 2: Embassy-RP Dual Channel PWM Splitting

We configure `PWM_SLICE0` using `Pwm::new_output_ab` and call `.split()`:

```rust
let pwm_config = PwmConfig::default();
let pwm = Pwm::new_output_ab(p.PWM_SLICE0, p.PIN_0, p.PIN_1, pwm_config);
let (Some(left_speed), Some(right_speed)) = pwm.split() else {
    unreachable!("Both pins A and B were provided to new_output_ab");
};
```

*Rationale*:
`Pwm::split()` returns `(Option<PwmOutput<'d>>, Option<PwmOutput<'d>>)`. Each `PwmOutput` independently implements `embedded_hal::pwm::SetDutyCycle`, allowing channel-specific compare register writes without mutexes or shared mutable state.

### Decision 3: MotorControl Abstraction with `PwmOutput`

In `tricycletops-fw/src/motor_control.rs`:
```rust
#[cfg(all(target_arch = "arm", target_os = "none"))]
pub struct MotorControl<'a> {
    speed_pin: PwmOutput<'a>,
    direction_pin: Output<'a>,
}
```
All existing methods (`set_duty`, `stop`, `max_duty`) invoke `SetDutyCycle` methods on `self.speed_pin`, preserving identical behavior and the existing `Motor` trait contract.

### Decision 4: Staged Dependency Tiers & Migration Strategy

Dependency updates in `Cargo.toml` are applied incrementally across four isolated tiers:
- **Tier 1 (Core Primitives)**:
  - `cortex-m`: `0.7.6` -> `0.7.10`
  - `cortex-m-rt`: `0.7.0` -> `0.7.7`
  - `static_cell`: `2.1.0` -> `2.1.1`
  - `portable-atomic`: `1.5.1` -> `1.15.0`
  - `reqwest`: `0.13.4` -> `0.13.5`
  - `embassy-time` specifier alignment: `0.5.0` -> `0.5.1`
  - *Verification*: `cargo test-host` & `cargo check --target thumbv6m-none-eabi`. Test Card 1 confirms MCU boots and advertises BLE.
- **Tier 2 (Defmt & USB Logger)**:
  - `defmt`: `1.1.1` -> `1.2.0`.
  - Remove all remaining references to `defmt-rtt`.
  - Validate in-tree `src/usb_logger.rs` (`#[defmt::global_logger]`, `defmt::Encoder`).
  - *Verification*: `cargo test-host` (verifying decoupled buffer and chunk tests) & `cargo check`. Test Card 2 confirms binary defmt frames stream over `/dev/ttyACM0` and decode via `defmt-print`.
- **Tier 3 (Embassy Git Patches & Trouble-Host 0.8)**:
  - Update `[patch.crates-io]` entries (`embassy-sync`, `embassy-executor`, `embassy-rp`, `embassy-time`, `embassy-usb`, `cyw43`, `cyw43-pio`) to upstream `main`.
  - Upgrade `trouble-host` to `0.8.0` and `bt-hci` to `0.10.0`.
  - Adapt `src/board.rs`: wrap CYW43 `bt_device` (implementing `bt_hci_transport::Transport`) with `ExternalController`, update PIO/DMA types for `PioSpi`, and preserve `usb_driver`.
  - Adapt `src/ble_peripheral.rs`: adapt `#[gatt_server]` macros, `HostResources`, and multi-task runner APIs for `trouble-host 0.8.0`.
  - *Verification*: `cargo test-host` & `cargo check`. Test Card 3 confirms mobile app connects, reads duty limits, and sends drive commands over BLE.
- **Tier 4 (Hardware PWM Slice Consolidation)**:
  - Refactor `motor_control.rs` and `board.rs` to single-slice `PWM_SLICE0`.
  - *Verification*: Physical jumper rewiring followed by Test Card 4 confirming wheel movements, steering, and watchdog failsafe.

### Decision 5: Interactive Hardware Test Cards

Each tier execution ends with a structured **Test Card**:
```text
======================= TEST CARD: Tier X =======================
1. Jumper Wiring: [Instructions or 'No change']
2. Flash Command: cargo run --release (or picotool)
3. Log Stream: defmt-print -e target/thumbv6m-none-eabi/release/tricycletops-fw serial --path /dev/ttyACM0 --dtr
4. Physical Test: [App commands, LED states, motor movements]
=================================================================
```
The user executes the card on the physical Pico W robot car and confirms results before moving to the next tier.

### Decision 6: Preservation of USB CDC-ACM Logging Architecture

The in-tree `src/usb_logger.rs` transport, `UsbDriver::new(p.USB, Irqs)` initialization in `Board::init`, and `USBCTRL_IRQ` binding in `board.rs` are strictly preserved across all HAL and PWM refactoring steps.

## Risks / Trade-offs

- **[Risk] Breaking Changes in Trouble-Host 0.8 & CYW43 Transport**:
  Upgrading Embassy git patches to `main` changes the Bluetooth transport trait from `Controller` to `Transport`, breaking `trouble-host 0.7`.
  *Mitigation*: Handled together in Tier 3 as a cohesive software upgrade. Verified with Test Card 3 (BLE connection and GATT characteristic validation) *before* any physical motor jumper rewiring is performed.
- **[Risk] Incorrect Jumper Wiring during Physical Rerouting**:
  Connecting a PWM signal to a Direction input could cause a motor to run at full speed unintentionally.
  *Mitigation*: Kept in Tier 4 after all software dependencies are verified. The Test Card for Tier 4 provides an explicit pin mapping table. The car watchdog stops motors if commands cease.
- **[Risk] Defmt 1.2 In-Tree Logger Compatibility**:
  Upgrading `defmt` to 1.2.0 could alter `#[defmt::global_logger]` trait methods or `defmt::Encoder` framing.
  *Mitigation*: Tier 2 is isolated into its own task with host unit tests (`cargo test-host`) and Test Card 2 to immediately verify USB CDC serial decoding via `defmt-print`.

## Migration Plan

1. **Phase 1 (Tier 1)**: Apply standalone dependency updates (`cortex-m`, `cortex-m-rt`, `static_cell`, `portable-atomic`, `reqwest`, `embassy-time`). Flash and verify baseline BLE advertising.
2. **Phase 2 (Tier 2)**: Update `defmt` to 1.2.0. Flash and verify USB CDC logging via `defmt-print`.
3. **Phase 3 (Tier 3)**: Update Embassy git patches to `main`, upgrade `trouble-host` to 0.8.0, and adapt `board.rs` / `ble_peripheral.rs`. Flash and verify BLE connection and car control commands.
4. **Phase 4 (Tier 4)**: Refactor firmware to `PWM_SLICE0` dual channels. Rewire physical breadboard jumpers. Flash and verify forward, reverse, steering, and watchdog halt.
5. **Phase 5 (Docs & Cleanup)**: Update `wiring_diagram.mmd` and documentation in `GEMINI.md`. Run workspace linters.
