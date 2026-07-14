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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.autoclicker.claude.R
import com.autoclicker.claude.ads.AdManager
import com.autoclicker.claude.service.AutoClickService
import com.autoclicker.claude.data.CommandBus
import com.autoclicker.claude.data.TapProfile
import com.autoclicker.claude.ui.screens.*
import com.autoclicker.claude.ui.theme.AutoClickerTheme

class MainActivity : ComponentActivity() {

    // Eagerly-bound so ActivityResult callbacks (import) can't touch it before
    // setContent runs. The delegate creates it lazily but deterministically.
    private val vm: MainViewModel by viewModels()
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

        // Initialize AdMob (App Open Ad on foreground)
        AdManager.initialize(application)

        setContent {
            AutoClickerTheme {
                MainContent(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        CommandBus.setUiActive(true)
    }

    override fun onStop() {
        super.onStop()
        CommandBus.setUiActive(false)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainContent(vm: MainViewModel) {
        val onboardingComplete by vm.onboardingComplete.collectAsState()
        val serviceConnected by vm.serviceConnected.collectAsState()
        val editingProfile by vm.editingProfile.collectAsState()

        // Prominent disclosure must be shown before sending the user to system Accessibility
        // settings. Required by Google Play policy for AccessibilityService apps.
        var showDisclosure by remember { mutableStateOf(false) }
        if (showDisclosure) {
            // Hardware Back = decline (same as the "No thanks" button).
            BackHandler {
                showDisclosure = false
                Toast.makeText(this, getString(R.string.disclosure_declined_toast), Toast.LENGTH_LONG).show()
            }
            AccessibilityDisclosureScreen(
                onAgree = {
                    showDisclosure = false
                    openAccessibilitySettings()
                },
                onDecline = {
                    showDisclosure = false
                    Toast.makeText(this, getString(R.string.disclosure_declined_toast), Toast.LENGTH_LONG).show()
                }
            )
            return
        }

        // Show onboarding if not complete and service not connected
        if (!onboardingComplete && !serviceConnected) {
            OnboardingScreen(
                onOpenAccessibility = { showDisclosure = true },
                onRequestBattery = { requestBatteryOptimization() },
                onComplete = {
                    // Ask for notification permission now (in context): the app has
                    // introduced itself and the user is about to run sessions that
                    // show a control notification.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    vm.completeOnboarding()
                }
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
            Triple("History", Icons.Default.History, 2),
            Triple("Settings", Icons.Default.Settings, 3)
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
            androidx.compose.animation.AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.padding(padding),
                transitionSpec = {
                    androidx.compose.animation.fadeIn(tween(200)) togetherWith
                        androidx.compose.animation.fadeOut(tween(150))
                },
                label = "tabTransition"
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(vm)
                    1 -> ProfileListScreen(
                        vm = vm,
                        onImport = { importLauncher.launch("application/json") },
                        onExport = { profile -> exportProfile(profile) }
                    )
                    2 -> HistoryScreen(vm)
                    3 -> SettingsScreen(
                        vm = vm,
                        onOpenAccessibility = { showDisclosure = true },
                        onRequestBattery = { requestBatteryOptimization() }
                    )
                }
            }
        }
    }

    private fun openAccessibilitySettings() {
        AdManager.suppressNextAppOpenAd()
        // Try to deep-link straight to Auto Clicker's own accessibility page so
        // the user doesn't have to hunt through the full services list. The
        // details action + component extra is honored on AOSP/Pixel/Samsung;
        // OEMs that don't support it fall back to the generic list.
        val componentName = android.content.ComponentName(this, AutoClickService::class.java)
        val deepLink = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
            putExtra("android.intent.extra.COMPONENT_NAME", componentName.flattenToString())
            // Some Settings builds use the fragment-args highlight mechanism instead.
            putExtra(":settings:fragment_args_key", componentName.flattenToString())
        }
        val launched = try {
            startActivity(deepLink); true
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); true } catch (_: Exception) { false }
        }
        if (launched) {
            Toast.makeText(this, getString(R.string.accessibility_settings_toast), Toast.LENGTH_LONG).show()
        }
    }

    private fun requestBatteryOptimization() {
        AdManager.suppressNextAppOpenAd()
        try {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Battery optimization already disabled", Toast.LENGTH_SHORT).show()
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
        val json = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().use { it.readText() }
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't open the file. Try again.", Toast.LENGTH_LONG).show()
            return
        }
        if (json.isNullOrBlank()) {
            Toast.makeText(this, "File is empty.", Toast.LENGTH_LONG).show()
            return
        }
        vm.importProfile(json) { success, error ->
            val msg = when {
                success -> "Script imported!"
                error != null -> error
                else -> "Couldn't import this script."
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }
}
