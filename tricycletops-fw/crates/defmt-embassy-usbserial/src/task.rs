//! Main task that runs the USB transport layer.

use embassy_time::{Duration, Timer};
use embassy_usb::{
    Builder, Config,
    class::cdc_acm::{CdcAcmClass, Sender, State},
    driver::{Driver, EndpointError},
};
use static_cell::{ConstStaticCell, StaticCell};

// TODO: Document the RAM usage of these buffers.

/// Config descriptor buffer
static CONFIG_DESCRIPTOR_BUF: ConstStaticCell<[u8; 256]> = ConstStaticCell::new([0u8; 256]);

/// BOS descriptor buffer
static BOS_DESCRIPTOR_BUF: ConstStaticCell<[u8; 256]> = ConstStaticCell::new([0u8; 256]);

/// MSOS descriptor buffer
static MSOS_DESCRIPTOR_BUF: ConstStaticCell<[u8; 256]> = ConstStaticCell::new([0u8; 256]);

/// Control buffer
static CONTROL_BUF: ConstStaticCell<[u8; 256]> = ConstStaticCell::new([0u8; 256]);

/// CDC ACM state.
static STATE: StaticCell<State> = StaticCell::new();

/// Run the USB driver and defmt logger tasks.
///
/// This function builds the USB device with the provided driver and configuration, and awaits both
/// it and the function that writes out buffered defmt messages over USB.
///
/// Along with the usb driver implementation, users must pass a USB configuration that is properly
/// set for USB-CDC. See [the library documentation][crate] for details about the requirements.
pub async fn run<D: Driver<'static>>(driver: D, config: Config<'static>) {
    // Create the state of the CDC ACM device.
    let state: &'static mut State<'static> = STATE.init(State::new());

    // Create the USB builder.
    let mut builder = Builder::new(
        driver,
        config,
        CONFIG_DESCRIPTOR_BUF.take(),
        BOS_DESCRIPTOR_BUF.take(),
        MSOS_DESCRIPTOR_BUF.take(),
        CONTROL_BUF.take(),
    );

    // Create the class on top of the builder.
    let packet_size = config.max_packet_size_0 as u16;
    let class = CdcAcmClass::new(&mut builder, state, packet_size);

    // Build the USB.
    let mut usb = builder.build();

    // Get the sender.
    let (sender, _) = class.split();

    // Run both futures concurrently.
    embassy_futures::join::join(usb.run(), logger(sender)).await;
}

/// USB logger task that writes full buffers out over USB.
///
/// When USB is connected, this enables the defmtusb controller and continuously attempts to flush
/// any full buffer out over USB via `sender`.
pub async fn logger<'d, D: Driver<'d>>(mut sender: Sender<'d, D>) {
    // Get a reference to the controller.
    let controller = &super::controller::CONTROLLER;
    // Only attempt to write what the sender will accept.
    let packet_size = sender.max_packet_size() as usize;

    'main: loop {
        // Wait for the device to be connected.
        sender.wait_connection().await;

        // Set the controller as enabled.
        controller.enable();

        // Continually attempt to write buffered defmt bytes out over USB.
        loop {
            let flush_res = controller
                .flush::<_, EndpointError>(async |bytes| {
                    let mut was_max_size = false;
                    for chunk in bytes.chunks(packet_size) {
                        was_max_size = chunk.len() == packet_size;
                        sender.write_packet(chunk).await?;
                    }
                    // The Embassy CDC ACM docs note that a transfer must be terminated with a
                    // shorter packet, so we track the size of the last chunk sent, and send a
                    // zero-length packet if the chunk was the maximum packet size to ensure it is
                    // processed by the host.
                    if was_max_size {
                        sender.write_packet(&[]).await?;
                    }
                    Ok(())
                })
                .await;

            match flush_res {
                Err(EndpointError::Disabled) => {
                    // USB endpoint is now disabled, so disable the controller (and so
                    // not accept any defmt log messages) and wait until reconnected.
                    controller.disable();
                    continue 'main;
                }
                Err(EndpointError::BufferOverflow) => {
                    unreachable!("Sent chunks are limited to Sender max packet size.")
                }
                Ok(()) => (),
            };

            // Wait the timeout.
            // TODO: Make this configurable.
            Timer::after(Duration::from_millis(100)).await;
        }
    }
}
