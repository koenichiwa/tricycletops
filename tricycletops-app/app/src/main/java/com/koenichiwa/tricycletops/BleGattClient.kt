package com.koenichiwa.tricycletops

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.util.Log
import com.koenichiwa.tricycletops.BleGattClient.Companion.MAX_DUTY_CYCLE_CHARACTERISTIC
import com.koenichiwa.tricycletops.BleGattClient.Companion.TRICYCLETOPS_SERVICE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Disconnecting,
}

class BleGattClient(
    private val context: Context,
) {
    companion object {
        val TRICYCLETOPS_SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val DRIVE_COMMAND_CHARACTERISTIC: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val MAX_DUTY_CYCLE_CHARACTERISTIC: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothGatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
    val connectedDevice: StateFlow<BleDevice?> = _connectedDevice.asStateFlow()

    private val _leftMaxDuty = MutableStateFlow<Int?>(null)
    val leftMaxDuty: StateFlow<Int?> = _leftMaxDuty.asStateFlow()

    private val _rightMaxDuty = MutableStateFlow<Int?>(null)
    val rightMaxDuty: StateFlow<Int?> = _rightMaxDuty.asStateFlow()

    @SuppressLint("MissingPermission")
    private val gattCallback =
        TricycleTopsCallback(
            onError = { err ->
                disconnect()
            },
            onConnectionStateChange = { gatt, state ->
                when (state) {
                    ConnectionState.Disconnected -> {
                        gatt.close()
                    }

                    ConnectionState.Connected -> {
                        gatt.discoverServices()
                    }

                    ConnectionState.Connecting, ConnectionState.Disconnecting -> {}
                }
            },
            onMaxDutyCycleChange = { leftMaxDutyCycle, rightMaxDutyCycle ->
                _leftMaxDuty.value = leftMaxDutyCycle
                _rightMaxDuty.value = rightMaxDutyCycle
            },
        )

    @SuppressLint("MissingPermission")
    fun connect(device: BleDevice) {
        if (!context.hasBlePermissions()) {
            Log.e("BleGattClient", "Permissions missing for BLE connection")
            return
        }

        _connectedDevice.value = device
        _connectionState.value = ConnectionState.Connecting

        try {
            bluetoothGatt = device.rawDevice.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            Log.e("BleGattClient", "Connection failed: ${e.localizedMessage}")
            _connectionState.value = ConnectionState.Disconnected
            _connectedDevice.value = null
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (!context.hasBlePermissions()) return

        _connectionState.value = ConnectionState.Disconnecting
        bluetoothGatt?.disconnect()
        _connectedDevice.value = null
        _leftMaxDuty.value = null
        _rightMaxDuty.value = null
        _connectionState.value = ConnectionState.Disconnected
    }

    @SuppressLint("MissingPermission")
    fun sendDriveCommand(
        left: Int,
        right: Int,
    ) {
        if (!context.hasBlePermissions()) return
        if (connectionState.value != ConnectionState.Connected) return
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(TRICYCLETOPS_SERVICE) ?: return
        val characteristic = service.getCharacteristic(DRIVE_COMMAND_CHARACTERISTIC) ?: return

        val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(left)
        buffer.putInt(right)
        val payload = buffer.array()

        val result =
            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            )

        when (result) {
            BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED -> {
                TODO()
            }

            BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY -> {
                TODO()
            }

            BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION -> {
                TODO()
            }

            BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND -> {
                TODO()
            }

            BluetoothStatusCodes.ERROR_UNKNOWN -> {
                TODO()
            }

            BluetoothStatusCodes.SUCCESS -> {
                TODO()
            }
        }
    }
}

private class TricycleTopsCallback(
    val onError: (error: String) -> Unit,
    val onConnectionStateChange: (gatt: BluetoothGatt, ConnectionState) -> Unit,
    val onMaxDutyCycleChange: (leftMaxDutyCycle: Int, rightMaxDutyCycle: Int) -> Unit,
) : BluetoothGattCallback() {
    override fun onConnectionStateChange(
        gatt: BluetoothGatt,
        status: Int,
        newState: Int,
    ) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            onError("onConnectionStateChange status: $status")
            return
        }

        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                onConnectionStateChange(gatt, ConnectionState.Connected)
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                onConnectionStateChange(gatt, ConnectionState.Disconnected)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(
        gatt: BluetoothGatt,
        status: Int,
    ) {
        if (status != BluetoothGatt.GATT_SUCCESS) return
        val service = gatt.getService(TRICYCLETOPS_SERVICE) ?: return
        val maxDutyCharacteristic = service.getCharacteristic(MAX_DUTY_CYCLE_CHARACTERISTIC) ?: return

        gatt.readCharacteristic(maxDutyCharacteristic)
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
    ) {
        // TODO log failure
        if (status != BluetoothGatt.GATT_SUCCESS) return
        if (characteristic.uuid != MAX_DUTY_CYCLE_CHARACTERISTIC) return
        if (value.size != 4) return

        val buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
        val leftMax = buffer.getShort().toInt() and 0xFFFF
        val rightMax = buffer.getShort().toInt() and 0xFFFF
        onMaxDutyCycleChange(leftMax, rightMax)
        Log.i("BleGattClient", "Discovered max duties - Left: $leftMax, Right: $rightMax")
    }
}
