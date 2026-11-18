# Design

## Context

See `proposal.md` for motivation.
Currently, documentation is scattered across three primary files/directories:
1. `README.md` (human landing page and setup guide)
2. `GEMINI.md` (AI agent workspace operational guide)
3. `openspec/specs/` (system behavioral specifications)

Because previous changes updated `openspec/specs/` and `GEMINI.md` without updating `README.md`, `README.md` still lists `f32` speed floats on characteristic `fff1` and omits `fff2` entirely. Additionally, `GEMINI.md` contains an obsolete file tree that references files removed in previous refactors.

## Goals / Non-Goals

**Goals:**
- Establish a strict documentation hierarchy with clear single sources of truth (SSOT).
- Bring `README.md` into alignment with the existing firmware/app contract (`i32` LE speeds, `u16` LE duty limits) and link directly to canonical OpenSpec specifications.
- Expose fast host-based firmware unit testing (`cargo test --lib --target x86_64-unknown-linux-gnu`) in `README.md` to facilitate testing without physical hardware.
- Prune detailed, brittle source code file trees from `GEMINI.md` to prevent maintenance rot, instructing AI assistants to discover files via tooling.
- Equip AI agents with an explicit workspace validation cheatsheet (`cargo fmt`, `cargo clippy`, `gradlew lint`, `pre-commit`) in `GEMINI.md`.
- Optimize `GEMINI.md` token efficiency by condensing repetitive overview text into dense bullet facts.
- Add operational guidance in `openspec/config.yaml` ensuring documentation synchronization is maintained across changes.

**Non-Goals:**
- No changes to application or firmware source code or compiled binaries.
- No introduction of complex external documentation generation systems (e.g. mdBook, Doxygen).
- Hardware wiring documentation and diagrams are deferred to a separate dedicated hardware ticket.
- Human `README.md` will not include code quality/linter setup to keep human onboarding minimal and uncluttered.

## Decisions

### Decision 1: OpenSpec (`openspec/specs/`) is the Canonical Specification Authority
- **Choice**: Designate `openspec/specs/` as the single authoritative source for GATT protocols, byte encodings, differential drive kinematics, deadband handling, and safety failsafes.
- **Rationale**: Having one authoritative place eliminates ambiguity when discrepancies occur.
- **Alternatives Considered**:
  - *Keep full duplicate byte tables and math in all three locations*: Rejected because manual multi-file synchronization inevitably drifts over time.
  - *Remove protocol information entirely from `README.md`*: Rejected because new human contributors need to understand the basic communication model at a glance before diving into specs.
- **Structure**:
  - `README.md` provides an accurate high-level summary of GATT UUIDs and payloads with a Markdown link to `openspec/specs/remote-car-control/spec.md`.
  - `GEMINI.md` maintains a concise lookup table for rapid agent reference, accompanied by an explicit directive pointing to `openspec/specs/` as the authoritative source.

### Decision 2: Remove Granular File Trees from `GEMINI.md`
- **Choice**: Replace granular multi-level file trees in `GEMINI.md` with a high-level component summary, directing agents to use `glob` and `grep_search` to map files dynamically.
- **Rationale**: As seen with the deleted `BleScannerManager.kt` and `BleDevice.kt` entries, file trees embedded in instructions quickly rot and waste token context.
- **Alternatives Considered**:
  - *Keep file tree updated manually*: Rejected due to high maintenance overhead across future refactors.

### Decision 3: Embed Documentation Synchronization Guidance in `openspec/config.yaml`
- **Choice**: Add advisory guidance under `operations.apply` and `operations.archive` in `openspec/config.yaml`.
- **Rationale**: When agents or developers work through OpenSpec tasks, the instructions injected from `openspec/config.yaml` will actively remind them to keep `README.md` and `GEMINI.md` synchronized whenever protocol contracts change.

### Decision 4: AI Agent Workspace Validation Cheatsheet & Context Compression
- **Choice**: Add standard validation commands (`cargo fmt`, `cargo clippy`, `gradlew lint`, `pre-commit`) to `GEMINI.md` and compress repetitive overview text into dense factual bullet points.
- **Rationale**: Agents must rigorously validate changes before completing tasks. Listing project-specific linters in `GEMINI.md` ensures agents run them without prompting, while compressing the overview saves tokens on every turn.

### Decision 5: Expose Host Unit Testing in README.md
- **Choice**: Include `cargo test --lib --target x86_64-unknown-linux-gnu` in `README.md` under Firmware Getting Started.
- **Rationale**: Contributors might assume firmware requires physical RP2040 hardware to verify. Highlighting the host testing capability clarifies that protocol decoders and differential drive calculations can be tested instantly locally.

## Risks / Trade-offs

- **[Risk]** Developers browsing on GitHub might have to click into `openspec/specs/` to read full scenario details.
  - **Mitigation:** The high-level summary in `README.md` will contain the essential parameters (`fff0`, `fff1`, `fff2`, data types, value ranges), with clear relative links to the specs directory.
- **[Risk]** Developers editing code without using OpenSpec might skip updating specs.
  - **Mitigation:** Standard PR review and git commit workflow guidance in `GEMINI.md` reinforce the precedence of `openspec/specs/`.
