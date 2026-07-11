package com.autoclicker.claude.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.autoclicker.claude.R
import com.autoclicker.claude.data.CommandBus
import com.autoclicker.claude.data.TapCommand
import com.autoclicker.claude.ui.MainActivity

class TapForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "autoclicker_active"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP = "com.autoclicker.claude.ACTION_STOP"
        private const val ACTION_PAUSE = "com.autoclicker.claude.ACTION_PAUSE"
        private const val ACTION_RESUME = "com.autoclicker.claude.ACTION_RESUME"
        private const val EXTRA_PROFILE_NAME = "profile_name"
        private const val EXTRA_IS_PAUSED = "is_paused"
        private const val EXTRA_TAP_COUNT = "tap_count"
        private const val EXTRA_ELAPSED_MS = "elapsed_ms"

        fun start(context: Context, profileName: String = "") {
            val intent = Intent(context, TapForegroundService::class.java).apply {
                putExtra(EXTRA_PROFILE_NAME, profileName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateState(context: Context, profileName: String, isPaused: Boolean) {
            val intent = Intent(context, TapForegroundService::class.java).apply {
                putExtra(EXTRA_PROFILE_NAME, profileName)
                putExtra(EXTRA_IS_PAUSED, isPaused)
            }
            context.startService(intent)
        }

        fun updateStats(context: Context, profileName: String, tapCount: Int, elapsedMs: Long) {
            val intent = Intent(context, TapForegroundService::class.java).apply {
                putExtra(EXTRA_PROFILE_NAME, profileName)
                putExtra(EXTRA_TAP_COUNT, tapCount)
                putExtra(EXTRA_ELAPSED_MS, elapsedMs)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TapForegroundService::class.java))
        }
    }

    private var profileName = ""
    private var tapCount = 0
    private var elapsedMs = 0L
    private var isPaused = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // System auto-restarted us with a null intent (START_STICKY). We must
            // still call startForeground within ~5s or Android throws
            // ForegroundServiceDidNotStartInTimeException. The real click session
            // is gone, so post the notification then stop cleanly.
            startForeground(NOTIFICATION_ID, buildNotification(isPaused))
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_STOP -> {
                CommandBus.send(TapCommand.Stop)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                CommandBus.send(TapCommand.Pause)
                profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: profileName
                updateNotification(true)
                return START_STICKY
            }
            ACTION_RESUME -> {
                CommandBus.send(TapCommand.Resume)
                profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: profileName
                updateNotification(false)
                return START_STICKY
            }
        }

        profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: profileName
        // Preserve the current paused state when the extra is absent (e.g. a stats
        // refresh) so a mid-run stats update doesn't wipe the "Paused" label.
        isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, isPaused)
        tapCount = intent.getIntExtra(EXTRA_TAP_COUNT, tapCount)
        elapsedMs = intent.getLongExtra(EXTRA_ELAPSED_MS, elapsedMs)

        startForeground(NOTIFICATION_ID, buildNotification(isPaused))
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateNotification(isPaused: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(isPaused))
    }

    private fun buildNotification(isPaused: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TapForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleAction = if (isPaused) {
            val resumeIntent = PendingIntent.getService(
                this, 2,
                Intent(this, TapForegroundService::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            Notification.Action.Builder(null, "Resume", resumeIntent).build()
        } else {
            val pauseIntent = PendingIntent.getService(
                this, 2,
                Intent(this, TapForegroundService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            Notification.Action.Builder(null, "Pause", pauseIntent).build()
        }

        val title = if (profileName.isNotBlank()) "Auto Clicker: $profileName" else "Auto Clicker Running"
        val elapsed = elapsedMs / 1000
        val timeStr = if (elapsed >= 3600) String.format("%d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)
        else String.format("%d:%02d", elapsed / 60, elapsed % 60)
        val text = if (isPaused) "Paused — tap Resume to continue"
        else "$tapCount taps • $timeStr"

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_click)
            .setContentIntent(openIntent)
            .addAction(toggleAction)
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Clicker Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while Auto Clicker is running"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
