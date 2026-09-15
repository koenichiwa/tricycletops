package com.koenichiwa.tricycletops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koenichiwa.tricycletops.ui.Joystick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedScreen(
    bleGattClient: BleGattClient,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectionState by bleGattClient.connectionState.collectAsState()
    val connectedDevice by bleGattClient.connectedDevice.collectAsState()
    val leftMaxDuty by bleGattClient.leftMaxDuty.collectAsState()
    val rightMaxDuty by bleGattClient.rightMaxDuty.collectAsState()

    val leftMaxDutyVal = leftMaxDuty ?: 1000 // Fallback if BLE hasn't read it yet
    val rightMaxDutyVal = rightMaxDuty ?: 1000 // Fallback if BLE hasn't read it yet

    var joystickX by remember { mutableFloatStateOf(0f) }
    var joystickY by remember { mutableFloatStateOf(0f) }

    // Map joystick coordinates to differential drive speeds
    // Y is forward/backward, X is turning left/right
    val leftMotorSpeed =
        remember(joystickX, joystickY) {
            (joystickY + joystickX).coerceIn(-1f, 1f)
        }
    val rightMotorSpeed =
        remember(joystickX, joystickY) {
            (joystickY - joystickX).coerceIn(-1f, 1f)
        }

    val leftMotorInt =
        remember(leftMotorSpeed, leftMaxDutyVal) {
            (leftMotorSpeed * leftMaxDutyVal).toInt().coerceIn(-leftMaxDutyVal, leftMaxDutyVal)
        }
    val rightMotorInt =
        remember(rightMotorSpeed, rightMaxDutyVal) {
            (rightMotorSpeed * rightMaxDutyVal).toInt().coerceIn(-rightMaxDutyVal, rightMaxDutyVal)
        }

    // Call the command when motor speeds change
    LaunchedEffect(leftMotorInt, rightMotorInt) {
        bleGattClient.sendDriveCommand(leftMotorInt, rightMotorInt)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = connectedDevice?.name ?: "Tricycletops Control",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDisconnect) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Scanner",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDisconnect) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Disconnect",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            when (connectionState) {
                                ConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                    ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothConnected,
                        contentDescription = "Connection Status",
                        tint =
                            when (connectionState) {
                                ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Status: ${connectionState.name}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = connectedDevice?.address ?: "No device connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (leftMaxDuty != null && rightMaxDuty != null) {
                            Text(
                                text = "Max Duty - L: $leftMaxDuty, R: $rightMaxDuty",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // Central Joystick Area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Steer Vehicle",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "Drag the circle to steer. Releasing will stop the car.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )

                Joystick(
                    onJoystickChange = { x, y ->
                        joystickX = x
                        joystickY = y
                    },
                )
            }

            // Motor Speed Telemetry Display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Telemetry / Outputs (Drive Command)",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Left Motor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.2f (%d)", leftMotorSpeed, leftMotorInt),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                    if (leftMotorInt != 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Right Motor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.2f (%d)", rightMotorSpeed, rightMotorInt),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color =
                                    if (rightMotorInt != 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
