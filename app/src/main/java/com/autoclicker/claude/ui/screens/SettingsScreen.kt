package com.autoclicker.claude.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoclicker.claude.data.*
import com.autoclicker.claude.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onOpenAccessibility: () -> Unit,
    onRequestBattery: () -> Unit
) {
    val defaults by vm.defaultSettings.collectAsState()
    val serviceConnected by CommandBus.serviceConnected.collectAsState()

    var intervalMs by remember(defaults) { mutableStateOf(defaults.intervalMs.toString()) }
    var holdMs by remember(defaults) { mutableStateOf(defaults.holdDurationMs.toString()) }
    var swipeMs by remember(defaults) { mutableStateOf(defaults.swipeDurationMs.toString()) }
    var stopCondition by remember(defaults) { mutableStateOf(defaults.stopCondition) }
    var stopValue by remember(defaults) { mutableStateOf(if (defaults.stopValue == 0) "" else defaults.stopValue.toString()) }
    var speedMode by remember(defaults) { mutableStateOf(defaults.speedMode) }
    var anti by remember(defaults) { mutableStateOf(defaults.antiDetection) }

    fun save() {
        vm.saveDefaultSettings(
            DefaultSettings(
                intervalMs = intervalMs.toLongOrNull() ?: 500L,
                holdDurationMs = holdMs.toLongOrNull() ?: 200L,
                swipeDurationMs = swipeMs.toLongOrNull() ?: 300L,
                stopCondition = stopCondition,
                stopValue = stopValue.toIntOrNull() ?: 0,
                speedMode = speedMode,
                antiDetection = anti
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Applied to new scripts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

        // ===== SPEED PRESETS =====
        Text("SPEED PRESETS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Row 1: Turbo, Fast, Normal
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SpeedPreset.entries.take(3).forEach { preset ->
                PresetChip(
                    preset = preset,
                    selected = intervalMs.toLongOrNull() == preset.intervalMs,
                    onClick = {
                        intervalMs = preset.intervalMs.toString()
                        save()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Row 2: Slow, Crawl, Hourly
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SpeedPreset.entries.drop(3).forEach { preset ->
                PresetChip(
                    preset = preset,
                    selected = intervalMs.toLongOrNull() == preset.intervalMs,
                    onClick = {
                        intervalMs = preset.intervalMs.toString()
                        save()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ===== MAIN SETTINGS CARD =====
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Speed Mode toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Speed Mode", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)

                    // Interval / Rate toggle
                    Row {
                        FilterChip(
                            selected = speedMode == SpeedMode.INTERVAL,
                            onClick = { speedMode = SpeedMode.INTERVAL; save() },
                            label = { Text("Interval", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                        )
                        FilterChip(
                            selected = speedMode == SpeedMode.RATE,
                            onClick = { speedMode = SpeedMode.RATE; save() },
                            label = { Text("Rate", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                        )
                    }
                }

                // Interval
                SettingRow(
                    icon = Icons.Default.Timer,
                    label = "Interval",
                    value = intervalMs,
                    onValueChange = { intervalMs = it; save() },
                    unit = "ms"
                )

                // Hold duration
                SettingRow(
                    icon = Icons.Default.TouchApp,
                    label = "Tap & Hold Duration",
                    value = holdMs,
                    onValueChange = { holdMs = it; save() },
                    unit = "ms"
                )

                // Swipe duration
                SettingRow(
                    icon = Icons.Default.SwipeRight,
                    label = "Swipe Duration",
                    value = swipeMs,
                    onValueChange = { swipeMs = it; save() },
                    unit = "ms"
                )

                // Stop After
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.StopCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Stop After", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = when (stopCondition) {
                                StopCondition.NEVER -> "Never Stop"
                                StopCondition.AFTER_TAPS -> "After Taps"
                                StopCondition.AFTER_SECONDS -> "After Time"
                                StopCondition.AFTER_LOOPS -> "After Loops"
                            },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .width(160.dp)
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf(
                                StopCondition.NEVER to "Never Stop",
                                StopCondition.AFTER_TAPS to "After Taps",
                                StopCondition.AFTER_SECONDS to "After Time",
                                StopCondition.AFTER_LOOPS to "After Loops"
                            ).forEach { (cond, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { stopCondition = cond; expanded = false; save() }
                                )
                            }
                        }
                    }
                }

                if (stopCondition != StopCondition.NEVER) {
                    SettingRow(
                        icon = Icons.Default.Numbers,
                        label = when (stopCondition) {
                            StopCondition.AFTER_TAPS -> "Number of taps"
                            StopCondition.AFTER_SECONDS -> "Seconds"
                            StopCondition.AFTER_LOOPS -> "Number of loops"
                            else -> ""
                        },
                        value = stopValue,
                        onValueChange = { stopValue = it; save() },
                        unit = when (stopCondition) {
                            StopCondition.AFTER_TAPS -> "taps"
                            StopCondition.AFTER_SECONDS -> "sec"
                            StopCondition.AFTER_LOOPS -> "loops"
                            else -> ""
                        }
                    )
                }
            }
        }

        // ===== TAP RANDOMIZATION =====
        Text("TAP RANDOMIZATION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {

                // Random Position Offset
                ToggleRow(
                    icon = Icons.Default.GpsFixed,
                    label = "Random Position Offset",
                    description = "Slightly shifts each tap to avoid detection",
                    checked = anti.randomPositionOffset,
                    onCheckedChange = { anti = anti.copy(randomPositionOffset = it, enabled = it || anti.intervalJitter); save() }
                )

                if (anti.randomPositionOffset) {
                    SliderRow(
                        label = "Offset radius",
                        value = anti.positionOffsetRadius,
                        range = 1f..50f,
                        unit = "px",
                        onValueChange = { anti = anti.copy(positionOffsetRadius = it); save() }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                // Interval Jitter
                ToggleRow(
                    icon = Icons.Default.Shuffle,
                    label = "Interval Jitter",
                    description = "Varies timing between taps",
                    checked = anti.intervalJitter,
                    onCheckedChange = { anti = anti.copy(intervalJitter = it, enabled = it || anti.randomPositionOffset); save() }
                )

                if (anti.intervalJitter) {
                    SliderRow(
                        label = "Jitter amount",
                        value = anti.jitterPercent.toFloat(),
                        range = 5f..50f,
                        unit = "%",
                        onValueChange = { anti = anti.copy(jitterPercent = it.toInt()); save() }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                // Humanize Hold
                ToggleRow(
                    icon = Icons.Default.Fingerprint,
                    label = "Humanize Hold Duration",
                    description = "Varies how long each tap is held",
                    checked = anti.humanizeHoldDuration,
                    onCheckedChange = { anti = anti.copy(humanizeHoldDuration = it, enabled = true); save() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                // Avoid Exact Repetition
                ToggleRow(
                    icon = Icons.Default.DoNotDisturb,
                    label = "Avoid Exact Repetition",
                    description = "No two taps hit the same pixel",
                    checked = anti.avoidExactRepetition,
                    onCheckedChange = { anti = anti.copy(avoidExactRepetition = it, enabled = true); save() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                // Micro-pauses
                ToggleRow(
                    icon = Icons.Default.Coffee,
                    label = "Random Micro-Pauses",
                    description = "Simulates human distraction with brief pauses",
                    checked = anti.microPauseProbability > 0f && anti.enabled,
                    onCheckedChange = {
                        anti = if (it) anti.copy(microPauseProbability = 0.05f, enabled = true)
                        else anti.copy(microPauseProbability = 0f)
                        save()
                    }
                )
            }
        }

        // ===== ACCESSIBILITY SERVICE =====
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (serviceConnected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (serviceConnected) Icons.Default.Accessibility else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (serviceConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Accessibility Service", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (serviceConnected) "Enabled" else "Disabled — tap to enable",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (serviceConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    )
                }
                FilledTonalButton(onClick = onOpenAccessibility, shape = RoundedCornerShape(12.dp)) { Text("Settings") }
            }
        }

        // Battery
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Battery Optimization", fontWeight = FontWeight.SemiBold)
                    Text("Disable to prevent system stopping the app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onRequestBattery, shape = RoundedCornerShape(12.dp)) { Text("Optimize") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ===== Composable helpers =====

@Composable
private fun PresetChip(
    preset: SpeedPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                preset.label,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                when {
                    preset.intervalMs < 1000 -> "${preset.intervalMs}ms"
                    preset.intervalMs < 60000 -> "${preset.intervalMs / 1000}s"
                    preset.intervalMs < 3600000 -> "${preset.intervalMs / 60000}min"
                    else -> "${preset.intervalMs / 3600000}hr"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(100.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.End)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(unit, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 30.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text("${value.toInt()}$unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(50.dp))
    }
}
