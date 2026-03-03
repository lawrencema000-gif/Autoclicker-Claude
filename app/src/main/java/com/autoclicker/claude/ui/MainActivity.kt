package com.autoclicker.claude.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoclicker.claude.data.TapProfile
import com.autoclicker.claude.ui.screens.*
import com.autoclicker.claude.ui.theme.AutoClickerTheme

class MainActivity : ComponentActivity() {

    private lateinit var vm: MainViewModel
    private var pendingExportProfile: TapProfile? = null

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { importFileUri(it) }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op, just requesting */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AutoClickerTheme {
                vm = viewModel()
                MainContent(vm)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainContent(vm: MainViewModel) {
        val onboardingComplete by vm.onboardingComplete.collectAsState()
        val serviceConnected by vm.serviceConnected.collectAsState()
        val editingProfile by vm.editingProfile.collectAsState()

        // Show onboarding if not complete and service not connected
        if (!onboardingComplete && !serviceConnected) {
            OnboardingScreen(
                onOpenAccessibility = { openAccessibilitySettings() },
                onRequestBattery = { requestBatteryOptimization() },
                onComplete = { vm.completeOnboarding() }
            )
            return
        }

        // Show profile editor if editing
        val profile = editingProfile
        if (profile != null) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    ProfileEditorScreen(vm, profile)
                }
            }
            return
        }

        // Main tabbed layout
        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = listOf(
            Triple("Clicker", Icons.Default.AdsClick, 0),
            Triple("Scripts", Icons.Default.Description, 1),
            Triple("Settings", Icons.Default.Settings, 2)
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index }
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTab) {
                    0 -> HomeScreen(vm)
                    1 -> ProfileListScreen(
                        vm = vm,
                        onImport = { importLauncher.launch("application/json") },
                        onExport = { profile -> exportProfile(profile) }
                    )
                    2 -> SettingsScreen(
                        vm = vm,
                        onOpenAccessibility = { openAccessibilitySettings() },
                        onRequestBattery = { requestBatteryOptimization() }
                    )
                }
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestBatteryOptimization() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Already optimized", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open battery settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportProfile(profile: TapProfile) {
        val json = vm.exportProfile(profile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, json)
            putExtra(Intent.EXTRA_SUBJECT, "${profile.name}.json")
        }
        startActivity(Intent.createChooser(intent, "Export Script"))
    }

    private fun importFileUri(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            if (json != null) {
                vm.importProfile(json)
                Toast.makeText(this, "Script imported!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to import: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
