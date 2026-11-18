# ble-device-discovery Specification

## Purpose

Provides Bluetooth Low Energy scanning to discover nearby Tricycletops robotic vehicles and present them for user connection.

## Requirements

### Requirement: Filter Discovered Devices by Identity
The scanner SHALL filter discovered BLE peripherals and only surface devices whose advertised local name contains "Tricycletops" (case-insensitive) or that advertise the Tricycletops GATT Service UUID (`0000fff0-0000-1000-8000-00805f9b34fb`).

#### Scenario: Matching device discovered by name
- **GIVEN** BLE scanning is active
- **WHEN** a peripheral advertising the local name "Tricycletops" is detected
- **THEN** the system SHALL add the device to the discovered list with its display name, MAC address, and RSSI

#### Scenario: Non-matching device ignored
- **GIVEN** BLE scanning is active
- **WHEN** a peripheral advertising an unrelated name and service is detected
- **THEN** the system SHALL NOT add the peripheral to the list of selectable devices

### Requirement: Scan State and Discovery Updates
The scanner SHALL expose its active scanning status and continuously update device signal strength without generating duplicate entries.

#### Scenario: Updating RSSI for an already discovered device
- **GIVEN** a Tricycletops device has already been discovered and listed
- **WHEN** a subsequent advertisement packet is received from the same device MAC address with updated RSSI
- **THEN** the system SHALL update the existing device's RSSI in place without creating a duplicate item

#### Scenario: Starting and stopping scan
- **WHEN** the user triggers a scan toggle or navigates to the scanner screen
- **THEN** the system SHALL update its scanning state indicator to reflect whether scanning is running or idle

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
