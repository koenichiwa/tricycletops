# Tasks

## 1. Dependencies & BSP Hardware Integration

- [x] 1.1 Update `tricycletops-fw/Cargo.toml` to replace `defmt-embassy-usbserial` with `embassy-usb-logger` and `log`, remove the vendored crate patch, and verify dependency resolution with `cargo check --target thumbv6m-none-eabi`.
- [x] 1.2 Update `tricycletops-fw/src/board.rs` to ensure `USBCTRL_IRQ => InterruptHandler<USB>` is bound in `bind_interrupts!`, initialize the USB driver in `Board::init()`, and verify compilation.

## 2. USB Logger Task & Firmware Spawning

- [x] 2.1 Implement and spawn the USB logger task in `tricycletops-fw/src/main.rs` using `embassy_usb_logger::run!`, update diagnostic logging to use the `log` facade, and remove vendored `crates/defmt-embassy-usbserial`.
- [x] 2.2 Verify firmware compilation (`cargo build --release --target thumbv6m-none-eabi`), verify host unit tests (`cargo test-host`), and ensure linter/formatter compliance (`cargo fmt --check` and `cargo clippy --target thumbv6m-none-eabi`).

## 3. Developer Documentation & Tooling Workflow

- [x] 3.1 Update `README.md` and `GEMINI.md` with instructions on streaming live plaintext logs over `/dev/ttyACM0` via USB serial without external decoding tools, and verify documentation matches actual project workflow.
