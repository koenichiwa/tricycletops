# Spec Delta

## MODIFIED Requirements

### Requirement: Touch Joystick and Differential Drive Kinematics
The system SHALL translate 2D joystick coordinates $(x, y) \in [-1.0, 1.0]$ into left and right motor speeds using circle-to-square (squircle) mapping math to stretch the joystick's potential across the full capabilities of the differential drive without premature saturation.

#### Scenario: Neutral joystick position
- **WHEN** joystick coordinates are at center $(0.0, 0.0)$
- **THEN** left and right motor speeds SHALL both equal $0.0$

#### Scenario: Straight forward driving
- **WHEN** joystick coordinates are at full forward $(0.0, 1.0)$
- **THEN** left and right motor speeds SHALL both equal $+1.0$

#### Scenario: Forward-right diagonal turn
- **WHEN** joystick coordinates are halfway up the forward-right diagonal $(0.5, 0.5)$
- **THEN** left motor speed SHALL equal $+0.707$ and right motor speed SHALL equal $0.0$

#### Scenario: Sharp forward-right cornering
- **WHEN** joystick coordinates are at the edge of the forward-right diagonal $(0.707, 0.707)$
- **THEN** left motor speed SHALL equal $+1.0$ and right motor speed SHALL equal $0.0$

#### Scenario: Pivot turn in place
- **WHEN** joystick coordinates are at full right $(1.0, 0.0)$
- **THEN** left motor speed SHALL equal $+1.0$ and right motor speed SHALL equal $-1.0$
