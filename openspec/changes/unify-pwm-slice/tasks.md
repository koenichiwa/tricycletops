# Tasks

## 1. Hardware PWM Driver Refactoring

- [ ] 1.1 Refactor `MotorControl` in `tricycletops-fw/src/motor_control.rs` to drive speed via `PwmOutput<'a>`, and verify host unit tests pass via `cargo test-host`.
- [ ] 1.2 Reconfigure `Board::init` in `tricycletops-fw/src/board.rs` to initialize `PWM_SLICE0` using `Pwm::new_output_ab(p.PWM_SLICE0, p.PIN_0, p.PIN_1, ...)`, split channels into Left (GP0) and Right (GP1), assign Direction pins to GP2 and GP3, and verify target compilation via `cargo check --target thumbv6m-none-eabi`.

## 2. Hardware Test Card & Physical Verification

- [ ] 2.1 Provide the hardware Test Card with physical jumper rewiring instructions (GP0=Left PWM, GP1=Right PWM, GP2=Left Dir, GP3=Right Dir), flash commands, and drive verification steps (forward, reverse, steering, watchdog halt) for the user to physically execute and confirm on the robot car.

## 3. Documentation & Workspace Validation

- [ ] 3.1 Update `GEMINI.md` and repository wiring documentation to reflect the new pin mappings (GP0=Left Speed, GP1=Right Speed, GP2=Left Dir, GP3=Right Dir) and unified `PWM_SLICE0` architecture.
- [ ] 3.2 Run workspace linters (`cargo fmt`, `cargo clippy --target thumbv6m-none-eabi`, and `pre-commit run --all-files`) and verify all checks pass cleanly.
