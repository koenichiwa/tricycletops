#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_futures::select::{Either, select};
#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_rp::gpio::Output;
#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_sync::blocking_mutex::raw::ThreadModeRawMutex;
#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_sync::signal::Signal;
#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_time::Timer;

#[derive(Copy, Clone, Debug, PartialEq, Eq, defmt::Format)]
pub enum IndicatorState {
    Advertising,
    Connected,
    Failsafe,
}

pub type PeripheralState = IndicatorState;

#[derive(Copy, Clone, Debug, PartialEq, Eq)]
pub struct BlinkPattern {
    pub on_ms: u32,
    pub off_ms: u32,
}

impl IndicatorState {
    pub const fn pattern(&self) -> BlinkPattern {
        match self {
            Self::Advertising => BlinkPattern {
                on_ms: 500,
                off_ms: 500,
            },
            Self::Connected => BlinkPattern {
                on_ms: 1000,
                off_ms: 0,
            },
            Self::Failsafe => BlinkPattern {
                on_ms: 100,
                off_ms: 100,
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_indicator_state_patterns() {
        assert_eq!(
            IndicatorState::Advertising.pattern(),
            BlinkPattern {
                on_ms: 500,
                off_ms: 500,
            }
        );
        assert_eq!(
            IndicatorState::Connected.pattern(),
            BlinkPattern {
                on_ms: 1000,
                off_ms: 0,
            }
        );
        assert_eq!(
            IndicatorState::Failsafe.pattern(),
            BlinkPattern {
                on_ms: 100,
                off_ms: 100,
            }
        );
    }
}

#[cfg(all(target_arch = "arm", target_os = "none"))]
#[embassy_executor::task]
pub async fn indicator_task(
    mut led_pin: Output<'static>,
    state_signal: &'static Signal<ThreadModeRawMutex, PeripheralState>,
) -> ! {
    let mut current_state = PeripheralState::Advertising;

    loop {
        if state_signal.signaled() {
            current_state = state_signal.wait().await;
        }

        match current_state {
            PeripheralState::Advertising => {
                led_pin.set_high();
                match select(Timer::after_millis(500), state_signal.wait()).await {
                    Either::First(_) => {
                        led_pin.set_low();
                        match select(Timer::after_millis(500), state_signal.wait()).await {
                            Either::First(_) => {}
                            Either::Second(new_state) => current_state = new_state,
                        }
                    }
                    Either::Second(new_state) => current_state = new_state,
                }
            }
            PeripheralState::Connected => {
                led_pin.set_high();
                current_state = state_signal.wait().await;
            }
            PeripheralState::Failsafe => {
                led_pin.set_high();
                match select(Timer::after_millis(100), state_signal.wait()).await {
                    Either::First(_) => {
                        led_pin.set_low();
                        match select(Timer::after_millis(100), state_signal.wait()).await {
                            Either::First(_) => {}
                            Either::Second(new_state) => current_state = new_state,
                        }
                    }
                    Either::Second(new_state) => current_state = new_state,
                }
            }
        }
    }
}
