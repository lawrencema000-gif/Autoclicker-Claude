package com.autoclicker.claude.ui.screens

import android.graphics.Paint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoclicker.claude.ads.BannerAd
import com.autoclicker.claude.data.*
import com.autoclicker.claude.ui.MainViewModel
import com.autoclicker.claude.util.PatternGenerator
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun HomeScreen(vm: MainViewModel) {
    val runState by vm.runState.collectAsState()
    val stats by vm.stats.collectAsState()
    val serviceConnected by vm.serviceConnected.collectAsState()
    val selectedMode by vm.selectedMode.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Text(
            text = "Auto Clicker",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Service status warning (prominent, at top)
        if (!serviceConnected) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Allow Auto Clicker to tap your screen — Settings → Accessibility",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // First-launch tutorial tip (dismissible)
        val homeTipSeen by vm.homeTipSeen.collectAsState()
        if (!homeTipSeen && serviceConnected) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Quick start",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Tap START, then tap the spot on screen you want to click. Auto Clicker will repeat it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    IconButton(onClick = { vm.dismissHomeTip() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Dismiss tip", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Mode selection — compact chips
        Text("Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeChip("Single Tap", Icons.Default.AdsClick, selectedMode == ClickMode.SINGLE_POINT, { vm.setSelectedMode(ClickMode.SINGLE_POINT) }, Modifier.weight(1f))
            ModeChip("Multi Tap", Icons.Default.GridView, selectedMode == ClickMode.MULTI_POINT, { vm.setSelectedMode(ClickMode.MULTI_POINT) }, Modifier.weight(1f))
            ModeChip("Pattern", Icons.Default.AutoAwesome, selectedMode == ClickMode.PATTERN_MODE, { vm.setSelectedMode(ClickMode.PATTERN_MODE) }, Modifier.weight(1f))
            ModeChip("Record", Icons.Default.FiberManualRecord, selectedMode == ClickMode.RECORD_MODE, { vm.setSelectedMode(ClickMode.RECORD_MODE) }, Modifier.weight(1f))
        }

        // Mode description
        val modeHint = when (selectedMode) {
            ClickMode.SINGLE_POINT -> "Tap START, then pick one point on screen. Clicks repeat at that spot."
            ClickMode.MULTI_POINT -> "Tap START, pick multiple points, press DONE. Clicks cycle through each point."
            ClickMode.PATTERN_MODE -> "Choose a pattern shape below. Clicks follow the pattern automatically."
            ClickMode.RECORD_MODE -> "Tap START, then perform taps and swipes. Press DONE to save and replay."
        }
        Text(modeHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Pattern configuration (only in pattern mode)
        if (selectedMode == ClickMode.PATTERN_MODE) {
            val patternConfig by vm.patternConfig.collectAsState()
            val customPoints by vm.customPatternPoints.collectAsState()
            PatternSection(vm, patternConfig, customPoints)
        }

        // Live stats (when running)
        AnimatedVisibility(visible = runState != RunState.IDLE) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("Taps", "${stats.totalTaps}")
                    StatItem("Time", formatElapsed(stats.elapsedMs))
                    StatItem("Loop", "${stats.currentLoop}")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // START / STOP button with pulse animation
        StartStopButton(
            isRunning = runState != RunState.IDLE,
            enabled = serviceConnected,
            onDisabledTap = {
                triggerHapticError(context)
                android.widget.Toast.makeText(
                    context,
                    "Allow Auto Clicker to tap your screen in Settings → Accessibility first",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            },
            onClick = {
                triggerHaptic(context)
                if (runState != RunState.IDLE) vm.stopExecution() else vm.quickStart()
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Banner ad
        BannerAd(modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StartStopButton(
    isRunning: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDisabledTap: () -> Unit = {}
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = if (!isRunning && enabled) 1.03f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseScale"
    )

    val btnColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            isRunning -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        label = "btnColor"
    )

    // Keep button always clickable; handle disabled state via onDisabledTap
    Button(
        onClick = { if (enabled) onClick() else onDisabledTap() },
        modifier = Modifier.fillMaxWidth().height(64.dp).scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = btnColor,
            contentColor = if (enabled) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(20.dp),
        enabled = true
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isRunning) "STOP" else "START",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        label = "chipBg"
    )
    Card(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(14.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PatternSection(vm: MainViewModel, patternConfig: PatternConfig, customPoints: List<ClickPoint>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Pattern Type", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)

            // All patterns in 2 rows
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(PatternType.CIRCLE to "Circle", PatternType.ZIGZAG to "Zigzag", PatternType.GRID to "Grid", PatternType.SPIRAL to "Spiral").forEach { (type, label) ->
                    FilterChip(selected = patternConfig.type == type, onClick = { vm.setPatternType(type) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(PatternType.DIAMOND to "Diamond", PatternType.RANDOM_AREA to "Random", PatternType.CUSTOM to "Custom").forEach { (type, label) ->
                    FilterChip(selected = patternConfig.type == type, onClick = { vm.setPatternType(type) }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
                }
            }

            PatternPreviewCanvas(patternConfig, customPoints)
            PatternParameterControls(patternConfig) { vm.updatePatternConfig(it) }

            if (patternConfig.type == PatternType.CUSTOM) {
                if (customPoints.isEmpty()) {
                    Text("Press START to place custom points on screen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    CustomPointsList(customPoints, { from, to -> vm.reorderCustomPatternPoint(from, to) }, { id -> vm.removeCustomPatternPoint(id) }, { vm.clearCustomPatternPoints() })
                }
            }
        }
    }
}

@Composable
private fun PatternPreviewCanvas(patternConfig: PatternConfig, customPoints: List<ClickPoint>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val textColor = primary.toArgb()
    val previewOffsets = remember(patternConfig, customPoints) {
        if (patternConfig.type == PatternType.CUSTOM) customPoints.map { Offset(it.x, it.y) }
        else PatternGenerator.generate(patternConfig.copy(centerX = 0f, centerY = 0f), 100L, 50L).map { Offset(it.x, it.y) }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))) {
        if (previewOffsets.isEmpty()) return@Canvas
        val cx = size.width / 2; val cy = size.height / 2
        val maxAbsX = previewOffsets.maxOf { abs(it.x) }.coerceAtLeast(1f)
        val maxAbsY = previewOffsets.maxOf { abs(it.y) }.coerceAtLeast(1f)
        val scale = minOf((size.width * 0.4f) / maxAbsX, (size.height * 0.4f) / maxAbsY)
        val scaledPoints = previewOffsets.map { Offset(cx + it.x * scale, cy + it.y * scale) }

        for (i in 0 until scaledPoints.size - 1) {
            drawLine(primary.copy(alpha = 0.3f), scaledPoints[i], scaledPoints[i + 1], 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)))
        }
        scaledPoints.forEachIndexed { _, pt ->
            drawCircle(primary.copy(alpha = 0.2f), 12f, pt)
            drawCircle(primary, 5f, pt)
        }
        val paint = Paint().apply { color = textColor; textSize = 18f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        scaledPoints.forEachIndexed { idx, pt -> drawContext.canvas.nativeCanvas.drawText("${idx + 1}", pt.x, pt.y - 16f, paint) }
    }
}

@Composable
private fun PatternParameterControls(config: PatternConfig, onConfigChange: (PatternConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (config.type) {
            PatternType.CIRCLE -> { SliderRow("Points", config.pointCount.toFloat(), 3f..24f, 21) { onConfigChange(config.copy(pointCount = it.toInt())) }; SliderRow("Radius", config.radius, 30f..300f) { onConfigChange(config.copy(radius = it)) } }
            PatternType.ZIGZAG -> { SliderRow("Points", config.pointCount.toFloat(), 3f..20f, 17) { onConfigChange(config.copy(pointCount = it.toInt())) }; SliderRow("Width", config.areaWidth, 50f..500f) { onConfigChange(config.copy(areaWidth = it)) }; SliderRow("Height", config.areaHeight, 50f..500f) { onConfigChange(config.copy(areaHeight = it)) } }
            PatternType.GRID -> { SliderRow("Rows", config.gridRows.toFloat(), 2f..8f, 6) { onConfigChange(config.copy(gridRows = it.toInt())) }; SliderRow("Cols", config.gridCols.toFloat(), 2f..8f, 6) { onConfigChange(config.copy(gridCols = it.toInt())) }; SliderRow("Space", config.gridSpacing, 20f..150f) { onConfigChange(config.copy(gridSpacing = it)) } }
            PatternType.SPIRAL -> { SliderRow("Points", config.pointCount.toFloat(), 4f..30f, 26) { onConfigChange(config.copy(pointCount = it.toInt())) }; SliderRow("Radius", config.radius, 30f..300f) { onConfigChange(config.copy(radius = it)) }; SliderRow("Turns", config.spiralRevolutions, 1f..5f, 8) { onConfigChange(config.copy(spiralRevolutions = it)) } }
            PatternType.DIAMOND -> { SliderRow("Points", config.pointCount.toFloat(), 4f..24f, 5) { onConfigChange(config.copy(pointCount = (it.toInt() / 4) * 4)) }; SliderRow("Radius", config.radius, 30f..300f) { onConfigChange(config.copy(radius = it)) } }
            PatternType.RANDOM_AREA -> { SliderRow("Points", config.pointCount.toFloat(), 3f..20f, 17) { onConfigChange(config.copy(pointCount = it.toInt())) }; SliderRow("Width", config.areaWidth, 50f..500f) { onConfigChange(config.copy(areaWidth = it)) }; SliderRow("Height", config.areaHeight, 50f..500f) { onConfigChange(config.copy(areaHeight = it)) } }
            PatternType.CUSTOM -> {}
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(50.dp))
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps, modifier = Modifier.weight(1f))
        Text(if (value == value.toInt().toFloat()) "${value.toInt()}" else "%.1f".format(value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun CustomPointsList(points: List<ClickPoint>, onReorder: (Int, Int) -> Unit, onDelete: (String) -> Unit, onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        com.autoclicker.claude.ui.components.ConfirmDialog(
            title = "Clear all points?",
            message = "This will remove all ${points.size} custom points. You'll need to pick new ones.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = onClear,
            onDismiss = { confirmClear = false }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Custom Points (${points.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { confirmClear = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Clear All", style = MaterialTheme.typography.labelSmall) }
        }
        points.forEachIndexed { index, point ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("#${index + 1}", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("(${point.x.toInt()}, ${point.y.toInt()})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { if (index > 0) onReorder(index, index - 1) }, modifier = Modifier.size(28.dp), enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up", modifier = Modifier.size(16.dp)) }
                IconButton(onClick = { if (index < points.size - 1) onReorder(index, index + 1) }, modifier = Modifier.size(28.dp), enabled = index < points.size - 1) { Icon(Icons.Default.KeyboardArrowDown, "Move down", modifier = Modifier.size(16.dp)) }
                IconButton(onClick = { onDelete(point.id) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

private fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format("%d:%02d", s / 60, s % 60)
}

private fun triggerHaptic(context: android.content.Context) {
    com.autoclicker.claude.ui.components.Haptics.trigger(context, com.autoclicker.claude.ui.components.HapticType.TAP)
}

private fun triggerHapticError(context: android.content.Context) {
    com.autoclicker.claude.ui.components.Haptics.trigger(context, com.autoclicker.claude.ui.components.HapticType.WARNING)
}
