# Spec Delta: remote-car-control

## MODIFIED Requirements

### Requirement: Wheel Drive Hardware Actuation
The firmware SHALL actuate each wheel motor using a raw unsigned duty cycle and an explicit direction flag, setting the direction GPIO pin LOW for forward motion and HIGH for reverse motion, and configuring PWM duty cycle to `max_duty - commanded_duty` when direction is HIGH to compensate for inverted PWM drive. The speed PWM signals for both left and right wheels SHALL be driven from channels A and B of a single shared RP2040 PWM hardware slice, ensuring synchronized base frequency while maintaining independent channel duty cycles.

#### Scenario: Forward wheel actuation
- **WHEN** a wheel is commanded with a non-zero duty cycle and Forward direction
- **THEN** the direction GPIO pin SHALL be set LOW and the PWM pin SHALL be set to the commanded duty cycle

#### Scenario: Reverse wheel actuation
- **WHEN** a wheel is commanded with a non-zero duty cycle and Reverse direction
- **THEN** the direction GPIO pin SHALL be set HIGH and the PWM pin SHALL be set to `max_duty - commanded_duty`

#### Scenario: Neutral wheel stop
- **WHEN** a wheel is commanded with zero duty cycle or stopped
- **THEN** the direction GPIO pin SHALL be set LOW and the PWM pin SHALL be set fully off

#### Scenario: Shared PWM slice independent duty control
- **WHEN** left and right wheels are commanded with different duty cycles
- **THEN** the firmware SHALL update channel A and channel B duty cycle compare registers on the shared PWM slice independently without altering slice frequency
