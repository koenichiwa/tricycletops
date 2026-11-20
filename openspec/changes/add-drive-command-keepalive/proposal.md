# Proposal: Drive Command Keep-Alive Stream

## Why

When driving the Tricycletops car in a constant direction (e.g., holding full forward or a steady turning angle), Jetpack Compose touch drag gestures do not emit motion events once the finger remains stationary. Because the Android repository only transmitted drive commands reactively upon pointer delta events, no GATT writes were dispatched during stationary holds.

Consequently, the firmware's autonomous safety command watchdog (500ms timeout) triggered a failsafe stop, abruptly cutting motor duty cycles to zero until the user adjusted or wiggled their finger. Adding a periodic keep-alive stream while driving maintains uninterrupted locomotion without disabling the critical dead-man's switch watchdog in firmware.

## What Changes

- **Periodic Keep-Alive Streaming**: While the joystick is held at an active non-zero position, the app continuously transmits drive packets at a regular interval (every 40ms / 25Hz), preventing the firmware command watchdog from timing out.
- **Combined Throttling and Keep-Alive**: Replace reactive debounce/throttle logic with a unified fixed-rate stream loop that simultaneously enforces the 25Hz transmission ceiling and guarantees the periodic keep-alive rate.
- **Instantaneous Touch Response**: Retain immediate transmission (0ms latency) upon the initial touch-down of a non-zero vector and immediate zero-duty transmission with loop cancellation upon finger release.
- **Specification Alignment**: Update `remote-car-control` specification to mandate both high-frequency throttling and periodic keep-alive retransmission during active non-zero joystick engagement.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `remote-car-control`: Update `Command Throttling and Immediate Release Stop` to require periodic keep-alive retransmission during stationary non-zero holds in addition to the 25Hz transmission ceiling and immediate release stop.

## Impact

- **Android App (`tricycletops-app`)**:
  - `VehicleControlRepositoryImpl`: Replace the reactive delay timer with a continuous coroutine ticker loop while driving is active.
  - Unit tests in `VehicleControlRepositoryImplTest`: Add tests verifying that a stationary non-zero joystick vector generates repeated periodic GATT writes across virtual time, and that releasing the joystick immediately halts the stream and sends zero duty.
- **Firmware (`tricycletops-fw`)**: No firmware code changes required; the firmware's 500ms command watchdog and failsafe state machine remain intact.
- **Documentation**: Specifications in `openspec/specs/remote-car-control/spec.md`.
