# Tasks

## 1. Streamline and Align README.md

- [x] 1.1 Update the Control Protocol section in `README.md` to reflect the active BLE contract (`fff1` signed `i32` LE speeds, `fff2` `u16` LE duty limits) and add a direct link to `openspec/specs/remote-car-control/spec.md`. Verify table values match the canonical spec.
- [x] 1.2 Simplify the repository directory structure in `README.md` to top-level modules (`tricycletops-fw/`, `tricycletops-app/`, `openspec/`) and verify it accurately reflects the workspace layout without brittle subfile paths.

## 2. Refine GEMINI.md

- [x] 2.1 Remove outdated and granular file trees from `GEMINI.md` (including obsolete `BleScannerManager.kt` and `BleDevice.kt` entries), replacing them with high-level module boundaries and dynamic discovery instructions (`glob`/`grep_search`). Verify no nonexistent paths remain.
- [x] 2.2 Add an explicit directive in `GEMINI.md` designating `openspec/specs/` as the single authoritative source of truth for BLE protocols, kinematics, and safety behaviors, keeping the existing compact GATT tables as quick reference only.

## 3. Embed Workflow Synchronization Guidance

- [x] 3.1 Update `openspec/config.yaml` under `operations.apply` and `operations.archive` to add guidance reminding agents and contributors to keep `README.md` and `GEMINI.md` in sync whenever protocol specifications change. Verify configuration syntax.

## 4. Documentation Verification

- [ ] 4.1 Run pre-commit checks (`check-yaml`, `check-toml`, trailing whitespace) and verify that all cross-references between `README.md`, `GEMINI.md`, and `openspec/specs/` resolve correctly.
