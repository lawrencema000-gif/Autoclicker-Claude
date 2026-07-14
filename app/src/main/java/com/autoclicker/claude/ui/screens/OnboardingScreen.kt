package com.autoclicker.claude.ui.screens

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.autoclicker.claude.data.CommandBus

@Composable
fun OnboardingScreen(
    onOpenAccessibility: () -> Unit,
    onRequestBattery: () -> Unit,
    onComplete: () -> Unit
) {
    val serviceConnected by CommandBus.serviceConnected.collectAsState()
    val context = LocalContext.current

    // Check battery optimization status, re-check when resumed from settings
    var batteryOptimized by remember { mutableStateOf(isBatteryOptimized(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimized = isBatteryOptimized(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Scrollable content area so onboarding never clips on small screens or
        // at large system font scales; the button stays pinned below.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // App icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Welcome to Auto Clicker",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Two quick steps to set up",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Step 1: Accessibility (required)
            StepCard(
                stepNumber = 1,
                title = "Let Auto Clicker tap your screen",
                description = "This opens your phone's Accessibility settings, where you turn Auto Clicker ON. It's how the app taps for you. We never read your screen.",
                icon = Icons.Default.Accessibility,
                completed = serviceConnected,
                actionLabel = if (serviceConnected) "Allowed" else "Set up",
                onAction = { if (!serviceConnected) onOpenAccessibility() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Step 2: Battery (optional but recommended)
            StepCard(
                stepNumber = 2,
                title = "Keep Auto Clicker running (optional)",
                description = "Let Auto Clicker ignore battery optimization so your phone is less likely to stop it during long sessions.",
                icon = Icons.Default.BatteryChargingFull,
                completed = batteryOptimized,
                actionLabel = if (batteryOptimized) "Done" else "Allow",
                onAction = { if (!batteryOptimized) onRequestBattery() }
            )
        }

        // Helpful hint when not ready
        if (!serviceConnected) {
            Text(
                "Step 1 is required. The app can't click without it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Get Started
        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = serviceConnected
        ) {
            Text("Get Started", fontWeight = FontWeight.Bold)
        }
    }
}

private fun isBatteryOptimized(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    completed: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (completed)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step number
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (completed) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (completed) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(20.dp))
                } else {
                    Text("$stepNumber", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onAction,
                enabled = !completed,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
