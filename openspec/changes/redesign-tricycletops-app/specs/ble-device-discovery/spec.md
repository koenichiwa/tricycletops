# Spec Delta

## Purpose

Provides Bluetooth Low Energy scanning to discover nearby Tricycletops robotic vehicles and present them for user connection.

## ADDED Requirements

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
The scanner SHALL verify Bluetooth adapter availability and runtime permissions before initiating scans.

#### Scenario: Bluetooth disabled
- **GIVEN** the device Bluetooth adapter is disabled
- **WHEN** the scanner screen is opened or scanning is requested
- **THEN** the system SHALL display a message indicating Bluetooth is disabled and refrain from scanning

#### Scenario: Missing Bluetooth permissions
- **GIVEN** Bluetooth permissions are not yet granted
- **WHEN** scanning is requested
- **THEN** the system SHALL prompt the user for required permissions and only start scanning once granted
