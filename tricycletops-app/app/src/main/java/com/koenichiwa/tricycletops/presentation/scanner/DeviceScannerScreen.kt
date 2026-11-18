package com.koenichiwa.tricycletops.presentation.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.koenichiwa.tricycletops.domain.model.VehicleConnectionState
import com.koenichiwa.tricycletops.presentation.scanner.components.DiscoveredDeviceCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScannerScreen(
    viewModel: DeviceScannerViewModel,
    onNavigateToControl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )

    fun checkPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            viewModel.onPermissionStatusChanged(allGranted)
        }

    LifecycleResumeEffect(Unit) {
        val granted = checkPermissions()
        viewModel.onPermissionStatusChanged(granted)
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        if (!checkPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(connectionState) {
        when (val state = connectionState) {
            is VehicleConnectionState.Connected -> {
                onNavigateToControl()
            }

            is VehicleConnectionState.Error -> {
                snackbarHostState.showSnackbar(state.message)
            }

            else -> Unit
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = "Tricycletops Scanner") },
            )
        },
        floatingActionButton = {
            if (uiState is ScannerUiState.Scanning || uiState is ScannerUiState.Idle) {
                FloatingActionButton(
                    onClick = { viewModel.toggleScan() },
                ) {
                    if (uiState is ScannerUiState.Scanning) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Scanning",
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start Scanning",
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when (val state = uiState) {
                is ScannerUiState.MissingPermissions -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Bluetooth Permissions Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        "Tricycletops needs Bluetooth scan and connect permissions to find and control your vehicle. Please grant them in system settings.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        val intent =
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                        context.startActivity(intent)
                                    },
                                ) {
                                    Text("Open Settings")
                                }
                            }
                        }
                    }
                }

                is ScannerUiState.BluetoothDisabled -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Bluetooth is Disabled",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please enable Bluetooth to discover and connect to your vehicle.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                is ScannerUiState.Connecting -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Connecting to vehicle...",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                is ScannerUiState.Idle, is ScannerUiState.Scanning -> {
                    val devices =
                        if (state is ScannerUiState.Scanning) {
                            state.devices
                        } else {
                            (state as ScannerUiState.Idle).devices
                        }
                    val isScanning = state is ScannerUiState.Scanning

                    if (devices.isEmpty()) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (isScanning) "Searching for Tricycletops vehicles..." else "Scan is paused",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Make sure your Tricycletops robotic vehicle is powered on and within range.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(devices, key = { it.address }) { vehicle ->
                                DiscoveredDeviceCard(
                                    vehicle = vehicle,
                                    onClick = { viewModel.connectToVehicle(vehicle) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
