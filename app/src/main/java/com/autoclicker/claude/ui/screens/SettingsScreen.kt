package com.autoclicker.claude.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.autoclicker.claude.data.CommandBus
import com.autoclicker.claude.data.DefaultSettings
import com.autoclicker.claude.data.StopCondition
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

    fun save() {
        vm.saveDefaultSettings(
            DefaultSettings(
                intervalMs = intervalMs.toLongOrNull() ?: 100L,
                holdDurationMs = holdMs.toLongOrNull() ?: 10L,
                swipeDurationMs = swipeMs.toLongOrNull() ?: 350L,
                stopCondition = stopCondition,
                stopValue = stopValue.toIntOrNull() ?: 0
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
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Default settings
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Default Values", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)

                SettingField(
                    label = "Tap Interval",
                    value = intervalMs,
                    onValueChange = { intervalMs = it; save() },
                    unit = "ms"
                )

                SettingField(
                    label = "Tap & Hold Duration",
                    value = holdMs,
                    onValueChange = { holdMs = it; save() },
                    unit = "ms"
                )

                SettingField(
                    label = "Swipe Duration",
                    value = swipeMs,
                    onValueChange = { swipeMs = it; save() },
                    unit = "ms"
                )

                // Stop condition dropdown
                var expanded by remember { mutableStateOf(false) }
                Text("Stop After", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = when (stopCondition) {
                            StopCondition.NEVER -> "Never"
                            StopCondition.AFTER_TAPS -> "After N Taps"
                            StopCondition.AFTER_SECONDS -> "After N Seconds"
                        },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Never") }, onClick = {
                            stopCondition = StopCondition.NEVER; expanded = false; save()
                        })
                        DropdownMenuItem(text = { Text("After N Taps") }, onClick = {
                            stopCondition = StopCondition.AFTER_TAPS; expanded = false; save()
                        })
                        DropdownMenuItem(text = { Text("After N Seconds") }, onClick = {
                            stopCondition = StopCondition.AFTER_SECONDS; expanded = false; save()
                        })
                    }
                }

                if (stopCondition != StopCondition.NEVER) {
                    SettingField(
                        label = if (stopCondition == StopCondition.AFTER_TAPS) "Number of taps" else "Seconds",
                        value = stopValue,
                        onValueChange = { stopValue = it; save() },
                        unit = if (stopCondition == StopCondition.AFTER_TAPS) "taps" else "sec"
                    )
                }
            }
        }

        // Accessibility service card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (serviceConnected)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (serviceConnected) Icons.Default.Accessibility else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (serviceConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Accessibility Service",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (serviceConnected) "Enabled" else "Disabled — tap to enable",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (serviceConnected)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
                FilledTonalButton(
                    onClick = onOpenAccessibility,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Settings")
                }
            }
        }

        // Battery optimization
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Battery Optimization", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Disable to prevent system from killing the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onRequestBattery,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Optimize")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    unit,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
