# Proposal: Enable USB Defmt Logging

## Why

The Tricycletops firmware currently relies on `defmt-rtt` for telemetry and diagnostic logging, which requires an external hardware debug probe (such as a Raspberry Pi Debug Probe or Picoprobe over SWD) to capture logs. Developers connecting to the Raspberry Pi Pico W solely via a standard USB cable and flashing with `picotool` cannot observe runtime firmware logs, making troubleshooting difficult without extra hardware.

## What Changes

- Replace the `defmt-rtt` global logger backend with `defmt-embassy-usbserial` and `embassy-usb` in `tricycletops-fw`.
- Bind the RP2040 USB controller interrupt (`USBCTRL_IRQ`) in the board support package (`board.rs`).
- Create and spawn an asynchronous USB logger task in firmware startup to run the USB CDC-ACM device stack and flush `defmt` frames over USB serial.
- Retain all existing `defmt` logging macros (`defmt::info!`, `defmt::debug!`, etc.) across the codebase with zero changes to diagnostic call sites.
- Update project documentation and operational instructions (`GEMINI.md` and `README.md`) to guide developers on reading live logs via `defmt-print`.

## Capabilities

### New Capabilities
*(None)*

### Modified Capabilities
*(None)*

## Impact

- **Firmware (`tricycletops-fw`)**:
  - `Cargo.toml`: Replaces `defmt-rtt` with `defmt-embassy-usbserial` and `embassy-usb`.
  - `src/main.rs`: Removes `defmt_rtt as _` import and spawns the background USB logger task.
  - `src/board.rs`: Configures the RP2040 USB peripheral driver and binds `USBCTRL_IRQ`.
- **Host Tooling**: Developers install `defmt-print` (`cargo install defmt-print`) and view logs via `defmt-print -e target/thumbv6m-none-eabi/release/tricycletops-fw serial --path /dev/ttyACM0 --dtr`.
- **Android App (`tricycletops-app`)**: No impact.
- **Protocol & Hardware**: No impact on BLE GATT services or motor PWM GPIO pin assignments.
