# Proposal

## Why

The current Android companion application (`tricycletops-app`) has mixed concerns across UI and Bluetooth communication, inconsistent command scheduling, and lacks clear separation between business logic, hardware interaction, and presentation. Redesigning the app from the ground up with a clean 3-layered architecture (Presentation, Domain, Data) and modern Jetpack Compose + Navigation provides a testable, maintainable, and responsive remote controller for the Tricycletops robotic vehicle.

## What Changes

- **BREAKING**: Remove all legacy Kotlin source files in `com.koenichiwa.tricycletops` and rebuild the application from scratch with fresh, unbiased domain and component naming.
- Introduce a 3-layered architecture:
  - **Presentation**: Jetpack Compose UI with `androidx.navigation` managing two main screens: `DeviceScannerScreen` and `VehicleControlScreen`, featuring modular UI components like `JoystickComponent` and `VehicleTelemetryComponent`.
  - **Domain**: Pure Kotlin models (`DiscoveredVehicle`, `VehicleDutyLimits`, `MotorDriveCommand`) and use cases (`ComputeDifferentialDriveUseCase`, `DriveVehicleUseCase`) with zero Android framework dependencies.
  - **Data**: BLE data sources (`BleVehicleScanner`, `BleVehicleGattClient`) encapsulating Android Bluetooth APIs under `VehicleControlRepositoryImpl`.
- Implement responsive joystick control with differential drive kinematics, support for desaturation scaling, 25Hz periodic command throttling during active driving, and immediate zero-latency stop emission when the joystick is released.
- Add lifecycle-aware failsafe stopping that sends a stop command and disconnects BLE when the app moves to the background or the screen turns off.
- Fetch per-wheel min-max duty cycle boundaries from firmware characteristic `fff2` (8 bytes: min and max per wheel) to dynamically scale motor drive commands above deadband thresholds on the app side.

## Capabilities

### New Capabilities
- `ble-device-discovery`: BLE scanning, filtering for peripherals named "Tricycletops" or advertising the control service, and presenting discovered vehicles with signal strength.
- `remote-car-control`: Establishing a GATT connection, reading min-max duty cycle parameters, translating touch joystick input into differential drive motor commands with desaturation and rate throttling, and managing failsafe vehicle stopping.

### Modified Capabilities
*(None - this establishes the initial capability specifications for the application)*

## Impact

- **Android App Codebase**: Complete clean rewrite of `tricycletops-app/app/src/main/java/com/koenichiwa/tricycletops/`.
- **Dependencies**: Leverages existing `androidx.compose`, `androidx.navigation:navigation-compose`, and Kotlin coroutines/flows.
- **Hardware/Firmware Contract**: Connects to the `tricycletops-fw` GATT service (`fff0`), writing drive commands to `fff1` and reading per-wheel min and max duty cycle limits from `fff2` (8 bytes).
