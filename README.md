# Tricycletops 🦖

Tricycletops is a Bluetooth Low Energy (BLE) controlled robotics/car platform consisting of:
1. **`tricycletops-fw`**: An asynchronous, `no_std` Rust firmware for the **RP2040** (configured for Raspberry Pi Pico W) driving a dual-motor differential drive system via PWM.
2. **`tricycletops-app`**: A Kotlin Jetpack Compose Android app acting as a BLE remote control to command the robotic car's speed and direction.

---

## Repository Structure

```
tricycletops/
├── tricycletops-fw/          # Async Rust Firmware (Embassy & trouble-host)
├── tricycletops-app/         # Kotlin Android Application (Jetpack Compose)
└── openspec/                 # OpenSpec specifications and documentation workflows
```

---

## System Prerequisites & Tools Installation

### 1. Firmware Tooling (`tricycletops-fw`)

To compile and flash Rust firmware to the RP2040 micro-controller, install the following:

*   **Rust Toolchain**: Use [rustup](https://rustup.rs/) to install Rust.
*   **Arm Cortex-M0+ Target**: Install the compilation target for the RP2040:
    ```bash
    rustup target add thumbv6m-none-eabi
    ```
*   **Picotool**: Required to flash the RP2040 board over a USB cable without a debugger. Follow the [Raspberry Pi Picotool Installation Guide](https://github.com/raspberrypi/picotool) to compile or install it on your OS.
*   **Probe-rs (Optional)**: If you use a physical SWD debug probe (like another Pico running Picoprobe), install [probe-rs](https://probe.rs/) to flash and debug in real-time.

### 2. Android App Tooling (`tricycletops-app`)

To compile and run the Android app:

*   **Java Development Kit (JDK 17 or higher)**: Download and install JDK from [Eclipse Temurin](https://adoptium.net/).
*   **Android Studio**: Install the official IDE from [Android Studio Download](https://developer.android.com/studio). This automatically configures the Android SDK and build tools.

---

## 🚀 Getting Started

### 🛠️ Setting up and Running the Firmware

1.  **Navigate to the firmware directory**:
    ```bash
    cd tricycletops-fw
    ```
2.  **Verify compilation**:
    Run a fast syntax and type check on the project:
    ```bash
    cargo check --target thumbv6m-none-eabi
    ```
3.  **Run host unit tests**:
    Execute fast unit tests on your host machine without physical hardware:
    ```bash
    cargo test --lib --target x86_64-unknown-linux-gnu
    ```
4.  **Build the binary**:
    Compile the release-optimized binary:
    ```bash
    cargo build --release --target thumbv6m-none-eabi
    ```
    *Note: The compilation automatically downloads the necessary Cyw43 Wi-Fi/Bluetooth firmware binaries into `target/cyw43-firmware/` via `build.rs`.*
5.  **Flash the RP2040 / Pi Pico W**:
    *   **Using Picotool (USB)**: Put your Pico W into BOOTSEL mode by holding the `BOOTSEL` button while plugging in the USB cable. Then run:
        ```bash
        cargo run
        ```
        This invokes `picotool load -u -v -x -t elf` as defined in `.cargo/config.toml` to load and automatically start the program.
    *   **Using Probe-rs (Debugger)**: Edit `.cargo/config.toml` to swap the runner to `probe-rs run --chip RP2040` and execute `cargo run`.

---

### 📱 Setting up and Running the Android App

1.  **Navigate to the app directory**:
    ```bash
    cd tricycletops-app
    ```
2.  **Compile and build the Debug APK**:
    Use the Gradle wrapper to build the package:
    ```bash
    ./gradlew assembleDebug
    ```
3.  **Run tests**:
    Validate build sanity by executing the unit tests:
    ```bash
    ./gradlew test
    ```
4.  **Install on a Device/Emulator**:
    - Open the `tricycletops-app` folder inside **Android Studio**.
    - Connect your Android phone via USB with **Developer Options & USB Debugging** enabled, or start an Android Virtual Device (AVD) emulator.
    - Click the green **Run 'app'** button (or press `Shift + F10`) in Android Studio to build, deploy, and launch the application.

---

## 📡 Control Protocol (BLE Spec)

When the firmware boots, the Raspberry Pi Pico W acts as a Bluetooth Low Energy (BLE) peripheral:

*   **Advertised Name**: `Tricycletops` (case-insensitive checks recommended)
*   **GATT Service UUID**: `fff0` (`0000fff0-0000-1000-8000-00805f9b34fb`)
*   **Drive Characteristic UUID**: `fff1` (`0000fff1-0000-1000-8000-00805f9b34fb`, Read / Write, 8-byte payload)
*   **Duty Limits Characteristic UUID**: `fff2` (`0000fff2-0000-1000-8000-00805f9b34fb`, Read, 8-byte payload)

> 📖 **Authoritative Specification**: The single source of truth for BLE protocols, packet codecs, differential drive kinematics, deadband handling, throttling (25Hz), and safety failsafes is defined in [`openspec/specs/remote-car-control/spec.md`](openspec/specs/remote-car-control/spec.md).

### Drive Command Payload (8 Bytes - Characteristic `fff1`)

To steer and move the Tricycletops car, write an 8-byte payload to characteristic `fff1` containing **two 32-bit signed integers (`i32`) in little-endian format**:

| Byte Offset | Data Type | Value Range | Description |
|---|---|---|---|
| **0 to 3** | `i32` (LE) | `-Left Max Duty` to `Left Max Duty` | **Left Motor Speed** (Positive = Forward, Negative = Reverse, `0` = Stop) |
| **4 to 7** | `i32` (LE) | `-Right Max Duty` to `Right Max Duty` | **Right Motor Speed** (Positive = Forward, Negative = Reverse, `0` = Stop) |

### Duty Limits Payload (8 Bytes - Characteristic `fff2`)

Characteristic `fff2` exposes the minimum (deadband) and maximum PWM duty cycle limits configured on the microcontroller. Central clients read this 8-byte payload containing **four 16-bit unsigned integers (`u16`) in little-endian format**:

| Byte Offset | Data Type | Description |
|---|---|---|
| **0 to 1** | `u16` (LE) | **Left Motor Minimum Duty Cycle Limit** |
| **2 to 3** | `u16` (LE) | **Left Motor Maximum Duty Cycle Limit** |
| **4 to 5** | `u16` (LE) | **Right Motor Minimum Duty Cycle Limit** |
| **6 to 7** | `u16` (LE) | **Right Motor Maximum Duty Cycle Limit** |

---

## 🛠️ Diagnostics & Logs

The firmware outputs highly optimized debug logs over RTT (Real-Time Transfer).
*   If using `probe-rs`, logs will stream directly to your terminal.
*   The onboard LED on the Raspberry Pi Pico W blinks once per second when advertising, and changes state when a central BLE device connects/disconnects.
