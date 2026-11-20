# Spec Delta: remote-car-control

## MODIFIED Requirements

### Requirement: Command Throttling and Immediate Release Stop
The system SHALL throttle active driving commands transmitted to characteristic `0000fff1-0000-1000-8000-00805f9b34fb` to a maximum rate of 25Hz, SHALL periodically retransmit the active command at 25Hz while the joystick is held at an active non-zero position, and SHALL immediately transmit a stop command `(0, 0)` upon joystick release.

#### Scenario: Continuous touch drag throttling
- **GIVEN** the user is actively dragging the joystick
- **WHEN** touch position updates occur faster than 25Hz (40ms)
- **THEN** drive command GATT writes SHALL NOT exceed 25 transmissions per second

#### Scenario: Active hold keep-alive transmission
- **GIVEN** the joystick is held at an active non-zero position $(x, y) \neq (0.0, 0.0)$
- **WHEN** touch input coordinates remain stationary without generating new drag events
- **THEN** the system SHALL continue transmitting the active drive command at 25Hz (every 40ms) to prevent peripheral command watchdog timeouts

#### Scenario: Immediate release stop
- **GIVEN** the vehicle is actively driving
- **WHEN** the user lifts their finger, releasing the joystick to $(0.0, 0.0)$
- **THEN** the system SHALL immediately cancel keep-alive transmissions and transmit a stop command with zero duty cycles, bypassing the 25Hz throttling delay
