# Proposal

## Why

The current differential drive kinematics implementation in `tricycletops-app` linearly scales the joystick inputs to motor speeds and applies early saturation (`max(abs(left), abs(right))`). Since the touch joystick is bounded to a perfect circle (a standard UI pattern), diagonal inputs hit saturation prematurely. For example, pushing the joystick halfway up the forward-right diagonal maxes out the left motor to 100% while keeping the right motor at 0%, limiting maneuverability and fine control. Using squircle mapping math transforms the circular bounds into a square, correctly stretching the joystick's potential across the full capabilities of the differential drive to provide smooth control across the entire plane without dead zones.

## What Changes

- The fundamental kinematics math transforming normalized joystick inputs (u, v) into differential wheel outputs (left, right) will be replaced with circle-to-square (squircle) mapping math.
- The `ComputeDifferentialDriveUseCase` in `tricycletops-app` will be updated to implement the new algebraic squircle equation.
- Tests will be updated to reflect the corrected squircle outputs instead of the linear outputs.
- The `remote-car-control` specification will be updated to require the squircle mapping behavior and desaturation mechanics will be removed since squircle math handles full corner reach naturally.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `remote-car-control`: Replace linear differential kinematics and desaturation scaling with squircle algebraic mapping for joystick to wheel mapping.

## Impact

- `tricycletops-app/app/src/main/java/com/koenichiwa/tricycletops/domain/usecase/ComputeDifferentialDriveUseCase.kt`
- `tricycletops-app/app/src/test/java/com/koenichiwa/tricycletops/domain/usecase/ComputeDifferentialDriveUseCaseTest.kt`
- `openspec/specs/remote-car-control/spec.md`