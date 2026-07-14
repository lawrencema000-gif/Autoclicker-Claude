package com.autoclicker.claude.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.autoclicker.claude.data.ActionType
import com.autoclicker.claude.data.TapProfile
import com.autoclicker.claude.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(vm: MainViewModel, profile: TapProfile) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var interval by remember(profile.id) { mutableStateOf(profile.intervalMs.toString()) }
    var loopCount by remember(profile.id) { mutableStateOf(if (profile.loopCount == 0) "" else profile.loopCount.toString()) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showRepickConfirm by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf<com.autoclicker.claude.data.ClickPoint?>(null) }

    editingStep?.let { step ->
        StepEditDialog(
            step = step,
            onSave = { vm.updateStepInEditing(it) },
            onDismiss = { editingStep = null }
        )
    }

    // Snapshot the steps as they were when this profile opened so step edits,
    // deletions, and re-picks count as unsaved changes (the text-field check
    // alone misses them because those mutate profile.steps, not the fields).
    val originalSteps = remember(profile.id) { profile.steps }

    val hasUnsavedChanges = name != profile.name ||
        interval != profile.intervalMs.toString() ||
        loopCount != (if (profile.loopCount == 0) "" else profile.loopCount.toString()) ||
        profile.steps != originalSteps

    fun attemptLeave() {
        if (hasUnsavedChanges) showDiscardConfirm = true else vm.cancelEditing()
    }

    // Hardware Back runs the same discard guard as the on-screen Back arrow.
    androidx.activity.compose.BackHandler(enabled = editingStep == null) { attemptLeave() }

    if (showDiscardConfirm) {
        com.autoclicker.claude.ui.components.ConfirmDialog(
            title = "Discard changes?",
            message = "Your edits will be lost if you leave without saving.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = { vm.cancelEditing() },
            onDismiss = { showDiscardConfirm = false }
        )
    }
    if (showRepickConfirm) {
        com.autoclicker.claude.ui.components.ConfirmDialog(
            title = "Re-pick steps?",
            message = "This will clear all ${profile.steps.size} existing steps. You'll tap the screen to set new ones.",
            confirmLabel = "Re-pick",
            destructive = true,
            onConfirm = { vm.rePickEditingPoints() },
            onDismiss = { showRepickConfirm = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { attemptLeave() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Edit Script",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                val context = androidx.compose.ui.platform.LocalContext.current
                FilledTonalButton(
                    onClick = {
                        val safeName = name.trim().ifBlank { "Untitled Script" }
                        val parsedInterval = interval.toLongOrNull()
                        if (parsedInterval == null || parsedInterval < 10L) {
                            android.widget.Toast.makeText(
                                context,
                                "Interval must be at least 10ms",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return@FilledTonalButton
                        }
                        val parsedLoops = loopCount.toIntOrNull()
                        if (loopCount.isNotBlank() && (parsedLoops == null || parsedLoops < 0)) {
                            android.widget.Toast.makeText(
                                context,
                                "Loops must be a positive number (or blank for infinite)",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return@FilledTonalButton
                        }
                        vm.updateEditingName(safeName)
                        vm.updateEditingInterval(parsedInterval)
                        vm.updateEditingLoopCount(parsedLoops ?: 0)
                        vm.saveEditingProfile()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }

        // Script metadata
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Script Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(
                        "Mode: ${when (profile.mode) {
                            com.autoclicker.claude.data.ClickMode.SINGLE_POINT -> "Single Point"
                            com.autoclicker.claude.data.ClickMode.MULTI_POINT -> "Multi Point"
                            com.autoclicker.claude.data.ClickMode.PATTERN_MODE -> "Pattern (${profile.patternConfig?.type?.name ?: "Circle"})"
                            com.autoclicker.claude.data.ClickMode.RECORD_MODE -> "Recorded Gesture"
                        }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = interval,
                            onValueChange = { interval = it },
                            label = { Text("Interval (ms)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = loopCount,
                            onValueChange = { loopCount = it },
                            label = { Text("Repeats (blank = forever)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            placeholder = { Text("∞") }
                        )
                    }
                }
            }
        }

        // Pattern config section (for pattern mode profiles)
        if (profile.mode == com.autoclicker.claude.data.ClickMode.PATTERN_MODE && profile.patternConfig != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pattern Configuration", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        val pc = profile.patternConfig
                        val typeName = pc.type.name.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')
                        Text(
                            "Shape: $typeName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Show only the fields that matter for this shape.
                        val detail = when (pc.type) {
                            com.autoclicker.claude.data.PatternType.GRID ->
                                "${pc.gridRows} × ${pc.gridCols} grid · ${pc.gridSpacing.toInt()}px apart"
                            com.autoclicker.claude.data.PatternType.ZIGZAG,
                            com.autoclicker.claude.data.PatternType.RANDOM_AREA ->
                                "${pc.pointCount} taps · area ${pc.areaWidth.toInt()}×${pc.areaHeight.toInt()}px"
                            com.autoclicker.claude.data.PatternType.SPIRAL ->
                                "${pc.pointCount} taps · size ${pc.radius.toInt()}px · ${pc.spiralRevolutions} loops"
                            com.autoclicker.claude.data.PatternType.CUSTOM ->
                                "${pc.customPoints.size} custom spots"
                            else -> "${pc.pointCount} taps · size ${pc.radius.toInt()}px"
                        }
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Points section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Steps (${profile.steps.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = {
                        if (profile.steps.isNotEmpty()) showRepickConfirm = true
                        else vm.rePickEditingPoints()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Re-pick steps")
                }
            }
        }

        // Step list
        if (profile.steps.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No points set. Tap 'Re-pick Points' to select targets.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            itemsIndexed(profile.steps, key = { _, s -> s.id }) { index, step ->
                Card(
                    onClick = { editingStep = step },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Step number
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "${index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                when (step.action) {
                                    ActionType.TAP -> "Tap"
                                    ActionType.SWIPE -> "Swipe"
                                    ActionType.LONG_PRESS -> "Long Press"
                                    ActionType.DELAY -> "Delay"
                                    ActionType.PATTERN -> "Pattern"
                                    ActionType.DOUBLE_TAP -> "Double Tap"
                                    ActionType.PINCH_IN -> "Pinch In"
                                    ActionType.PINCH_OUT -> "Pinch Out"
                                },
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                buildString {
                                    when (step.action) {
                                        ActionType.TAP, ActionType.LONG_PRESS, ActionType.PATTERN, ActionType.DOUBLE_TAP ->
                                            append("(${step.x.toInt()}, ${step.y.toInt()}) • ")
                                        ActionType.SWIPE ->
                                            append("(${step.x.toInt()}, ${step.y.toInt()}) → (${step.swipeToX.toInt()}, ${step.swipeToY.toInt()}) • ")
                                        ActionType.PINCH_IN, ActionType.PINCH_OUT ->
                                            append("(${step.x.toInt()}, ${step.y.toInt()}) ⇄ (${step.swipeToX.toInt()}, ${step.swipeToY.toInt()}) • ")
                                        ActionType.DELAY ->
                                            append("Wait ")
                                    }
                                    if (step.delayMaxMs > 0) {
                                        append("${step.delayMinMs}-${step.delayMaxMs}ms (random)")
                                    } else {
                                        append("${step.delayBefore}ms")
                                        if (step.action == ActionType.SWIPE || step.action == ActionType.PINCH_IN || step.action == ActionType.PINCH_OUT) {
                                            append(" • ${step.swipeDuration}ms gesture")
                                        }
                                    }
                                    if (step.repeatCount > 1) append(" • ×${step.repeatCount}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { vm.removeStepFromEditing(step.id) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom padding
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
