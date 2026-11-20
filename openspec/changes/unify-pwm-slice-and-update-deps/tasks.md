# Tasks

## 1. Tier 1: Core Primitives Dependency Updates

- [ ] 1.1 Fetch upstream Embassy RP example `Cargo.toml` (`https://raw.githubusercontent.com/embassy-rs/embassy/refs/heads/main/examples/rp/Cargo.toml`) and query crates.io (giving Embassy example versions precedence), and record target versions into `.target_versions.scratch`. Don't commit this file.
- [ ] 1.2 Fetch relevant upstream Embassy RP code examples (`pwm.rs`, `blinky.rs`, `wifi_blinky.rs`) into `.examples.scratch/` to serve as local reference patterns for syntax, executor entry macros, and HAL subsystem patterns. Don't commit this directory.
- [ ] 1.3 Incrementally update Tier 1 dependencies (`cortex-m`, `cortex-m-rt`, `static_cell`, `portable-atomic`, `reqwest`, `embassy-time` specifier) in `tricycletops-fw/Cargo.toml`, verify compilation with `cargo test-host` and `cargo check --target thumbv6m-none-eabi`, and provide Test Card 1 for interactive flashing and BLE advertising verification.

## 2. Tier 2: Logging Dependencies & USB Transport Verification

- [ ] 2.1 Update `defmt` to 1.2.0 in `tricycletops-fw/Cargo.toml`, confirm obsolete `defmt-rtt` references are removed, verify in-tree `usb_logger.rs` via `cargo test-host` and `cargo check --target thumbv6m-none-eabi`, and provide Test Card 2 for interactive flashing and USB serial log decoding via `defmt-print` on `/dev/ttyACM0`.

## 3. Tier 3: Embassy Git Patches & Trouble-Host 0.8 Migration

- [ ] 3.1 Update `[patch.crates-io]` in `tricycletops-fw/Cargo.toml` to upstream Embassy `main`, upgrade `trouble-host` to 0.8.0 and `bt-hci` to 0.10.0, and resolve cargo dependency tree with `cargo check`.
- [ ] 3.2 Adapt `src/board.rs` to upstream Embassy `main` (`cyw43` transport interface, DMA channel handling for `PioSpi`, preserving `usb_driver` and `USBCTRL_IRQ`).
- [ ] 3.3 Refactor `src/ble_peripheral.rs` for `trouble-host 0.8.0` (`#[gatt_server]` macros, `HostResources`, runner architecture).
- [ ] 3.4 Verify compilation (`cargo check --target thumbv6m-none-eabi`) and host tests (`cargo test-host`), and provide Test Card 3 for interactive flashing and verifying mobile app connection and drive command reception.

## 4. Tier 4: Hardware PWM Slice Consolidation

- [ ] 4.1 Refactor `MotorControl` in `tricycletops-fw/src/motor_control.rs` to drive speed via `PwmOutput<'a>`, and verify host unit tests pass via `cargo test-host`.
- [ ] 4.2 Reconfigure `Board::init` in `tricycletops-fw/src/board.rs` to initialize `PWM_SLICE0` using `Pwm::new_output_ab(p.PWM_SLICE0, p.PIN_0, p.PIN_1, ...)`, split channels into Left (GP0) and Right (GP1), assign Direction pins to GP2 and GP3, and verify target compilation via `cargo check --target thumbv6m-none-eabi`.
- [ ] 4.3 Provide Test Card 4 containing physical jumper rewiring instructions, flash commands, and drive verification steps (forward, reverse, steering, watchdog halt) for the user to physically execute and confirm on the robot car.

## 5. Documentation & Workspace Cleanup

- [ ] 5.1 Update `wiring_diagram.mmd` and `GEMINI.md` to reflect the new pin mappings (GP0=Left Speed, GP1=Right Speed, GP2=Left Dir, GP3=Right Dir) and unified `PWM_SLICE0` architecture, and verify pre-commit checks pass.
- [ ] 5.2 Delete `.target_versions.scratch` and `.examples.scratch/` after verification and confirm the workspace is clean.
