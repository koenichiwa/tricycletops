# Design

## Context

See `proposal.md` for motivation. The system consists of an RP2040 micro-controller running bare-metal `no_std` Rust and an Android remote control application (`tricycletops-app`). The Android app computes differential drive kinematics based on a circular joystick constraint. Currently, `ComputeDifferentialDriveUseCase` converts normalized joystick vectors (u, v) into left and right motor speeds linearly, which maxes out motor duties prematurely when moving diagonally. 

## Goals / Non-Goals

**Goals:**
- Replace linear differential kinematics math with circle-to-square (squircle) mapping in the Android App.
- Ensure smooth scaling of motor speeds across all joystick positions.

**Non-Goals:**
- Changes to the physical firmware, motor control loop, or hardware interaction; this is purely an app-side mathematical update.
- Altering the BLE protocol.

## Decisions

### 1. Kinematics Mathematical Model

The `ComputeDifferentialDriveUseCase` will be updated to implement the following squircle equations where `u` is the x-axis and `v` is the y-axis:

```math
x = \frac{1}{\sqrt{2}} \left( \sqrt{(1+u)(1+v)} - \sqrt{(1-u)(1-v)} \right)
```
```math
y = \frac{1}{\sqrt{2}} \left( \sqrt{(1-u)(1+v)} - \sqrt{(1+u)(1-v)} \right)
```

In this model, `x` directly represents the Left Wheel speed, and `y` represents the Right Wheel speed. 
*Note: While `u`, `v`, `x`, and `y` are the standard algebraic variables used in the squircle math proof, they are detrimental to long-term code maintainability. The implementation in Kotlin MUST translate these into human-readable semantic variable names (e.g., `forwardSpeed` for `v`, `turnSpeed` for `u`, `leftMotorSpeed` for `x`, `rightMotorSpeed` for `y`).*

*Note: We must handle floating-point precision issues carefully; if `(1+u)(1+v)` evaluates to a tiny negative number due to float imprecision near -1.0 boundaries, the `sqrt` function in Kotlin will return `NaN`. We must clamp the multiplicands to `0.0f` before applying `sqrt`.*

**Alternatives Considered:**
- Keeping the existing linear scaling but altering the bounding box of the joystick to be a square. *Rationale for Rejection: Touch joysticks are almost universally circular in UI design. A square joystick feels unnatural to users.*

### 2. Desaturation Removal

The existing code includes a desaturation step:
`val maxMag = max(1.0f, max(abs(rawLeft), abs(rawRight)))`
`val leftSpeed = rawLeft / maxMag`

Because the squircle mapping math inherently bounds the outputs exactly to the `[-1.0, 1.0]` square constraint, this manual desaturation logic is no longer required and will be removed to simplify the use case.

## Risks / Trade-offs

- **Risk: Floating-Point Precision causing NaN** → **Mitigation:** When calculating the inner square root terms like `(1+u)(1+v)`, clamp the result to `0.0f` to prevent `sqrt` operations on negative numbers caused by minor floating-point inaccuracies.
- **Risk: Breaking Existing Unit Tests** → **Mitigation:** The unit tests in `ComputeDifferentialDriveUseCaseTest` currently assert the linear scaling values. These will need to be rewritten to assert the squircle curve values.