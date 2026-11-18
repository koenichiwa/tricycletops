# Tasks

## 1. Manifest and Data Layer Reactive State

- [x] 1.1 Remove redundant `tools:targetApi="s"` from `BLUETOOTH_SCAN` in `AndroidManifest.xml` and verify Android Lint does not report `ObsoleteSdkInt`.
- [x] 1.2 Update `BleVehicleScanner.hasPermissions()` to verify both `Manifest.permission.BLUETOOTH_SCAN` and `Manifest.permission.BLUETOOTH_CONNECT`.
- [x] 1.3 Add reactive `isBluetoothEnabled: StateFlow<Boolean>` to `BleScannerDataSource` and implement it in `BleVehicleScanner` using a `BroadcastReceiver` for `BluetoothAdapter.ACTION_STATE_CHANGED`.
- [x] 1.4 Expose `isBluetoothEnabled: StateFlow<Boolean>` through `VehicleControlRepository` and implement in `VehicleControlRepositoryImpl`.
- [x] 1.5 Expose `isBluetoothEnabled: StateFlow<Boolean>` through `DiscoverVehiclesUseCase`.

## 2. ViewModel and Presentation State Modeling

- [x] 2.1 Define `ScannerUiState` sealed hierarchy in `presentation/scanner/ScannerUiState.kt` representing `MissingPermissions`, `BluetoothDisabled`, `Idle`, `Scanning`, and `Connecting`.
- [x] 2.2 Refactor `DeviceScannerViewModel` to combine permissions status, Bluetooth enabled state, scanning state, and connection state into `uiState: StateFlow<ScannerUiState>`, and add `onPermissionStatusChanged(Boolean)`.
- [x] 2.3 Create `DeviceScannerViewModelTest.kt` to unit test all state transitions (`MissingPermissions`, `BluetoothDisabled`, `Scanning`, `Connecting`) and auto-scan logic.

## 3. Passive Compose UI Refactoring

- [x] 3.1 Refactor `DeviceScannerScreen.kt` to passively render `when (uiState)` with distinct views for permission rationale, Bluetooth disabled notification, scanning, and connecting.
- [x] 3.2 Update `DeviceScannerScreen.kt` to forward permission checks from `rememberLauncherForActivityResult` and `LifecycleResumeEffect` directly to `viewModel.onPermissionStatusChanged(Boolean)`.

## 4. Verification and Lint Validation

- [x] 4.1 Run `./gradlew test` to ensure all existing and new unit tests pass.
- [x] 4.2 Run `./gradlew lintDebug` to verify no `MissingPermission`, `StaticFieldLeak`, or `ObsoleteSdkInt` issues are present.
