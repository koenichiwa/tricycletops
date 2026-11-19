# Design: Enable USB Logging via Embassy USB Logger

## Context

The Tricycletops firmware (`tricycletops-fw`) runs `#![no_std]` on the RP2040 (Raspberry Pi Pico W) using Embassy async runtime. For developers connected solely via USB and flashing with `picotool`, logs were previously inaccessible without an external SWD debug probe.

While `defmt-embassy-usbserial` was initially considered, it is unmaintained and causes upstream dependency friction with Embassy. Because this diagnostic logger is temporary until hardware telemetry is finalized, flash size is not a limiting factor. Adopting upstream Embassy's officially maintained `embassy-usb-logger` provides real-time plaintext diagnostic logging over USB serial without third-party maintenance burden.

## Goals / Non-Goals

**Goals:**
- Provide real-time diagnostic logging over the onboard micro-USB connector via USB CDC-ACM (virtual COM port `/dev/ttyACM0`).
- Use Embassy's officially maintained `embassy-usb-logger` crate from the upstream Embassy repository.
- Decouple USB log transport from real-time vehicle tasks so motor PWM driving and BLE communication continue uninterrupted.
- Allow viewing logs with standard host-side serial tools (`cat /dev/ttyACM0`, `tio`, `minicom`, `screen`) without binary decoding tools.

**Non-Goals:**
- Implementing interactive two-way serial communication or CLI command processing (stdin).
- Modifying BLE peripheral behavior, GATT characteristics, or motor control algorithms.

## Decisions

### 1. Global Logger Transport: `embassy-usb-logger`
- **Decision:** Use upstream Embassy's `embassy-usb-logger` alongside the standard `log` crate facade.
- **Rationale:** `embassy-usb-logger` is maintained directly inside the Embassy project repository, ensuring version synchronization with `embassy-rp`, `embassy-usb`, and `embassy-sync`. It eliminates unmaintained external crates and vendored patches.
- **Alternatives Considered:**
  - `defmt-embassy-usbserial`: Unmaintained third-party crate tied to Embassy 0.5, requiring vendored crates and ongoing patch maintenance.
  - In-tree custom defmt CDC driver: Adds unnecessary custom maintenance burden when an official Embassy USB logger already exists.

### 2. Peripheral Driver and Interrupt Architecture in `board.rs`
- **Decision:** Register `USBCTRL_IRQ => InterruptHandler<embassy_rp::peripherals::USB>` inside `board.rs`'s `bind_interrupts!` macro. Initialize the `embassy_rp::usb::Driver` in `Board::init()` and expose it on `Board`.
- **Rationale:** Keeps all hardware resource management, pin allocations, and interrupt bindings consolidated inside the Board Support Package (`board.rs`).

### 3. USB Configuration and Task Execution
- **Decision:** Spawn the USB logger task in `main.rs` using `embassy_usb_logger::run!(1024, log::LevelFilter::Info, driver)`.
- **Rationale:** The `run!` macro allocates internal ring buffers, initializes USB CDC-ACM endpoints, and runs the background USB task concurrently.

### 4. Host Decoding Workflow
- **Decision:** Standardize host-side log viewing on standard serial tools:
  ```bash
  cat /dev/ttyACM0
  # or
  tio /dev/ttyACM0
  ```
- **Rationale:** Emits plaintext ASCII directly, eliminating the need to install or maintain `defmt-print` or sync ELF symbols on host machines.

## Risks / Trade-offs

- **[Trade-off] Flash & String Overhead:** The `log` facade formats format strings on-chip rather than offloading string tables via `defmt`.
  - *Mitigation:* The RP2040 has 2MB of external flash; the release firmware binary easily fits with ample headroom. The logger is temporary and will be removed once dedicated telemetry is finalized.
- **[Risk] Unconnected USB port buffer stalls:** If the Pico is powered from a charger or battery with no host reading `/dev/ttyACM0`, logger calls must not block vehicle control.
  - *Mitigation:* `embassy-usb-logger` uses internal non-blocking ring buffers that drop messages upon overflow when the CDC endpoint is disconnected or unread.
