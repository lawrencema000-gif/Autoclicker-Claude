package com.autoclicker.claude.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * Theme-aware colors for canvas overlays (bubble, toolbar, crosshair).
 * Automatically adapts to the device's dark/light mode setting.
 */
object OverlayTheme {
    fun isDark(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    // Base panel background
    fun panelBackground(context: Context): Int =
        if (isDark(context)) Color.argb(235, 15, 23, 42) else Color.argb(240, 30, 41, 59)

    // Button label text
    fun onPanel(context: Context): Int = Color.WHITE

    // Secondary text
    fun onPanelMuted(context: Context): Int =
        if (isDark(context)) Color.parseColor("#94A3B8") else Color.parseColor("#CBD5E1")

    // Primary accent (used for START / active buttons)
    fun primary(): Int = Color.parseColor("#38BDF8")

    // Success (running)
    fun success(): Int = Color.parseColor("#34D399")

    // Warning (paused)
    fun warning(): Int = Color.parseColor("#F59E0B")

    // Error (stop)
    fun error(): Int = Color.parseColor("#F87171")
}
