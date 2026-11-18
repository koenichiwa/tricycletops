# Proposal

## Why

The Android application (`tricycletops-app`) performs all floating-point differential drive kinematics, desaturation scaling, and deadband duty cycle mapping, sending signed integer duty cycles over BLE. However, the RP2040 firmware (`tricycletops-fw`) currently exposes only 4 bytes (max duties) on characteristic `fff2` instead of the 8 bytes (`left_min`, `left_max`, `right_min`, `right_max`) required by the specification, and its low-level motor driver mixes signed integer math with hardware actuation. Aligning the firmware to provide 8-byte duty limits with configurable deadbands and refactoring wheel control to consume raw unsigned duty cycles and explicit direction flags ensures full protocol compatibility and clean hardware abstraction.

## What Changes

- **Update BLE characteristic `fff2` payload**: Expand the GATT read characteristic `fff2` from 4 bytes to 8 bytes (`u16` LE: `left_min`, `left_max`, `right_min`, `right_max`).
- **Configurable Motor Deadbands**: Introduce firmware constants for `LEFT_MOTOR_MIN_DUTY` and `RIGHT_MOTOR_MIN_DUTY` exposed through `fff2` so the Android app can dynamically offset motor deadbands.
- **Refactor `MotorControl` driver**: Update `MotorControl` in `tricycletops-fw` to operate with an unsigned raw duty cycle (`u16`) and an explicit `Direction` flag (`Forward`, `Reverse`), with a dedicated `stop()` method.
- **Refactor `CarControl` decomposition**: Update `CarControl` to decode signed `i32` commands from characteristic `fff1` into raw `(duty: u16, direction: Direction)` tuples before delegating to individual wheel drivers.
- **Update Project Documentation**: Update `GEMINI.md` to reflect the 8-byte `fff2` characteristic format.

## Capabilities

### New Capabilities
*(None)*

### Modified Capabilities
- `remote-car-control`: Update duty cycle retrieval specification to require the peripheral to expose 8-byte per-wheel min-max limits, and define low-level wheel actuation requirements using raw duty cycles and direction flags.

## Impact

- **Firmware (`tricycletops-fw`)**:
  - `ble_peripheral.rs`: Updates `fff2` characteristic definition from `[u8; 4]` to `[u8; 8]` and encodes min-max bounds.
  - `car_control.rs`: Splits signed `i32` speed into raw `u16` duty and `Direction` enum, exposes 8-byte duty limits `((left_min, left_max), (right_min, right_max))`.
  - `motor_control.rs`: Replaces signed `set_speed(i32)` with `set_duty(u16, Direction)` and `stop()`.
  - `main.rs`: Passes configured min-max limits to car control and BLE peripheral.
- **Android App (`tricycletops-app`)**:
  - Fully compatible with the existing `BleVehicleGattClient.parseDutyLimits` 8-byte parser; will now receive non-zero `minDuty` values and dynamically apply deadband scaling.
- **Documentation**:
  - `GEMINI.md` specification updated for `fff2`.
