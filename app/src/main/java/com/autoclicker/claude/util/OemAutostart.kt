package com.autoclicker.claude.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

/**
 * OEM-specific autostart / background-run permission deeplinks.
 * The #1 reliability complaint across every Android auto-clicker is that aggressive
 * battery savers on Xiaomi/MIUI, OPPO/Realme, OnePlus, Vivo, Huawei, and Samsung
 * kill the accessibility service after 30-60 minutes. Each OEM hides the
 * "allow autostart / background run" toggle on a different settings screen with
 * a different intent. This file maps them.
 *
 * The mappings are best-effort — OEMs frequently rename activities. We try each
 * candidate intent in order and fall back to the generic battery optimization
 * screen if all fail.
 */
object OemAutostart {

    enum class Vendor(val displayName: String) {
        XIAOMI("Xiaomi / Redmi / POCO"),
        OPPO("OPPO / Realme"),
        ONEPLUS("OnePlus"),
        VIVO("Vivo / iQOO"),
        HUAWEI("Huawei / Honor"),
        SAMSUNG("Samsung"),
        ASUS("ASUS"),
        LETV("LeEco"),
        HONOR("Honor"),
        OTHER("Other");
    }

    fun detectVendor(): Vendor {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return when {
            "xiaomi" in m || "redmi" in b || "poco" in b -> Vendor.XIAOMI
            "oppo" in m || "realme" in m || "realme" in b -> Vendor.OPPO
            "oneplus" in m -> Vendor.ONEPLUS
            "vivo" in m || "iqoo" in b -> Vendor.VIVO
            "huawei" in m -> Vendor.HUAWEI
            "honor" in m || "honor" in b -> Vendor.HONOR
            "samsung" in m -> Vendor.SAMSUNG
            "asus" in m -> Vendor.ASUS
            "letv" in m -> Vendor.LETV
            else -> Vendor.OTHER
        }
    }

    /** Whether this device's OEM is known to aggressively kill background services.
     *  Samsung IS included (its "put unused apps to sleep" / adaptive battery is a
     *  frequent killer), matching this file's header and the vendor instructions. */
    fun isAggressiveOem(): Boolean = detectVendor() != Vendor.OTHER

    /**
     * Attempts to open the OEM-specific autostart screen. Returns true if any
     * intent resolved successfully. Falls back to the generic battery
     * optimization screen if no OEM-specific activity is found.
     */
    fun openAutostartScreen(context: Context): Boolean {
        val candidates = candidatesFor(detectVendor())
        for (intent in candidates) {
            if (tryStart(context, intent)) return true
        }
        // Fallback: generic Android settings
        return tryStart(context, Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        })
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun candidatesFor(vendor: Vendor): List<Intent> = when (vendor) {
        Vendor.XIAOMI -> listOf(
            componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            componentIntent("com.miui.securitycenter", "com.miui.appmanager.AppManagerMainActivity")
        )
        Vendor.OPPO -> listOf(
            componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            componentIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            componentIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
        )
        Vendor.ONEPLUS -> listOf(
            componentIntent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
        )
        Vendor.VIVO -> listOf(
            componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        )
        Vendor.HUAWEI, Vendor.HONOR -> listOf(
            componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
        )
        Vendor.SAMSUNG -> listOf(
            componentIntent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:com.autoclicker.claude")
            }
        )
        Vendor.ASUS -> listOf(
            componentIntent("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity"),
            componentIntent("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")
        )
        Vendor.LETV -> listOf(
            componentIntent("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
        )
        Vendor.OTHER -> emptyList()
    }

    private fun componentIntent(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))

    /** Short user-facing instruction for what to look for once the screen opens. */
    fun instructionsFor(vendor: Vendor): String = when (vendor) {
        Vendor.XIAOMI -> "Find Auto Clicker in the list and turn ON 'Autostart'."
        Vendor.OPPO -> "Toggle ON 'Allow auto-launch' for Auto Clicker."
        Vendor.ONEPLUS -> "Toggle ON 'Allow background activity' for Auto Clicker."
        Vendor.VIVO -> "Toggle ON 'Auto-start' for Auto Clicker."
        Vendor.HUAWEI, Vendor.HONOR -> "Tap Auto Clicker → toggle ON 'Auto-launch', 'Secondary launch', and 'Run in background'."
        Vendor.SAMSUNG -> "Tap Auto Clicker → 'Allow background activity'. Also disable 'Put unused apps to sleep'."
        Vendor.ASUS -> "Find Auto Clicker → enable 'Auto-start'."
        Vendor.LETV -> "Find Auto Clicker → enable 'Autoboot'."
        Vendor.OTHER -> "If you find an autostart, background-activity, or battery-optimization toggle, allow it."
    }

    fun showHint(context: Context) {
        Toast.makeText(context, instructionsFor(detectVendor()), Toast.LENGTH_LONG).show()
    }
}
