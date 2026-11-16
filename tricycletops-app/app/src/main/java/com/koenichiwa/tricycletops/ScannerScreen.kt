package com.koenichiwa.tricycletops

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.koenichiwa.tricycletops.bluetooth.BleState
import com.koenichiwa.tricycletops.domain.Device
import com.koenichiwa.tricycletops.ui.TricycletopsScaffold

@Composable
fun ScannerScreen(
    onDeviceSelect: (Device) -> Unit,
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier,
) {
    val devices by viewModel.deviceState.collectAsState()
    val bleState by viewModel.bleState.collectAsState()

    val context = LocalContext.current
//    val bluetoothManager = remember(context) {
//        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
//    }
//    val bluetoothAdapter = remember(bluetoothManager) {
//        bluetoothManager?.adapter
//    }

    // Map domain Devices to UI BleDevices
//    val uiDevices = remember(devices, bluetoothAdapter) {
//        devices.mapNotNull { domainDevice ->
//            bluetoothAdapter?.let { adapter ->
//                try {
//                    @SuppressLint("MissingPermission")
//                    val remoteDevice = adapter.getRemoteDevice(domainDevice.id)
//                    @SuppressLint("MissingPermission")
//                    val name = remoteDevice.name
//                    BleDevice(
//                        address = domainDevice.id,
//                        name = name,
//                        rssi = -65, // placeholder RSSI since domain model only exposes id
//                        rawDevice = remoteDevice,
//                    )
//                } catch (e: Exception) {
//                    null
//                }
//            }
//        }
//    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.updatePermissions()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Launcher for requesting runtime Bluetooth permissions
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { _ ->
            viewModel.updatePermissions()
        }

    TricycletopsScaffold(
        modifier = modifier,
        titleText = "TricycleTops",
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScannerIntroSection()

            ScannerStatusCard(
                bleState = bleState,
            )

            ScanControls(
                bleState = bleState,
                onGrantPermissions = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        ),
                    )
                },
            )

            DiscoveredDevicesHeader(deviceCount = devices.size)

            if (devices.isEmpty()) {
                EmptyDevicesView(modifier = Modifier.weight(1f))
            } else {
                val sortedDevices = devices.sortedBy { it.id }
                LazyColumn(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {

                    items(devices.size, key = { idx -> sortedDevices[idx] }) {
                        val device = sortedDevices[it]
                        DeviceItemCard(
                            device = device,
                            onClick = { onDeviceSelect(device) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerIntroSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "BLE Vehicle Scanner",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = "Scan and find the TricycleTops hardware peripheral to control your robot vehicle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun ScannerStatusCard(
    bleState: BleState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when (bleState) {
                        is BleState.Error, BleState.Off, BleState.NoPermission -> {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        }

                        BleState.Scanning -> {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        }

                        BleState.Ready -> {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                    },
            ),
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    when (bleState) {
                        is BleState.Error, BleState.Off, BleState.NoPermission -> {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        }

                        BleState.Scanning -> {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        }

                        BleState.Ready -> {
                            Color.Transparent
                        }
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (bleState) {
                is BleState.Error, BleState.Off, BleState.NoPermission -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Status",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp),
                    )
                }

                BleState.Scanning -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp),
                    ) {
                        ScanPulseAnimation()
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Scanning",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                BleState.Ready -> {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Status",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                val headerText =
                    when (bleState) {
                        is BleState.Error, BleState.Off, BleState.NoPermission -> "Status: Issue detected"
                        BleState.Scanning -> "Status: Scanning..."
                        BleState.Ready -> "Status: Ready to Scan..."
                    }
                val detailText =
                    when (bleState) {
                        is BleState.Error -> bleState.cause.localizedMessage ?: "Unknown error occurred"
                        BleState.Off -> "Bluetooth is disabled. Please enable it."
                        BleState.NoPermission -> "Bluetooth permissions are required."
                        BleState.Scanning -> "Searching for nearby TricycleTops vehicles..."
                        BleState.Ready -> "Ready to scan for vehicles"
                    }
                val color =
                    when (bleState) {
                        is BleState.Error, BleState.Off, BleState.NoPermission -> MaterialTheme.colorScheme.error
                        BleState.Scanning, BleState.Ready -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                Text(
                    text = headerText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )

                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun ScanControls(
    bleState: BleState,
    onGrantPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = bleState is BleState.NoPermission
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = {
                if (bleState is BleState.NoPermission) {
                    onGrantPermissions()
                }
            },
            modifier = Modifier.weight(1f),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (bleState == BleState.Scanning) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else if (bleState is BleState.NoPermission) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                ),
            enabled = enabled,
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Scan Icon",
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text =
                    when (bleState) {
                        is BleState.Error -> "Scan Error"
                        BleState.Off -> "Bluetooth Disabled"
                        BleState.NoPermission -> "Grant Permissions"
                        BleState.Ready -> "Ready"
                        BleState.Scanning -> "Scanning Active"
                    },
            )
        }
    }
}

@Composable
private fun DiscoveredDevicesHeader(
    deviceCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Discovered Devices",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "$deviceCount found",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyDevicesView(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                ).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "No Devices",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No devices discovered yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DeviceItemCard(
    device: Device,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onClick() },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            ),
        border =
            BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = device.id,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    SuggestionChip(
                        onClick = {},
                        label = { Text("TricycleTops Vehicle", fontSize = 10.sp) },
                        colors =
                            SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                labelColor = MaterialTheme.colorScheme.primary,
                            ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Address: ${device.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Signal strength (RSSI)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color =
                        when {
                            device.rssi > -60 -> Color(0xFF4CAF50)

                            // Strong signal (Green)
                            device.rssi > -80 -> Color(0xFFFF9800)

                            // Medium signal (Orange)
                            else -> Color(0xFFF44336) // Weak signal (Red)
                        },
                )
                Text(
                    text = "Signal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ScanPulseAnimation() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.4f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1200, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "alpha",
    )

    Box(
        modifier =
            Modifier
                .size(48.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = alpha,
                ).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), shape = CircleShape),
    )
}
