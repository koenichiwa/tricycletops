# remote-car-control Specification

## Purpose

Establishes GATT communication with a Tricycletops peripheral, reads per-wheel min-max motor limits, translates touch joystick movement into throttled differential drive motor commands with desaturation, and manages vehicle failsafe stopping.

## Requirements

### Requirement: GATT Connection and Duty Cycle Retrieval
The system SHALL connect to the selected Tricycletops GATT peripheral, discover service `0000fff0-0000-1000-8000-00805f9b34fb`, and read characteristic `0000fff2-0000-1000-8000-00805f9b34fb` (8 bytes: four 16-bit little-endian unsigned integers: `left_min`, `left_max`, `right_min`, `right_max`) to determine the active duty cycle range for each wheel, gracefully falling back to legacy formats or default limits if unavailable.

#### Scenario: Successful connection and min-max duty reading
- **GIVEN** the user has selected a discovered Tricycletops vehicle
- **WHEN** the BLE GATT connection is established and services are discovered
- **THEN** the system SHALL read characteristic `fff2` and store the min and max duty cycle bounds for both left and right wheels

#### Scenario: Reading legacy 4-byte max duty limits
- **GIVEN** an established GATT connection to a peripheral exposing 4 bytes on characteristic `fff2`
- **WHEN** the system reads characteristic `fff2`
- **THEN** the system SHALL set `minDuty` to 0 and `maxDuty` to the received bounds for both left and right wheels

#### Scenario: Fallback to default duty limits on read failure
- **GIVEN** an established GATT connection where characteristic `fff2` is absent or read fails
- **WHEN** service discovery completes
- **THEN** the system SHALL initialize duty limits to default bounds with `minDuty = 0` and `maxDuty = 1000` for both wheels

#### Scenario: Peripheral disconnection
- **GIVEN** an active vehicle control session
- **WHEN** the peripheral terminates the connection or link is lost
- **THEN** the system SHALL update connection state to disconnected and return to the scanner screen, displaying an alert if disconnection occurred due to an error

### Requirement: Touch Joystick and Differential Drive Kinematics
The system SHALL translate 2D joystick coordinates $(x, y) \in [-1.0, 1.0]$ into left and right motor speeds using differential drive kinematics with desaturation scaling to preserve turning curvature at high speed.

#### Scenario: Neutral joystick position
- **WHEN** joystick coordinates are at center $(0.0, 0.0)$
- **THEN** left and right motor speeds SHALL both equal $0.0$

#### Scenario: Straight forward driving
- **WHEN** joystick coordinates are at full forward $(0.0, 1.0)$
- **THEN** left and right motor speeds SHALL both equal $+1.0$

#### Scenario: Desaturation scaling on high throttle and steering
- **WHEN** joystick coordinates are at full forward-right $(1.0, 1.0)$
- **THEN** the system SHALL desaturate wheel speeds such that left motor speed equals $+1.0$ and right motor speed equals $0.0$ without exceeding maximum magnitude $1.0$

### Requirement: Duty Cycle Translation with Deadband Threshold
The system SHALL map normalized motor speeds $s \in [-1.0, 1.0]$ to signed integer duty cycles using the configured minimum and maximum limits for each wheel.

#### Scenario: Zero motor speed produces zero duty
- **WHEN** normalized motor speed is $0.0$
- **THEN** the calculated duty cycle SHALL be $0$ regardless of minimum duty limits

#### Scenario: Scaling positive speed above minimum threshold
- **GIVEN** a wheel with min duty 200 and max duty 1000
- **WHEN** normalized speed is $+0.5$
- **THEN** the calculated duty cycle SHALL be $200 + 0.5 \times (1000 - 200) = 600$

#### Scenario: Scaling reverse speed above minimum threshold
- **GIVEN** a wheel with min duty 200 and max duty 1000
- **WHEN** normalized speed is $-1.0$
- **THEN** the calculated duty cycle SHALL be $-1000$

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

### Requirement: Manual Emergency Stop
The system SHALL provide an immediate emergency stop control on the vehicle control interface to halt the vehicle without requiring peripheral disconnection.

#### Scenario: User triggers emergency stop
- **GIVEN** an active vehicle control session
- **WHEN** the user activates the emergency stop button
- **THEN** the system SHALL immediately reset joystick coordinates to center $(0.0, 0.0)$ and transmit a zero duty cycle stop command to the peripheral

### Requirement: Lifecycle Failsafe Vehicle Stopping
The system SHALL immediately issue a vehicle stop command and terminate the GATT connection when the app lifecycle moves to the background or the screen turns off.

#### Scenario: App moved to background or screen turned off
- **GIVEN** an active vehicle control session
- **WHEN** the application receives an `ON_STOP` lifecycle event
- **THEN** the system SHALL immediately send a zero duty cycle stop command and disconnect the BLE GATT peripheral

### Requirement: Peripheral Duty Limit Advertisement
The Tricycletops BLE GATT peripheral SHALL expose characteristic `0000fff2-0000-1000-8000-00805f9b34fb` as an 8-byte read-only payload containing four 16-bit little-endian unsigned integers: `left_min`, `left_max`, `right_min`, and `right_max`.

#### Scenario: Peripheral provides configured min and max duty limits
- **WHEN** a central client reads characteristic `fff2`
- **THEN** the peripheral SHALL respond with an 8-byte buffer encoding `left_min`, `left_max`, `right_min`, and `right_max` in 16-bit little-endian unsigned integers

### Requirement: Wheel Drive Hardware Actuation
The firmware SHALL actuate each wheel motor using a raw unsigned duty cycle and an explicit direction flag, setting the direction GPIO pin LOW for forward motion and HIGH for reverse motion, and configuring PWM duty cycle to `max_duty - commanded_duty` when direction is HIGH to compensate for inverted PWM drive.

#### Scenario: Forward wheel actuation
- **WHEN** a wheel is commanded with a non-zero duty cycle and Forward direction
- **THEN** the direction GPIO pin SHALL be set LOW and the PWM pin SHALL be set to the commanded duty cycle

#### Scenario: Reverse wheel actuation
- **WHEN** a wheel is commanded with a non-zero duty cycle and Reverse direction
- **THEN** the direction GPIO pin SHALL be set HIGH and the PWM pin SHALL be set to `max_duty - commanded_duty`

#### Scenario: Neutral wheel stop
- **WHEN** a wheel is commanded with zero duty cycle or stopped
- **THEN** the direction GPIO pin SHALL be set LOW and the PWM pin SHALL be set fully off

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

#### Scenario: Disconnection halts motors and disarms watchdog
- **GIVEN** an active BLE connection with motors running or peripheral in failsafe
- **WHEN** the central disconnects or the link is terminated
- **THEN** the firmware SHALL immediately halt both wheel motors, disarm the 500ms command watchdog, and SHALL NOT trigger failsafe state while disconnected

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
- **THEN** the status LED SHALL immediately return to the 1Hz advertising blink pattern and SHALL NOT transition to failsafe or solid connected while waiting for reconnection
