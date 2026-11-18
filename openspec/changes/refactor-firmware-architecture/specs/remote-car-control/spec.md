# Spec Delta: remote-car-control

## ADDED Requirements

### Requirement: Peripheral Command Watchdog and Disconnect Failsafe
The peripheral firmware SHALL automatically halt motor drive by setting duty cycles to zero if no drive command is received on characteristic `0000fff1-0000-1000-8000-00805f9b34fb` within 500 milliseconds or if the BLE link disconnects.

#### Scenario: Automatic motor stop on command timeout
- **GIVEN** the vehicle is actively driving with non-zero motor duty cycles
- **WHEN** 500 milliseconds elapse without receiving a new drive command write on characteristic `fff1`
- **THEN** the firmware SHALL immediately set all motor duty cycles to zero

#### Scenario: Automatic motor stop on BLE link disconnection
- **GIVEN** the vehicle is actively driving with non-zero motor duty cycles
- **WHEN** the BLE central disconnects or the link is terminated
- **THEN** the firmware SHALL immediately set all motor duty cycles to zero and resume advertising

#### Scenario: Resuming motion upon new command after watchdog halt
- **GIVEN** the vehicle has halted due to a watchdog timeout
- **WHEN** a valid drive command is subsequently written to characteristic `fff1`
- **THEN** the firmware SHALL apply the requested motor speeds and reset the watchdog timer

### Requirement: Resilient Motor Control Loop Error Recovery
The firmware motor control task SHALL catch actuator driver errors, safely set motor duty cycles to zero, and continue operating without terminating the task.

#### Scenario: Recovery from hardware driver error
- **GIVEN** the motor control task is active
- **WHEN** an actuation or PWM driver error occurs while updating motor speeds
- **THEN** the task SHALL attempt to zero motor duty cycles, log the diagnostic error, and continue waiting for subsequent drive commands
