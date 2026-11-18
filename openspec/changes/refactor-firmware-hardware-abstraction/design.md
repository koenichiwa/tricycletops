# Design: Firmware Hardware Abstraction & Resilient State Indication

## Context

See `proposal.md` for motivation. In the current firmware:
- `MotorControl` is coupled to `embassy_rp::pwm::Pwm` and `embassy_rp::gpio::Output`, forcing `motor_control.rs` and `car_control.rs` to be gated under `#[cfg(all(target_arch = "arm", target_os = "none"))]`. Consequently, `CarControl::set_speeds`, `stop`, deadband calculations, and watchdog behavior cannot be unit-tested on the developer host via `cargo test`.
- `indicator_task` relies on ~40 lines of nested `select` branches and has an unreached `Failsafe` state.
- `CarControl` halts on watchdog timeout (500ms) or actuator error, but does not notify the visual indicator.
- `ble_peripheral::run` panics on advertisement failure, hard-resetting the microcontroller on transient BLE disturbances.

## Goals / Non-Goals

**Goals:**
- **Zero App Contract Changes**: Preserve 100% compatibility with the BLE GATT layout (Service `fff0`, Drive `fff1`, Limits `fff2`, device name `Tricycletops`, 8-byte little-endian payloads).
- **100% Host Testable Kinematics**: Abstract motor actuation behind a `Motor` trait so `CarControl` can be compiled and thoroughly tested on x86_64 host with `MockMotor`.
- **Target Isolation in BSP**: Confine all RP2040-specific GPIO and PWM types to `board.rs` and concrete driver structs.
- **Declarative Indicator**: Replace nested `select` branching with a clean `BlinkPattern` model and an interruptible phase stepper.
- **Visible Failsafe Feedback**: Wire the watchdog timeout and actuator errors in `CarControl` to trigger `IndicatorState::Failsafe` (5Hz rapid blinking).
- **Zero-Panic BLE Resilience**: Replace `panic!` in `ble_peripheral.rs` with error logging and backoff retries.

**Non-Goals:**
- Acceleration / slew-rate limiting (explicitly deferred per user direction).
- Multi-crate Cargo workspace restructuring.
- Modifying Android app code or GATT UUIDs.

## Decisions

### 1. `Motor` Trait Abstraction

Define an explicit `Motor` trait in `src/motor_control.rs`:

```rust
pub trait Motor {
    type Error: core::fmt::Debug;

    fn max_duty(&self) -> u16;
    fn set_duty(&mut self, duty: u16, direction: Direction) -> Result<(), Self::Error>;
    fn stop(&mut self) -> Result<(), Self::Error>;
}
```

`CarControl<L: Motor, R: Motor>` becomes generic over `L` and `R` (or a single `M: Motor` type if both motors share the driver type). It contains no RP2040-specific code and compiles on any platform.

*Alternatives considered:*
- *Directly using `embedded-hal::pwm::SetDutyCycle`*: Rejected because our motors require combined H-bridge logic (PWM duty + Direction pin with inverted duty cycle `max_duty - duty` for reverse). A high-level `Motor` trait cleanly captures both pins and the inversion contract.

### 2. Driver Structure & Mock Support

- `MotorControl<'a>` implements `Motor` for RP2040 using `Pwm<'a>` and `Output<'a>`.
- `MockMotor` is implemented for `#[cfg(test)]` (or in host builds), recording duty cycles, directions, and error flags.

```rust
#[cfg(test)]
#[derive(Default, Debug)]
pub struct MockMotor {
    pub max_duty: u16,
    pub current_duty: u16,
    pub current_direction: Direction,
    pub stopped: bool,
    pub fail_set_duty: bool,
}
```

*Rationale*: Enables testing forward, reverse, stopping, clamping, and error recovery on the host without hardware or QEMU.

### 3. Declarative `BlinkPattern` State Machine

Refactor `indicator.rs` to map states to timing patterns:

```rust
#[derive(Copy, Clone, Debug, PartialEq, Eq, defmt::Format)]
pub enum IndicatorState {
    Advertising,
    Connected,
    Failsafe,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct BlinkPattern {
    pub on_ms: u32,
    pub off_ms: u32,
}

impl IndicatorState {
    pub const fn pattern(&self) -> BlinkPattern {
        match self {
            Self::Advertising => BlinkPattern { on_ms: 500, off_ms: 500 }, // 1Hz blink
            Self::Connected   => BlinkPattern { on_ms: 1000, off_ms: 0 },  // Solid ON
            Self::Failsafe    => BlinkPattern { on_ms: 100, off_ms: 100 }, // 5Hz rapid blink
        }
    }
}
```

The runner loop executes each phase using an interruptible helper:

```rust
async fn step_phase(
    led: &mut Output<'_>,
    high: bool,
    duration: Duration,
    signal: &'static Signal<ThreadModeRawMutex, IndicatorState>,
) -> Option<IndicatorState> {
    if high { led.set_high(); } else { led.set_low(); }
    match select(Timer::after(duration), signal.wait()).await {
        Either::First(_) => None,
        Either::Second(new_state) => Some(new_state),
    }
}
```

*Rationale*: Flattens the control flow from 40+ lines of nested `select` into a ~15-line loop that responds immediately to signal transitions without lag.

### 4. Active Watchdog Failsafe Diagnostics

Pass `indicator_signal: &'static Signal<ThreadModeRawMutex, IndicatorState>` into `CarControl::run`:
- On `with_timeout` elapsed: halt motors and call `indicator_signal.signal(IndicatorState::Failsafe)`.
- On `PwmError` or motor failure: halt motors and call `indicator_signal.signal(IndicatorState::Failsafe)`.
- On receiving a valid drive command: if previously in failsafe, set speeds and call `indicator_signal.signal(IndicatorState::Connected)`.
- On BLE disconnect in `ble_peripheral`: immediately signal stop `(0, 0)` and `IndicatorState::Advertising`.

*Rationale*: Eliminates the dead `Failsafe` state and provides clear visual feedback on the physical robot when BLE communication stalls.

### 5. Resilient Advertising Loop

In `ble_peripheral.rs`, replace `panic!("[adv] error: {e:?}")` with:

```rust
Err(e) => {
    warn!("[adv] advertising failed: {:?}, retrying...", defmt::Debug2Format(&e));
    Timer::after_millis(100).await;
}
```

*Rationale*: Transient packet collisions or client aborts during connection negotiation will not crash or restart the RP2040.

## Risks / Trade-offs

- **[Risk] State Signal Overwrites between BLE and CarControl**:
  `Signal` holds only the latest state. If `ble_peripheral` signals `Connected` and `CarControl` simultaneously signals `Failsafe` due to initial watchdog timeout before first command:
  → *Mitigation*: `CarControl` only trips watchdog after the first command has been received or stays in initial idle until commanded, preventing spurious failsafe transitions during initial connection.
- **[Risk] Monomorphization Code Size**:
  `CarControl<L, R>` uses generic types.
  → *Mitigation*: Both wheels use identical `MotorControl<'static>` instances on target hardware, so only a single concrete instance is compiled into flash.
