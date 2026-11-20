# Proposal

## Why

Upstream embedded Rust dependencies (`cortex-m`, `defmt`, `embassy-*`, `cyw43*`, `trouble-host`) in `tricycletops-fw` are significantly behind their latest releases, but attempting a monolithic "all-at-once" upgrade fails due to cascading breaking changes across Embassy HAL drivers, trouble-host GATT server macros, and defmt diagnostic formatters. To successfully modernize the firmware toolchain without introducing untraceable regressions, an incremental, dependency-graph-guided upgrade process is required that isolates compiler fixes and verifies physical hardware operation step-by-step.

## What Changes

- **Target Version Discovery**: Temporarily relax dependency version requirements (`>=current`) and evaluate `[patch.crates-io]` removal via `cargo update` to discover and snapshot target versions into `.scratch.target_versions.txt`.
- **Baseline Restoration & Dependency Graph Clustering**: Restore the original `Cargo.toml`/`Cargo.lock` baseline, then generate baseline `cargo metadata` and reverse dependency trees (`cargo tree -i`) to group tightly coupled crates and designate a driver crate ("Group Leader") for each cluster based on current constraints.
- **Incremental Group Upgrade Loop**: Elevate dependencies group-by-group through small version increments driven by the group leader, allowing Cargo's resolver to compute compatible peer versions while fixing compiler errors with minimal surgical code changes.
- **Interactive Hardware Test Card Protocol**: Require flashing to physical hardware (`cargo run`) and streaming live logs (`defmt-print`) via USB serial before pinning resolved versions for each increment.
- **Cleanup & Validation**: Remove temporary scratch artifacts (`.scratch.*`) and validate that release builds, host unit tests (`cargo test-host`), and repository linters pass.

## Capabilities

### New Capabilities
*(None)*

### Modified Capabilities
*(None - internal dependency update with no protocol or external behavioral changes; `skip_specs: true`)*

## Impact

- **Firmware (`tricycletops-fw`)**:
  - `Cargo.toml` & `Cargo.lock`: Updated version constraints and dependency resolutions.
  - `src/`: Minimal, targeted source code adaptations strictly required to resolve compiler breaks from upgraded crate APIs.
- **Temporary State**:
  - Scratch files (`.scratch.target_versions.txt`, `.scratch.dependency_graph.json`, `.scratch.dependency_tree.txt`, `.scratch.crate_groups`) used during the migration and ignored by git.
- **Android App & BLE Protocols**:
  - Unaffected (`remote-car-control` GATT service and packet formats remain strictly invariant).
