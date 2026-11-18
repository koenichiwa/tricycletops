# Tasks: Simplify MockMotor Cfg Attributes

## 1. Simplify Conditional Compilation Attributes

- [x] 1.1 In `tricycletops-fw/src/motor_control.rs`, replace `#[cfg(any(test, not(all(target_arch = "arm", target_os = "none"))))]` with `#[cfg(test)]` on `MockMotor`, `impl MockMotor`, and `impl Motor for MockMotor`.
- [x] 1.2 Run host unit tests (`cargo test-host`) to verify mock motor and dependent tests in `car_control` and `motor_control` compile and pass.

## 2. Validation & Tooling

- [x] 2.1 Verify target type-checking and release build (`cargo check --target thumbv6m-none-eabi` and `cargo build --release --target thumbv6m-none-eabi`) to ensure test mock code is cleanly excluded from bare-metal builds.
- [x] 2.2 Run `cargo fmt --check` and `cargo clippy --target thumbv6m-none-eabi` to ensure workspace code standards and linter checks pass with zero warnings.
