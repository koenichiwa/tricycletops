package com.koenichiwa.tricycletops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.koenichiwa.tricycletops.ui.theme.TricycleTopsTheme

class MainActivity : ComponentActivity() {
    private lateinit var bleScanner: BleScanner
    private lateinit var bleGattClient: BleGattClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bleScanner = BleScanner(applicationContext)
        bleGattClient = BleGattClient(applicationContext)

        setContent {
            TricycleTopsTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "scanner") {
                    composable("scanner") {
                        ScannerScreen(
                            onDeviceSelect = { device ->
                                bleGattClient.connect(device)
                                navController.navigate("connected")
                            },
                            bleScanner = bleScanner,
                        )
                    }
                    composable("connected") {
                        ConnectedScreen(
                            bleGattClient = bleGattClient,
                            onDisconnect = {
                                bleGattClient.disconnect()
                                navController.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bleScanner.checkBluetoothState()
    }

    override fun onStop() {
        super.onStop()
        bleScanner.stopScanning()
        bleGattClient.disconnect()
    }
}
