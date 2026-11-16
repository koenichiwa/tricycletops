package com.koenichiwa.tricycletops.presentation.control.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.koenichiwa.tricycletops.domain.model.JoystickVector
import kotlin.math.hypot

@Composable
fun JoystickComponent(
    onValueChange: (JoystickVector) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    knobRadiusRatio: Float = 0.25f,
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }

    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    val knobColor = MaterialTheme.colorScheme.primary

    Box(
        modifier =
            modifier
                .size(size)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                            val maxRadius = size.toPx() / 2f
                            val delta = offset - center
                            val distance = hypot(delta.x, delta.y)
                            val constrainedOffset =
                                if (distance > maxRadius) {
                                    delta * (maxRadius / distance)
                                } else {
                                    delta
                                }
                            thumbOffset = constrainedOffset
                            val normalizedX = (constrainedOffset.x / maxRadius).coerceIn(-1f, 1f)
                            val normalizedY = (-constrainedOffset.y / maxRadius).coerceIn(-1f, 1f)
                            onValueChange(JoystickVector(x = normalizedX, y = normalizedY))
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val maxRadius = size.toPx() / 2f
                            val newOffset = thumbOffset + dragAmount
                            val distance = hypot(newOffset.x, newOffset.y)
                            val constrainedOffset =
                                if (distance > maxRadius) {
                                    newOffset * (maxRadius / distance)
                                } else {
                                    newOffset
                                }
                            thumbOffset = constrainedOffset
                            val normalizedX = (constrainedOffset.x / maxRadius).coerceIn(-1f, 1f)
                            val normalizedY = (-constrainedOffset.y / maxRadius).coerceIn(-1f, 1f)
                            onValueChange(JoystickVector(x = normalizedX, y = normalizedY))
                        },
                        onDragEnd = {
                            thumbOffset = Offset.Zero
                            onValueChange(JoystickVector.ZERO)
                        },
                        onDragCancel = {
                            thumbOffset = Offset.Zero
                            onValueChange(JoystickVector.ZERO)
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val baseRadius = size.toPx() / 2f
            val knobRadius = baseRadius * knobRadiusRatio

            drawCircle(
                color = baseColor,
                radius = baseRadius,
                center = center,
            )
            drawCircle(
                color = borderColor,
                radius = baseRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )

            drawLine(
                color = borderColor.copy(alpha = 0.3f),
                start = Offset(center.x, center.y - baseRadius),
                end = Offset(center.x, center.y + baseRadius),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = borderColor.copy(alpha = 0.3f),
                start = Offset(center.x - baseRadius, center.y),
                end = Offset(center.x + baseRadius, center.y),
                strokeWidth = 1.dp.toPx(),
            )

            val knobCenter = center + thumbOffset
            drawCircle(
                color = knobColor.copy(alpha = 0.3f),
                radius = knobRadius + 4.dp.toPx(),
                center = knobCenter,
            )
            drawCircle(
                color = knobColor,
                radius = knobRadius,
                center = knobCenter,
            )
        }
    }
}
