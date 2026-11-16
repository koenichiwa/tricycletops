package com.koenichiwa.tricycletops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.koenichiwa.tricycletops.data.ble.BleVehicleGattClient
import com.koenichiwa.tricycletops.data.ble.BleVehicleScanner
import com.koenichiwa.tricycletops.data.repository.VehicleControlRepositoryImpl
import com.koenichiwa.tricycletops.presentation.navigation.AppNavHost
import com.koenichiwa.tricycletops.presentation.theme.TricycletopsTheme

class MainActivity : ComponentActivity() {
    private val repository by lazy {
        val scanner = BleVehicleScanner(applicationContext)
        val gattClient = BleVehicleGattClient(applicationContext)
        VehicleControlRepositoryImpl(
            scanner = scanner,
            gattClient = gattClient,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            TricycletopsTheme {
                val navController = rememberNavController()
                AppNavHost(
                    navController = navController,
                    repository = repository,
                )
            }
        }
    }
}
