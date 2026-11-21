# Tasks

## 1. Update Differential Kinematics Math

- [ ] 1.1 Implement the squircle mapping math `x = 1/sqrt(2) * (sqrt((1+u)(1+v)) - sqrt((1-u)(1-v)))` inside `ComputeDifferentialDriveUseCase.kt` to calculate `leftSpeed` and `rightSpeed` (remembering to clamp the multiplicands to `0.0f` before `sqrt`), and verify by compiling the app.
- [ ] 1.2 Remove the legacy linear scaling and desaturation math (`val maxMag = max(1.0f, max(abs(rawLeft), abs(rawRight)))`) from `ComputeDifferentialDriveUseCase.kt`, and verify by compiling the app.

## 2. Update Kinematics Unit Tests

- [ ] 2.1 Update `ComputeDifferentialDriveUseCaseTest.kt` to assert the new expected squircle output values for all joystick positions (e.g. `(0.5, 0.5)` -> `(~0.707, 0.0)`), and verify that `./gradlew test` passes.