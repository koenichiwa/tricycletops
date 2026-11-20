# Proposal: Enable Unified Defmt USB Logging via In-Tree Transport

## Why

The Tricycletops firmware originally relied on `defmt-rtt` for diagnostic logging, requiring an external hardware debug probe (such as a Raspberry Pi Debug Probe or Picoprobe over SWD) to observe logs. Developers connecting to the Raspberry Pi Pico W solely via a standard USB cable and flashing with `picotool` cannot observe runtime firmware logs without additional hardware.

A previous iteration introduced `embassy-usb-logger` using the standard `log` facade. However, this caused an architectural split: the CYW43 Wi-Fi/Bluetooth driver, Trouble BLE host stack, Embassy HAL, and `panic-probe` all use `defmt`. As a result, those critical subsystem logs and panic traces were silently dropped or trapped in unread RTT buffers, while only top-level application strings streamed over USB.

Community crates like `embassy-defmt-usb` (https://github.com/sunipkm/embassy-defmt-usb) and `defmt-embassy-usbserial` (https://github.com/robjwells/defmt-embassy-usbserial) demonstrate how to transport `defmt` frames across USB CDC-ACM virtual serial ports using `embassy-usb`. However, pulling in external third-party crates directly introduces version-mismatch and bit-rot risks against our pinned Embassy git revisions. To achieve reliable, 100% unified logging across all firmware layers—application, BLE stack, Wi-Fi driver, Embassy HAL, and panic traces—we will build an in-tree `defmt` USB transport under `src/usb_logger.rs` using `embassy-usb`, drawing architectural inspiration from both `sunipkm/embassy-defmt-usb` and `robjwells/defmt-embassy-usbserial`.

## What Changes

- Implement an in-tree `defmt` USB transport under `src/usb_logger.rs` using `embassy-usb` (specifically `embassy_usb::class::cdc_acm`) that provides a `#[defmt::global_logger]` and an asynchronous CDC-ACM drain task, taking architectural cues from `sunipkm/embassy-defmt-usb` and `robjwells/defmt-embassy-usbserial`.
- Remove `embassy-usb-logger` and the `log` crate from `tricycletops-fw`.
- Revert application code (`src/main.rs`, `src/car_control.rs`, `src/ble_peripheral.rs`) back to `defmt::*` macros (`info!`, `warn!`, `debug!`, `error!`) and `Debug2Format`.
- Keep RP2040 USB controller interrupt binding (`USBCTRL_IRQ`) in `board.rs` and spawn the `src/usb_logger.rs` async task in `main.rs`.
- Stream binary `defmt` frames over the USB CDC-ACM virtual serial port (`/dev/ttyACM0`), decoded on host machines via `defmt-print`.
- Decouple buffer management and framing logic in `src/usb_logger.rs` from ARM hardware peripherals to enable host unit testing (`cargo test-host`).
- Update `README.md` and `GEMINI.md` to document the unified `defmt` workflow and host-side `defmt-print` usage.

## Capabilities

### New Capabilities
*(None)*

### Modified Capabilities
*(None)*

## Impact

- **Firmware (`tricycletops-fw`)**:
  - `Cargo.toml`: Removes `embassy-usb-logger` and `log`. Leverages existing `embassy-usb` dependency and adds the in-tree `src/usb_logger.rs` transport.
  - `src/lib.rs`: Exposes `usb_logger` for host test targets while keeping hardware drivers gated to ARM.
  - `src/usb_logger.rs`: Implements decoupled buffer structures (`LogBuffer`, `Controller`) with unit tests alongside ARM-specific CDC-ACM transport.
  - `src/main.rs`: Reverts to `defmt::*` and spawns the `src/usb_logger.rs` CDC drain task.
  - `src/car_control.rs`, `src/ble_peripheral.rs`: Reverts to `defmt::*` macros.
  - `src/board.rs`: Retains USB driver initialization and `USBCTRL_IRQ` binding.
- **Host Tooling**: Logs are decoded using `defmt-print`:
  ```bash
  defmt-print -e target/thumbv6m-none-eabi/release/tricycletops-fw serial --path /dev/ttyACM0 --dtr
  ```
- **Android App (`tricycletops-app`)**: No impact.
- **Protocol & Hardware**: No impact on BLE GATT services or motor PWM GPIO pin assignments.
