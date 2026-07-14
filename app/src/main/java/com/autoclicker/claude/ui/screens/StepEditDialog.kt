package com.autoclicker.claude.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.autoclicker.claude.data.ActionType
import com.autoclicker.claude.data.ClickPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepEditDialog(
    step: ClickPoint,
    onSave: (ClickPoint) -> Unit,
    onDismiss: () -> Unit
) {
    var action by remember { mutableStateOf(step.action) }
    var delay by remember { mutableStateOf(step.delayBefore.toString()) }
    var hold by remember { mutableStateOf(step.holdDuration.toString()) }
    var x by remember { mutableStateOf(step.x.toInt().toString()) }
    var y by remember { mutableStateOf(step.y.toInt().toString()) }
    var x2 by remember { mutableStateOf(step.swipeToX.toInt().toString()) }
    var y2 by remember { mutableStateOf(step.swipeToY.toInt().toString()) }
    var swipeDuration by remember { mutableStateOf(step.swipeDuration.toString()) }
    var repeats by remember { mutableStateOf(step.repeatCount.toString()) }
    var useDelayRange by remember { mutableStateOf(step.delayMaxMs > 0L) }
    var delayMin by remember { mutableStateOf((if (step.delayMinMs > 0) step.delayMinMs else step.delayBefore).toString()) }
    var delayMax by remember { mutableStateOf((if (step.delayMaxMs > 0) step.delayMaxMs else step.delayBefore * 2).toString()) }

    val needsDestination = action == ActionType.SWIPE || action == ActionType.PINCH_IN || action == ActionType.PINCH_OUT
    val needsHold = action in listOf(ActionType.TAP, ActionType.LONG_PRESS, ActionType.DOUBLE_TAP)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .widthIn(min = 280.dp, max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Edit Step", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                // ===== Action type =====
                Text("Action", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                ActionTypeRow(
                    selected = action,
                    onSelect = { action = it }
                )

                // ===== Position =====
                Text("Position (pixels from top-left)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Tip: it's easier to press Back and use \"Re-pick points\" to set these by tapping the screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = x, onValueChange = { x = it.filter { c -> c.isDigit() } },
                        label = { Text("Horizontal") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = y, onValueChange = { y = it.filter { c -> c.isDigit() } },
                        label = { Text("Vertical") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                if (needsDestination) {
                    Text(
                        if (action == ActionType.SWIPE) "Swipe end" else "Second finger",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = x2, onValueChange = { x2 = it.filter { c -> c.isDigit() } },
                            label = { Text("X2") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = y2, onValueChange = { y2 = it.filter { c -> c.isDigit() } },
                            label = { Text("Y2") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = swipeDuration,
                        onValueChange = { swipeDuration = it.filter { c -> c.isDigit() } },
                        label = { Text("Duration (ms)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                // ===== Timing =====
                Text("Timing", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Random wait before this step", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Waits a random time between the Min and Max below before this step, instead of the normal speed. Makes timing look less robotic.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = useDelayRange, onCheckedChange = { useDelayRange = it })
                }

                if (useDelayRange) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = delayMin,
                            onValueChange = { delayMin = it.filter { c -> c.isDigit() } },
                            label = { Text("Min ms") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = delayMax,
                            onValueChange = { delayMax = it.filter { c -> c.isDigit() } },
                            label = { Text("Max ms") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = delay,
                        onValueChange = { delay = it.filter { c -> c.isDigit() } },
                        label = { Text("Delay before (ms)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                if (needsHold) {
                    OutlinedTextField(
                        value = hold,
                        onValueChange = { hold = it.filter { c -> c.isDigit() } },
                        label = { Text("Hold duration (ms)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = repeats,
                    onValueChange = { repeats = it.filter { c -> c.isDigit() } },
                    label = { Text("Repeat this step N times") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = {
                        val updated = step.copy(
                            action = action,
                            x = x.toFloatOrNull() ?: step.x,
                            y = y.toFloatOrNull() ?: step.y,
                            swipeToX = if (needsDestination) (x2.toFloatOrNull() ?: 0f) else 0f,
                            swipeToY = if (needsDestination) (y2.toFloatOrNull() ?: 0f) else 0f,
                            swipeDuration = if (needsDestination) (swipeDuration.toLongOrNull() ?: 300L) else step.swipeDuration,
                            holdDuration = if (needsHold) (hold.toLongOrNull() ?: 50L) else step.holdDuration,
                            delayBefore = delay.toLongOrNull()?.coerceAtLeast(1L) ?: step.delayBefore,
                            delayMinMs = if (useDelayRange) (delayMin.toLongOrNull()?.coerceAtLeast(1L) ?: 0L) else 0L,
                            delayMaxMs = if (useDelayRange) (delayMax.toLongOrNull()?.coerceAtLeast(1L) ?: 0L) else 0L,
                            repeatCount = (repeats.toIntOrNull() ?: 1).coerceAtLeast(1)
                        )
                        onSave(updated)
                        onDismiss()
                    }) { Text("Save") }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ActionTypeRow(selected: ActionType, onSelect: (ActionType) -> Unit) {
    val options = listOf(
        ActionType.TAP to "Tap",
        ActionType.DOUBLE_TAP to "Double tap",
        ActionType.LONG_PRESS to "Long press",
        ActionType.SWIPE to "Swipe",
        ActionType.PINCH_IN to "Pinch in",
        ActionType.PINCH_OUT to "Pinch out",
        ActionType.DELAY to "Wait"
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { (type, label) ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(label, style = MaterialTheme.typography.bodySmall) }
            )
        }
    }
}
