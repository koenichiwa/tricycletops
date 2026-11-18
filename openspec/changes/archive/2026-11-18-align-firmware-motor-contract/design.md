# Design

## Context

See `proposal.md` for motivation. The system consists of an RP2040 micro-controller running bare-metal `no_std` Rust with Embassy and `trouble-host`, paired with an Android remote control application (`tricycletops-app`). The Android app computes differential drive kinematics and maps speeds to signed duty cycles (`MotorDriveCommand(leftDuty, rightDuty)`), which it writes to characteristic `fff1`. It also queries characteristic `fff2` to determine the per-wheel minimum and maximum duty limits.

Currently, the firmware exposes only a 4-byte payload on `fff2` (`[left_max: u16, right_max: u16]`), omitting minimum duty limits. Furthermore, `MotorControl` handles signed `i32` values directly, intertwining speed clamping and sign analysis with low-level PWM/GPIO actuation.

## Goals / Non-Goals

**Goals:**
- Expand BLE GATT characteristic `fff2` in `ble_peripheral.rs` from 4 bytes to 8 bytes (`[left_min: u16, left_max: u16, right_min: u16, right_max: u16]`).
- Define configurable motor deadband constants in firmware (`LEFT_MOTOR_MIN_DUTY`, `RIGHT_MOTOR_MIN_DUTY`) and expose them through `fff2`.
- Decouple low-level hardware actuation from direction logic by introducing a `Direction` enum (`Forward`, `Reverse`) and updating `MotorControl` to take raw unsigned duty (`u16`) and `Direction`.
- Decompose incoming signed `i32` drive commands into `(duty: u16, direction: Direction)` at the `CarControl` boundary before delegating to `MotorControl`.
- Keep `GEMINI.md` protocol documentation synchronized with the 8-byte `fff2` characteristic format.

**Non-Goals:**
- Modifying the 8-byte signed `i32` wire format of `fff1` (preserves unambiguous `0` stop and compatibility with the Android app).
- Implementing over-the-air runtime calibration of deadband values (statically configured in firmware).

## Decisions

### 1. Characteristic `fff2` 8-Byte Layout
- **Layout**: Four 16-bit little-endian unsigned integers:
  ```
  [0..2]: left_min  (u16 LE)
  [2..4]: left_max  (u16 LE)
  [4..6]: right_min (u16 LE)
  [6..8]: right_max (u16 LE)
  ```
- **Source Values**: `left_max` and `right_max` are dynamically queried from hardware PWM slices via `Pwm::max_duty_cycle()`. `left_min` and `right_min` are provided via firmware constants (`LEFT_MOTOR_MIN_DUTY`, `RIGHT_MOTOR_MIN_DUTY`, default `200`).
- **Rationale**: Matches the Android client's `BleVehicleGattClient.parseDutyLimits` and the OpenSpec `remote-car-control` specification, enabling the app to scale motor speed above static friction thresholds.
- **Alternatives Considered**: Retaining 4 bytes with implicit zero min-duty. Rejected because physical DC motors require non-zero initial torque to break static friction (deadband).

### 2. Wire Protocol vs. Firmware Internal Abstraction
- **Decision**: Keep the BLE wire contract on `fff1` as two signed 32-bit integers (`left: i32, right: i32`), but decompose them into `(duty: u16, direction: Direction)` at the `CarControl` boundary.
- **Rationale**:
  - `0` is mathematically and physically unambiguous: a commanded duty of 0 means complete stop, requiring both direction pins to be pulled LOW and PWM disabled.
  - Avoids unaligned byte structures (e.g. 6-byte payloads) on the wire.
  - Prevents breaking changes in the Android app domain models and use cases.
- **Alternatives Considered**: Changing `fff1` to explicit `(u16 duty, u8 direction)` fields per wheel. Rejected due to the "stop ambiguity" (what does `duty = 0, dir = Reverse` mean to the H-bridge?) and unnecessary Android app churn.

### 3. Separation of Concerns in `MotorControl` and `CarControl`
- **`motor_control.rs`**:
  ```rust
  #[derive(Copy, Clone, Debug, PartialEq, Eq, defmt::Format)]
  pub enum Direction {
      Forward,
      Reverse,
  }

  pub struct MotorControl<'a> {
      speed_pin: Pwm<'a>,
      direction_pin: Output<'a>,
  }

  impl<'a> MotorControl<'a> {
      pub fn set_duty(&mut self, duty: u16, direction: Direction) -> Result<(), PwmError>;
      pub fn stop(&mut self) -> Result<(), PwmError>;
      pub fn max_duty(&self) -> u16;
  }
  ```
  - `set_duty` clamps input `duty` to `max_duty`. If `duty == 0`, it calls `stop()`.
  - For `Forward`: `direction_pin.set_low()`, `speed_pin.set_duty_cycle(duty)`.
  - For `Reverse`: `direction_pin.set_high()`, `speed_pin.set_duty_cycle(max_duty - duty)`.
  - `stop`: `direction_pin.set_low()`, `speed_pin.set_duty_cycle_fully_off()`.
- **`car_control.rs`**:
  - Contains duty limits tuple: `((left_min, left_max), (right_min, right_max))`.
  - Converts incoming signed `speed: i32` into `(u16, Direction)`:
    ```rust
    fn split_speed(speed: i32, max_duty: u16) -> (u16, Direction) {
        let clamped = speed.clamp(-(max_duty as i32), max_duty as i32);
        if clamped >= 0 {
            (clamped as u16, Direction::Forward)
        } else {
            (clamped.unsigned_abs() as u16, Direction::Reverse)
        }
    }
    ```
  - Calls `self.left_motor.set_duty(left_duty, left_dir)` and `self.right_motor.set_duty(right_duty, right_dir)`.

## Risks / Trade-offs

- **[Risk]** Inverted PWM switching glitch when rapidly reversing direction.
  - *Mitigation*: Set `direction_pin` before updating `speed_pin` PWM duty cycle, ensuring monotonic pin state transitions.
- **[Risk]** Over-aggressive min_duty causing unexpected lurching on initial touch.
  - *Mitigation*: Set default deadband to a conservative value (`200` out of ~65535, or `< 0.5%`), providing a safe floor that can be adjusted based on physical testing.
