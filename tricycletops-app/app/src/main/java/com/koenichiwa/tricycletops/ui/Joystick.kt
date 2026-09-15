package com.koenichiwa.tricycletops.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun Joystick(
    onJoystickChange: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 220,
    knobSizeDp: Int = 70,
) {
    val coroutineScope = rememberCoroutineScope()
    val joystickOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    Box(
        modifier =
            modifier
                .size(sizeDp.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = CircleShape,
                ).border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { sizeDp.dp.toPx() }
        val knobSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { knobSizeDp.dp.toPx() }
        val maxRadius = (sizePx - knobSizePx) / 2f

        Box(
            modifier =
                Modifier
                    .offset {
                        IntOffset(
                            joystickOffset.value.x.toInt(),
                            joystickOffset.value.y.toInt(),
                        )
                    }.size(knobSizeDp.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ).pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { },
                            onDragEnd = {
                                coroutineScope.launch {
                                    joystickOffset.animateTo(Offset.Zero)
                                    onJoystickChange(0f, 0f)
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    joystickOffset.animateTo(Offset.Zero)
                                    onJoystickChange(0f, 0f)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val combinedOffset = joystickOffset.value + dragAmount
                                val distance = sqrt(combinedOffset.x * combinedOffset.x + combinedOffset.y * combinedOffset.y)

                                val finalOffset =
                                    if (distance <= maxRadius) {
                                        combinedOffset
                                    } else {
                                        val angle = atan2(combinedOffset.y, combinedOffset.x)
                                        Offset(
                                            x = cos(angle) * maxRadius,
                                            y = sin(angle) * maxRadius,
                                        )
                                    }

                                coroutineScope.launch {
                                    joystickOffset.snapTo(finalOffset)
                                }

                                val relativeX = if (maxRadius > 0) finalOffset.x / maxRadius else 0f
                                val relativeY = if (maxRadius > 0) -finalOffset.y / maxRadius else 0f // Invert Y axis
                                onJoystickChange(relativeX, relativeY)
                            },
                        )
                    },
        )
    }
}
