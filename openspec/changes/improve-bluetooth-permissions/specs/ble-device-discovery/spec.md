# Spec Delta

## MODIFIED Requirements

### Requirement: Bluetooth and Permission Handling
The scanner SHALL verify Bluetooth adapter availability and runtime permissions before initiating scans, handle permission denials gracefully, and react to runtime state changes.

#### Scenario: Bluetooth disabled
- **GIVEN** the device Bluetooth adapter is disabled
- **WHEN** the scanner screen is opened or scanning is requested
- **THEN** the system SHALL display a clear message indicating Bluetooth is disabled and refrain from scanning

#### Scenario: Bluetooth adapter toggled at runtime
- **GIVEN** the scanner screen is active
- **WHEN** the Bluetooth adapter is disabled or re-enabled by the user externally (e.g., via system Quick Settings)
- **THEN** the system SHALL reactively update its state without requiring an activity pause or screen restart, pausing scan when disabled and resuming when re-enabled

#### Scenario: Missing Bluetooth permissions
- **GIVEN** Bluetooth permissions (BLUETOOTH_SCAN and BLUETOOTH_CONNECT) are not yet granted
- **WHEN** scanning is requested or the scanner screen is presented
- **THEN** the system SHALL request the required permissions from the user and start scanning once granted

#### Scenario: Bluetooth permissions permanently denied
- **GIVEN** Bluetooth permissions have been denied by the user
- **WHEN** the scanner screen is presented or scan is triggered
- **THEN** the system SHALL display an educational permission rationale and provide an action to open application system settings

#### Scenario: Permissions granted externally upon app resumption
- **GIVEN** the application was backgrounded while permissions were missing
- **WHEN** the user grants permissions in system settings and returns to the app
- **THEN** the system SHALL detect the newly granted permissions on lifecycle resume and automatically initiate scanning
