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

        fun stop(context: Context) {
            context.stopService(Intent(context, TapForegroundService::class.java))
        }
    }

    private var profileName = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

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

        profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: ""
        val isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false)

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

        val title = if (profileName.isNotBlank()) "AutoClicker: $profileName" else "AutoClicker Running"
        val text = if (isPaused) "Paused — tap Resume to continue" else "Tapping in progress"

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
                "AutoClicker Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while AutoClicker is running"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
