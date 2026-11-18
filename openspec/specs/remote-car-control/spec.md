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
The system SHALL throttle active driving commands transmitted to characteristic `0000fff1-0000-1000-8000-00805f9b34fb` to a maximum rate of 25Hz, while immediately transmitting a stop command `(0, 0)` upon joystick release.

#### Scenario: Continuous touch drag throttling
- **GIVEN** the user is actively dragging the joystick
- **WHEN** touch position updates occur faster than 25Hz (40ms)
- **THEN** drive command GATT writes SHALL NOT exceed 25 transmissions per second

#### Scenario: Immediate release stop
- **GIVEN** the vehicle is actively driving
- **WHEN** the user lifts their finger, releasing the joystick to $(0.0, 0.0)$
- **THEN** the system SHALL immediately transmit a stop command with zero duty cycles, bypassing the 25Hz throttling delay

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
