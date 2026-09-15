package com.koenichiwa.tricycletops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.koenichiwa.tricycletops.ui.theme.TricycleTopsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TricycleTopsTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "scanner") {
                    composable("scanner") {
                        val scannerViewModel: ScannerViewModel = viewModel()
                        ScannerScreen(
                            onDeviceSelect = { device ->
                                navController.navigate("connected/${device.address}/${device.name ?: "Unknown"}")
                            },
                            viewModel = scannerViewModel,
                        )
                    }
                    composable(
                        route = "connected/{address}/{name}",
                        arguments = listOf(
                            navArgument("address") { type = NavType.StringType },
                            navArgument("name") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val address = backStackEntry.arguments?.getString("address") ?: ""
                        val name = backStackEntry.arguments?.getString("name")?.let {
                            if (it == "Unknown") null else it
                        }

                        val connectedViewModel: ConnectedViewModel = viewModel()

                        ConnectedScreen(
                            viewModel = connectedViewModel,
                            deviceAddress = address,
                            deviceName = name,
                            onDisconnect = {
                                connectedViewModel.disconnect()
                                navController.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }
}
