package com.koenichiwa.tricycletops.bluetooth
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class BluetoothStatePublisher(
    val context: Context,
    val bluetoothAdapter: BluetoothAdapter,
) {
    fun observe(): Flow<Boolean> =
        callbackFlow {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) {
                            return
                        }

                        when (
                            intent.getIntExtra(
                                BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.ERROR,
                            )
                        ) {
                            BluetoothAdapter.STATE_ON -> trySend(true)
                            BluetoothAdapter.STATE_OFF -> trySend(false)
                        }
                    }
                }

            context.registerReceiver(
                receiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            )

            trySend(bluetoothAdapter.isEnabled)

            awaitClose {
                context.unregisterReceiver(receiver)
            }
        }.distinctUntilChanged()
}
