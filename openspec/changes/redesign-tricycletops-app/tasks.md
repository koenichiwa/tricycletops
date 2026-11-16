# Tasks

## 1. Clean Slate & Package Scaffolding

- [x] 1.1 Remove all legacy Kotlin source files in `tricycletops-app/app/src/main/java/com/koenichiwa/tricycletops` and scaffold the 3-layered directory structure (`presentation`, `domain`, `data`). Verify directory layout is clean.
- [x] 1.2 Rebuild the foundational UI theme (`Color.kt`, `Theme.kt`, `Type.kt`) under `presentation/theme`. Verify compilation.

## 2. Domain Layer & Unit Tests

- [x] 2.1 Implement domain entities (`DiscoveredVehicle`, `WheelDutyLimits`, `VehicleDutyLimits`, `JoystickVector`, `MotorDriveCommand`, `VehicleConnectionState`). Verify model compilation.
- [x] 2.2 Implement `ComputeDifferentialDriveUseCase` containing differential drive kinematics, desaturation scaling, and min-max duty cycle mapping.
- [x] 2.3 Implement unit tests in `ComputeDifferentialDriveUseCaseTest` covering neutral position, forward/reverse, pivot turns, desaturation scaling, zero-speed clamping, and min-duty deadband offsets. Verify with `./gradlew test`.
- [x] 2.4 Define `VehicleControlRepository` interface and domain use cases (`DiscoverVehiclesUseCase`, `ConnectVehicleUseCase`, `DriveVehicleUseCase`). Verify interface contracts compile.

## 3. Data Layer (BLE Stack)

- [x] 3.1 Implement `BleGattConstants` and `BleVehicleScanner` with `BluetoothLeScanner` handling and name/service filtering for Tricycletops peripherals. Verify compilation.
- [x] 3.2 Implement `BleVehicleGattClient` handling GATT connection lifecycle, service discovery (`fff0`), reading 8-byte min-max duty limits (`fff2`), and writing drive commands (`fff1`). Verify compilation.
- [x] 3.3 Implement `VehicleControlRepositoryImpl` managing connection state, 25Hz throttling flow for active dragging, and zero-latency stop command emission on joystick release. Verify repository logic with unit tests via `./gradlew test`.

## 4. Presentation Layer (Compose UI & Navigation)

- [x] 4.1 Implement `DeviceScannerViewModel` and `DeviceScannerScreen` with `DiscoveredDeviceCard`, Bluetooth state observation, permission requests, and scan toggle. Verify compilation.
- [x] 4.2 Implement `JoystickComponent` touch component handling drag gesture detection, circular boundary constraint, and release snap-to-center. Verify Composable compilation.
- [x] 4.3 Implement `VehicleControlViewModel` and `VehicleControlScreen` integrating `JoystickComponent`, `VehicleTelemetryComponent`, and lifecycle observer for failsafe stopping on `ON_STOP`. Verify compilation.
- [x] 4.4 Implement `AppNavHost` and `MainActivity` integrating `androidx.navigation` between `DeviceScannerScreen` and `VehicleControlScreen`. Verify full app navigation graph compiles.

## 5. Verification & Code Quality

- [x] 5.1 Run KtLint checks via `./gradlew ktlintCheck` and resolve any formatting or style warnings.
- [x] 5.2 Run the full test suite and assemble the debug APK via `./gradlew test assembleDebug` to verify end-to-end build integrity.
