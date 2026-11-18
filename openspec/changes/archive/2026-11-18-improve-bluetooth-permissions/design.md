# Design

## Context

In `tricycletops-app`, Bluetooth operations require `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` on Android 12+ (minSdk 34). The app follows a 3-layer architecture: Presentation (Compose + ViewModels), Domain (pure Kotlin use cases and interfaces), and Data (BLE drivers and repository implementation).

Previously, permission denial and Bluetooth adapter checks were performed directly within Composable `remember` variables, creating state fragmentation, bypassing the repository, causing stale state when Bluetooth was toggled in Quick Settings, and making screen logic untestable in unit tests.

## Goals / Non-Goals

**Goals:**
- Reactively observe Bluetooth adapter hardware state in `BleVehicleScanner` using an Android `BroadcastReceiver` for `BluetoothAdapter.ACTION_STATE_CHANGED`, exposing `isBluetoothEnabled: StateFlow<Boolean>` via `VehicleControlRepository`.
- Enforce both `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` in `BleVehicleScanner.hasPermissions()` prior to invoking adapter or scanner APIs.
- Model all scanner UI states cohesively in `DeviceScannerViewModel` via `ScannerUiState` (`MissingPermissions`, `BluetoothDisabled`, `Idle`, `Scanning`, `Connecting`).
- Keep ViewModels 100% pure Kotlin and context-free: permissions are verified in the UI layer (`ContextCompat.checkSelfPermission`) and communicated via `viewModel.onPermissionStatusChanged(Boolean)`.
- Make scanner state transitions completely testable in JVM unit tests (`DeviceScannerViewModelTest`).
- Keep `DeviceScannerScreen` passive: simply render UI based on `ScannerUiState` and forward lifecycle/permission events.
- Remove obsolete `tools:targetApi="s"` from `AndroidManifest.xml`.

**Non-Goals:**
- Adding location permissions (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`) — the manifest declares `neverForLocation` for `BLUETOOTH_SCAN`.
- Injecting `Context` or Android framework classes into ViewModels.
- Running Binder IPC permission checks at 60Hz in control loops.

## Decisions

### 1. Reactive Bluetooth Hardware State in Data Layer

- **Decision**: Monitor `BluetoothAdapter.ACTION_STATE_CHANGED` in `BleVehicleScanner` via a `BroadcastReceiver` registered with `context.applicationContext`. Expose this status through `BleScannerDataSource` and `VehicleControlRepository` as `val isBluetoothEnabled: StateFlow<Boolean>`.
- **Rationale**: If the user toggles Bluetooth off/on in the Android Quick Settings shade without backgrounding the app, a passive check on resume will miss the change. A broadcast receiver guarantees immediate reactive updates across the entire system.
- **Alternatives Considered**: Polling `BluetoothAdapter.isEnabled` or querying it inside Compose effects. Rejected due to battery waste, state lag, and architectural pollution of UI layers.

### 2. ViewModel State Ownership via `ScannerUiState`

- **Decision**: Introduce a sealed hierarchy `ScannerUiState`:
  ```kotlin
  sealed interface ScannerUiState {
      data object MissingPermissions : ScannerUiState
      data object BluetoothDisabled : ScannerUiState
      data class Idle(val devices: List<DiscoveredVehicle>) : ScannerUiState
      data class Scanning(val devices: List<DiscoveredVehicle>) : ScannerUiState
      data class Connecting(val vehicleAddress: String) : ScannerUiState
  }
  ```
- **Rationale**:
  - Eliminates fragmented `remember` state variables in Compose.
  - Ensures a single source of truth for the screen.
  - Enables comprehensive JVM unit tests (`DeviceScannerViewModelTest`) without Robolectric or emulator instrumentation.
  - The UI remains completely passive, pattern-matching on `uiState`.

### 3. Context-Free Permission Event Channel

- **Decision**: The UI checks `ContextCompat.checkSelfPermission` in `LifecycleResumeEffect` or permission launcher callbacks and notifies the ViewModel via `viewModel.onPermissionStatusChanged(isGranted: Boolean)`.
- **Rationale**:
  - Preserves pure Kotlin Domain and Presentation ViewModel layers.
  - Avoids `Context` leaks in ViewModels (`StaticFieldLeak`).
  - Allows ViewModel logic to decide when to automatically trigger scanning without embedding business logic in Compose.

### 4. Localized Driver Suppressions vs. Viral Annotation Leaks

- **Decision**: Keep `@SuppressLint("MissingPermission")` localized strictly to concrete BLE data sources (`BleVehicleScanner` and `BleVehicleGattClient`), backed by defensive `try/catch SecurityException` blocks.
- **Rationale**:
  - Permission verification is an architectural precondition managed by the UI and ViewModel before initiating scanning or connections.
  - Adding `@RequiresPermission` propagates virally across interfaces, polluting the pure Kotlin Domain layer and forcing `Context` into ViewModels.

### 5. Manifest Cleanup

- **Decision**: Remove `tools:targetApi="s"` from `BLUETOOTH_SCAN` in `AndroidManifest.xml`.
- **Rationale**: Since `minSdk = 34`, API 31 (S) targeting annotations are obsolete and generate Android Lint `ObsoleteSdkInt` warnings.

## Risks / Trade-offs

- **[Risk] BroadcastReceiver lifecycle / memory leaks**
  - *Mitigation*: Register the receiver on `context.applicationContext` or manage clean registration/unregistration. Since `BleVehicleScanner` is bound to the application lifetime, `applicationContext` registration avoids activity leaks.
- **[Risk] Permission revoked while connected**
  - *Mitigation*: Concrete data sources continue to wrap all Bluetooth framework calls in `try/catch SecurityException`.
