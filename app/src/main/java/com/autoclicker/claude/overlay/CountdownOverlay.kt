package com.autoclicker.claude.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.*

@SuppressLint("ClickableViewAccessibility")
class CountdownOverlay(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: CountdownView? = null
    @Volatile private var cancelled = false

    /**
     * @param onComplete runs when the countdown finishes normally.
     * @param onCancel   runs if the user taps the overlay to cancel before it fires.
     */
    @SuppressLint("ClickableViewAccessibility")
    suspend fun showCountdown(seconds: Int = 3, onCancel: (() -> Unit)? = null, onComplete: () -> Unit) {
        if (overlayView != null) return
        cancelled = false

        val view = CountdownView(service)
        // Touchable so the user can tap anywhere to cancel before clicking begins.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        view.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) cancelled = true
            true
        }

        overlayView = view
        try { wm.addView(view, params) } catch (_: Exception) { overlayView = null; onComplete(); return }

        for (i in seconds downTo 1) {
            if (cancelled) { dismiss(); onCancel?.invoke(); return }
            view.setCount(i)
            delay(1000)
        }

        if (cancelled) { dismiss(); onCancel?.invoke(); return }
        dismiss()
        onComplete()
    }

    fun dismiss() {
        overlayView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlayView = null
    }

    private inner class CountdownView(context: Context) : View(context) {
        private var count = 3
        private val density = context.resources.displayMetrics.density

        private val bgPaint = Paint().apply { color = Color.argb(120, 0, 0, 0) }
        private val circlePaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            isAntiAlias = true
            style = Paint.Style.FILL
            alpha = 200
        }
        private val numberPaint = Paint().apply {
            color = Color.WHITE
            textSize = 80f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        private val labelPaint = Paint().apply {
            color = Color.parseColor("#CCFFFFFF")
            textSize = 18f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        fun setCount(c: Int) {
            count = c
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            val cx = width / 2f; val cy = height / 2f
            canvas.drawCircle(cx, cy, 80f * density, circlePaint)
            canvas.drawText("$count", cx, cy + 28f * density, numberPaint)
            canvas.drawText("Starting soon…  tap anywhere to cancel", cx, cy + 70f * density, labelPaint)
        }
    }
}
