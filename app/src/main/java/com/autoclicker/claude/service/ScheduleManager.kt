package com.autoclicker.claude.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.autoclicker.claude.data.ProfileRepository
import com.autoclicker.claude.data.Schedule
import com.autoclicker.claude.data.TapProfile
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Cron-style scheduling for profiles. Each profile may have an optional
 * Schedule attached; this manager translates that into AlarmManager alarms
 * that fire IntentTriggerReceiver.ACTION_START with the profile_id extra.
 *
 * Boot resilience: BootReceiver re-registers all alarms after the device
 * restarts, since AlarmManager state is not persisted across reboots.
 */
object ScheduleManager {

    private const val TAG = "ScheduleManager"

    /**
     * Cancel any existing alarm for this profile and register a new one for
     * the next firing if the schedule is enabled and has at least one day.
     */
    fun reschedule(context: Context, profile: TapProfile) {
        cancelFor(context, profile.id)
        val schedule = profile.schedule ?: return
        if (!schedule.enabled || !schedule.isAnyDay()) return

        val nextFireMs = nextFireTime(schedule) ?: return
        val intent = Intent(IntentTriggerReceiver.ACTION_START).apply {
            `package` = context.packageName
            putExtra(IntentTriggerReceiver.EXTRA_PROFILE_ID, profile.id)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(context, profile.id.hashCode(), intent, flags)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            // Use setExactAndAllowWhileIdle so doze mode doesn't shift the fire time.
            // Falls back gracefully on Android 12+ where SCHEDULE_EXACT_ALARM is gated.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFireMs, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFireMs, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFireMs, pi)
            }
            Log.d(TAG, "Scheduled ${profile.name} at $nextFireMs (${schedule.hour}:${schedule.minute})")
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked. Use inexact as fallback.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFireMs, pi)
        }
    }

    fun cancelFor(context: Context, profileId: String) {
        val intent = Intent(IntentTriggerReceiver.ACTION_START).apply {
            `package` = context.packageName
        }
        val pi = PendingIntent.getBroadcast(
            context,
            profileId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi)
        pi.cancel()
    }

    /** Re-register alarms for every profile with a schedule. Called after boot,
     *  app startup, and after any profile schedule change. */
    suspend fun rescheduleAll(context: Context) {
        val repo = ProfileRepository(context)
        val profiles = repo.profiles.first()
        for (profile in profiles) {
            reschedule(context, profile)
        }
    }

    /**
     * Compute the next time-of-day this schedule should fire (epoch millis).
     * Returns null if no day is selected.
     */
    fun nextFireTime(schedule: Schedule, fromMs: Long = System.currentTimeMillis()): Long? {
        if (!schedule.isAnyDay()) return null
        val cal = Calendar.getInstance()
        cal.timeInMillis = fromMs
        // Search up to 7 days ahead for the next selected day-of-week.
        // Calendar.DAY_OF_WEEK: Sunday=1, Monday=2, ..., Saturday=7. Our bitmask
        // uses 0=Sun..6=Sat, so we shift down by one.
        for (i in 0..7) {
            val candidate = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, i)
                set(Calendar.HOUR_OF_DAY, schedule.hour)
                set(Calendar.MINUTE, schedule.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayBit = candidate.get(Calendar.DAY_OF_WEEK) - 1
            val isOnSelectedDay = schedule.runsOn(dayBit)
            val isInFuture = candidate.timeInMillis > fromMs
            if (isOnSelectedDay && isInFuture) return candidate.timeInMillis
        }
        return null
    }
}
