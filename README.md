# Tricycletops 🦖

Tricycletops is a Bluetooth Low Energy (BLE) controlled robotics/car platform consisting of:
1. **`tricycletops-fw`**: An asynchronous, `no_std` Rust firmware for the **RP2040** (configured for Raspberry Pi Pico W) driving a dual-motor differential drive system via PWM.
2. **`tricycletops-app`**: A Kotlin Jetpack Compose Android app acting as a BLE remote control to command the robotic car's speed and direction.

---

## Repository Structure

```
tricycletops/
├── tricycletops-fw/          # Async Rust Firmware (Embassy & trouble-host)
│   ├── src/                  # Firmware source code (motors, BLE peripheral, main)
│   ├── .cargo/               # Embedded targets and build configurations
│   ├── memory.x              # RP2040 memory map
│   └── Cargo.toml            # Rust cargo manifest
│
└── tricycletops-app/         # Kotlin Android Application (Jetpack Compose)
    ├── app/                  # Android application module source & configs
    ├── gradle/               # Declarative Gradle version catalogs
    └── build.gradle.kts      # Project-level build script
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
3.  **Build the binary**:
    Compile the release-optimized binary:
    ```bash
    cargo build --release --target thumbv6m-none-eabi
    ```
    *Note: The compilation automatically downloads the necessary Cyw43 Wi-Fi/Bluetooth firmware binaries into `target/cyw43-firmware/` via `build.rs`.*
4.  **Flash the RP2040 / Pi Pico W**:
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

*   **Advertised Name**: `TricycleTops`
*   **GATT Service UUID**: `fff0` (represented as `[0xf0, 0xff]` in advertising packets)
*   **Characteristic UUID**: `fff1` (Read / Write, 8-byte payload)

### Drive Command Structure

To steer and move the Tricycletops car, write an 8-byte payload to characteristic `fff1` containing two **little-endian 32-bit floats** (`f32`):

| Byte Range | Type  | Value Range     | Description |
|---|---|---|---|
| **0 to 3** | `f32` (little-endian) | `-1.0` to `1.0` | **Left Motor Speed** (Positive = Forward, Negative = Reverse, `0.0` = Stop) |
| **4 to 7** | `f32` (little-endian) | `-1.0` to `1.0` | **Right Motor Speed** (Positive = Forward, Negative = Reverse, `0.0` = Stop) |

---

## 🛠️ Diagnostics & Logs

The firmware outputs highly optimized debug logs over RTT (Real-Time Transfer).
*   If using `probe-rs`, logs will stream directly to your terminal.
*   The onboard LED on the Raspberry Pi Pico W blinks once per second when advertising, and changes state when a central BLE device connects/disconnects.
