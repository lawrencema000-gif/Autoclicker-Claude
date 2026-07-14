package com.autoclicker.claude.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.autoclicker.claude.data.Schedule
import com.autoclicker.claude.data.TapProfile
import com.autoclicker.claude.service.ScheduleManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    profile: TapProfile,
    onSave: (Schedule?) -> Unit,
    onDismiss: () -> Unit
) {
    var enabled by remember { mutableStateOf(profile.schedule?.enabled ?: false) }
    var hour by remember { mutableIntStateOf(profile.schedule?.hour ?: 9) }
    var minute by remember { mutableIntStateOf(profile.schedule?.minute ?: 0) }
    // Bitmask: bit 0=Sun, 1=Mon, ..., 6=Sat
    var daysMask by remember { mutableIntStateOf(profile.schedule?.daysOfWeek ?: Schedule.EVERY_DAY) }
    var showTimePicker by remember { mutableStateOf(false) }

    val nextFire = remember(enabled, hour, minute, daysMask) {
        if (!enabled || daysMask == 0) null
        else ScheduleManager.nextFireTime(Schedule(true, hour, minute, daysMask))
    }
    val nextFireText = nextFire?.let {
        val fmt = SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault())
        "Next: ${fmt.format(Date(it))}"
    } ?: "No upcoming runs"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(20.dp).widthIn(min = 280.dp, max = 360.dp)) {
                Text("Schedule", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(profile.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                // Enabled switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Run on schedule", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (enabled) nextFireText else "Off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                if (enabled) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    // Time picker button
                    Text("Time", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    OutlinedCard(
                        onClick = { showTimePicker = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val hour12 = when {
                                hour == 0 -> 12
                                hour > 12 -> hour - 12
                                else -> hour
                            }
                            Text(
                                String.format("%d:%02d", hour12, minute),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (hour < 12) "AM" else "PM",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Day-of-week chips
                    Text("Days", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val labels = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                        labels.forEachIndexed { i, label ->
                            val selected = (daysMask shr i) and 1 == 1
                            DayChip(label, selected) {
                                daysMask = if (selected) daysMask and (1 shl i).inv() else daysMask or (1 shl i)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { daysMask = Schedule.EVERY_DAY },
                            label = { Text("Every day", style = MaterialTheme.typography.bodySmall) }
                        )
                        AssistChip(
                            onClick = { daysMask = Schedule.WEEKDAYS },
                            label = { Text("Weekdays", style = MaterialTheme.typography.bodySmall) }
                        )
                        AssistChip(
                            onClick = { daysMask = Schedule.WEEKENDS },
                            label = { Text("Weekends", style = MaterialTheme.typography.bodySmall) }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "For scheduled runs to work, keep Auto Clicker turned on in Accessibility settings and allow it to run in the background. Timing may vary by a few minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (profile.schedule != null) {
                        TextButton(onClick = { onSave(null); onDismiss() }) { Text("Remove") }
                        Spacer(Modifier.width(4.dp))
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            val s = if (enabled && daysMask != 0)
                                Schedule(enabled = true, hour = hour, minute = minute, daysOfWeek = daysMask)
                            else null
                            onSave(s)
                            onDismiss()
                        }
                    ) { Text("Save") }
                }
            }
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour
                    minute = state.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = state) }
        )
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = colors,
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(label, color = textColor, fontWeight = FontWeight.Bold)
        }
    }
}
