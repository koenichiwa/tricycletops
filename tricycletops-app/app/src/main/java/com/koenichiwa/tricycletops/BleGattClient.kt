package com.koenichiwa.tricycletops

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val gattCallback =
        object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _connectionState.value = ConnectionState.Disconnected
                    _connectedDevice.value = null
                    _leftMaxDuty.value = null
                    _rightMaxDuty.value = null
                    _errorMessage.value = "GATT connection failed (status: $status)"
                    gatt.close()
                    bluetoothGatt = null
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _connectionState.value = ConnectionState.Connected
                        _leftMaxDuty.value = null
                        _rightMaxDuty.value = null
                        gatt.discoverServices()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        _connectionState.value = ConnectionState.Disconnected
                        _connectedDevice.value = null
                        _leftMaxDuty.value = null
                        _rightMaxDuty.value = null
                        gatt.close()
                        bluetoothGatt = null
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _errorMessage.value = "Failed to discover GATT services: $status"
                    return
                }

                val service = gatt.getService(TRICYCLETOPS_SERVICE)

                if (service == null) {
                    _errorMessage.value = "Tricycletops service not found"
                    return
                }
                val driveCharacteristic =
                    service.getCharacteristic(
                        DRIVE_COMMAND_CHARACTERISTIC,
                    )
                if (driveCharacteristic == null) {
                    _errorMessage.value = "Tricycletops drive characteristic not found"
                }

                val maxDutyCharacteristic =
                    service.getCharacteristic(
                        MAX_DUTY_CYCLE_CHARACTERISTIC,
                    )
                if (maxDutyCharacteristic != null) {
                    gatt.readCharacteristic(maxDutyCharacteristic)
                } else {
                    Log.e("BleGattClient", "Tricycletops max_duty characteristic not found")
                }
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
                _leftMaxDuty.value = leftMax
                _rightMaxDuty.value = rightMax
                Log.i("BleGattClient", "Discovered max duties - Left: $leftMax, Right: $rightMax")
            }
        }

    @SuppressLint("MissingPermission")
    fun connect(device: BleDevice) {
        if (!context.hasBlePermissions()) {
            _errorMessage.value = "Permissions missing for BLE connection"
            return
        }

        _connectedDevice.value = device
        _connectionState.value = ConnectionState.Connecting

        try {
            bluetoothGatt = device.rawDevice.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            _errorMessage.value = "Connection failed: ${e.localizedMessage}"
            _connectionState.value = ConnectionState.Disconnected
            _connectedDevice.value = null
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (!context.hasBlePermissions()) return

        _connectionState.value = ConnectionState.Disconnecting
        bluetoothGatt?.disconnect()
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

        try {
            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            )
        } catch (e: Exception) {
            Log.e("BleGattClient", "Failed to write characteristic: ${e.localizedMessage}")
        }
    }
}
