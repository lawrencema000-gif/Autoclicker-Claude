package com.autoclicker.claude.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduleManager.rescheduleAll(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
