# Design: Enable Unified Defmt USB Logging via In-Tree Transport

## Context

The Tricycletops firmware (`tricycletops-fw`) runs `#![no_std]` on the RP2040 (Raspberry Pi Pico W) using Embassy async runtime. For developers connected solely via USB and flashing with `picotool`, logs were previously inaccessible without an external SWD debug probe.

An interim attempt using `embassy-usb-logger` with the `log` facade created a fragmented logging architecture:
- Application code used `log::info!`.
- Underlying driver and runtime stacks (`cyw43`, `trouble-host`, `embassy-rp`, `panic-probe`) use `defmt`.
- Because `embassy-usb-logger` only drains `log` records over USB, driver logs and panic messages were routed to `defmt-rtt` and lost.

Community projects like [`sunipkm/embassy-defmt-usb`](https://github.com/sunipkm/embassy-defmt-usb) and [`robjwells/defmt-embassy-usbserial`](https://github.com/robjwells/defmt-embassy-usbserial) demonstrate proven approaches for transporting `defmt` frames across USB CDC-ACM endpoints using `embassy-usb`. However, depending directly on third-party crates introduces version mismatch and maintenance risks against our specific Embassy git revisions. To achieve a clean, unified logging experience across the entire system, we will implement an in-tree `defmt` USB transport under `src/usb_logger.rs` using `embassy-usb`, drawing design inspiration from these reference implementations.

## Goals / Non-Goals

**Goals:**
- Unify 100% of firmware logging under `defmt` (application code, CYW43 driver, Trouble BLE host, Embassy HAL, and panic traces).
- Stream `defmt` frames over the onboard micro-USB connector via USB CDC-ACM (`/dev/ttyACM0`) using `embassy-usb`.
- Implement the transport in-tree under `src/usb_logger.rs`, incorporating architectural patterns from community reference crates (`sunipkm/embassy-defmt-usb`, `robjwells/defmt-embassy-usbserial`) while avoiding external dependency drift.
- Ensure logger buffering is non-blocking so motor PWM and BLE tasks are never stalled when USB is disconnected or unread.
- Standardize host log decoding via `defmt-print`.

**Non-Goals:**
- Implementing bidirectional serial communication or serial shell/CLI commands.
- Modifying BLE peripheral behavior, GATT characteristics, or motor control algorithms.

## Decisions

### 1. In-Tree Defmt USB Transport (`src/usb_logger.rs`) with `embassy-usb`
- **Decision:** Build a minimal, zero-external-dependency crate/module under `src/usb_logger.rs` implementing `#[defmt::global_logger]` and a CDC-ACM worker using `embassy-usb`.
- **Rationale:**
  - `defmt::global_logger` requires implementing `acquire`, `release`, and `write` methods that capture encoded log frames into an internal buffer.
  - An asynchronous worker task drains this buffer to an `embassy_usb::class::cdc_acm::CdcAcmClass` endpoint (`Sender`).
  - Keeping this in-tree (~120–160 lines of code) provides complete compatibility with our exact pinned Embassy revisions while avoiding third-party crate abandonment or bit rot.
- **Architectural Inspiration from Community Reference Crates:**
  - **`sunipkm/embassy-defmt-usb`**: Inspires the buffering strategy (double-buffering / frame buffer swapping upon completion to minimize critical-section contention) and clean CDC-ACM USB descriptor layout.
  - **`robjwells/defmt-embassy-usbserial`**: Inspires the modular CDC-ACM drain loop using `embassy_usb::class::cdc_acm::Sender`, explicit connection lifecycle handling (`sender.wait_connection().await`), and non-blocking packet transmission handling across bus resets and unread ports.
- **Alternatives Considered:**
  - *Direct dependency on `defmt-embassy-usbserial` or `embassy-defmt-usb`*: Rather than taking a direct crate dependency that introduces version conflicts or requires vendoring patches, we adopt their core architectural patterns directly into our in-tree implementation.
  - *`embassy-usb-logger`*: Drops all `defmt` logs from `cyw43`, `trouble-host`, and `panic-probe`, causing split-brain logging.

### 2. Unified Logging API
- **Decision:** Use `defmt::*` (`info!`, `warn!`, `debug!`, `error!`, `trace!`) across all firmware modules (`main.rs`, `car_control.rs`, `ble_peripheral.rs`). Remove the `log` crate from the firmware.
- **Rationale:** Aligns application code with the embedded ecosystem conventions already used by `cyw43`, `trouble-host`, and `embassy-rp`. Enables `panic-probe` messages to route through the same diagnostic channel.

### 3. Non-Blocking Ring Buffer Architecture
- **Decision:** The in-tree logger uses an internal ring buffer. When the buffer is full (e.g. host is not connected or serial port is closed), incoming bytes overwrite or drop frames rather than blocking the calling thread.
- **Rationale:** Embedded safety critical: logging statements inside interrupt handlers or high-priority control loops must never block or cause motor PWM deadline misses.

### 4. Peripheral Driver and Interrupt Architecture in `board.rs`
- **Decision:** Keep `USBCTRL_IRQ => InterruptHandler<embassy_rp::peripherals::USB>` bound inside `board.rs`'s `bind_interrupts!` macro, and initialize the `embassy_rp::usb::Driver` in `Board::init()`.
- **Rationale:** Keeps all hardware resource management, pin allocations, and interrupt bindings consolidated inside the Board Support Package (`board.rs`).

### 5. Host Decoding Workflow
- **Decision:** Standardize host-side log viewing using `defmt-print`:
  ```bash
  defmt-print -e target/thumbv6m-none-eabi/release/tricycletops-fw serial --path /dev/ttyACM0 --dtr
  ```
- **Rationale:** `defmt-print` reads the ELF symbol table from the target binary on the host to decode binary frames in real-time, displaying timestamps, file/line locations, and formatted messages.

## Risks / Trade-offs

- **[Trade-off] Host Tool Requirement:** Developers must install `defmt-print` (`cargo install defmt-print`) and provide the matching ELF binary.
  - *Mitigation:* Document the exact `defmt-print` command in `README.md` and `GEMINI.md`. `defmt-print` is standard tooling in the embedded Rust ecosystem.
- **[Risk] Frame Loss During Disconnection:** If the Pico is running on battery without a USB host, the logger buffer will fill and drop frames.
  - *Mitigation:* Ring buffer drops excess bytes without blocking or crashing the MCU, ensuring continuous car control.
