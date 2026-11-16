package com.koenichiwa.tricycletops.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.koenichiwa.tricycletops.domain.repository.VehicleControlRepository
import com.koenichiwa.tricycletops.domain.usecase.ConnectVehicleUseCase
import com.koenichiwa.tricycletops.domain.usecase.DiscoverVehiclesUseCase
import com.koenichiwa.tricycletops.domain.usecase.DriveVehicleUseCase
import com.koenichiwa.tricycletops.presentation.control.VehicleControlScreen
import com.koenichiwa.tricycletops.presentation.control.VehicleControlViewModel
import com.koenichiwa.tricycletops.presentation.scanner.DeviceScannerScreen
import com.koenichiwa.tricycletops.presentation.scanner.DeviceScannerViewModel

@Composable
fun AppNavHost(
    navController: NavHostController,
    repository: VehicleControlRepository,
    modifier: Modifier = Modifier,
    startDestination: String = NavRoute.Scanner.route,
) {
    val discoverVehiclesUseCase = DiscoverVehiclesUseCase(repository)
    val connectVehicleUseCase = ConnectVehicleUseCase(repository)
    val driveVehicleUseCase = DriveVehicleUseCase(repository)

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(NavRoute.Scanner.route) {
            val scannerViewModel: DeviceScannerViewModel =
                viewModel(
                    factory =
                        DeviceScannerViewModel.provideFactory(
                            discoverVehiclesUseCase = discoverVehiclesUseCase,
                            connectVehicleUseCase = connectVehicleUseCase,
                        ),
                )
            DeviceScannerScreen(
                viewModel = scannerViewModel,
                onNavigateToControl = {
                    navController.navigate(NavRoute.Control.route) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(NavRoute.Control.route) {
            val controlViewModel: VehicleControlViewModel =
                viewModel(
                    factory =
                        VehicleControlViewModel.provideFactory(
                            connectVehicleUseCase = connectVehicleUseCase,
                            driveVehicleUseCase = driveVehicleUseCase,
                        ),
                )
            VehicleControlScreen(
                viewModel = controlViewModel,
                onNavigateBack = {
                    navController.popBackStack(NavRoute.Scanner.route, inclusive = false)
                },
            )
        }
    }
}
