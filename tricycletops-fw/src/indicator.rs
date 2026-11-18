#[derive(Copy, Clone, Debug, PartialEq, Eq, defmt::Format)]
pub enum PeripheralState {
    Advertising,
    Connected,
    Failsafe,
}
