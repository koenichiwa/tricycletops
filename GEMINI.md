# Tricycletops AI Assistant Instructions (`GEMINI.md`)

This document serves as the primary instructional context and reference for AI assistants working in this repository. It defines the workspace structure, building/running commands, protocol specifications, and development conventions.

---

## 🦖 Project Overview

**Tricycletops** is a hobby robotics project comprising a 3-wheeled, dual-motor differential drive robotic car controlled over Bluetooth Low Energy (BLE).

The project is split into two main components:
1. **`tricycletops-fw`**: An asynchronous, bare-metal (`no_std`) Rust firmware for the RP2040 micro-controller (on a Raspberry Pi Pico W), implementing the BLE peripheral stack and motor PWM control.
2. **`tricycletops-app`**: A Kotlin Jetpack Compose Android app acting as a BLE remote controller (scanning, connecting, and sending joystick steering commands).

---

## 📂 Repository Structure

```
tricycletops/
├── tricycletops-fw/          # Async Rust Firmware (Embassy & trouble-host)
│   ├── src/
│   │   ├── main.rs           # Core entry point, task spawner, board initialization
│   │   ├── lib.rs            # Library root exposing testable modules
│   │   ├── board.rs          # BSP: GPIO pins, PWM slices, CYW43 setup, motor driver instantiation
│   │   ├── ble_peripheral.rs # trouble-host BLE peripheral stack and GATT server
│   │   ├── car_control.rs    # Dual motor driver with watchdog timeout and speed kinematics
│   │   ├── motor_control.rs  # Individual motor direction & PWM speed driver
│   │   ├── protocol.rs       # GATT byte codecs for drive commands and duty limits
│   │   └── indicator.rs      # State-driven LED patterns for peripheral connection status
│   ├── .cargo/               # Embedded build configs and runner specifications
│   ├── memory.x              # RP2040 memory map layout
│   └── Cargo.toml            # Firmware Cargo dependencies and patches
│
└── tricycletops-app/         # Kotlin Android Application (Jetpack Compose)
    ├── app/
    │   ├── build.gradle.kts  # App dependencies, SDK targets (Min: 34, Target: 36)
    │   └── src/
    │       ├── main/
    │       │   ├── AndroidManifest.xml
    │       │   └── java/.../tricycletops/
    │       │       ├── MainActivity.kt       # Scanner Jetpack Compose UI entry point
    │       │       ├── BleScannerManager.kt  # Handles Bluetooth permission, scanning logic
    │       │       └── BleDevice.kt          # UI model representing a discovered BLE peripheral
    │       └── test/java/.../tricycletops/
    │           └── ExampleUnitTest.kt        # Local unit tests
```

---

## 📡 BLE Control Specification

The firmware implements a GATT peripheral advertising the local name `Tricycletops` with a specific control service.

* **Device Advertisement Name**: `Tricycletops` (case-insensitive checks are recommended)
* **GATT Service UUID**: `fff0` (Full 128-bit: `0000fff0-0000-1000-8000-00805f9b34fb`)
* **Drive Characteristic UUID**: `fff1` (Read / Write, 8-byte payload)
* **Duty Limits Characteristic UUID**: `fff2` (Read, 8-byte payload)

### 🏎️ Drive Command Payload (8 Bytes - Characteristic `fff1`)

The characteristic `fff1` expects a write containing **two 32-bit signed integers (`i32`) in little-endian format**:

| Byte Offset | Data Type | Value Range | Description |
|:---|:---|:---|:---|
| **0 to 3** | `i32` (LE) | `-Left Max Duty` to `Left Max Duty` | **Left Motor Speed** (Positive = Forward, Negative = Reverse, `0` = Stop) |
| **4 to 7** | `i32` (LE) | `-Right Max Duty` to `Right Max Duty` | **Right Motor Speed** (Positive = Forward, Negative = Reverse, `0` = Stop) |

### 📊 Duty Limits Payload (8 Bytes - Characteristic `fff2`)

The characteristic `fff2` exposes the minimum (deadband) and maximum PWM duty cycle limits configured on the microcontroller. It contains **four 16-bit unsigned integers (`u16`) in little-endian format**:

| Byte Offset | Data Type | Description |
|:---|:---|:---|
| **0 to 1** | `u16` (LE) | **Left Motor Minimum Duty Cycle Limit** |
| **2 to 3** | `u16` (LE) | **Left Motor Maximum Duty Cycle Limit** |
| **4 to 5** | `u16` (LE) | **Right Motor Minimum Duty Cycle Limit** |
| **6 to 7** | `u16` (LE) | **Right Motor Maximum Duty Cycle Limit** |

---

## 🛠️ Building, Running, and Testing Commands

### 🔌 Firmware (`tricycletops-fw`)

All firmware commands must be run inside the `tricycletops-fw/` directory:

* **Host Unit Tests**:
  ```bash
  cargo test --lib --target x86_64-unknown-linux-gnu
  ```
* **Syntax & Type Check**:
  ```bash
  cargo check --target thumbv6m-none-eabi
  ```
* **Build (Release Profile)**:
  ```bash
  cargo build --release --target thumbv6m-none-eabi
  ```
  *Note: Compiling downloads the CYW43 Wi-Fi/Bluetooth binaries via `build.rs` into `target/cyw43-firmware/`.*
* **Flash & Run (via USB / picotool)**:
  ```bash
  cargo run
  ```
  *Note: The runner command is defined in `.cargo/config.toml`. Ensure the Pico W is in BOOTSEL mode (hold button while plugging in USB).*
* **Debugging & Log Stream**:
  The firmware uses `defmt` over RTT (Real-Time Transfer). Logs are streamed to the terminal when using `probe-rs run --chip RP2040`.

### 📱 Android Application (`tricycletops-app`)

All app commands must be run inside the `tricycletops-app/` directory:

* **Compile & Build Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
* **Run Local Unit Tests**:
  ```bash
  ./gradlew test
  ```
* **Install and Deploy**:
  Open the project in **Android Studio**, connect a physical Android device (with USB debugging enabled) or emulator, and click the **Run 'app'** button (`Shift + F10`).

---

## 📐 Development Conventions & Guidelines

### 🦀 Firmware (Rust)

1. **Embedded Constraints (`no_std`)**:
   - The target environment is strictly `#![no_std]` and `#![no_main]`. Dynamic memory allocation (`alloc`) is **disabled**; all resources must be statically allocated. Use `StaticCell` for dynamic structures that require `'static` lifetimes.
2. **Dual Library/Binary Architecture & Host Testing**:
   - `src/lib.rs` exports decoupled modules (`protocol`, `car_control`, `motor_control`, `indicator`) with target-gated Cortex-M dependencies, enabling fast host-based unit tests (`cargo test --lib --target x86_64-unknown-linux-gnu`) without requiring target hardware.
   - `src/board.rs` encapsulates RP2040 GPIO pins, PWM slices, DMA channels, and CYW43 wireless initialization into a dedicated Board Support Package (BSP).
3. **Autonomous Command Watchdog & Disconnection Failsafe**:
   - `CarControl::run` enforces an autonomous 500ms watchdog timeout (`embassy_time::with_timeout`). If no drive command is received within 500ms, or when a GATT disconnection occurs, motors are immediately halted to prevent runaway vehicle behavior.
4. **Resilient Motor Control Loop**:
   - Actuator driver errors (`PwmError`) are safely caught in `CarControl::run`, motor duty cycles are zeroed, and the task continues listening for subsequent commands without terminating.
5. **Asynchronous Execution (Embassy)**:
   - Always prefer asynchronous execution over busy-waiting loops.
   - Use `embassy_time::Timer` for delays (`Timer::after_secs(1).await` or `Timer::after_millis(x).await`).
6. **Hardware & PWM Drivers**:
   - Motor driving is implemented via RP2040 hardware PWM slices (`PWM_SLICE0` for PIN_0/PIN_1 and `PWM_SLICE1` for PIN_2/PIN_3).
   - Low-level actuation uses raw `u16` duty cycles and an explicit `Direction` enum (`Forward`, `Reverse`) in `MotorControl`, mapping forward motion to direction pin LOW and reverse to HIGH with inverted duty (`max_duty - duty`).
   - `CarControl` decomposes incoming signed `i32` commands from characteristic `fff1` into `(duty: u16, direction: Direction)` tuples via `split_speed()`.
   - Ensure fully-off state is explicitly set via `set_duty_cycle_fully_off()` (or `stop()`) to prevent motor creep.
7. **Diagnostic Logging**:
   - Never print to `stdout`/`stderr` using standard macros. Always use `defmt::info!`, `defmt::debug!`, `defmt::warn!`, or `defmt::error!`.
8. **Status Indication & BLE Handling**:
   - The BLE peripheral runs via `trouble-host` stack inside `ble_peripheral::run()`.
   - `indicator::indicator_task` drives the onboard status LED (PIN_15) based on `PeripheralState` signals:
     - `Advertising`: 500ms on / 500ms off blinking.
     - `Connected`: Solid ON.
     - `Disconnected`: Rapid flash (100ms on / 100ms off) before returning to advertising.

### 🤖 Android Application (Kotlin)

1. **Reactive State Pattern**:
   - Use Kotlin `StateFlow` and `MutableStateFlow` to manage and expose asynchronous events (like device discovery, scanning state, and connection status) to the UI.
2. **Modern Declarative UI (Jetpack Compose & Material 3)**:
   - Keep screens highly modular and use standard Compose components.
   - The primary UI theme is defined in `com.koenichiwa.tricycletops.ui.theme`.
3. **Runtime Permissions**:
   - Always prompt and check for necessary permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`) before starting a scan. Ensure UI states handle denied permissions gracefully.
4. **Testing Standards**:
   - Keep local unit tests up-to-date inside `app/src/test/java/...` to verify service UUID parsing, peripheral name matching, and protocol message formatting.
