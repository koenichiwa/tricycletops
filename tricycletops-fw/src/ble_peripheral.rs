use defmt::{info, warn};
use embassy_futures::join::join;
use embassy_rp::gpio::Output;
use embassy_sync::blocking_mutex::raw::ThreadModeRawMutex;
use embassy_sync::signal::Signal;
use embassy_time::Timer;
use trouble_host::prelude::*;

use crate::car_control::DriveCommand;

/// Max number of connections
const CONNECTIONS_MAX: usize = 1;

/// Max number of L2CAP channels.
const L2CAP_CHANNELS_MAX: usize = 2; // Signal + att

// GATT Server definition
#[gatt_server]
struct Server {
    car_service: CarService,
}

/// Car control service
#[gatt_service(uuid = "fff0")]
struct CarService {
    #[characteristic(uuid = "fff1", read, write, value = [0u8; 8])]
    drive_command: [u8; 8],
    #[characteristic(uuid = "fff2", read, value = [0u8; 4])]
    max_duty: [u8; 4],
}

/// Run the BLE stack.
pub async fn run<C>(
    controller: C,
    command_signal: &'static Signal<ThreadModeRawMutex, DriveCommand>,
    led_pin: Output<'static>,
    max_duties: (u16, u16),
) where
    C: Controller,
{
    // Using a fixed "random" address can be useful for testing. In real scenarios, one would
    // use e.g. the MAC 6 byte array as the address (how to get that varies by the platform).
    let address: Address = Address::random([0xff, 0x8f, 0x1a, 0x05, 0xe4, 0xff]);
    info!("Our address = {:?}", address);

    let mut resources: HostResources<C, DefaultPacketPool, CONNECTIONS_MAX, L2CAP_CHANNELS_MAX> =
        HostResources::new();
    let stack = trouble_host::new(controller, &mut resources)
        .set_random_address(address)
        .build();
    let runner = stack.runner();
    let mut peripheral = stack.peripheral();

    info!("Starting advertising and GATT service");
    let server = Server::new_with_config(GapConfig::Peripheral(PeripheralConfig {
        name: "Tricycletops",
        appearance: &appearance::motorized_device::GENERIC_MOTORIZED_DEVICE,
    }))
    .unwrap();

    let led_toggle = blink(led_pin);

    let ble_stack = join(ble_task(runner), async {
        loop {
            match advertise("Tricycletops", &mut peripheral, &server).await {
                Ok(conn) => {
                    // set up tasks when the connection is established to a central, so they don't run when no one is connected.
                    let _ = gatt_events_task(&server, &conn, command_signal, max_duties).await;
                }
                Err(e) => {
                    let e = defmt::Debug2Format(&e);
                    panic!("[adv] error: {e:?}");
                }
            }
        }
    });

    let _ = join(led_toggle, ble_stack).await;
}

/// This is a background task that is required to run forever alongside any other BLE tasks.
async fn ble_task<C: Controller, P: PacketPool>(mut runner: Runner<'_, C, P>) {
    loop {
        if let Err(e) = runner.run().await {
            let e = defmt::Debug2Format(&e);
            panic!("[ble_task] error: {e:?}");
        }
    }
}

async fn blink(mut led_pin: Output<'static>) {
    loop {
        led_pin.toggle();
        Timer::after_secs(1).await;
    }
}

/// Stream Events until the connection closes.
///
/// This function will handle the GATT events and process them.
/// This is how we interact with read and write requests.
async fn gatt_events_task<P: PacketPool>(
    server: &Server<'_>,
    conn: &GattConnection<'_, '_, P>,
    command_signal: &'static Signal<ThreadModeRawMutex, DriveCommand>,
    max_duties: (u16, u16),
) -> Result<(), Error> {
    let drive_command_char = server.car_service.drive_command;
    let max_duty_char = server.car_service.max_duty;

    let mut max_duty_bytes = [0u8; 4];
    max_duty_bytes[0..2].copy_from_slice(&max_duties.0.to_le_bytes());
    max_duty_bytes[2..4].copy_from_slice(&max_duties.1.to_le_bytes());
    conn.set(&max_duty_char, &max_duty_bytes)?;

    let reason = loop {
        match conn.next().await {
            GattConnectionEvent::Disconnected { reason } => break reason,
            GattConnectionEvent::Gatt { event } => {
                if let GattEvent::Write(write_event) = &event
                    && write_event.handle() == drive_command_char.handle
                {
                    let mut command = None;

                    write_event.with_data(|_, data| {
                        command = decode_drive_command(data);
                    });

                    if let Some(command) = command {
                        conn.set(&drive_command_char, &encode_drive_command(command))?;
                        command_signal.signal(command);
                    }
                }
                let reply = event.accept();
                // This step is also performed at drop(), but writing it explicitly is necessary
                // in order to ensure reply is sent.
                match reply {
                    Ok(reply) => reply.send().await,
                    Err(e) => warn!("[gatt] error sending response: {:?}", e),
                }
            }
            _ => {} // ignore other Gatt Connection Events
        }
    };
    info!("[gatt] disconnected: {:?}", reason);
    Ok(())
}

/// Create an advertiser to use to connect to a BLE Central, and wait for it to connect.
async fn advertise<'values, 'server, C: Controller>(
    name: &'values str,
    peripheral: &mut Peripheral<'values, C, DefaultPacketPool>,
    server: &'server Server<'values>,
) -> Result<GattConnection<'values, 'server, DefaultPacketPool>, BleHostError<C::Error>> {
    let mut advertiser_data = [0; 31];
    let len = AdStructure::encode_slice(
        &[
            AdStructure::Flags(LE_GENERAL_DISCOVERABLE | BR_EDR_NOT_SUPPORTED),
            AdStructure::IncompleteServiceUuids16(&[[0xf0, 0xff]]),
            AdStructure::CompleteLocalName(name.as_bytes()),
        ],
        &mut advertiser_data[..],
    )?;
    let advertiser = peripheral
        .advertise(
            &AdvertisementParameters::default(),
            Advertisement::ConnectableScannableUndirected {
                adv_data: &advertiser_data[..len],
                scan_data: &[],
            },
        )
        .await?;
    info!("[adv] advertising");
    let conn = advertiser.accept().await?.with_attribute_server(server)?;
    info!("[adv] connection established");
    Ok(conn)
}

fn decode_drive_command(data: &[u8]) -> Option<DriveCommand> {
    if data.len() != 8 {
        return None;
    }

    Some(DriveCommand {
        left: i32::from_le_bytes(data[0..4].try_into().ok()?),
        right: i32::from_le_bytes(data[4..8].try_into().ok()?),
    })
}

fn encode_drive_command(cmd: DriveCommand) -> [u8; 8] {
    let mut bytes = [0u8; 8];
    bytes[..4].copy_from_slice(&cmd.left.to_le_bytes());
    bytes[4..].copy_from_slice(&cmd.right.to_le_bytes());
    bytes
}
