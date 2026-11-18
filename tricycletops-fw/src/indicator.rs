#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_futures::select::{Either, select};
#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_rp::gpio::Output;
#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_sync::blocking_mutex::raw::ThreadModeRawMutex;
#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_sync::signal::Signal;
#[cfg(all(target_arch = "arm", target_os = "none"))]
use embassy_time::{Duration, Timer};

#[derive(Copy, Clone, Debug, PartialEq, Eq, defmt::Format)]
pub enum IndicatorState {
    Advertising,
    Connected,
    Failsafe,
}

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

#[cfg(all(target_arch = "arm", target_os = "none"))]
async fn step_phase<M: embassy_sync::blocking_mutex::raw::RawMutex>(
    led: &mut Output<'_>,
    high: bool,
    duration: Duration,
    signal: &'static Signal<M, IndicatorState>,
) -> Option<IndicatorState> {
    if high {
        led.set_high();
    } else {
        led.set_low();
    }
    match select(Timer::after(duration), signal.wait()).await {
        Either::First(_) => None,
        Either::Second(new_state) => Some(new_state),
    }
}

#[cfg(all(target_arch = "arm", target_os = "none"))]
#[embassy_executor::task]
pub async fn indicator_task(
    mut led_pin: Output<'static>,
    state_signal: &'static Signal<ThreadModeRawMutex, IndicatorState>,
) -> ! {
    let mut current_state = IndicatorState::Advertising;

    loop {
        if state_signal.signaled() {
            current_state = state_signal.wait().await;
        }

        let pattern = current_state.pattern();

        if pattern.off_ms == 0 {
            led_pin.set_high();
            current_state = state_signal.wait().await;
            continue;
        }

        if let Some(new_state) = step_phase(
            &mut led_pin,
            true,
            Duration::from_millis(pattern.on_ms as u64),
            state_signal,
        )
        .await
        {
            current_state = new_state;
            continue;
        }

        if let Some(new_state) = step_phase(
            &mut led_pin,
            false,
            Duration::from_millis(pattern.off_ms as u64),
            state_signal,
        )
        .await
        {
            current_state = new_state;
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
