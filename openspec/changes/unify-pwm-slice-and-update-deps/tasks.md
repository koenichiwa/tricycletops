# Tasks

## 1. Incremental Dependency Updates

- [ ] 1.1 Upgrade Tier 1 dependencies (`cortex-m`, `cortex-m-rt`, `static_cell`, `portable-atomic`, `reqwest`) in `tricycletops-fw/Cargo.toml`, verify compilation with `cargo test --lib` and `cargo check --target thumbv6m-none-eabi`, and provide Test Card 1 for interactive flashing and BLE advertising verification.
- [ ] 1.2 Upgrade Tier 2 logging dependencies (`defmt`, `defmt-rtt`) in `tricycletops-fw/Cargo.toml`, verify compilation, and provide Test Card 2 for interactive flashing and RTT log streaming verification.

## 2. Hardware PWM Slice Consolidation

- [ ] 2.1 Refactor `MotorControl` in `tricycletops-fw/src/motor_control.rs` to drive speed via `PwmOutput<'a>`, and verify host unit tests pass via `cargo test --lib`.
- [ ] 2.2 Reconfigure `Board::init` in `tricycletops-fw/src/board.rs` to initialize `PWM_SLICE0` using `Pwm::new_output_ab(p.PWM_SLICE0, p.PIN_0, p.PIN_1, ...)`, split channels into Left (GP0) and Right (GP1), assign Direction pins to GP2 and GP3, and verify target compilation via `cargo check --target thumbv6m-none-eabi`.
- [ ] 2.3 Provide Test Card 3 containing physical jumper rewiring instructions, flash commands, and drive verification steps (forward, reverse, steering, watchdog halt) for the user to physically execute and confirm on the robot car.

## 3. Documentation & Diagram Alignment

- [ ] 3.1 Update `wiring_diagram.mmd` and `GEMINI.md` to reflect the new pin mappings (GP0=Left Speed, GP1=Right Speed, GP2=Left Dir, GP3=Right Dir) and unified `PWM_SLICE0` architecture, and verify pre-commit checks pass.
