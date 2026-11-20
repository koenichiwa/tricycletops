# Design: Drive Command Keep-Alive Stream

## Context

The firmware (`tricycletops-fw`) implements an autonomous 500ms command watchdog in `CarControl::run`. If no drive command packet arrives on characteristic `fff1` within 500ms, the firmware halts the motors and enters failsafe state.

In the Android application (`tricycletops-app`), `detectDragGestures` in Jetpack Compose only triggers `onDrag` callbacks while the touch position actively changes. When the user holds the joystick in a fixed direction (e.g. steady full-forward), touch pointer delta events cease. In `VehicleControlRepositoryImpl`, commands were dispatched only reactively. Because no recurring loop existed to re-transmit the stationary position, the 500ms watchdog tripped, causing intermittent stopping during driving.

## Goals / Non-Goals

**Goals:**
- Provide continuous, periodic GATT writes at 25Hz (every 40ms) while the joystick is held at any non-zero coordinate.
- Guarantee 0ms latency on the initial touch event (immediate start) and on release (immediate zero-duty stop).
- Unify throttling (maximum 25Hz) and keep-alive (minimum 25Hz while active) into a clean, single-coroutine streaming architecture in `VehicleControlRepositoryImpl`.
- Maintain testability using injectable coroutine scopes, clocks, and fake GATT data sources.

**Non-Goals:**
- Modifying firmware watchdog behavior or timing (firmware watchdog remains 500ms as a safety invariant).
- Streaming packets when the joystick is at neutral `(0, 0)`. Once released, the stream is canceled and idle BLE bandwidth is zero.
- Re-architecting the UI gesture detector; `JoystickComponent` continues emitting `JoystickVector` changes on start, drag, and release.

## Decisions

### 1. Architecture: Continuous Coroutine Streaming Loop in Repository
- **Choice**: In `VehicleControlRepositoryImpl`, maintain an `activeVector` variable and a single `streamJob: Job?`. When `drive(vector)` receives a non-zero vector:
  - If `streamJob` is inactive: transmit immediately (0ms initial latency), record `lastSentTime`, and launch a loop `while (isActive) { delay(throttleIntervalMs); sendDriveCommand(activeVector); }`.
  - If `streamJob` is already active: update `activeVector`. The loop will automatically pick up and transmit the latest vector on its next 40ms tick.
- **Alternatives considered**:
  - *Heartbeat ticker separate from throttling job*: Retaining the reactive debounce for drag events and adding a separate 200ms keep-alive timer when idle. **Rejected**: Having two independent timers complicates synchronization, mutex locking, and testing, whereas a single 25Hz stream loop inherently provides both rate limiting (ceiling of 25Hz) and keep-alive (floor of 25Hz).
  - *Firmware watchdog removal or extension (e.g. 10s)*: **Rejected**: Compromises vehicle safety. A dead-man's switch is required to prevent runaway crashes if the app crashes, OS suspends the process, or the phone drops BLE connection.

### 2. Immediate Release Stop & Clean Cancellation
- **Choice**: When `drive(JoystickVector.ZERO)` or `stopImmediate()` is called:
  - Cancel `streamJob`.
  - Set `activeVector = null`.
  - Immediately call `gattClient.writeDriveCommand(MotorDriveCommand.STOP)`.
- **Rationale**: Immediate cancellation ensures no stray non-zero packets can be sent after release, preventing overshoot.

## Risks / Trade-offs

- **[Risk]** Coroutine cancellation timing in rapid drag/release sequences.
  → **Mitigation**: Synchronize vector updates, stream job cancellation, and stop command transmission under the repository's `throttleMutex` to prevent race conditions.
- **[Risk]** Test execution timing in unit tests.
  → **Mitigation**: Use existing virtual clock / `timeProvider` and structured coroutine delays so unit tests can assert exact packet counts across virtual and simulated time.
