package com.autoclicker.claude.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-registers all profile schedules after device reboot. AlarmManager
 * alarms are cleared on reboot, so without this every scheduled profile
 * would silently stop firing after the user restarts their phone.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduleManager.rescheduleAll(context.applicationContext)
            } catch (e: Exception) {
                // A DataStore read failure or a corrupt profile must not crash
                // the boot broadcast — just skip re-registration this cycle.
                Log.e("BootReceiver", "Failed to reschedule after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
