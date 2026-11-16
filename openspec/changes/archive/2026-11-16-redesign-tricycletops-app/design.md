# Design

## Context

See `proposal.md` for motivation. The application is a remote controller for the Tricycletops dual-motor robotic vehicle. The target runtime is Android (minSdk 34, compileSdk 36) using Jetpack Compose and Kotlin Coroutines. The peripheral is an RP2040 micro-controller running bare-metal Rust with the `trouble-host` BLE stack, exposing GATT service `fff0`, drive command characteristic `fff1` (write, 8 bytes: two signed `i32` LE), and min-max duty cycle limits `fff2` (read, 8 bytes: four unsigned `u16` LE: `left_min`, `left_max`, `right_min`, `right_max`).

## Goals / Non-Goals

**Goals:**
- Implement a clean 3-layered architecture: Presentation (Compose + Navigation), Domain (pure Kotlin use cases & entities), and Data (BLE abstractions & repository).
- Isolate differential drive kinematics and desaturation scaling in a standalone, unit-testable pure Kotlin use case (`ComputeDifferentialDriveUseCase`) that is easy to tune or swap.
- Support deadband offsets via per-wheel min-max duty cycles fetched dynamically from `fff2`.
- Throttle continuous joystick drive commands to 25Hz (every 40ms) while guaranteeing zero-delay stop packet transmission upon joystick release.
- Automatically stop the vehicle and disconnect the peripheral when the app is placed in the background or the screen turns off.
- Completely remove obsolete Kotlin files and replace them with fresh, modular code adhering to KtLint guidelines without naming bias from legacy files.

**Non-Goals:**
- Background BLE service or background vehicle driving (app is active only in the foreground).
- Multi-robot simultaneous connections (single vehicle pairing).

## Decisions

### 1. Architectural Layers and Package Layout
```
com.koenichiwa.tricycletops/
├── MainActivity.kt
├── presentation/
│   ├── navigation/
│   │   ├── AppNavHost.kt
│   │   └── NavRoute.kt                    // Type-safe Compose navigation routes
│   ├── scanner/
│   │   ├── DeviceScannerScreen.kt
│   │   ├── DeviceScannerViewModel.kt
│   │   └── components/
│   │       └── DiscoveredDeviceCard.kt
│   ├── control/
│   │   ├── VehicleControlScreen.kt
│   │   ├── VehicleControlViewModel.kt
│   │   └── components/
│   │       ├── JoystickComponent.kt       // Interactive Canvas/drag joystick
│   │       └── VehicleTelemetryComponent.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── domain/
│   ├── model/
│   │   ├── DiscoveredVehicle.kt
│   │   ├── VehicleConnectionState.kt
│   │   ├── WheelDutyLimits.kt             // minDuty, maxDuty per wheel
│   │   ├── VehicleDutyLimits.kt            // left & right WheelDutyLimits
│   │   ├── JoystickVector.kt              // x, y in [-1.0, 1.0]
│   │   └── MotorDriveCommand.kt           // leftDuty, rightDuty
│   ├── usecase/
│   │   ├── ComputeDifferentialDriveUseCase.kt  // Pure Kotlin math & desaturation
│   │   ├── DiscoverVehiclesUseCase.kt
│   │   ├── ConnectVehicleUseCase.kt
│   │   └── DriveVehicleUseCase.kt
│   └── repository/
│       └── VehicleControlRepository.kt    // Domain interface
└── data/
    ├── repository/
    │   └── VehicleControlRepositoryImpl.kt
    └── ble/
        ├── BleGattConstants.kt
        ├── BleVehicleScanner.kt           // BluetoothLeScanner + permission check
        └── BleVehicleGattClient.kt        // GATT client, fff1 write, fff2 read
```
*Rationale*: Isolating domain logic from Android framework APIs enables rapid local unit testing without Robolectric or emulator overhead. The data layer owns BLE callback mechanics and GATT state machines.

### 2. Differential Drive Kinematics & Desaturation Scaling
- **Input**: Normalized touch vector $(x, y) \in [-1.0, 1.0]$, where $y > 0$ is forward, $x > 0$ is turn right.
- **Linear Drive Calculation**:
  $$v = y \quad (\text{throttle}), \quad \omega = x \quad (\text{steering})$$
  $$\text{rawLeft} = v + \omega, \quad \text{rawRight} = v - \omega$$
- **Desaturation**:
  $$\text{maxMag} = \max(1.0, \max(|\text{rawLeft}|, |\text{rawRight}|))$$
  $$\text{leftSpeed} = \frac{\text{rawLeft}}{\text{maxMag}}, \quad \text{rightSpeed} = \frac{\text{rawRight}}{\text{maxMag}}$$
- **Duty Cycle Mapping with Deadband Threshold**:
  For each wheel with normalized speed $s \in [-1.0, 1.0]$, and limits `minDuty`, `maxDuty`:
  $$\text{duty} = \begin{cases} 0 & \text{if } s = 0.0 \\ \operatorname{sign}(s) \times \operatorname{round}\left(\text{minDuty} + |s| \times (\text{maxDuty} - \text{minDuty})\right) & \text{if } |s| > 0.0 \end{cases}$$
*Rationale*: Simple clamping (`coerceIn(-1.0, 1.0)`) degrades steering authority when driving at full forward throttle. Desaturation preserves turning curvature even at high speeds. Incorporating `minDuty` ensures static friction / motor deadband can be overcome cleanly.

### 3. Command Throttling & Immediate Stop
- **Drag Handling**: While actively dragging, position updates are emitted into a Kotlin Flow and sampled at 25Hz (40ms interval).
- **Zero-Latency Stop**: When the user lifts their finger, `JoystickComponent` snaps to $(0.0, 0.0)$, bypassing the 25Hz ticker to trigger an immediate GATT write with `(0, 0)`.
*Rationale*: Touch events occur at 60-120Hz, which would overwhelm the BLE GATT buffer. Throttling active movement to 25Hz maintains smooth control while release-to-stop must have zero latency for safety.

### 4. Lifecycle Failsafe Integration
- `VehicleControlScreen` observes Activity/Compose lifecycle events (`Lifecycle.Event.ON_STOP` / `ON_PAUSE`).
- On background transition, `VehicleControlViewModel` signals the repository to transmit an immediate stop command and disconnect GATT.

## Risks / Trade-offs

- **[Risk]** BLE GATT write queue collisions during rapid drag events.
  - *Mitigation*: The repository serializes drive writes or uses `WRITE_TYPE_NO_RESPONSE` for high-frequency drive updates, dropping intermediate frames if a write is already in flight.
- **[Risk]** Stop command lost over BLE when user releases joystick quickly.
  - *Mitigation*: The immediate stop write is prioritized, and releasing triggers multiple rapid stop packets (or sequential write) to guarantee receipt.
- **[Risk]** Missing runtime permissions on newer Android versions.
  - *Mitigation*: Check `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` explicitly on launch with declarative permission handling in `DeviceScannerScreen`.
