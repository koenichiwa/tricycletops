# Design

## Context

The Tricycletops RP2040 firmware currently configures motor speed actuation across two distinct hardware PWM slices:
- Left Motor: Speed on GP1 (`PWM_SLICE0`, Channel B), Direction on GP0.
- Right Motor: Speed on GP3 (`PWM_SLICE1`, Channel B), Direction on GP2.

The RP2040 hardware PWM peripheral contains 8 dual-channel slices (16 PWM outputs total: 8 `A` channels and 8 `B` channels). Each slice has a shared counter and wrap register (`top`), but separate compare registers (`cc.a` and `cc.b`). Driving two DC gearmotors requires identical PWM frequency and duty scaling, making a shared slice optimal.

Additionally, several core dependencies in `tricycletops-fw/Cargo.toml` (`cortex-m`, `cortex-m-rt`, `static_cell`, `portable-atomic`, `defmt`, `defmt-rtt`) have published newer versions. To maintain stability, updates should be applied in staged risk tiers paired with interactive on-hardware test cards.

See `proposal.md` for motivation and `specs/remote-car-control/spec.md` for behavioral requirements.

## Goals / Non-Goals

**Goals:**
- Consolidate Left and Right motor speed actuation onto `PWM_SLICE0` Channels A and B.
- Refactor `Board::init` to initialize `PWM_SLICE0` via `Pwm::new_output_ab` and separate channels using `pwm.split()`.
- Update `MotorControl` to hold `PwmOutput<'a>` implementing `SetDutyCycle`.
- Update `wiring_diagram.mmd` and repo documentation.
- Upgrade Tier 1 (`cortex-m`, `cortex-m-rt`, `static_cell`, `portable-atomic`) and Tier 2 (`defmt`, `defmt-rtt`) dependencies.
- Establish an interactive execution flow where each step provides a structured hardware Test Card for user flashing and physical validation.

**Non-Goals:**
- Upgrading `embassy-*` and `cyw43*` git patches (retained at git rev `1d3c3de` to avoid breaking changes in the HAL or network/BLE stacks).
- Upgrading `trouble-host` to 0.8 in this change (kept at 0.7 to avoid GATT macro or connection event API breakage).
- Modifying central BLE protocols, GATT UUIDs, packet encodings, or the Android application.

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

*Alternatives Considered*:
- *Option B (PWM Slice 1)*: GP2 (Ch A) and GP3 (Ch B) for speed, GP0 and GP1 for direction. Option A was selected because GP0/GP1 are header pins 1 and 2, matching Slice 0 index conventions.

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

### Decision 4: Dependency Source Precedence, Scratch File Tracking & Incremental Updates

Dependency updates in `Cargo.toml` are applied incrementally, one (minor) version at a time, across staged risk tiers:
- **Source Precedence**:
  1. **Primary**: Upstream Embassy RP example configuration (`https://raw.githubusercontent.com/embassy-rs/embassy/refs/heads/main/examples/rp/Cargo.toml`). Versions and features specified here take absolute precedence over crates.io.
  2. **Secondary**: crates.io for standalone packages not specified in Embassy's RP example.
- **Scratch File & Reference Caching**:
  - Discovered target versions are recorded into a temporary scratch file (`.target_versions.scratch`) during online discovery.
  - Selected upstream Embassy RP code examples (`pwm.rs`, `blinky.rs`, `usb_logger.rs`, `watchdog.rs`) are fetched into `.examples.scratch/` to provide immediate local reference for syntax, macro, and HAL patterns. (Hardware watchdog from `watchdog.rs` remains out of scope for car command watchdog timeouts to prevent unwanted MCU reboots on joystick release).
  - Both `.target_versions.scratch` and `.examples.scratch/` are uncommitted and cleaned up in Task 3.2.
- **Incremental Cadence**:
  Updates proceed one minor version at a time. If compilation breaks, the issue is diagnosed and fixed before moving forward.
- **Breakage Remediation & Upstream Reference**:
  If breaking changes or compilation errors occur, the agent consults the local reference examples in `.examples.scratch/` and any additional examples discovered via:
  ```bash
  curl -s "https://api.github.com/repos/embassy-rs/embassy/contents/examples/rp/src/bin?ref=main" | jq '.[] | {path, download_url}'
  ```
  to inspect and mirror current upstream usage patterns.
- **Tiers**:
  1. **Tier 1 (Core & Primitives)**: `cortex-m`, `cortex-m-rt`, `static_cell`, `portable-atomic`, `reqwest` (build).
  2. **Tier 2 (Defmt & RTT Logging)**: `defmt`, `defmt-rtt`.

### Decision 5: Interactive Hardware Test Cards

Each task execution ends with a structured **Test Card**:
```text
======================= TEST CARD: Task X =======================
1. Jumper Wiring: [Instructions or 'No change']
2. Flash Command: cargo run --release / probe-rs run ...
3. Expected Logs: [Expected RTT output lines]
4. Physical Test: [App commands, LED states, motor movements]
=================================================================
```
The user executes the card on the physical Pico W robot car and confirms results before moving to the next task.

## Risks / Trade-offs

- **[Risk] Incorrect Jumper Wiring during Physical Rerouting**:
  Connecting a PWM signal to a Direction input could cause a motor to run at full speed unintentionally.
  *Mitigation*: The Test Card for the PWM refactor will provide an explicit table mapping Pico pins to motor driver pins (IN1..IN4). The car watchdog will also stop motors if no valid command stream is active.
- **[Risk] Shared Wrap Register (`max_duty_cycle`) Conflict**:
  Both channels on Slice 0 share the same top/wrap value.
  *Mitigation*: Both motors are identical and use `PwmConfig::default()`. Changing duty cycle on one channel only writes to that channel's compare register (`cc.a` vs `cc.b`) without modifying the shared wrap register.
- **[Risk] Defmt Protocol Mismatch**:
  Upgrading `defmt` to 1.2 may require updated host tools (`defmt-print` or `probe-rs`).
  *Mitigation*: Tier 2 is isolated into its own task with a test card to immediately verify RTT log decoding.

## Migration Plan

1. Apply Tier 1 and Tier 2 dependency updates. Flash and verify firmware operation with existing wiring.
2. Refactor firmware code to use `PWM_SLICE0` dual channels.
3. Rewire physical breadboard jumpers according to the updated wiring diagram.
4. Flash updated firmware and verify differential steering, forward/reverse drive, and watchdog timeout.
5. Update `wiring_diagram.mmd` and documentation.
