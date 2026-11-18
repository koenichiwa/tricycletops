# Proposal

## Why

Modern Android (API 31+, minSdk 34) decouples Bluetooth operations into `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`. Currently, `tricycletops-app` has architectural and UX gaps in Bluetooth state and permission handling:
1. Bluetooth adapter status is either queried in ad-hoc UI calls or not reactively observed when toggled externally (e.g., via Quick Settings).
2. Permission states and fallback rationale branching reside inside UI `remember` variables rather than being modeled in the ViewModel, making UI decision logic untestable.
3. Scanner permission checks previously omitted `BLUETOOTH_CONNECT`.

Refactoring this workflow establishes Clean Architecture boundaries: the Data layer reactively observes Bluetooth adapter hardware changes, the UI delegates permission outcomes to the ViewModel, and `DeviceScannerViewModel` models all screen states (missing permissions, disabled Bluetooth, scanning, idle, connecting) in a testable, cohesive `StateFlow<ScannerUiState>`.

## What Changes

- **Reactive Bluetooth Adapter State (Data & Repo)**:
  - Add `val isBluetoothEnabled: StateFlow<Boolean>` to `BleScannerDataSource` and `BleVehicleScanner`, using an Android `BroadcastReceiver` listening to `BluetoothAdapter.ACTION_STATE_CHANGED`.
  - Expose `isBluetoothEnabled` through `VehicleControlRepository`.
- **Permission Check Enhancement**:
  - Verify both `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` in `BleVehicleScanner.hasPermissions()`.
- **ViewModel State Modeling**:
  - Model scanner states via a sealed `ScannerUiState` in `DeviceScannerViewModel`:
    - `MissingPermissions` (requires user to grant Nearby Devices permissions)
    - `BluetoothDisabled` (prompts user to enable Bluetooth)
    - `Idle` (has permissions & Bluetooth enabled, not scanning)
    - `Scanning` (active scan in progress with list of discovered vehicles)
    - `Connecting(address)` (connection attempt in progress)
  - Expose `fun onPermissionStatusChanged(isGranted: Boolean)` to receive permission check outcomes from the UI.
  - Automatically start scanning when permissions and Bluetooth are both enabled.
- **Passive Compose UI Boundary (`DeviceScannerScreen`)**:
  - Delegate state presentation entirely to `ScannerUiState`.
  - Use `rememberLauncherForActivityResult` and `LifecycleResumeEffect` solely to check `ContextCompat.checkSelfPermission` and notify the ViewModel via `onPermissionStatusChanged`.
  - Display educational permission rationale and system settings link (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) when in `ScannerUiState.MissingPermissions`.
  - Display Bluetooth disabled notice when in `ScannerUiState.BluetoothDisabled`.
- **Manifest Hygiene**:
  - Remove redundant `tools:targetApi="s"` from `BLUETOOTH_SCAN` in `AndroidManifest.xml` to prevent `ObsoleteSdkInt` lint warnings.
- **Unit Testing**:
  - Add comprehensive unit tests for `DeviceScannerViewModel` covering state transitions between missing permissions, disabled Bluetooth, scanning, and vehicle connection.

## Capabilities

### Modified Capabilities
- `ble-device-discovery`: Updates `Bluetooth and Permission Handling` requirements to specify reactive handling of Bluetooth adapter state changes, permission status tracking and rationale fallback via ViewModel state, and automatic resumption of scanning.

## Impact

- **Affected Files**:
  - `tricycletops-app/app/src/main/AndroidManifest.xml`
  - `tricycletops-app/app/src/main/java/com/koenichiwa/tricycletops/data/ble/BleScannerDataSource.kt`
  - `tricycletops-app/app/src/main/java/com/koenichiwa/tricycletops/data/ble/BleVehicleScanner.kt`
  - `tricycletops-app/app/src/main/java/com/koenichiwa/tricycletops/domain/repository/VehicleControlRepository.kt`
  - `tricycletops-app/app/src/main/java/com/koenichiwa/tricycletops/data/repository/VehicleControlRepositoryImpl.kt`
  - `tricycletops-app/app/src/main/java/com/koenichiwa/tricycletops/presentation/scanner/DeviceScannerViewModel.kt`
  - `tricycletops-app/app/src/main/java/com/koenichiwa/tricycletops/presentation/scanner/ScannerUiState.kt`
  - `tricycletops-app/app/src/main/java/com/koenichiwa/tricycletops/presentation/scanner/DeviceScannerScreen.kt`
  - `tricycletops-app/app/src/test/java/com/koenichiwa/tricycletops/presentation/scanner/DeviceScannerViewModelTest.kt`
- **Domain & Clean Architecture Boundaries**:
  - ViewModels remain 100% pure Kotlin and context-free with no Binder IPC or Android framework imports.
  - Hardware adapter state flows up from Data layer through Repository.
