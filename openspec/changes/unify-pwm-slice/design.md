# Design: Unify PWM Slice

## Context

The Tricycletops RP2040 firmware currently configures motor speed actuation across two distinct hardware PWM slices:
- Left Motor: Speed on GP1 (`PWM_SLICE0`, Channel B), Direction on GP0.
- Right Motor: Speed on GP3 (`PWM_SLICE1`, Channel B), Direction on GP2.

The RP2040 hardware PWM peripheral contains 8 dual-channel slices (16 PWM outputs total: 8 `A` channels and 8 `B` channels). Each slice has a shared counter and wrap register (`top`), but separate compare registers (`cc.a` and `cc.b`). Driving two DC gearmotors requires identical PWM frequency and duty scaling, making a shared slice optimal.

Consolidating both speed outputs onto `PWM_SLICE0` Channels A and B frees an entire RP2040 PWM slice (`PWM_SLICE1`) for future peripherals while synchronizing the base carrier frequency across both wheels.

See `proposal.md` for motivation and `specs/remote-car-control/spec.md` for behavioral requirements.

## Goals / Non-Goals

**Goals:**
- Consolidate Left and Right motor speed actuation onto `PWM_SLICE0` Channels A and B.
- Refactor `Board::init` to initialize `PWM_SLICE0` via `Pwm::new_output_ab` and separate channels using `pwm.split()`, while preserving `usb_driver` and `USBCTRL_IRQ`.
- Update `MotorControl` to hold `PwmOutput<'a>` implementing `SetDutyCycle`.
- Update `GEMINI.md` and repository wiring documentation to reflect the new pin routing.
- Provide a structured interactive hardware Test Card for physical rewiring and flashing validation.

**Non-Goals:**
- Upgrading firmware dependencies (`cortex-m`, `defmt`, `trouble-host`, Embassy git patches) — dependency updates are intentionally isolated from hardware actuation changes.
- Modifying central BLE protocols, GATT UUIDs, packet encodings, or the Android application (`tricycletops-app`).
- Changing motor control kinematics, deadbands, or differential drive calculations.

## Decisions

### Decision 1: Pin Assignment on RP2040

We assign the motor control pins as follows:
- **GP0 (Header Pin 1)**: Left Motor Speed (`PWM_SLICE0`, Channel A)
- **GP1 (Header Pin 2)**: Right Motor Speed (`PWM_SLICE0`, Channel B)
- **GND (Header Pin 3)**: Common Ground Bus
- **GP2 (Header Pin 4)**: Left Motor Direction (GPIO Output)
- **GP3 (Header Pin 5)**: Right Motor Direction (GPIO Output)

*Rationale*:
- Uses contiguous pins (GP0–GP3) on header pins 1–5 of the Raspberry Pi Pico W.
- Grouping speed signals on GP0/GP1 naturally maps to Slice 0 channels A and B.
- Grouping direction signals on GP2/GP3 allows identical GPIO output handling.
- Does not conflict with CYW43 control pins (GP23, GP24, GP25, GP29), onboard LED (GP15), or USB.

*Alternatives Considered*:
- Keeping separate slices (wastes `PWM_SLICE1` without benefit).
- Non-contiguous pin layout (e.g., GP0/GP2 for PWM, GP1/GP3 for Dir), which breaks dual-channel slice alignment on GP0/GP1.

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

*Alternatives Considered*:
- Wrapping a shared `Pwm` struct in a `RefCell` or mutex: Introduces unnecessary runtime overhead and complexity.

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

*Alternatives Considered*:
- Making `MotorControl` generic over `P: SetDutyCycle`: Adds generic type parameters throughout `Board` and `main.rs` without immediate benefit.

### Decision 4: Interactive Hardware Test Card

Execution concludes with a structured **Test Card**:
```text
======================= TEST CARD: PWM Slice Unification =======================
1. Jumper Rewiring:
   - GP0 (Header Pin 1) -> Left Motor Driver PWM (Speed)
   - GP1 (Header Pin 2) -> Right Motor Driver PWM (Speed)
   - GP2 (Header Pin 4) -> Left Motor Driver DIR (Direction)
   - GP3 (Header Pin 5) -> Right Motor Driver DIR (Direction)
   - GND (Header Pin 3) -> Motor Driver Logic GND
2. Flash Command: cd tricycletops-fw && cargo run --release
3. Log Stream: defmt-print -e target/thumbv6m-none-eabi/release/tricycletops-fw serial --path /dev/ttyACM0 --dtr
4. Physical Test:
   - Forward drive: Both wheels spin forward smoothly.
   - Reverse drive: Both wheels spin in reverse.
   - Left / Right steering: Wheels differentiate speeds correctly.
   - Watchdog failsafe: Halting app or releasing joystick immediately stops both motors.
=================================================================================
```
The user executes the card on the physical Pico W robot car and confirms results.

## Risks / Trade-offs

- **[Risk] Incorrect Jumper Wiring during Physical Rerouting**:
  Connecting a PWM signal to a Direction input could cause a motor to run at full speed unintentionally.
  *Mitigation*: The Test Card provides an explicit pin mapping table and step-by-step verification checklist. The autonomous 500ms command watchdog halts motors if commands cease.

## Migration Plan

1. **Firmware Implementation**:
   - Refactor `MotorControl` in `src/motor_control.rs` to use `PwmOutput<'a>` and validate host tests with `cargo test-host`.
   - Reconfigure `Board::init` in `src/board.rs` to initialize `PWM_SLICE0` in dual-output mode and verify target compilation with `cargo check --target thumbv6m-none-eabi`.
2. **Physical Rewiring & Verification**:
   - Rewire jumper wires on breadboard according to Test Card pin assignments.
   - Flash firmware and verify drive maneuvers and failsafe behavior on the robot car.
3. **Documentation & Validation**:
   - Update `GEMINI.md` hardware conventions and wiring documentation.
   - Run workspace linters (`cargo fmt`, `cargo clippy`, `pre-commit`).
