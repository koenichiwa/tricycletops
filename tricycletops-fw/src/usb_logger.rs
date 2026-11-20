//! In-tree `defmt` USB transport using `embassy-usb` CDC-ACM.
//!
//! Provides decoupled double-buffered queue and chunk framing logic for host unit testing,
//! alongside a `#[defmt::global_logger]` implementation and asynchronous CDC-ACM drain task
//! for ARM Cortex-M targets.

use embassy_sync::waitqueue::AtomicWaker;

pub const BUFFER_SIZE: usize = 1024;
pub const MAX_PACKET_SIZE: usize = 64;

static WAKER: AtomicWaker = AtomicWaker::new();

#[derive(Debug)]
pub struct LogBuffer {
    data: [u8; BUFFER_SIZE],
    cursor: usize,
}

impl LogBuffer {
    pub const fn new() -> Self {
        Self {
            data: [0u8; BUFFER_SIZE],
            cursor: 0,
        }
    }

    pub fn remaining(&self) -> usize {
        BUFFER_SIZE - self.cursor
    }

    pub fn cursor(&self) -> usize {
        self.cursor
    }

    pub fn data(&self) -> &[u8] {
        &self.data[..self.cursor]
    }

    pub fn write(&mut self, bytes: &[u8]) -> usize {
        let n = bytes.len().min(self.remaining());
        self.data[self.cursor..self.cursor + n].copy_from_slice(&bytes[..n]);
        self.cursor += n;
        n
    }

    pub fn reset(&mut self) {
        self.cursor = 0;
    }
}

impl Default for LogBuffer {
    fn default() -> Self {
        Self::new()
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Chunk {
    pub data: [u8; MAX_PACKET_SIZE],
    pub len: usize,
    pub done: bool,
}

pub struct Controller {
    pub(crate) buffers: [LogBuffer; 2],
    pub(crate) active: usize,
    pub(crate) flush_pending: bool,
}

impl Controller {
    pub const fn new() -> Self {
        Self {
            buffers: [LogBuffer::new(), LogBuffer::new()],
            active: 0,
            flush_pending: false,
        }
    }

    pub fn write(&mut self, bytes: &[u8]) {
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

    pub fn on_frame_end(&mut self) {
        if self.buffers[self.active].cursor > 0 && !self.flush_pending {
            self.flush_pending = true;
            self.active ^= 1;
            WAKER.wake();
        }
    }

    pub fn is_flush_pending(&self) -> bool {
        self.flush_pending
    }

    pub fn get_chunk(&mut self, read_cursor: &mut usize, max_len: usize) -> Chunk {
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

    pub fn reset_on_disconnect(&mut self) {
        let flush_idx = self.active ^ 1;
        self.buffers[flush_idx].reset();
        self.flush_pending = false;
    }
}

impl Default for Controller {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(all(target_arch = "arm", target_os = "none"))]
use {
    core::cell::UnsafeCell,
    core::future::poll_fn,
    core::sync::atomic::{AtomicBool, Ordering},
    core::task::Poll,
    embassy_usb::class::cdc_acm::device::{CdcAcmClass, Config as CdcAcmConfig, Sender, State},
    embassy_usb::driver::{Driver, EndpointError},
    embassy_usb::{Builder, Config, UsbDevice},
    static_cell::StaticCell,
};

#[cfg(all(target_arch = "arm", target_os = "none"))]
struct UsbLogger {
    taken: AtomicBool,
    restore: UnsafeCell<critical_section::RestoreState>,
    encoder: UnsafeCell<defmt::Encoder>,
    controller: UnsafeCell<Controller>,
}

// SAFETY: Access to the internal `UnsafeCell` fields (`restore`, `encoder`, `controller`) is
// strictly synchronized via critical sections (disabling interrupts) and guarded by the `taken`
// atomic flag to prevent concurrent or reentrant access across execution contexts.
#[cfg(all(target_arch = "arm", target_os = "none"))]
unsafe impl Sync for UsbLogger {}

#[cfg(all(target_arch = "arm", target_os = "none"))]
static LOGGER: UsbLogger = UsbLogger::new();

#[cfg(all(target_arch = "arm", target_os = "none"))]
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
        // SAFETY: `critical_section::acquire()` disables interrupts to enter a critical section,
        // returning the previous interrupt state to be restored on release.
        let restore = unsafe { critical_section::acquire() };

        if self.taken.load(Ordering::Relaxed) {
            panic!("defmt logger taken reentrantly");
        }

        self.taken.store(true, Ordering::Relaxed);

        // SAFETY: We have entered a critical section and verified `self.taken` was false.
        // Exclusive access to `restore`, `encoder`, and `controller` is guaranteed until `release`.
        unsafe {
            self.restore.get().write(restore);
            let encoder = &mut *self.encoder.get();
            let controller = &mut *self.controller.get();
            encoder.start_frame(|bytes| {
                controller.write(bytes);
            });
        }
    }

    // SAFETY: The caller must ensure that `acquire()` has been called before `write()`,
    // establishing a critical section and exclusive mutable access to the encoder and controller.
    unsafe fn write(&self, bytes: &[u8]) {
        // SAFETY: Exclusive access is guaranteed by the critical section established by `acquire()`.
        let encoder = unsafe { &mut *self.encoder.get() };
        let controller = unsafe { &mut *self.controller.get() };
        encoder.write(bytes, |b| {
            controller.write(b);
        });
    }

    // SAFETY: The caller must ensure that `acquire()` was called prior to `release()` and that the
    // critical section is still active.
    unsafe fn release(&self) {
        if !self.taken.load(Ordering::Relaxed) {
            panic!("defmt release outside of critical section");
        }

        // SAFETY: We hold exclusive access via the critical section and `self.taken`. We finalize the
        // frame, read back the restore state, reset `self.taken`, and release the critical section.
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

    // SAFETY: The caller must ensure that calling `flush()` does not violate aliasing rules
    // or is done within a synchronized context.
    unsafe fn flush(&self) {
        // SAFETY: Mutable access to controller to trigger frame flushing on demand.
        unsafe {
            let controller = &mut *self.controller.get();
            controller.on_frame_end();
        }
    }
}

#[cfg(all(target_arch = "arm", target_os = "none"))]
#[defmt::global_logger]
struct UsbDefmtLogger;

// SAFETY: `UsbDefmtLogger` satisfies defmt's global logger contract by routing calls to `LOGGER`,
// which manages reentrancy checks and critical section synchronization.
#[cfg(all(target_arch = "arm", target_os = "none"))]
unsafe impl defmt::Logger for UsbDefmtLogger {
    fn acquire() {
        LOGGER.acquire();
    }

    // SAFETY: Delegates to `LOGGER.flush()` satisfying `defmt::Logger::flush` contract.
    unsafe fn flush() {
        // SAFETY: Permitted by `defmt::Logger::flush` contract.
        unsafe { LOGGER.flush() };
    }

    // SAFETY: Delegates to `LOGGER.release()`, restoring the critical section entered in `acquire()`.
    unsafe fn release() {
        // SAFETY: Permitted by `defmt::Logger::release` contract following `acquire()`.
        unsafe { LOGGER.release() };
    }

    // SAFETY: Delegates to `LOGGER.write()`, valid between `acquire()` and `release()` in a critical section.
    unsafe fn write(bytes: &[u8]) {
        // SAFETY: Permitted by `defmt::Logger::write` contract between `acquire()` and `release()`.
        unsafe { LOGGER.write(bytes) };
    }
}

/// Drains buffered defmt frames to the USB host over CDC-ACM.
#[cfg(all(target_arch = "arm", target_os = "none"))]
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
                    // SAFETY: Accessing controller inside `critical_section::with` guarantees
                    // mutual exclusion against `UsbLogger`'s acquire/write/release.
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
                    // SAFETY: Mutable access to controller inside `critical_section::with` guarantees
                    // mutual exclusion against logger interrupt/thread contexts.
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
                    if was_max_packet
                        && let Err(EndpointError::Disabled) = sender.write_packet(&[]).await
                    {
                        disconnected = true;
                        break;
                    }
                    read_cursor = 0;
                    break;
                }
            }

            if disconnected {
                critical_section::with(|_cs| {
                    // SAFETY: Mutable access inside `critical_section::with` guarantees mutual exclusion
                    // while clearing pending buffers on USB disconnect.
                    let controller = unsafe { &mut *LOGGER.controller.get() };
                    controller.reset_on_disconnect();
                });
                break;
            }
        }
    }
}

/// Initializes the USB device and CDC-ACM class for logging.
#[cfg(all(target_arch = "arm", target_os = "none"))]
pub fn init_usb<D: Driver<'static>>(driver: D) -> (UsbDevice<'static, D>, Sender<'static, D>) {
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
    let class = CdcAcmClass::new(&mut builder, state, CdcAcmConfig::default());
    let (sender, _) = class.split();

    let usb = builder.build();
    (usb, sender)
}

/// All-in-one runner that joins USB device task and CDC-ACM logger drain task.
#[cfg(all(target_arch = "arm", target_os = "none"))]
pub async fn run<D: Driver<'static>>(driver: D) -> ! {
    let (mut usb, sender) = init_usb(driver);
    let usb_fut = usb.run();
    let cdc_fut = cdc_task(sender);
    match embassy_futures::join::join(usb_fut, cdc_fut).await {}
}

/// Embassy executor task that runs the USB device and CDC-ACM logger.
#[cfg(all(target_arch = "arm", target_os = "none"))]
#[embassy_executor::task]
pub async fn logger_task(driver: crate::board::UsbDriver) -> ! {
    run(driver).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_log_buffer_write_within_capacity() {
        let mut buf = LogBuffer::new();
        assert_eq!(buf.cursor(), 0);
        assert_eq!(buf.remaining(), BUFFER_SIZE);

        let data = [0x5au8; 100];
        let written = buf.write(&data);
        assert_eq!(written, 100);
        assert_eq!(buf.cursor(), 100);
        assert_eq!(buf.remaining(), BUFFER_SIZE - 100);
        assert_eq!(buf.data(), &data[..]);
    }

    #[test]
    fn test_log_buffer_write_overflow_clamps_and_reset() {
        let mut buf = LogBuffer::new();
        let payload1 = [0x11u8; BUFFER_SIZE - 10];
        assert_eq!(buf.write(&payload1), BUFFER_SIZE - 10);

        let payload2 = [0x22u8; 50];
        // Only 10 bytes remaining
        assert_eq!(buf.write(&payload2), 10);
        assert_eq!(buf.remaining(), 0);
        assert_eq!(buf.cursor(), BUFFER_SIZE);

        // Subsequent writes return 0
        assert_eq!(buf.write(&[0x33; 10]), 0);

        buf.reset();
        assert_eq!(buf.cursor(), 0);
        assert_eq!(buf.remaining(), BUFFER_SIZE);
        assert_eq!(buf.data().len(), 0);
    }

    #[test]
    fn test_controller_single_chunk_extraction() {
        let mut ctrl = Controller::new();
        assert!(!ctrl.is_flush_pending());

        let payload = [1, 2, 3, 4, 5, 6, 7, 8];
        ctrl.write(&payload);
        ctrl.on_frame_end();
        assert!(ctrl.is_flush_pending());

        let mut read_cursor = 0;
        let chunk = ctrl.get_chunk(&mut read_cursor, MAX_PACKET_SIZE);
        assert_eq!(chunk.len, payload.len());
        assert!(chunk.done);
        assert_eq!(&chunk.data[..chunk.len], &payload);
        assert!(!ctrl.is_flush_pending());

        // Further reads return empty done chunk
        let next_chunk = ctrl.get_chunk(&mut read_cursor, MAX_PACKET_SIZE);
        assert_eq!(next_chunk.len, 0);
        assert!(next_chunk.done);
    }

    #[test]
    fn test_controller_exact_max_packet_size_chunk() {
        let mut ctrl = Controller::new();
        let payload = [0xaau8; MAX_PACKET_SIZE];
        ctrl.write(&payload);
        ctrl.on_frame_end();
        assert!(ctrl.is_flush_pending());

        let mut read_cursor = 0;
        let chunk = ctrl.get_chunk(&mut read_cursor, MAX_PACKET_SIZE);
        assert_eq!(chunk.len, MAX_PACKET_SIZE);
        assert!(chunk.done);
        assert_eq!(&chunk.data[..chunk.len], &payload);
        assert!(!ctrl.is_flush_pending());
    }

    #[test]
    fn test_controller_multi_chunk_extraction() {
        let mut ctrl = Controller::new();
        // 150 bytes: 64 + 64 + 22 bytes
        let mut payload = [0u8; 150];
        for (i, b) in payload.iter_mut().enumerate() {
            *b = (i % 256) as u8;
        }

        ctrl.write(&payload);
        ctrl.on_frame_end();
        assert!(ctrl.is_flush_pending());

        let mut read_cursor = 0;
        let chunk1 = ctrl.get_chunk(&mut read_cursor, MAX_PACKET_SIZE);
        assert_eq!(chunk1.len, 64);
        assert!(!chunk1.done);
        assert_eq!(&chunk1.data[..64], &payload[0..64]);

        let chunk2 = ctrl.get_chunk(&mut read_cursor, MAX_PACKET_SIZE);
        assert_eq!(chunk2.len, 64);
        assert!(!chunk2.done);
        assert_eq!(&chunk2.data[..64], &payload[64..128]);

        let chunk3 = ctrl.get_chunk(&mut read_cursor, MAX_PACKET_SIZE);
        assert_eq!(chunk3.len, 22);
        assert!(chunk3.done);
        assert_eq!(&chunk3.data[..22], &payload[128..150]);

        assert!(!ctrl.is_flush_pending());
    }

    #[test]
    fn test_controller_double_buffering_swap() {
        let mut ctrl = Controller::new();

        let data1 = [0x11u8; 50];
        ctrl.write(&data1);
        ctrl.on_frame_end();
        assert!(ctrl.is_flush_pending());

        // Write data into the active alternate buffer while buffer 1 is awaiting flush
        let data2 = [0x22u8; 30];
        ctrl.write(&data2);

        // Drain buffer 1
        let mut read_cursor = 0;
        let chunk1 = ctrl.get_chunk(&mut read_cursor, MAX_PACKET_SIZE);
        assert_eq!(chunk1.len, 50);
        assert!(chunk1.done);
        assert_eq!(&chunk1.data[..50], &data1);

        // Alternate buffer automatically becomes flush pending
        assert!(ctrl.is_flush_pending());

        // Drain buffer 2
        let chunk2 = ctrl.get_chunk(&mut read_cursor, MAX_PACKET_SIZE);
        assert_eq!(chunk2.len, 30);
        assert!(chunk2.done);
        assert_eq!(&chunk2.data[..30], &data2);

        assert!(!ctrl.is_flush_pending());
    }

    #[test]
    fn test_controller_drop_on_overflow() {
        let mut ctrl = Controller::new();

        // Fill buffer 0 (1024 bytes)
        let fill_buf0 = [0xaa; BUFFER_SIZE];
        ctrl.write(&fill_buf0);

        // Writing one more byte triggers buffer swap to buffer 1 and marks flush pending
        let overflow_bytes = [0xbb; 100];
        ctrl.write(&overflow_bytes);
        assert!(ctrl.is_flush_pending());

        // Fill remainder of buffer 1
        let fill_buf1 = [0xcc; BUFFER_SIZE - 100];
        ctrl.write(&fill_buf1);

        // Both buffers are now completely full (2048 bytes buffered total).
        // Additional writes must be safely dropped without panic or blocking.
        let dropped_data = [0xff; 500];
        ctrl.write(&dropped_data);

        // Drain buffer 0: exactly 1024 bytes (16 chunks of 64 bytes)
        let mut read_cursor = 0;
        for i in 0..16 {
            let chunk = ctrl.get_chunk(&mut read_cursor, MAX_PACKET_SIZE);
            assert_eq!(chunk.len, 64);
            assert_eq!(chunk.done, i == 15);
            assert_eq!(chunk.data, [0xaa; 64]);
        }

        // Buffer 1 now automatically pending
        assert!(ctrl.is_flush_pending());

        // Drain first 100 bytes of buffer 1 (chunk of 64 bytes + chunk of 36 bytes)
        let chunk_b1 = ctrl.get_chunk(&mut read_cursor, MAX_PACKET_SIZE);
        assert_eq!(chunk_b1.len, 64);
        assert_eq!(chunk_b1.data, [0xbb; 64]);

        let chunk_b2 = ctrl.get_chunk(&mut read_cursor, 36);
        assert_eq!(chunk_b2.len, 36);
        assert_eq!(&chunk_b2.data[..36], &[0xbb; 36]);
    }

    #[test]
    fn test_controller_reset_on_disconnect() {
        let mut ctrl = Controller::new();
        ctrl.write(&[1, 2, 3, 4]);
        ctrl.on_frame_end();
        assert!(ctrl.is_flush_pending());

        ctrl.reset_on_disconnect();
        assert!(!ctrl.is_flush_pending());
    }
}
