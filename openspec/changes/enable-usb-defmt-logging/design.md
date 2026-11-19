# Design: Enable USB Defmt Logging

## Context

The Tricycletops firmware (`tricycletops-fw`) runs `#![no_std]` on the RP2040 (Raspberry Pi Pico W) using Embassy async runtime and `defmt` for structured logging. Currently, `defmt-rtt` is configured as the global logger, streaming binary frames into an SRAM ring buffer accessed exclusively via SWD debug probes (such as Raspberry Pi Debug Probe or Picoprobe).

For users connected solely via a USB cable and flashing with `picotool`, logs are inaccessible. See `proposal.md` for motivation.

## Goals / Non-Goals

**Goals:**
- Provide real-time diagnostic logging over the onboard micro-USB connector via USB CDC-ACM (virtual COM port).
- Retain all existing `defmt` macros (`defmt::info!`, `defmt::debug!`, etc.) across firmware crates without modifying diagnostic call sites.
- Decouple USB log transport from real-time vehicle tasks so motor PWM driving and BLE communication continue uninterrupted.
- Provide a standardized host-side decoding workflow via `defmt-print`.

**Non-Goals:**
- Converting the firmware from `defmt` to standard text logging (`log` crate).
- Implementing two-way interactive USB serial communication or CLI command processing (stdin).
- Modifying BLE peripheral behavior, GATT characteristics, or motor control algorithms.

## Decisions

### 1. Global Logger Transport: `defmt-embassy-usbserial`
- **Decision:** Replace `defmt-rtt` with `defmt-embassy-usbserial` (v0.2.1) and `embassy-usb` (v0.5.0).
- **Rationale:** `defmt-embassy-usbserial` provides a `#[defmt::global_logger]` implementation designed for Embassy USB CDC-ACM drivers. It eliminates the need to change any log macros in the codebase while using Embassy's asynchronous driver architecture.
- **Alternatives Considered:**
  - `embassy-usb-logger`: Implements the `log` crate facade rather than `defmt`. Adopting it would require modifying all dependencies' feature flags and refactoring every log call site in `src/`.
  - Hardware UART: Requires dedicated external USB-to-UART hardware adapters and solder/jumper connections to GPIO pins.

### 2. Peripheral Driver and Interrupt Architecture in `board.rs`
- **Decision:** Register `USBCTRL_IRQ => InterruptHandler<embassy_rp::peripherals::USB>` inside `board.rs`'s `bind_interrupts!` macro. Initialize the `embassy_rp::usb::Driver` in `Board::init()` and pass it to a background logger task.
- **Rationale:** Keeps all hardware resource management, pin allocations, and interrupt bindings consolidated inside the Board Support Package (`board.rs`).
- **Alternatives Considered:**
  - Initializing USB in `main.rs`: Violates the BSP separation established in `board.rs`.

### 3. USB Configuration and Descriptors
- **Decision:** Configure standard CDC-ACM descriptor attributes with composite IAD (`composite_with_iads = true`, class `0xEF`, subclass `0x02`, protocol `0x01`). Set product name to `"Tricycletops Defmt Logger"`.
- **Rationale:** Ensures plug-and-play driver recognition as `/dev/ttyACM*` across Linux and macOS without requiring custom kernel drivers.

### 4. Host Decoding via `defmt-print`
- **Decision:** Standardize host-side log viewing on `defmt-print`:
  ```bash
  defmt-print -e target/thumbv6m-none-eabi/release/tricycletops-fw serial --path /dev/ttyACM0 --dtr
  ```
- **Rationale:** `defmt-print` (v1.1+) includes native serial support with automatic `rzcobs` frame boundary synchronization and DTR signaling required for USB CDC-ACM devices.

## Risks / Trade-offs

- **[Risk] Unconnected USB port causes buffer backpressure:** If the Pico is powered from battery or a USB charger with no host terminal reading `/dev/ttyACM0`, logging writes could stall.
  - *Mitigation:* `defmt-embassy-usbserial` uses internal ring buffers that discard oldest frames upon overflow rather than blocking async tasks.
- **[Risk] Intermittent frame corruption upon terminal reconnection:** Reconnecting `defmt-print` mid-execution may catch a partial binary frame.
  - *Mitigation:* Modern `defmt` uses `rzcobs` encoding with zero-byte delimiters; `defmt-print` automatically detects and skips malformed partial frames until the next frame boundary.
- **[Risk] Target vs. Host test compilation conflicts:** `embassy-usb` and `embassy-rp::usb` are target-only dependencies.
  - *Mitigation:* Gate USB driver and logger task initialization under `#[cfg(all(target_arch = "arm", target_os = "none"))]` ensuring `cargo test-host` runs unmodified.
