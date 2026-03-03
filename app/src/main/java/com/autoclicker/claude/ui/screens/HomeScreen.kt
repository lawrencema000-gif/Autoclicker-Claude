package com.autoclicker.claude.ui.screens

import android.graphics.Paint
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoclicker.claude.data.ClickMode
import com.autoclicker.claude.data.ClickPoint
import com.autoclicker.claude.data.PatternConfig
import com.autoclicker.claude.data.PatternType
import com.autoclicker.claude.data.RunState
import com.autoclicker.claude.ui.MainViewModel
import com.autoclicker.claude.util.PatternGenerator
import kotlin.math.abs

@Composable
fun HomeScreen(vm: MainViewModel) {
    val runState by vm.runState.collectAsState()
    val stats by vm.stats.collectAsState()
    val serviceConnected by vm.serviceConnected.collectAsState()
    val selectedMode by vm.selectedMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Auto Clicker",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Mode selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModeCard(
                title = "1-Point",
                description = "Single tap",
                icon = Icons.Default.AdsClick,
                selected = selectedMode == ClickMode.SINGLE_POINT,
                onClick = { vm.setSelectedMode(ClickMode.SINGLE_POINT) },
                modifier = Modifier.weight(1f)
            )
            ModeCard(
                title = "Multi-Point",
                description = "Sequence",
                icon = Icons.Default.GridView,
                selected = selectedMode == ClickMode.MULTI_POINT,
                onClick = { vm.setSelectedMode(ClickMode.MULTI_POINT) },
                modifier = Modifier.weight(1f)
            )
            ModeCard(
                title = "Pattern",
                description = "Shapes",
                icon = Icons.Default.AutoAwesome,
                selected = selectedMode == ClickMode.PATTERN_MODE,
                onClick = { vm.setSelectedMode(ClickMode.PATTERN_MODE) },
                modifier = Modifier.weight(1f)
            )
        }

        // Pattern configuration (shown when pattern mode selected)
        if (selectedMode == ClickMode.PATTERN_MODE) {
            val patternConfig by vm.patternConfig.collectAsState()
            val customPoints by vm.customPatternPoints.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Pattern Type", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)

                    // Row 1: Circle, Zigzag, Grid, Spiral
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            PatternType.CIRCLE to "Circle",
                            PatternType.ZIGZAG to "Zigzag",
                            PatternType.GRID to "Grid",
                            PatternType.SPIRAL to "Spiral"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = patternConfig.type == type,
                                onClick = { vm.setPatternType(type) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Row 2: Diamond, Random, Custom
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            PatternType.DIAMOND to "Diamond",
                            PatternType.RANDOM_AREA to "Random",
                            PatternType.CUSTOM to "Custom"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = patternConfig.type == type,
                                onClick = { vm.setPatternType(type) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Pattern preview canvas
                    PatternPreviewCanvas(
                        patternConfig = patternConfig,
                        customPoints = customPoints
                    )

                    // Parameter sliders
                    PatternParameterControls(
                        config = patternConfig,
                        onConfigChange = { vm.updatePatternConfig(it) }
                    )

                    // Custom points list
                    if (patternConfig.type == PatternType.CUSTOM) {
                        if (customPoints.isEmpty()) {
                            Text(
                                "Press START to place custom points on screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            CustomPointsList(
                                points = customPoints,
                                onReorder = { from, to -> vm.reorderCustomPatternPoint(from, to) },
                                onDelete = { id -> vm.removeCustomPatternPoint(id) },
                                onClear = { vm.clearCustomPatternPoints() }
                            )
                        }
                    }
                }
            }
        }

        // Live stats (when running)
        if (runState != RunState.IDLE) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("Taps", "${stats.totalTaps}")
                    StatItem("Time", formatElapsed(stats.elapsedMs))
                    StatItem("Loop", "${stats.currentLoop}")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Start/Stop button
        val btnColor by animateColorAsState(
            targetValue = if (runState != RunState.IDLE)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary,
            label = "btnColor"
        )

        Button(
            onClick = {
                if (runState != RunState.IDLE) vm.stopExecution() else vm.quickStart()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = btnColor),
            shape = RoundedCornerShape(20.dp),
            enabled = serviceConnected
        ) {
            Icon(
                imageVector = if (runState != RunState.IDLE) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (runState != RunState.IDLE) "STOP" else "START",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (!serviceConnected) {
            Text(
                text = "Accessibility Service not enabled. Go to Settings to enable it.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PatternPreviewCanvas(
    patternConfig: PatternConfig,
    customPoints: List<ClickPoint>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = primary.toArgb()

    // Generate preview points
    val previewOffsets = remember(patternConfig, customPoints) {
        if (patternConfig.type == PatternType.CUSTOM) {
            customPoints.map { Offset(it.x, it.y) }
        } else {
            val previewConfig = patternConfig.copy(centerX = 0f, centerY = 0f)
            val generated = PatternGenerator.generate(previewConfig, 100L, 50L)
            generated.map { Offset(it.x, it.y) }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
    ) {
        val cx = size.width / 2
        val cy = size.height / 2

        if (previewOffsets.isEmpty()) {
            return@Canvas
        }

        // Scale to fit
        val maxAbsX = previewOffsets.maxOf { abs(it.x) }.coerceAtLeast(1f)
        val maxAbsY = previewOffsets.maxOf { abs(it.y) }.coerceAtLeast(1f)
        val scaleX = (size.width * 0.4f) / maxAbsX
        val scaleY = (size.height * 0.4f) / maxAbsY
        val scale = minOf(scaleX, scaleY)

        val scaledPoints = previewOffsets.map { Offset(cx + it.x * scale, cy + it.y * scale) }

        // Draw connecting lines (execution order)
        for (i in 0 until scaledPoints.size - 1) {
            drawLine(
                color = primary.copy(alpha = 0.3f),
                start = scaledPoints[i],
                end = scaledPoints[i + 1],
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
            )
        }

        // Draw points
        scaledPoints.forEachIndexed { idx, pt ->
            drawCircle(color = primary.copy(alpha = 0.2f), radius = 14f, center = pt)
            drawCircle(color = primary, radius = 6f, center = pt)
        }

        // Draw order numbers
        val paint = Paint().apply {
            color = textColor
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        scaledPoints.forEachIndexed { idx, pt ->
            drawContext.canvas.nativeCanvas.drawText(
                "${idx + 1}",
                pt.x,
                pt.y - 18f,
                paint
            )
        }
    }
}

@Composable
private fun PatternParameterControls(
    config: PatternConfig,
    onConfigChange: (PatternConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (config.type) {
            PatternType.CIRCLE -> {
                SliderRow("Points", config.pointCount.toFloat(), 3f..24f, 21) {
                    onConfigChange(config.copy(pointCount = it.toInt()))
                }
                SliderRow("Radius", config.radius, 30f..300f) {
                    onConfigChange(config.copy(radius = it))
                }
            }
            PatternType.ZIGZAG -> {
                SliderRow("Points", config.pointCount.toFloat(), 3f..20f, 17) {
                    onConfigChange(config.copy(pointCount = it.toInt()))
                }
                SliderRow("Width", config.areaWidth, 50f..500f) {
                    onConfigChange(config.copy(areaWidth = it))
                }
                SliderRow("Height", config.areaHeight, 50f..500f) {
                    onConfigChange(config.copy(areaHeight = it))
                }
            }
            PatternType.GRID -> {
                SliderRow("Rows", config.gridRows.toFloat(), 2f..8f, 6) {
                    onConfigChange(config.copy(gridRows = it.toInt()))
                }
                SliderRow("Columns", config.gridCols.toFloat(), 2f..8f, 6) {
                    onConfigChange(config.copy(gridCols = it.toInt()))
                }
                SliderRow("Spacing", config.gridSpacing, 20f..150f) {
                    onConfigChange(config.copy(gridSpacing = it))
                }
            }
            PatternType.SPIRAL -> {
                SliderRow("Points", config.pointCount.toFloat(), 4f..30f, 26) {
                    onConfigChange(config.copy(pointCount = it.toInt()))
                }
                SliderRow("Radius", config.radius, 30f..300f) {
                    onConfigChange(config.copy(radius = it))
                }
                SliderRow("Turns", config.spiralRevolutions, 1f..5f, 8) {
                    onConfigChange(config.copy(spiralRevolutions = it))
                }
            }
            PatternType.DIAMOND -> {
                SliderRow("Points", config.pointCount.toFloat(), 4f..24f, 5) {
                    onConfigChange(config.copy(pointCount = (it.toInt() / 4) * 4))
                }
                SliderRow("Radius", config.radius, 30f..300f) {
                    onConfigChange(config.copy(radius = it))
                }
            }
            PatternType.RANDOM_AREA -> {
                SliderRow("Points", config.pointCount.toFloat(), 3f..20f, 17) {
                    onConfigChange(config.copy(pointCount = it.toInt()))
                }
                SliderRow("Width", config.areaWidth, 50f..500f) {
                    onConfigChange(config.copy(areaWidth = it))
                }
                SliderRow("Height", config.areaHeight, 50f..500f) {
                    onConfigChange(config.copy(areaHeight = it))
                }
            }
            PatternType.CUSTOM -> {
                // No sliders for custom - points are placed manually
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (value == value.toInt().toFloat()) "${value.toInt()}" else "%.1f".format(value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun CustomPointsList(
    points: List<ClickPoint>,
    onReorder: (Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Custom Points (${points.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Clear All", style = MaterialTheme.typography.labelSmall)
            }
        }
        points.forEachIndexed { index, point ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "#${index + 1}",
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "(${point.x.toInt()}, ${point.y.toInt()})",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = { if (index > 0) onReorder(index, index - 1) },
                    modifier = Modifier.size(28.dp),
                    enabled = index > 0
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, "Move up", modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { if (index < points.size - 1) onReorder(index, index + 1) },
                    modifier = Modifier.size(28.dp),
                    enabled = index < points.size - 1
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Move down", modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { onDelete(point.id) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close, "Remove",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "modeCardBg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "modeCardIcon"
    )

    Card(
        onClick = onClick,
        modifier = modifier.height(120.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp),
        border = if (!selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format("%d:%02d", s / 60, s % 60)
}
