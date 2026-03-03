package com.autoclicker.claude.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.autoclicker.claude.R
import com.autoclicker.claude.model.ClickMode
import com.autoclicker.claude.model.ClickPoint
import com.autoclicker.claude.model.ClickScript
import com.autoclicker.claude.ui.main.MainActivity

class FloatingWidgetService : Service() {

    companion object {
        const val CHANNEL_ID = "autoclicker_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.autoclicker.claude.STOP"
        var instance: FloatingWidgetService? = null
            private set
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var targetViews = mutableListOf<View>()
    private var currentScript = ClickScript()
    private var isExpanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingWidget()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeAllTargets()
        floatingView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        instance = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingWidget() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_widget, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        windowManager.addView(floatingView, params)

        val btnPlay = floatingView!!.findViewById<ImageButton>(R.id.btn_play)
        val btnPause = floatingView!!.findViewById<ImageButton>(R.id.btn_pause)
        val btnStop = floatingView!!.findViewById<ImageButton>(R.id.btn_stop)
        val btnAddTarget = floatingView!!.findViewById<ImageButton>(R.id.btn_add_target)
        val btnClose = floatingView!!.findViewById<ImageButton>(R.id.btn_close)
        val tvClickCount = floatingView!!.findViewById<TextView>(R.id.tv_click_count)
        val expandedPanel = floatingView!!.findViewById<LinearLayout>(R.id.expanded_panel)
        val btnToggle = floatingView!!.findViewById<ImageButton>(R.id.btn_toggle)

        expandedPanel.visibility = View.GONE

        btnToggle.setOnClickListener {
            isExpanded = !isExpanded
            expandedPanel.visibility = if (isExpanded) View.VISIBLE else View.GONE
            btnToggle.setImageResource(
                if (isExpanded) R.drawable.ic_collapse else R.drawable.ic_expand
            )
        }

        btnPlay.setOnClickListener {
            val service = AutoClickService.instance ?: return@setOnClickListener
            if (AutoClickService.isRunning) {
                service.resumeClicking()
            } else {
                if (currentScript.clickPoints.isEmpty() && targetViews.isEmpty()) {
                    // Default: single click at center of screen
                    val metrics = resources.displayMetrics
                    currentScript.clickPoints.add(
                        ClickPoint(
                            x = metrics.widthPixels / 2f,
                            y = metrics.heightPixels / 2f,
                            delay = currentScript.globalInterval
                        )
                    )
                }
                service.setOnClickCountUpdate { count ->
                    tvClickCount.text = "$count clicks"
                }
                service.startClicking(currentScript)
            }
            btnPlay.visibility = View.GONE
            btnPause.visibility = View.VISIBLE
        }

        btnPause.setOnClickListener {
            AutoClickService.instance?.pauseClicking()
            btnPlay.visibility = View.VISIBLE
            btnPause.visibility = View.GONE
        }

        btnStop.setOnClickListener {
            AutoClickService.instance?.stopClicking()
            btnPlay.visibility = View.VISIBLE
            btnPause.visibility = View.GONE
            tvClickCount.text = "0 clicks"
        }

        btnAddTarget.setOnClickListener {
            addClickTarget()
        }

        btnClose.setOnClickListener {
            AutoClickService.instance?.stopClicking()
            removeAllTargets()
            stopSelf()
        }

        // Drag functionality
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        btnToggle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        AutoClickService.instance?.setOnStatusUpdate { running ->
            if (!running) {
                btnPlay.visibility = View.VISIBLE
                btnPause.visibility = View.GONE
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addClickTarget() {
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2
        val centerY = metrics.heightPixels / 2

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val targetView = inflater.inflate(R.layout.click_target, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = centerX - 30
            y = centerY - 30
        }

        windowManager.addView(targetView, params)
        targetViews.add(targetView)

        val targetIndex = targetViews.size - 1
        val tvNumber = targetView.findViewById<TextView>(R.id.tv_target_number)
        val btnRemove = targetView.findViewById<ImageButton>(R.id.btn_remove_target)
        tvNumber.text = "${targetIndex + 1}"

        // Add to script
        val clickPoint = ClickPoint(
            x = (centerX).toFloat(),
            y = (centerY).toFloat(),
            delay = currentScript.globalInterval
        )
        if (targetIndex < currentScript.clickPoints.size) {
            currentScript.clickPoints[targetIndex] = clickPoint
        } else {
            currentScript.clickPoints.add(clickPoint)
        }
        currentScript.mode = if (currentScript.clickPoints.size > 1) ClickMode.MULTI else ClickMode.SINGLE

        btnRemove.setOnClickListener {
            val idx = targetViews.indexOf(targetView)
            if (idx >= 0 && idx < currentScript.clickPoints.size) {
                currentScript.clickPoints.removeAt(idx)
            }
            targetViews.remove(targetView)
            try { windowManager.removeView(targetView) } catch (_: Exception) {}
            updateTargetNumbers()
        }

        // Drag target
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        targetView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(targetView, params)

                    // Update click point position
                    val idx = targetViews.indexOf(targetView)
                    if (idx >= 0 && idx < currentScript.clickPoints.size) {
                        currentScript.clickPoints[idx].x = params.x.toFloat() + 30
                        currentScript.clickPoints[idx].y = params.y.toFloat() + 30
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateTargetNumbers() {
        targetViews.forEachIndexed { index, view ->
            view.findViewById<TextView>(R.id.tv_target_number)?.text = "${index + 1}"
        }
    }

    private fun removeAllTargets() {
        targetViews.forEach { view ->
            try { windowManager.removeView(view) } catch (_: Exception) {}
        }
        targetViews.clear()
        currentScript.clickPoints.clear()
    }

    fun updateInterval(interval: Long) {
        currentScript.globalInterval = interval
        currentScript.clickPoints.forEach { it.delay = interval }
    }

    fun updateRepeatCount(count: Int) {
        currentScript.repeatCount = count
    }

    fun setScript(script: ClickScript) {
        currentScript = script
    }

    fun getCurrentScript(): ClickScript = currentScript

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Clicker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Auto Clicker service notification"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingWidgetService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Auto Clicker Running")
                .setContentText("Tap to open app")
                .setSmallIcon(R.drawable.ic_click)
                .setContentIntent(pendingIntent)
                .addAction(Notification.Action.Builder(null, "Stop", stopPendingIntent).build())
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Auto Clicker Running")
                .setContentText("Tap to open app")
                .setSmallIcon(R.drawable.ic_click)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }
}
