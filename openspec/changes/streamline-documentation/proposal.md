# Proposal

## Why

Documentation across `README.md`, `GEMINI.md`, and `openspec/` currently suffers from redundancy and drift: `README.md` documents an outdated BLE protocol contract (`f32` instead of `i32` and lacks `fff2` limits), while `GEMINI.md` duplicates protocol tables and retains stale file tree paths. Establishing `openspec/specs/` as the single source of truth for contracts and defining distinct, non-overlapping roles for each documentation file prevents future drift and keeps AI agent context clean.

## What Changes

- **Designate `openspec/specs/` as Canonical Contract Authority**: Explicitly document that system behavioral specifications, BLE UUIDs, packet formats, and kinematics reside authoritatively in `openspec/specs/`.
- **Streamline `README.md`**:
  - Fix the Control Protocol section to reflect the current BLE contract (`fff1` signed `i32` LE motor speeds, `fff2` `u16` LE duty limits).
  - Add explicit links pointing developers to `openspec/specs/remote-car-control/spec.md` for in-depth protocol and behavior specs.
  - Simplify repository structure representation to high-level modules only to prevent maintenance rot.
- **Clean `GEMINI.md`**:
  - Remove stale and granular source tree file listings (e.g., references to deleted `BleScannerManager.kt` and `BleDevice.kt`).
  - Retain essential, fast-lookup command cheatsheets and core coding conventions (`no_std`, `StaticCell`, defmt, StateFlow, Jetpack Compose).
  - Add explicit guidance instructing AI assistants to treat `openspec/specs/` as the definitive behavioral specification.
- **Workflow & Sync Guidance in `openspec/config.yaml`**:
  - Add guidance under operations (`apply`/`archive`) reminding developers and agents to maintain consistency between specs, `README.md`, and `GEMINI.md` when contracts change.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
None (this change does not modify system behavioral requirements; `.openspec.yaml` sets `skip_specs: true`).

## Impact

- **Affected Files**:
  - `README.md`: Protocol update and link to specs; simplified repo tree.
  - `GEMINI.md`: File tree pruning and cross-reference to `openspec/specs/`.
  - `openspec/config.yaml`: Added operation guidance for documentation synchronization.
- **Code & API Impact**: No code or external runtime API changes.
- **AI Agent Context**: Reduced context overhead and reduced risk of agents referencing nonexistent files or outdated packet schemas.
