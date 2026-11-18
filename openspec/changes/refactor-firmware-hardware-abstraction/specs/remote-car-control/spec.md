# Spec Delta: remote-car-control

## ADDED Requirements

### Requirement: Autonomous Peripheral Command Watchdog
The firmware SHALL enforce an autonomous 500ms watchdog timeout while connected. If no valid drive command is received on characteristic `fff1` within 500ms, the peripheral SHALL immediately halt both wheel motors and enter a failsafe state.

#### Scenario: Normal command stream keeps motors active
- **GIVEN** an active BLE connection with motors running
- **WHEN** drive commands arrive at a rate faster than 500ms
- **THEN** wheel motor actuation SHALL continue uninterrupted

#### Scenario: Command stream interruption triggers safety halt
- **GIVEN** an active BLE connection with motors running
- **WHEN** 500ms elapses without receiving a new drive command
- **THEN** the firmware SHALL stop both wheel motors and transition to the failsafe state

#### Scenario: Recovery upon receiving valid command
- **GIVEN** the peripheral is in the failsafe state due to a command timeout
- **WHEN** a valid drive command is received on characteristic `fff1`
- **THEN** the firmware SHALL actuate the motors according to the new command and clear the failsafe state

### Requirement: Peripheral Operational State Indication
The peripheral SHALL drive the status indicator LED to reflect its current operational state: 1Hz blinking (500ms ON / 500ms OFF) when advertising, solid ON when connected and actively controlled, and 5Hz rapid blinking (100ms ON / 100ms OFF) when in failsafe halt.

#### Scenario: Advertising state indication
- **GIVEN** the peripheral is not connected to a central
- **WHEN** BLE advertising is active
- **THEN** the status LED SHALL blink at 1Hz (500ms ON / 500ms OFF)

#### Scenario: Connected state indication
- **GIVEN** a central establishes a BLE connection
- **WHEN** the connection is active and drive commands are received normally
- **THEN** the status LED SHALL remain solid ON

#### Scenario: Failsafe state indication
- **GIVEN** an active BLE connection
- **WHEN** the command watchdog times out or a motor actuator error occurs
- **THEN** the status LED SHALL rapidly blink at 5Hz (100ms ON / 100ms OFF)

#### Scenario: Disconnection transitions back to advertising
- **GIVEN** the peripheral is connected or in failsafe
- **WHEN** the central disconnects or the link is terminated
- **THEN** the status LED SHALL immediately return to the 1Hz advertising blink pattern
