package com.autoclicker.claude.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autoclicker.claude.data.ClickMode
import com.autoclicker.claude.data.RunState
import com.autoclicker.claude.data.TapProfile
import com.autoclicker.claude.ui.MainViewModel

@Composable
fun ProfileListScreen(
    vm: MainViewModel,
    onImport: () -> Unit,
    onExport: (TapProfile) -> Unit
) {
    val profiles by vm.profiles.collectAsState()
    val runState by vm.runState.collectAsState()
    val serviceConnected by vm.serviceConnected.collectAsState()

    val sorted = profiles.sortedByDescending { it.updatedAt }
    val categories = sorted.map { it.category.ifBlank { "Uncategorized" } }.distinct()
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val filtered = if (selectedCategory == null) sorted else sorted.filter { (it.category.ifBlank { "Uncategorized" }) == selectedCategory }

    Box(modifier = Modifier.fillMaxSize()) {
        if (sorted.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No scripts yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Go to the Clicker tab and tap START to create your first automation script",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "Scripts",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Category filter chips
                if (categories.size > 1) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("All") })
                            categories.forEach { cat ->
                                FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = if (selectedCategory == cat) null else cat }, label = { Text(cat) })
                            }
                        }
                    }
                }

                items(filtered, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        canPlay = serviceConnected && runState == RunState.IDLE && profile.steps.isNotEmpty(),
                        onPlay = { vm.startProfile(profile) },
                        onEdit = { vm.editProfile(profile) },
                        onDuplicate = { vm.duplicateProfile(profile.id) },
                        onExport = { onExport(profile) },
                        onDelete = { vm.deleteProfile(profile.id) },
                        onSchedule = { schedule -> vm.setProfileSchedule(profile, schedule) }
                    )
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        // Import FAB (shows spinner while importing)
        val isImporting by vm.isImporting.collectAsState()
        FloatingActionButton(
            onClick = { if (!isImporting) onImport() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.FileDownload, contentDescription = "Import Script")
            }
        }
    }
}

private fun formatDays(mask: Int): String {
    if (mask == com.autoclicker.claude.data.Schedule.EVERY_DAY) return "every day"
    if (mask == com.autoclicker.claude.data.Schedule.WEEKDAYS) return "weekdays"
    if (mask == com.autoclicker.claude.data.Schedule.WEEKENDS) return "weekends"
    val labels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    return labels.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.joinToString(", ")
}

@Composable
private fun ProfileCard(
    profile: TapProfile,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onSchedule: (com.autoclicker.claude.data.Schedule?) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (showScheduleDialog) {
        ScheduleDialog(
            profile = profile,
            onSave = onSchedule,
            onDismiss = { showScheduleDialog = false }
        )
    }

    if (showDeleteConfirm) {
        com.autoclicker.claude.ui.components.ConfirmDialog(
            title = "Delete \"${profile.name}\"?",
            message = "This script will be permanently deleted. This action cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                com.autoclicker.claude.ui.components.Haptics.trigger(context, com.autoclicker.claude.ui.components.HapticType.ERROR)
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${when (profile.mode) { ClickMode.SINGLE_POINT -> "Single"; ClickMode.MULTI_POINT -> "Multi"; ClickMode.PATTERN_MODE -> "Pattern"; ClickMode.RECORD_MODE -> "Recorded" }} • ${profile.steps.size} steps • ${profile.intervalMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                profile.schedule?.takeIf { it.enabled }?.let { sched ->
                    Text(
                        "⏰ ${String.format("%02d:%02d", sched.hour, sched.minute)} on ${formatDays(sched.daysOfWeek)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Play
            IconButton(onClick = onPlay, enabled = canPlay) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Run",
                    tint = if (canPlay) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }

            // Overflow
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { showMenu = false; onEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (profile.schedule?.enabled == true) "Edit schedule" else "Schedule") },
                        onClick = { showMenu = false; showScheduleDialog = true },
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = { showMenu = false; onDuplicate() },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Export") },
                        onClick = { showMenu = false; onExport() },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; showDeleteConfirm = true },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}
