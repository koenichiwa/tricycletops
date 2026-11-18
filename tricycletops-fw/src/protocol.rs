pub use crate::car_control::DriveCommand;

#[derive(Clone, Copy, Debug, PartialEq, Eq, defmt::Format)]
pub struct DutyLimits {
    pub left_min: u16,
    pub left_max: u16,
    pub right_min: u16,
    pub right_max: u16,
}
