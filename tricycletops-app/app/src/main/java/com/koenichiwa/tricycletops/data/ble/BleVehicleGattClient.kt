package com.koenichiwa.tricycletops.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import com.koenichiwa.tricycletops.domain.model.DiscoveredVehicle
import com.koenichiwa.tricycletops.domain.model.MotorDriveCommand
import com.koenichiwa.tricycletops.domain.model.VehicleConnectionState
import com.koenichiwa.tricycletops.domain.model.VehicleDutyLimits
import com.koenichiwa.tricycletops.domain.model.WheelDutyLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BleVehicleGattClient(
    private val context: Context,
) : BleGattDataSource {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter get() = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<VehicleConnectionState>(VehicleConnectionState.Disconnected)
    override val connectionState: StateFlow<VehicleConnectionState> = _connectionState.asStateFlow()

    private var activeGatt: BluetoothGatt? = null
    private var activeVehicle: DiscoveredVehicle? = null
    private var driveCharacteristic: BluetoothGattCharacteristic? = null
    private var dutyCharacteristic: BluetoothGattCharacteristic? = null

    private val gattCallback =
        object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    cleanUp()
                    _connectionState.value = VehicleConnectionState.Disconnected
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        try {
                            gatt.discoverServices()
                        } catch (_: SecurityException) {
                            cleanUp()
                            _connectionState.value =
                                VehicleConnectionState.Error("Permission denied discovering services")
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        cleanUp()
                        _connectionState.value = VehicleConnectionState.Disconnected
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    cleanUp()
                    _connectionState.value = VehicleConnectionState.Error("Service discovery failed with status $status")
                    return
                }

                val service = gatt.getService(BleGattConstants.SERVICE_UUID)
                if (service == null) {
                    cleanUp()
                    _connectionState.value = VehicleConnectionState.Error("Tricycletops GATT service not found")
                    return
                }

                driveCharacteristic = service.getCharacteristic(BleGattConstants.DRIVE_CHARACTERISTIC_UUID)
                dutyCharacteristic = service.getCharacteristic(BleGattConstants.DUTY_LIMITS_CHARACTERISTIC_UUID)

                val vehicle = activeVehicle
                if (vehicle == null) {
                    cleanUp()
                    _connectionState.value = VehicleConnectionState.Disconnected
                    return
                }

                val dutyChar = dutyCharacteristic
                if (dutyChar != null) {
                    try {
                        gatt.readCharacteristic(dutyChar)
                    } catch (_: SecurityException) {
                        _connectionState.value =
                            VehicleConnectionState.Connected(
                                vehicle = vehicle,
                                dutyLimits = VehicleDutyLimits.DEFAULT,
                            )
                    }
                } else {
                    _connectionState.value =
                        VehicleConnectionState.Connected(
                            vehicle = vehicle,
                            dutyLimits = VehicleDutyLimits.DEFAULT,
                        )
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                val vehicle = activeVehicle ?: return

                if (characteristic.uuid == BleGattConstants.DUTY_LIMITS_CHARACTERISTIC_UUID &&
                    status == BluetoothGatt.GATT_SUCCESS
                ) {
                    val limits = parseDutyLimits(value)
                    _connectionState.value =
                        VehicleConnectionState.Connected(
                            vehicle = vehicle,
                            dutyLimits = limits,
                        )
                } else {
                    _connectionState.value =
                        VehicleConnectionState.Connected(
                            vehicle = vehicle,
                            dutyLimits = VehicleDutyLimits.DEFAULT,
                        )
                }
            }
        }

    @SuppressLint("MissingPermission")
    override fun connect(vehicle: DiscoveredVehicle) {
        disconnect()

        val adapter = adapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = VehicleConnectionState.Error("Bluetooth is unavailable or disabled")
            return
        }

        val device: BluetoothDevice =
            try {
                adapter.getRemoteDevice(vehicle.address)
            } catch (e: IllegalArgumentException) {
                _connectionState.value = VehicleConnectionState.Error("Invalid MAC address: ${e.message}")
                return
            }

        activeVehicle = vehicle
        _connectionState.value = VehicleConnectionState.Connecting(vehicle.address)

        try {
            activeGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            _connectionState.value = VehicleConnectionState.Error("Permission denied connecting to ${vehicle.address}")
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        val gatt = activeGatt ?: return
        val address = activeVehicle?.address ?: ""
        if (address.isNotEmpty()) {
            _connectionState.value = VehicleConnectionState.Disconnecting(address)
        }

        try {
            gatt.disconnect()
        } catch (_: SecurityException) {
            // Ignore security exception
        } finally {
            cleanUp()
            _connectionState.value = VehicleConnectionState.Disconnected
        }
    }

    @SuppressLint("MissingPermission")
    override fun writeDriveCommand(command: MotorDriveCommand): Boolean {
        val gatt = activeGatt ?: return false
        val characteristic = driveCharacteristic ?: return false

        val payload =
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(command.leftDuty)
                putInt(command.rightDuty)
            }.array()

        val writeType =
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

        return try {
            val status = gatt.writeCharacteristic(characteristic, payload, writeType)
            status == BluetoothStatusCodes.SUCCESS
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun cleanUp() {
        try {
            activeGatt?.close()
        } catch (_: SecurityException) {
            // Ignore
        }
        activeGatt = null
        driveCharacteristic = null
        dutyCharacteristic = null
    }

    private fun parseDutyLimits(data: ByteArray): VehicleDutyLimits {
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            if (data.size >= 8) {
                val leftMin = buffer.short.toInt() and 0xFFFF
                val leftMax = buffer.short.toInt() and 0xFFFF
                val rightMin = buffer.short.toInt() and 0xFFFF
                val rightMax = buffer.short.toInt() and 0xFFFF
                VehicleDutyLimits(
                    left = WheelDutyLimits(minDuty = leftMin, maxDuty = leftMax),
                    right = WheelDutyLimits(minDuty = rightMin, maxDuty = rightMax),
                )
            } else if (data.size >= 4) {
                val leftMax = buffer.short.toInt() and 0xFFFF
                val rightMax = buffer.short.toInt() and 0xFFFF
                VehicleDutyLimits(
                    left = WheelDutyLimits(minDuty = 0, maxDuty = leftMax),
                    right = WheelDutyLimits(minDuty = 0, maxDuty = rightMax),
                )
            } else {
                VehicleDutyLimits.DEFAULT
            }
        } catch (_: Exception) {
            VehicleDutyLimits.DEFAULT
        }
    }
}
