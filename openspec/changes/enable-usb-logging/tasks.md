# Tasks

## 1. In-Tree Defmt USB Transport (`src/usb_logger.rs`)

- [x] 1.1 Implement the in-tree `defmt` USB transport under `src/usb_logger.rs` implementing `#[defmt::global_logger]` and an async CDC-ACM drain task using `embassy-usb`, taking inspiration from `sunipkm/embassy-defmt-usb` (buffering strategy) and `robjwells/defmt-embassy-usbserial` (CDC-ACM sender loop).
- [x] 1.2 Update `tricycletops-fw/Cargo.toml` to remove `embassy-usb-logger` and `log`, and verify dependency compilation with `cargo check --target thumbv6m-none-eabi`.
- [x] 1.3 Refactor `src/usb_logger.rs` and `src/lib.rs` to decouple `LogBuffer`, `Chunk`, and `Controller` from ARM-specific USB drivers, allowing compilation on host test targets.
- [ ] 1.4 Add host unit tests in `src/usb_logger.rs` covering buffer writes, chunk extraction up to `MAX_PACKET_SIZE`, and non-blocking drop-on-overflow behavior, verifying with `cargo test-host`.

## 2. Firmware Integration & Verification

- [x] 2.1 Revert `main.rs`, `car_control.rs`, and `ble_peripheral.rs` to `defmt::*` macros, encapsulate USB builder and `logger_task` in `src/usb_logger.rs`, and spawn it in `main.rs`.
- [ ] 2.2 Verify firmware compilation (`cargo build --release --target thumbv6m-none-eabi`), verify host unit tests (`cargo test-host`), and ensure linter/formatter compliance (`cargo fmt --check` and `cargo clippy --target thumbv6m-none-eabi`).

## 3. Developer Documentation & Tooling Workflow

- [x] 3.1 Update `README.md` and `GEMINI.md` with instructions on streaming live `defmt` logs over `/dev/ttyACM0` via `defmt-print`, and verify documentation matches actual project workflow.
