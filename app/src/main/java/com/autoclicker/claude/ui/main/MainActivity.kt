package com.autoclicker.claude.ui.main

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.autoclicker.claude.R
import com.autoclicker.claude.databinding.ActivityMainBinding
import com.autoclicker.claude.model.ClickMode
import com.autoclicker.claude.model.ClickScript
import com.autoclicker.claude.service.AutoClickService
import com.autoclicker.claude.service.FloatingWidgetService
import com.autoclicker.claude.util.ScriptStorage
import com.autoclicker.claude.util.SettingsManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var scripts = mutableListOf<ClickScript>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scripts = ScriptStorage.loadScripts(this)

        setupUI()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupUI() {
        val interval = SettingsManager.getDefaultInterval(this)
        val repeatCount = SettingsManager.getDefaultRepeatCount(this)

        binding.etInterval.setText(interval.toString())
        binding.etRepeatCount.setText(if (repeatCount == -1) "" else repeatCount.toString())

        // Mode toggle
        binding.toggleMode.check(R.id.btn_single_mode)
        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_single_mode -> binding.tvModeDescription.text = "Tap one point repeatedly"
                    R.id.btn_multi_mode -> binding.tvModeDescription.text = "Set multiple tap points in sequence"
                }
            }
        }

        // Start button
        binding.btnStart.setOnClickListener {
            if (!isAccessibilityEnabled()) {
                showAccessibilityDialog()
                return@setOnClickListener
            }
            if (!hasOverlayPermission()) {
                requestOverlayPermission()
                return@setOnClickListener
            }

            val intervalVal = binding.etInterval.text.toString().toLongOrNull() ?: 300L
            val repeatVal = binding.etRepeatCount.text.toString().toIntOrNull() ?: -1

            SettingsManager.setDefaultInterval(this, intervalVal)
            SettingsManager.setDefaultRepeatCount(this, repeatVal)

            val mode = if (binding.toggleMode.checkedButtonId == R.id.btn_single_mode)
                ClickMode.SINGLE else ClickMode.MULTI

            val script = ClickScript(
                mode = mode,
                globalInterval = intervalVal,
                repeatCount = repeatVal
            )

            startFloatingService(script)
        }

        // Stop button
        binding.btnStopService.setOnClickListener {
            stopFloatingService()
        }

        // Accessibility settings button
        binding.btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        // Export button
        binding.btnExport.setOnClickListener {
            val script = FloatingWidgetService.instance?.getCurrentScript()
            if (script != null) {
                val json = ScriptStorage.exportScript(script)
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("script", json))
                Toast.makeText(this, "Script copied to clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No active script to export", Toast.LENGTH_SHORT).show()
            }
        }

        // Import button
        binding.btnImport.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (text != null) {
                val script = ScriptStorage.importScript(text)
                if (script != null) {
                    scripts.add(script)
                    ScriptStorage.saveScripts(this, scripts)
                    Toast.makeText(this, "Script imported: ${script.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid script data", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Save current button
        binding.btnSaveCurrent.setOnClickListener {
            val script = FloatingWidgetService.instance?.getCurrentScript()
            if (script != null) {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Save Script")
                val input = android.widget.EditText(this)
                input.hint = "Script name"
                builder.setView(input)
                builder.setPositiveButton("Save") { _, _ ->
                    script.name = input.text.toString().ifEmpty { "Untitled" }
                    scripts.add(script.copy())
                    ScriptStorage.saveScripts(this, scripts)
                    Toast.makeText(this, "Script saved!", Toast.LENGTH_SHORT).show()
                }
                builder.setNegativeButton("Cancel", null)
                builder.show()
            } else {
                Toast.makeText(this, "Start the clicker first to create a script", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateServiceStatus() {
        val accessibilityEnabled = isAccessibilityEnabled()
        val overlayEnabled = hasOverlayPermission()

        binding.tvAccessibilityStatus.text = if (accessibilityEnabled) "Enabled" else "Disabled"
        binding.tvAccessibilityStatus.setTextColor(
            getColor(if (accessibilityEnabled) R.color.status_enabled else R.color.status_disabled)
        )

        binding.tvOverlayStatus.text = if (overlayEnabled) "Enabled" else "Disabled"
        binding.tvOverlayStatus.setTextColor(
            getColor(if (overlayEnabled) R.color.status_enabled else R.color.status_disabled)
        )

        binding.btnStart.isEnabled = accessibilityEnabled && overlayEnabled
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Permission Required")
            .setMessage("Auto Clicker needs accessibility permission to perform taps. Please enable it in Settings.")
            .setPositiveButton("Open Settings") { _, _ -> openAccessibilitySettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun checkPermissions() {
        if (!isAccessibilityEnabled() || !hasOverlayPermission()) {
            updateServiceStatus()
        }
    }

    private fun startFloatingService(script: ClickScript) {
        val intent = Intent(this, FloatingWidgetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Pass script after service starts
        android.os.Handler(mainLooper).postDelayed({
            FloatingWidgetService.instance?.setScript(script)
        }, 500)

        Toast.makeText(this, "Auto Clicker started! Use the floating panel.", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingWidgetService::class.java)
        stopService(intent)
        AutoClickService.instance?.stopClicking()
        Toast.makeText(this, "Auto Clicker stopped", Toast.LENGTH_SHORT).show()
    }
}
