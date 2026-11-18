pub use crate::car_control::DriveCommand;

#[derive(Clone, Copy, Debug, PartialEq, Eq, defmt::Format)]
pub struct DutyLimits {
    pub left_min: u16,
    pub left_max: u16,
    pub right_min: u16,
    pub right_max: u16,
}

impl DutyLimits {
    pub const fn new(left_min: u16, left_max: u16, right_min: u16, right_max: u16) -> Self {
        Self {
            left_min,
            left_max,
            right_min,
            right_max,
        }
    }
}

pub fn decode_drive_command(data: &[u8]) -> Option<DriveCommand> {
    if data.len() != 8 {
        return None;
    }

    Some(DriveCommand {
        left: i32::from_le_bytes(data[0..4].try_into().ok()?),
        right: i32::from_le_bytes(data[4..8].try_into().ok()?),
    })
}

pub fn encode_drive_command(cmd: DriveCommand) -> [u8; 8] {
    let mut bytes = [0u8; 8];
    bytes[0..4].copy_from_slice(&cmd.left.to_le_bytes());
    bytes[4..8].copy_from_slice(&cmd.right.to_le_bytes());
    bytes
}

pub fn encode_duty_limits(limits: DutyLimits) -> [u8; 8] {
    let mut bytes = [0u8; 8];
    bytes[0..2].copy_from_slice(&limits.left_min.to_le_bytes());
    bytes[2..4].copy_from_slice(&limits.left_max.to_le_bytes());
    bytes[4..6].copy_from_slice(&limits.right_min.to_le_bytes());
    bytes[6..8].copy_from_slice(&limits.right_max.to_le_bytes());
    bytes
}

pub fn decode_duty_limits(data: &[u8]) -> Option<DutyLimits> {
    if data.len() != 8 {
        return None;
    }

    Some(DutyLimits {
        left_min: u16::from_le_bytes(data[0..2].try_into().ok()?),
        left_max: u16::from_le_bytes(data[2..4].try_into().ok()?),
        right_min: u16::from_le_bytes(data[4..6].try_into().ok()?),
        right_max: u16::from_le_bytes(data[6..8].try_into().ok()?),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_decode_drive_command_valid() {
        let payload = [
            0x2c, 0x01, 0x00, 0x00, // left = 300
            0xd4, 0xfe, 0xff, 0xff, // right = -300
        ];
        let cmd = decode_drive_command(&payload).expect("decoding should succeed");
        assert_eq!(cmd.left, 300);
        assert_eq!(cmd.right, -300);
    }

    #[test]
    fn test_decode_drive_command_invalid_lengths() {
        assert_eq!(decode_drive_command(&[]), None);
        assert_eq!(decode_drive_command(&[0x01, 0x02, 0x03]), None);
        assert_eq!(decode_drive_command(&[0; 7]), None);
        assert_eq!(decode_drive_command(&[0; 9]), None);
        assert_eq!(decode_drive_command(&[0; 16]), None);
    }

    #[test]
    fn test_decode_drive_command_extremes() {
        let zero_cmd = decode_drive_command(&[0; 8]).expect("zero command");
        assert_eq!(zero_cmd.left, 0);
        assert_eq!(zero_cmd.right, 0);

        let mut max_payload = [0u8; 8];
        max_payload[0..4].copy_from_slice(&i32::MAX.to_le_bytes());
        max_payload[4..8].copy_from_slice(&i32::MIN.to_le_bytes());

        let extreme_cmd = decode_drive_command(&max_payload).expect("extreme command");
        assert_eq!(extreme_cmd.left, i32::MAX);
        assert_eq!(extreme_cmd.right, i32::MIN);
    }

    #[test]
    fn test_encode_drive_command_and_roundtrip() {
        let cmd = DriveCommand {
            left: 1234,
            right: -5678,
        };
        let encoded = encode_drive_command(cmd);
        assert_eq!(&encoded[0..4], &1234i32.to_le_bytes());
        assert_eq!(&encoded[4..8], &(-5678i32).to_le_bytes());

        let decoded = decode_drive_command(&encoded).expect("roundtrip decode");
        assert_eq!(decoded, cmd);
    }

    #[test]
    fn test_encode_and_decode_duty_limits() {
        let limits = DutyLimits {
            left_min: 200,
            left_max: 2500,
            right_min: 250,
            right_max: 2500,
        };
        let encoded = encode_duty_limits(limits);
        assert_eq!(&encoded[0..2], &200u16.to_le_bytes());
        assert_eq!(&encoded[2..4], &2500u16.to_le_bytes());
        assert_eq!(&encoded[4..6], &250u16.to_le_bytes());
        assert_eq!(&encoded[6..8], &2500u16.to_le_bytes());

        let decoded = decode_duty_limits(&encoded).expect("duty limits roundtrip");
        assert_eq!(decoded, limits);
    }

    #[test]
    fn test_decode_duty_limits_invalid_lengths() {
        assert_eq!(decode_duty_limits(&[]), None);
        assert_eq!(decode_duty_limits(&[0; 7]), None);
        assert_eq!(decode_duty_limits(&[0; 9]), None);
    }
}
