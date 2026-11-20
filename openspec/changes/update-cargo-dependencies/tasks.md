# Tasks

## 1. Setup & Baseline Preparation

- [x] 1.1 Add `.scratch.*` patterns to `.gitignore` and verify with `git status` that scratch files are ignored
- [x] 1.2 Create backup copies of `tricycletops-fw/Cargo.toml` and `tricycletops-fw/Cargo.lock` per `design.md` Procedure Step 1
- [x] 1.3 Verify current baseline builds and host tests pass cleanly (`cargo check --target thumbv6m-none-eabi` and `cargo test-host`)

## 2. Target Version Discovery & Baseline Restoration

- [x] 2.1 Determine target versions and create `.scratch.target_versions.txt` per `design.md` Procedure Step 2
- [x] 2.2 Restore original baseline `Cargo.toml` and `Cargo.lock` and verify clean build per `design.md` Procedure Step 3

## 3. Dependency Group Determination

- [x] 3.1 Generate baseline dependency graph metadata, full dependency tree, and reverse dependencies per `design.md` Procedure Step 4
- [x] 3.2 Create `.scratch.crate_groups` as the authoritative source of truth for crate groups, leaders, and version tracking per `design.md` Procedure Step 4
- [x] 3.3 Pause and let the user review the files

## 4. Incremental Group Upgrade Execution

- [ ] 4.1 Incrementally update each crate group from `.scratch.crate_groups` by executing the upgrade cycle (Steps 5.1–5.8 in `design.md`) until all groups reach their target versions. Follow the contract in `design.md` to the letter
- [ ] 4.2 Verify all completion criteria in `design.md` Procedure Step 6 are satisfied

## 5. Finalization & Repository Verification

- [ ] 5.1 Verify final `Cargo.toml` and `Cargo.lock` against `.scratch.target_versions.txt`
- [ ] 5.2 Remove `.scratch.*` and backup files, ensuring a clean git working tree
- [ ] 5.3 Run full repository checks: `cargo build --release --target thumbv6m-none-eabi`, `cargo test-host`, `cargo fmt --check`, and `cargo clippy --target thumbv6m-none-eabi`
