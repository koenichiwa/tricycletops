# Design

## Context

The repository contains code and configuration in Rust, Kotlin, YAML, and Markdown. The build requires Java for Kotlin/Android and the Rust toolchain for the firmware. See proposal.md for motivation - the goal is to formalize these dependencies explicitly.

## Goals / Non-Goals

**Goals:**
- Provide a single source of truth for the required toolchains (Java, Rust, Android SDK).
- Provide centralized orchestrations via `mise` tasks.

**Non-Goals:**
- Introducing complex CI/CD pipeline changes (just the local environment).
- Modifying the underlying build scripts (`build.gradle.kts` or `Cargo.toml`).

## Decisions

**1. Using `mise.toml` for toolchains and tasks**
- *Rationale:* `mise` acts as both a version manager (replacing `nvm`/`sdkman`/`rustup`) and a task runner (replacing `make`). It is explicitly designed for polyglot repositories.
- *Decisions:*
  - Java: Set to `21` to run the Gradle daemon optimally (while cross-compiling to Java 11).
  - Rust: Set to `nightly` (as requested in the exploration phase).
  - Tasks: Implement aliases for android-build, android-test, android-lint, android-install, fw-flash (via `cargo run`), fw-build, fw-check, fw-test-host, and a doctor script.

## Risks / Trade-offs

- **Risk:** New developers might not have `mise` installed.
  **Mitigation:** `mise` is just an accelerator. The underlying `./gradlew` and `cargo` commands still work perfectly without it.
