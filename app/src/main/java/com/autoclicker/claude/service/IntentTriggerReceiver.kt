package com.autoclicker.claude.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autoclicker.claude.data.CommandBus
import com.autoclicker.claude.data.ProfileRepository
import com.autoclicker.claude.data.TapCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * External trigger surface: Tasker, MacroDroid, ADB shell, custom apps.
 *
 * Supported intents (all exported, package-scoped):
 *   com.autoclicker.claude.action.START      [extra: profile_name (String) | profile_id (String)]
 *   com.autoclicker.claude.action.STOP
 *   com.autoclicker.claude.action.PAUSE
 *   com.autoclicker.claude.action.RESUME
 *   com.autoclicker.claude.action.RESTART_LAST
 *
 * Usage from Tasker action "Send Intent":
 *   Action: com.autoclicker.claude.action.START
 *   Extra:  profile_name:Mining Tap
 *   Target: Broadcast Receiver
 *
 * Usage from ADB:
 *   adb shell am broadcast -a com.autoclicker.claude.action.START \
 *     --es profile_name "Mining Tap" -p com.autoclicker.claude
 */
class IntentTriggerReceiver(private val appContext: Context) : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val repo by lazy { ProfileRepository(appContext) }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START -> {
                val name = intent.getStringExtra(EXTRA_PROFILE_NAME)
                val id = intent.getStringExtra(EXTRA_PROFILE_ID)
                scope.launch {
                    val profiles = repo.profiles.first()
                    val target = when {
                        !id.isNullOrBlank() -> profiles.firstOrNull { it.id == id }
                        !name.isNullOrBlank() -> profiles.firstOrNull { it.name.equals(name, ignoreCase = true) }
                        else -> null
                    } ?: profiles.firstOrNull()  // No selector = first saved profile
                    target?.let { CommandBus.send(TapCommand.StartProfile(it)) }
                }
            }
            ACTION_STOP -> CommandBus.send(TapCommand.Stop)
            ACTION_PAUSE -> CommandBus.send(TapCommand.Pause)
            ACTION_RESUME -> CommandBus.send(TapCommand.Resume)
            ACTION_RESTART_LAST -> CommandBus.send(TapCommand.RestartLastProfile)
        }
    }

    companion object {
        const val ACTION_START = "com.autoclicker.claude.action.START"
        const val ACTION_STOP = "com.autoclicker.claude.action.STOP"
        const val ACTION_PAUSE = "com.autoclicker.claude.action.PAUSE"
        const val ACTION_RESUME = "com.autoclicker.claude.action.RESUME"
        const val ACTION_RESTART_LAST = "com.autoclicker.claude.action.RESTART_LAST"
        const val EXTRA_PROFILE_NAME = "profile_name"
        const val EXTRA_PROFILE_ID = "profile_id"
    }
}
