# Tasks

## 1. Streamline and Align README.md

- [x] 1.1 Update the Control Protocol section in `README.md` to reflect the active BLE contract (`fff1` signed `i32` LE speeds, `fff2` `u16` LE duty limits) and add a direct link to `openspec/specs/remote-car-control/spec.md`. Verify table values match the canonical spec.
- [x] 1.2 Simplify the repository directory structure in `README.md` to top-level modules (`tricycletops-fw/`, `tricycletops-app/`, `openspec/`) and verify it accurately reflects the workspace layout without brittle subfile paths.
- [x] 1.3 Add host unit test instructions (`cargo test --lib --target host-tuple`) to the Firmware Getting Started section in `README.md`.

## 2. Refine & Optimize GEMINI.md

- [x] 2.1 Remove outdated and granular file trees from `GEMINI.md` (including obsolete `BleScannerManager.kt` and `BleDevice.kt` entries), replacing them with high-level module boundaries and dynamic discovery instructions (`glob`/`grep_search`). Verify no nonexistent paths remain.
- [x] 2.2 Add an explicit directive in `GEMINI.md` designating `openspec/specs/remote-car-control/spec.md` as the single authoritative source of truth for BLE protocols, kinematics, and safety behaviors under the BLE Control Specification section.
- [x] 2.3 Add a workspace validation cheatsheet to `GEMINI.md` documenting commands for Cargo fmt/clippy, Gradle lint, and pre-commit checks.
- [x] 2.4 Condense repetitive project overview and workspace structure text in `GEMINI.md` into concise, high-signal bullet facts to reduce per-turn token overhead.

## 3. Embed Workflow Synchronization Guidance

- [x] 3.1 Update `openspec/config.yaml` under `operations.apply` and `operations.archive` to add guidance reminding agents and contributors to keep `README.md` and `GEMINI.md` in sync whenever protocol specifications change. Verify configuration syntax.

## 4. Documentation Verification

- [x] 4.1 Run pre-commit checks (`check-yaml`, `check-toml`, trailing whitespace) and verify that all cross-references between `README.md`, `GEMINI.md`, and `openspec/specs/` resolve correctly.
- [x] 4.2 Re-run pre-commit checks and verify `README.md` and `GEMINI.md` formatting and cross-references.
