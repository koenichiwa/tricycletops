package com.koenichiwa.tricycletops

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koenichiwa.tricycletops.ui.theme.TricycleTopsTheme

class MainActivity : ComponentActivity() {
    private lateinit var bleScannerManager: BleScannerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bleScannerManager = BleScannerManager(applicationContext)

        setContent {
            TricycleTopsTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "scanner") {
                    composable("scanner") {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            topBar = {
                                @OptIn(ExperimentalMaterial3Api::class)
                                TopAppBar(
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.DirectionsCar,
                                                contentDescription = "App Icon",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(
                                                text = "TricycleTops",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        ) { innerPadding ->
                            BleScannerScreen(
                                bleScannerManager = bleScannerManager,
                                onDeviceSelected = { device ->
                                    bleScannerManager.connect(device)
                                    navController.navigate("joystick")
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                    composable("joystick") {
                        JoystickScreen(
                            bleScannerManager = bleScannerManager,
                            onDisconnect = {
                                bleScannerManager.disconnect()
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bleScannerManager.checkBluetoothState()
    }

    override fun onStop() {
        super.onStop()
        bleScannerManager.stopScanning()
    }
}

@Composable
fun BleScannerScreen(
    bleScannerManager: BleScannerManager,
    onDeviceSelected: (BleDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isScanning by bleScannerManager.isScanning.collectAsState()
    val devices by bleScannerManager.discoveredDevices.collectAsState()
    val isBluetoothEnabled by bleScannerManager.isBluetoothEnabled.collectAsState()
    val errorMessage by bleScannerManager.errorMessage.collectAsState()

    var hasPermissionsState by remember { mutableStateOf(bleScannerManager.hasPermissions()) }

    // Launcher for requesting runtime Bluetooth permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        hasPermissionsState = allGranted
        if (allGranted) {
            bleScannerManager.checkBluetoothState()
            bleScannerManager.startScanning()
        }
    }

    LaunchedEffect(Unit) {
        bleScannerManager.checkBluetoothState()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App intro
        Text(
            text = "BLE Vehicle Scanner",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Scan and find the TricycleTops hardware peripheral to control your robot vehicle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    errorMessage != null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    isScanning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            ),
            border = BorderStroke(
                width = 1.dp,
                color = when {
                    errorMessage != null -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    isScanning -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else -> Color.Transparent
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isScanning) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp)
                    ) {
                        ScanPulseAnimation()
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Scanning",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Icon(
                        imageVector = if (errorMessage != null) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = "Status",
                        tint = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = when {
                            errorMessage != null -> "Status: Issue Detected"
                            isScanning -> "Status: Scanning..."
                            else -> "Status: Ready to Scan"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Text(
                        text = errorMessage
                            ?: if (isScanning) "Searching for nearby Bluetooth devices..." else "Press Scan to begin searching.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Scan controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (!hasPermissionsState) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        )
                    } else {
                        bleScannerManager.checkBluetoothState()
                        if (isScanning) {
                            bleScannerManager.stopScanning()
                        } else {
                            bleScannerManager.startScanning()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Scan Icon",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = if (!hasPermissionsState) "Grant Permissions" else if (isScanning) "Stop Scan" else "Start Scan"
                )
            }

            OutlinedButton(
                onClick = { bleScannerManager.clearDevices() },
                enabled = devices.isNotEmpty() && !isScanning
            ) {
                Text("Clear")
            }
        }

        // List Header / Device count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Discovered Devices",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "${devices.size} found",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Devices List
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = "No Devices",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No devices discovered yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices, key = { it.address }) { device ->
                    DeviceItemCard(
                        device = device,
                        onClick = { onDeviceSelected(device) }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceItemCard(
    device: BleDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (device.isTricycletops) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (device.isTricycletops) 2.dp else 1.dp,
            color = if (device.isTricycletops) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = device.name ?: "Unknown Device",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (device.isTricycletops) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    if (device.isTricycletops) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("TricycleTops Vehicle", fontSize = 10.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                labelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Address: ${device.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Signal strength (RSSI)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        device.rssi > -60 -> Color(0xFF4CAF50) // Strong signal (Green)
                        device.rssi > -80 -> Color(0xFFFF9800) // Medium signal (Orange)
                        else -> Color(0xFFF44336) // Weak signal (Red)
                    }
                )
                Text(
                    text = "Signal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                alpha = alpha
            )
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), shape = CircleShape)
    )
}
