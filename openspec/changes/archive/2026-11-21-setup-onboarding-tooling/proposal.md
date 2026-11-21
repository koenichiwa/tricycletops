# Proposal

## Why

To make it easier for new contributors to onboard onto the project and to eliminate cross-platform inconsistencies ("works on my machine" issues). The project is polyglot (Rust, Kotlin, YAML, Markdown) and requires specific tools that are currently implicit.

## What Changes

- Add `mise.toml` to declare standard toolchain versions (Java 21, Rust nightly, Android SDK) and provide unified run tasks acting as a cross-project orchestration tool.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
None.

## Impact

- Development environment tooling will become reproducible and explicit.
- Standardized task orchestration (e.g. `mise run android-build`, `mise run fw-flash`).
- No impact on end-user runtime behavior.
