# Proposal: Enable USB Logging via Embassy USB Logger

## Why

The Tricycletops firmware originally relied on `defmt-rtt` for diagnostic logging, requiring an external hardware debug probe (such as a Raspberry Pi Debug Probe or Picoprobe over SWD) to observe logs. Developers connecting to the Raspberry Pi Pico W solely via a standard USB cable and flashing with `picotool` cannot observe runtime firmware logs without additional hardware.

While `defmt-embassy-usbserial` was considered, it is an unmaintained third-party crate that creates tight coupling and version mismatches with upstream Embassy. Because this diagnostic logger is temporary until dedicated hardware telemetry is finalized, flash footprint is not a concern. Using upstream Embassy's officially maintained `embassy-usb-logger` provides reliable, real-time plaintext diagnostic logging over USB serial without third-party maintenance burden or host-side binary decoders.

## What Changes

- Replace `defmt-rtt` / `defmt-embassy-usbserial` with upstream Embassy's `embassy-usb-logger` and the standard `log` crate in `tricycletops-fw`.
- Bind the RP2040 USB controller interrupt (`USBCTRL_IRQ`) in the board support package (`board.rs`).
- Create and spawn an asynchronous USB logger task using `embassy_usb_logger::run!` in firmware startup to run the USB CDC-ACM device stack and flush log lines over USB serial.
- Stream plaintext log messages over `/dev/ttyACM0` viewable with standard serial tools (`cat`, `tio`, `minicom`, `screen`).
- Update project documentation and operational instructions (`GEMINI.md` and `README.md`) to guide developers on reading live logs over USB serial.

## Capabilities

### New Capabilities
*(None)*

### Modified Capabilities
*(None)*

## Impact

- **Firmware (`tricycletops-fw`)**:
  - `Cargo.toml`: Replaces `defmt-embassy-usbserial` with `embassy-usb-logger` and `log`.
  - `src/main.rs`: Spawns the background USB logger task via `embassy_usb_logger::run!`.
  - `src/board.rs`: Configures the RP2040 USB peripheral driver and binds `USBCTRL_IRQ`.
- **Host Tooling**: Plaintext logs can be monitored directly with standard terminal commands (`cat /dev/ttyACM0`, `tio /dev/ttyACM0`) without requiring `defmt-print`.
- **Android App (`tricycletops-app`)**: No impact.
- **Protocol & Hardware**: No impact on BLE GATT services or motor PWM GPIO pin assignments.
