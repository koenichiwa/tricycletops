#![cfg(all(target_arch = "arm", target_os = "none"))]

//! In-tree `defmt` USB transport using `embassy-usb` CDC-ACM.
//!
//! Provides a `#[defmt::global_logger]` implementation that buffers defmt frames
//! in a double-buffered queue and drains them asynchronously to the USB host via CDC-ACM.

use core::cell::UnsafeCell;
use core::future::poll_fn;
use core::sync::atomic::{AtomicBool, Ordering};
use core::task::Poll;

use embassy_sync::waitqueue::AtomicWaker;
use embassy_usb::class::cdc_acm::{CdcAcmClass, Sender, State};
use embassy_usb::driver::{Driver, EndpointError};
use embassy_usb::{Builder, Config, UsbDevice};
use static_cell::StaticCell;

const BUFFER_SIZE: usize = 1024;
const MAX_PACKET_SIZE: usize = 64;

static WAKER: AtomicWaker = AtomicWaker::new();

struct LogBuffer {
    data: [u8; BUFFER_SIZE],
    cursor: usize,
}

impl LogBuffer {
    const fn new() -> Self {
        Self {
            data: [0u8; BUFFER_SIZE],
            cursor: 0,
        }
    }

    fn remaining(&self) -> usize {
        BUFFER_SIZE - self.cursor
    }

    fn write(&mut self, bytes: &[u8]) -> usize {
        let n = bytes.len().min(self.remaining());
        self.data[self.cursor..self.cursor + n].copy_from_slice(&bytes[..n]);
        self.cursor += n;
        n
    }

    fn reset(&mut self) {
        self.cursor = 0;
    }
}

struct Chunk {
    data: [u8; MAX_PACKET_SIZE],
    len: usize,
    done: bool,
}

struct Controller {
    buffers: [LogBuffer; 2],
    active: usize,
    flush_pending: bool,
}

impl Controller {
    const fn new() -> Self {
        Self {
            buffers: [LogBuffer::new(), LogBuffer::new()],
            active: 0,
            flush_pending: false,
        }
    }

    fn write(&mut self, bytes: &[u8]) {
        let written = self.buffers[self.active].write(bytes);
        if written < bytes.len() {
            // Active buffer is full. If the alternate buffer is available, swap immediately.
            if !self.flush_pending {
                self.flush_pending = true;
                self.active ^= 1;
                WAKER.wake();
                let remainder = &bytes[written..];
                self.buffers[self.active].write(remainder);
            }
            // If the alternate buffer is already pending/flushing, drop excess bytes to avoid blocking.
        }
    }

    fn on_frame_end(&mut self) {
        if self.buffers[self.active].cursor > 0 && !self.flush_pending {
            self.flush_pending = true;
            self.active ^= 1;
            WAKER.wake();
        }
    }

    fn is_flush_pending(&self) -> bool {
        self.flush_pending
    }

    fn get_chunk(&mut self, read_cursor: &mut usize, max_len: usize) -> Chunk {
        let max_len = max_len.min(MAX_PACKET_SIZE);
        if !self.flush_pending {
            return Chunk {
                data: [0; MAX_PACKET_SIZE],
                len: 0,
                done: true,
            };
        }

        let flush_idx = self.active ^ 1;
        let buf = &mut self.buffers[flush_idx];
        let available = buf.cursor.saturating_sub(*read_cursor);
        let len = available.min(max_len);
        let mut data = [0u8; MAX_PACKET_SIZE];
        if len > 0 {
            data[..len].copy_from_slice(&buf.data[*read_cursor..*read_cursor + len]);
            *read_cursor += len;
        }

        let done = *read_cursor >= buf.cursor;
        if done {
            buf.reset();
            *read_cursor = 0;
            if self.buffers[self.active].cursor > 0 {
                self.active ^= 1;
                self.flush_pending = true;
            } else {
                self.flush_pending = false;
            }
        }

        Chunk { data, len, done }
    }

    fn reset_on_disconnect(&mut self) {
        let flush_idx = self.active ^ 1;
        self.buffers[flush_idx].reset();
        self.flush_pending = false;
    }
}

struct UsbLogger {
    taken: AtomicBool,
    restore: UnsafeCell<critical_section::RestoreState>,
    encoder: UnsafeCell<defmt::Encoder>,
    controller: UnsafeCell<Controller>,
}

unsafe impl Sync for UsbLogger {}

static LOGGER: UsbLogger = UsbLogger::new();

impl UsbLogger {
    const fn new() -> Self {
        Self {
            taken: AtomicBool::new(false),
            restore: UnsafeCell::new(critical_section::RestoreState::invalid()),
            encoder: UnsafeCell::new(defmt::Encoder::new()),
            controller: UnsafeCell::new(Controller::new()),
        }
    }

    fn acquire(&self) {
        let restore = unsafe { critical_section::acquire() };

        if self.taken.load(Ordering::Relaxed) {
            panic!("defmt logger taken reentrantly");
        }

        self.taken.store(true, Ordering::Relaxed);

        unsafe {
            self.restore.get().write(restore);
            let encoder = &mut *self.encoder.get();
            let controller = &mut *self.controller.get();
            encoder.start_frame(|bytes| {
                controller.write(bytes);
            });
        }
    }

    unsafe fn write(&self, bytes: &[u8]) {
        let encoder = unsafe { &mut *self.encoder.get() };
        let controller = unsafe { &mut *self.controller.get() };
        encoder.write(bytes, |b| {
            controller.write(b);
        });
    }

    unsafe fn release(&self) {
        if !self.taken.load(Ordering::Relaxed) {
            panic!("defmt release outside of critical section");
        }

        unsafe {
            let encoder = &mut *self.encoder.get();
            let controller = &mut *self.controller.get();
            encoder.end_frame(|bytes| {
                controller.write(bytes);
            });

            controller.on_frame_end();

            let restore = self.restore.get().read();
            self.taken.store(false, Ordering::Relaxed);
            critical_section::release(restore);
        }
    }

    unsafe fn flush(&self) {
        unsafe {
            let controller = &mut *self.controller.get();
            controller.on_frame_end();
        }
    }
}

#[defmt::global_logger]
struct UsbDefmtLogger;

unsafe impl defmt::Logger for UsbDefmtLogger {
    fn acquire() {
        LOGGER.acquire();
    }

    unsafe fn flush() {
        unsafe { LOGGER.flush() };
    }

    unsafe fn release() {
        unsafe { LOGGER.release() };
    }

    unsafe fn write(bytes: &[u8]) {
        unsafe { LOGGER.write(bytes) };
    }
}

/// Drains buffered defmt frames to the USB host over CDC-ACM.
pub async fn cdc_task<D: Driver<'static>>(mut sender: Sender<'static, D>) -> ! {
    let packet_size = (sender.max_packet_size() as usize).min(MAX_PACKET_SIZE);

    loop {
        // Wait until USB connection is established/enabled by the host
        sender.wait_connection().await;

        let mut read_cursor = 0;
        loop {
            // Wait until a buffer has pending data to flush
            poll_fn(|cx| {
                critical_section::with(|_cs| {
                    WAKER.register(cx.waker());
                    let controller = unsafe { &*LOGGER.controller.get() };
                    if controller.is_flush_pending() {
                        Poll::Ready(())
                    } else {
                        Poll::Pending
                    }
                })
            })
            .await;

            // Drain the pending buffer in chunks matching endpoint max packet size
            let mut was_max_packet = false;
            let mut disconnected = false;

            loop {
                let chunk = critical_section::with(|_cs| {
                    let controller = unsafe { &mut *LOGGER.controller.get() };
                    controller.get_chunk(&mut read_cursor, packet_size)
                });

                if chunk.len > 0 {
                    was_max_packet = chunk.len == packet_size;
                    if let Err(EndpointError::Disabled) =
                        sender.write_packet(&chunk.data[..chunk.len]).await
                    {
                        disconnected = true;
                        break;
                    }
                }

                if chunk.done {
                    if was_max_packet {
                        if let Err(EndpointError::Disabled) = sender.write_packet(&[]).await {
                            disconnected = true;
                            break;
                        }
                    }
                    read_cursor = 0;
                    break;
                }
            }

            if disconnected {
                critical_section::with(|_cs| {
                    let controller = unsafe { &mut *LOGGER.controller.get() };
                    controller.reset_on_disconnect();
                });
                break;
            }
        }
    }
}

/// Initializes the USB device and CDC-ACM class for logging.
pub fn init_usb<D: Driver<'static>>(
    driver: D,
) -> (UsbDevice<'static, D>, Sender<'static, D>) {
    let mut config = Config::new(0xc0de, 0xcafe);
    config.manufacturer = Some("Tricycletops");
    config.product = Some("Tricycletops Defmt Logger");
    config.serial_number = Some("defmt");
    config.max_power = 100;
    config.max_packet_size_0 = 64;

    static CONFIG_DESCRIPTOR: StaticCell<[u8; 256]> = StaticCell::new();
    static BOS_DESCRIPTOR: StaticCell<[u8; 256]> = StaticCell::new();
    static MSOS_DESCRIPTOR: StaticCell<[u8; 256]> = StaticCell::new();
    static CONTROL_BUF: StaticCell<[u8; 64]> = StaticCell::new();
    static STATE: StaticCell<State> = StaticCell::new();

    let mut builder = Builder::new(
        driver,
        config,
        CONFIG_DESCRIPTOR.init([0; 256]),
        BOS_DESCRIPTOR.init([0; 256]),
        MSOS_DESCRIPTOR.init([0; 256]),
        CONTROL_BUF.init([0; 64]),
    );

    let state = STATE.init(State::new());
    let class = CdcAcmClass::new(&mut builder, state, 64);
    let (sender, _) = class.split();

    let usb = builder.build();
    (usb, sender)
}

/// All-in-one runner that joins USB device task and CDC-ACM logger drain task.
pub async fn run<D: Driver<'static>>(driver: D) -> ! {
    let (mut usb, sender) = init_usb(driver);
    let usb_fut = usb.run();
    let cdc_fut = cdc_task(sender);
    embassy_futures::join::join(usb_fut, cdc_fut).await;
    loop {}
}
