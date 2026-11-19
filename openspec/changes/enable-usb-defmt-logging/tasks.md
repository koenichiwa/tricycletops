# Tasks

## 1. Dependencies & BSP Hardware Integration

- [x] 1.1 Update `tricycletops-fw/Cargo.toml` to replace `defmt-rtt` with `defmt-embassy-usbserial` and `embassy-usb`, and verify that cargo resolves dependencies cleanly with `cargo check --target thumbv6m-none-eabi`.
- [x] 1.2 Update `tricycletops-fw/src/board.rs` to bind `USBCTRL_IRQ => InterruptHandler<USB>` in `bind_interrupts!`, initialize the USB driver in `Board::init()`, and verify compilation with `cargo check --target thumbv6m-none-eabi`.

## 2. USB Logger Task & Firmware Spawning

- [ ] 2.1 Implement the background USB CDC-ACM logger task with `defmt_embassy_usbserial::run`, spawn it in `main.rs` during board startup, and remove the `defmt_rtt as _` import.
- [ ] 2.2 Verify firmware compilation (`cargo build --release --target thumbv6m-none-eabi`), verify host unit tests (`cargo test-host`), and ensure linter/formatter compliance (`cargo fmt --check` and `cargo clippy --target thumbv6m-none-eabi`).

## 3. Developer Documentation & Tooling Workflow

- [ ] 3.1 Update `README.md` and `GEMINI.md` with instructions on installing `defmt-print` and streaming live logs over `/dev/ttyACM0` via USB serial, and verify documentation matches actual project workflow.
