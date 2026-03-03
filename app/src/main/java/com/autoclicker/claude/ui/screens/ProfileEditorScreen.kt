package com.autoclicker.claude.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
                IconButton(onClick = { vm.cancelEditing() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Edit Script",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = {
                        vm.updateEditingName(name)
                        vm.updateEditingInterval(interval.toLongOrNull() ?: 100L)
                        vm.updateEditingLoopCount(loopCount.toIntOrNull() ?: 0)
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
                            label = { Text("Loops (0=∞)") },
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
                        Text(
                            "Type: ${profile.patternConfig.type.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Points: ${profile.patternConfig.pointCount} | Radius: ${profile.patternConfig.radius.toInt()}px | Center: (${profile.patternConfig.centerX.toInt()}, ${profile.patternConfig.centerY.toInt()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (profile.patternConfig.type == com.autoclicker.claude.data.PatternType.GRID) {
                            Text(
                                "Grid: ${profile.patternConfig.gridRows}x${profile.patternConfig.gridCols} | Spacing: ${profile.patternConfig.gridSpacing.toInt()}px",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    onClick = { vm.rePickEditingPoints() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Re-pick Points")
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
                                },
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                when (step.action) {
                                    ActionType.TAP, ActionType.LONG_PRESS, ActionType.PATTERN ->
                                        "(${step.x.toInt()}, ${step.y.toInt()}) • ${step.delayBefore}ms delay • ${step.holdDuration}ms hold"
                                    ActionType.SWIPE ->
                                        "(${step.x.toInt()}, ${step.y.toInt()}) → (${step.swipeToX.toInt()}, ${step.swipeToY.toInt()}) • ${step.swipeDuration}ms"
                                    ActionType.DELAY ->
                                        "Wait ${step.delayBefore}ms"
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
