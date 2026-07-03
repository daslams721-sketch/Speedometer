package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TripPoint
import kotlin.math.cos

@Composable
fun RouteCanvas(
    points: List<TripPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No route points recorded yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        return
    }

    // Determine coordinate bounds
    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLng = points.minOf { it.longitude }
    val maxLng = points.maxOf { it.longitude }

    val avgLatRad = Math.toRadians((minLat + maxLat) / 2.0)
    // Adjust longitude span based on latitude to prevent distortion
    val latSpan = maxLat - minLat
    val lngSpan = (maxLng - minLng) * cos(avgLatRad)

    val maxSpan = maxOf(latSpan, lngSpan, 0.0001)

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val pathStartColor = Color(0xFF4CAF50) // Neon Green
    val pathMidColor = Color(0xFFFF9800)   // Amber
    val pathEndColor = Color(0xFFF44336)   // Hot Pink/Red

    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            val width = size.width
            val height = size.height

            // Draw a subtle coordinate grid
            val numGridLines = 6
            for (i in 1 until numGridLines) {
                val x = (width / numGridLines) * i
                val y = (height / numGridLines) * i
                
                // Vertical gridline
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // Horizontal gridline
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            // Map GPS points to local canvas coordinates
            val canvasPoints = points.map { point ->
                // Longitude map to X
                val xOffset = (point.longitude - minLng) * cos(avgLatRad)
                val xRatio = if (maxSpan > 0) xOffset / maxSpan else 0.5
                val xPx = (xRatio * width).toFloat()

                // Latitude map to Y (Y is inverted in screen coordinates)
                val yOffset = maxLat - point.latitude
                val yRatio = if (maxSpan > 0) yOffset / maxSpan else 0.5
                val yPx = (yRatio * height).toFloat()

                Offset(xPx, yPx)
            }

            // Draw the actual route line
            if (canvasPoints.size > 1) {
                val routePath = Path().apply {
                    moveTo(canvasPoints.first().x, canvasPoints.first().y)
                    for (i in 1 until canvasPoints.size) {
                        lineTo(canvasPoints[i].x, canvasPoints[i].y)
                    }
                }

                // Draw a beautiful gradient track line
                drawPath(
                    path = routePath,
                    brush = Brush.linearGradient(
                        colors = listOf(pathStartColor, pathMidColor, pathEndColor),
                        start = canvasPoints.first(),
                        end = canvasPoints.last()
                    ),
                    style = Stroke(
                        width = 8f,
                        pathEffect = PathEffect.cornerPathEffect(15f)
                    )
                )
            }

            // Draw Start Marker
            val startOffset = canvasPoints.first()
            drawCircle(
                color = Color.White,
                radius = 12f,
                center = startOffset
            )
            drawCircle(
                color = pathStartColor,
                radius = 8f,
                center = startOffset
            )

            // Draw End Marker
            if (canvasPoints.size > 1) {
                val endOffset = canvasPoints.last()
                drawCircle(
                    color = Color.White,
                    radius = 12f,
                    center = endOffset
                )
                drawCircle(
                    color = pathEndColor,
                    radius = 8f,
                    center = endOffset
                )
            }
        }
    }
}
