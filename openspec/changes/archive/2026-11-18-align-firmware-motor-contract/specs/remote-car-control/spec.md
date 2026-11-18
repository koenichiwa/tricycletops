# Spec Delta

## ADDED Requirements

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
