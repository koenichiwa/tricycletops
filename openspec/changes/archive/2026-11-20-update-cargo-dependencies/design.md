# Design

## Context

The firmware in `tricycletops-fw` is a bare-metal `#![no_std]` application running on the RP2040 (Pico W), integrating Embassy async runtime, hardware PWM, USB CDC-ACM serial logging (`defmt`), and Bluetooth Low Energy via `trouble-host` and `cyw43`. See `proposal.md` for motivation.

Several direct dependencies and patched git dependencies from the Embassy ecosystem are deeply interdependent. Previous attempts to perform a simultaneous upgrade across all crates failed because compiler errors and API breaks in low-level HAL and Bluetooth stacks cascaded without clear points of failure.

## Goals / Non-Goals

**Goals:**
- Provide a deterministic, graph-informed migration algorithm that discovers target versions and isolates coupled crate clusters.
- Use a "Group Leader" pattern to drive version increments one step at a time, allowing Cargo's resolver to compute compatible versions for peer crates in the group.
- Constrain all code changes strictly to the minimum required to fix compiler errors resulting from dependency upgrades.
- Enforce an interactive hardware Test Card verification protocol after each increment before pinning versions.
- Ensure zero regression in external vehicle control and BLE discovery behaviors.

**Non-Goals:**
- Hardware PWM slice consolidation or GPIO rewiring (factored out into the dedicated `unify-pwm-slice` change).
- Protocol modifications to `fff1` drive commands or `fff2` duty limits.
- Refactoring firmware architecture or modifying business logic beyond direct compiler compatibility fixes.

## Decisions

### Step-by-Step Contract: Incremental Cargo Dependency Updates

#### Test Card Template
```text
================== TEST CARD: Update Cargo dependency versions ==================
1. Flash Command: cd tricycletops-fw && cargo run
2. Log Stream: < Update depending on affected crates > defmt-print -e target/thumbv6m-none-eabi/release/tricycletops-fw serial --path /dev/ttyACM0 --dtr
3. Physical Test: < Update depending on affected crates >
=================================================================================
```

#### Procedure

1. **Create backups**
   - Create backup copies of `Cargo.toml` and `Cargo.lock`.

2. **Determine target versions**
   - Remove the `[patch]` version section from `Cargo.toml`.
   - Change all dependency version requirements to `>=x.x.x`, preserving each dependency's current minimum version.
   - Run:
     ```sh
     cargo update
     cargo tree --depth 1 > ../.scratch.target_versions.txt
     ```
   - Use `.scratch.target_versions.txt` to identify the target versions for the dependencies.
   - **Do not change `.scratch.target_versions.txt` during the next steps.**

3. **Restore the baseline**
   - Restore the original `Cargo.toml` and `Cargo.lock` from the backups.
   - Run:
     ```sh
     cargo clean && cargo check --target thumbv6m-none-eabi
     ```
   - Confirm that the project builds successfully from the original baseline.

4. **Determine tightly coupled crate groups**
   - Generate a machine-readable dependency graph:
     ```sh
     cargo metadata --format-version 1 > ../.scratch.dependency_graph.json
     ```
   - Generate a human-readable dependency tree:
     ```sh
     cargo tree --workspace --all-targets > ../.scratch.dependency_tree.txt
     ```
   - Inspect relevant reverse dependencies using:
     ```sh
     cargo tree --workspace -i <crate-name>
     ```
   - Use these dependency relationships to identify crates that are tightly coupled and should be upgraded together.
   - Create `.scratch.crate_groups` containing the identified groups. This file is the **authoritative source of truth** for groups.
   - Assign each group a descriptive label and define a group leader:
     - The leader is the crate whose version is incremented one step at a time to drive the upgrade of the group.
     - The leader should be selected based on the dependency relationships and version constraints in the group.
     - Prefer a crate whose version changes are likely to determine or constrain the compatible versions of the other crates in the group.
     - The leader may change if the dependency graph changes during the migration.
     - The agent must record the reason for selecting the leader.
   - Record the following for each group in `.scratch.crate_groups`:
     - Group leader and reason for selection.
     - Member crates.
     - Current versions.
     - Target versions.
     - Relevant dependency relationships.

5. **Incrementally update each crate group**
   Process the crate groups **one at a time**. For each group, repeatedly perform small version increments until **all crates in that group have reached their target versions**.

   1. **Select the next group**
      - Select a crate group from `.scratch.crate_groups`.
      - Determine the current resolved versions and the target versions for the crates in that group.
      - If the group is already at its target versions, skip it and continue to the next group.

   2. **Perform one small increment**
      - Select one crate in the group.
      - Identify the smallest version greater than the currently pinned version.
      - Bump that crate by that increment.
      - Let Cargo choose the compatible versions of the other crates in the group (set them to `>=[current_version]`).
      - Remove the crates from `[patch]` version section from `Cargo.toml`, if applicable
      - Run:
        ```sh
        cargo update
        ```
      - Do not manually bump all crates in the group at once.

   3. **Check for compilation errors**
      - Check whether the project compiles (`cargo check --target thumbv6m-none-eabi`).
      - If there are compilation errors, fix them with the **smallest necessary code changes**.
      - Do not make unrelated refactors or changes.
      - If small changes are not sufficient, explain the situation to the user and let them decide on the best course of action.

   4. **Run the test card**
      - Show the user the Test Card defined above.
      - Pause and wait for the user's response before continuing.

   5. **Pin the current group state**
      - After the user approves the current increment, pin the versions of the crates in the current group to the versions currently resolved by Cargo.
      - This establishes the current increment as the new baseline for the next increment.

   6. **Update the dependency groups**
      - Re-run the relevant `cargo tree -i ...` commands.
      - Update `.scratch.crate_groups` to reflect the current dependency graph and any changes to the crate groupings.

   7. **Continue incrementing the same group**
      - Compare the group's current versions against its target versions.
      - If any crate in the group has not yet reached its target version, return to **step 5.2** and perform another small increment.
      - Repeat this cycle:
        - small version increment
        - `cargo update`
        - fix compilation errors
        - show the test card
        - wait for user approval
        - pin the resolved versions
        - update `.scratch.crate_groups` according to step 4.
      - **Do not move to the next group until the current group has reached its target versions.** If that is not possible, explain the situation to the user and let them decide the best course of action.

   8. **Move to the next group**
      - Once every crate in the current group has reached its target version, select the next group and repeat the process.

6. **Completion criteria**
   - Every crate group has been incrementally upgraded until its crates reach the target versions identified in `.scratch.target_versions.txt`.
   - Each group may require **multiple small increments**; do not assume one increment is sufficient.
   - The project compiles successfully after each approved incremental upgrade.
   - `.scratch.crate_groups` is kept up to date after each increment.
   - The final `Cargo.toml` and `Cargo.lock` contain the completed dependency upgrades.

## Risks / Trade-offs

- **[Risk] Upstream breaking API changes in `trouble-host` (e.g. 0.7 to 0.8) and `bt-hci`**
  - *Mitigation*: Isolate BLE stack crates into their own tier after foundational HAL, runtime, and diagnostic crates are fully verified on hardware.
- **[Risk] Embassy git patch divergence**
  - *Mitigation*: If crates.io releases lack required features, update all `embassy-*` and `cyw43*` patches to a single synchronized upstream git commit hash.
- **[Risk] Incremental upgrade deadlocks or regression**
  - *Mitigation*: Since backups are created in Step 1 and each increment is pinned as a known-good baseline in Step 5.5, any failed step can be cleanly rolled back to the prior baseline without losing earlier progress.
