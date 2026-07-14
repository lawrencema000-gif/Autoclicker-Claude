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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autoclicker.claude.ads.BannerAd
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
        Text("These are the defaults for the next time you press START. They don't change a run that's already going.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

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
                Text(
                    if (speedMode == SpeedMode.INTERVAL) "Interval = the wait between taps (in milliseconds; 1000 = 1 second)."
                    else "Rate = how many taps per second.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 30.dp, top = 2.dp)
                )

                // Interval
                SettingRow(
                    icon = Icons.Default.Timer,
                    label = "Interval (wait between taps)",
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

        // ===== TAP RANDOMIZATION (Advanced) =====
        var showAdvanced by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ADVANCED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null, modifier = Modifier.size(16.dp)
                )
            }
        }

        if (!showAdvanced) {
            Text(
                "Make the taps look more human, so games are less likely to detect the auto-clicker. Optional — most people can leave this off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        if (showAdvanced) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {

                // Random Position Offset
                ToggleRow(
                    icon = Icons.Default.GpsFixed,
                    label = "Vary tap location",
                    description = "Moves each tap by a few pixels — helps with apps that detect bots",
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
                    label = "Randomize tap timing",
                    description = "Slightly changes the delay between taps so it looks human",
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
                    label = "Vary hold time",
                    description = "Holds each tap for slightly different durations",
                    checked = anti.humanizeHoldDuration,
                    onCheckedChange = { anti = anti.copy(humanizeHoldDuration = it, enabled = true); save() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                // Avoid Exact Repetition
                ToggleRow(
                    icon = Icons.Default.DoNotDisturb,
                    label = "Never tap the same pixel twice",
                    description = "Avoids being flagged for suspiciously identical taps",
                    checked = anti.avoidExactRepetition,
                    onCheckedChange = { anti = anti.copy(avoidExactRepetition = it, enabled = true); save() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))

                // Micro-pauses
                ToggleRow(
                    icon = Icons.Default.Coffee,
                    label = "Occasional tiny pauses",
                    description = "Adds brief random pauses, like a real person getting distracted",
                    checked = anti.microPauseProbability > 0f && anti.enabled,
                    onCheckedChange = {
                        anti = if (it) anti.copy(microPauseProbability = 0.05f, enabled = true)
                        else anti.copy(microPauseProbability = 0f)
                        save()
                    }
                )
            }
        }
        } // end if (showAdvanced)

        // ===== QUICK CONTROLS =====
        Text("QUICK CONTROLS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Floating Bubble
        val bubbleEnabled by CommandBus.bubbleEnabled.collectAsState()
        val bubbleTipSeen by vm.bubbleTipSeen.collectAsState()
        var showBubbleTip by remember { mutableStateOf(false) }
        if (showBubbleTip) {
            com.autoclicker.claude.ui.components.ConfirmDialog(
                title = "Floating bubble enabled",
                message = "A draggable bubble now appears on your screen.\n\n• Tap: play / pause\n• Long-press: pick new points\n• Drag: move anywhere",
                confirmLabel = "Got it",
                cancelLabel = "Turn off",
                // "Got it" keeps the bubble on; only "Turn off" disables it. A scrim
                // tap or back press just closes (onDismiss) without disabling.
                onConfirm = { vm.dismissBubbleTip() },
                onCancel = { vm.dismissBubbleTip(); CommandBus.setBubbleEnabled(false) },
                onDismiss = { showBubbleTip = false }
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Circle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Floating Bubble", fontWeight = FontWeight.SemiBold)
                    Text("Tap to pause, long-press to pick points, drag to move", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = bubbleEnabled,
                    onCheckedChange = { enabled ->
                        CommandBus.setBubbleEnabled(enabled)
                        if (enabled && !bubbleTipSeen) showBubbleTip = true
                    }
                )
            }
        }

        // ===== VOLUME BUTTON TRIGGER =====
        val volumeEnabled by CommandBus.volumeTriggerEnabled.collectAsState()
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Volume Button Trigger", fontWeight = FontWeight.SemiBold)
                    Text("Vol Up = Start/Pause, Vol Down = Stop. While ON, your volume buttons control the clicker instead of changing volume.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = volumeEnabled, onCheckedChange = { CommandBus.setVolumeTriggerEnabled(it) })
            }
        }

        // ===== PAUSE ON TOUCH =====
        val pauseOnTouchEnabled by CommandBus.pauseOnTouchEnabled.collectAsState()
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PanTool, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pause when I touch", fontWeight = FontWeight.SemiBold)
                    Text("Auto-pauses when you touch the screen, resumes ~1.5s after you let go", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = pauseOnTouchEnabled, onCheckedChange = { CommandBus.setPauseOnTouchEnabled(it) })
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
                    Text("Keep running in background", fontWeight = FontWeight.SemiBold)
                    Text("Stops your phone from pausing Auto Clicker to save battery during long sessions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onRequestBattery, shape = RoundedCornerShape(12.dp)) { Text("Allow") }
            }
        }

        // OEM autostart (Xiaomi/OPPO/Vivo/Huawei/etc) — the #1 reliability fix
        if (com.autoclicker.claude.util.OemAutostart.isAggressiveOem()) {
            val ctx = LocalContext.current
            val vendor = com.autoclicker.claude.util.OemAutostart.detectVendor()
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow autostart (${vendor.displayName})", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Required so your device doesn't kill Auto Clicker after a few minutes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        com.autoclicker.claude.util.OemAutostart.instructionsFor(vendor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            com.autoclicker.claude.ads.AdManager.suppressNextAppOpenAd()
                            com.autoclicker.claude.util.OemAutostart.openAutostartScreen(ctx)
                            com.autoclicker.claude.util.OemAutostart.showHint(ctx)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Open autostart settings") }
                }
            }
        }

        // ===== HELP =====
        Text("HELP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HelpSection()

        // Banner ad at bottom of settings
        BannerAd(modifier = Modifier.padding(top = 8.dp))

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HelpSection() {
    val faqs = listOf(
        "How do I start clicking?" to
            "Go to the Clicker tab, keep \"Single Tap\" selected, press START, then tap the exact spot on your screen you want tapped. It repeats there until you press STOP.",
        "How do I make it tap inside a game?" to
            "Turn on the Floating Bubble (or Volume Button Trigger) below, open your game, then long-press the bubble (or press Volume Up) to pick the spot right inside the game. The tap point can be anywhere on screen.",
        "How do I stop it?" to
            "Press STOP on the Clicker tab, tap the STOP (red) button on the little floating toolbar, or pull down your notification shade and tap Stop. The Volume Down button also stops it if you enabled the Volume trigger.",
        "It clicks somewhere else — how do I move the spot?" to
            "While it's running, tap the crosshair once to pause, then drag it to a new spot and let go. Or press STOP and start again on the new spot.",
        "Why did it stop after a while?" to
            "Your phone probably paused it to save battery. In Settings, tap \"Allow\" under Keep running in background, and (on Xiaomi/OPPO/Vivo/Huawei/Samsung) tap \"Open autostart settings\" and allow Auto Clicker.",
        "How fast will it tap?" to
            "See \"Speed\" on the Clicker tab. Change it with the Speed presets at the top of Settings, or fine-tune the interval below them.",
        "What is a \"script\"?" to
            "Every setup you run is saved automatically as a script in the Scripts tab, so you can re-run, rename, edit, or schedule it later."
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        faqs.forEach { (q, a) ->
            var expanded by remember { mutableStateOf(false) }
            Card(
                onClick = { expanded = !expanded },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(q, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (expanded) {
                        Spacer(Modifier.height(6.dp))
                        Text(a, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
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
