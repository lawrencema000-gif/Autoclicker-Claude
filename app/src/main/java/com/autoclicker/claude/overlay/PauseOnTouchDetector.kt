package com.autoclicker.claude.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.autoclicker.claude.data.CommandBus
import com.autoclicker.claude.data.RunState
import com.autoclicker.claude.data.TapCommand

/**
 * Pause-on-touch detector. Universal #1 request across every Android auto-clicker
 * — users want to briefly touch their phone (scroll, dismiss a popup) without
 * killing the macro.
 *
 * Approach: a 1×1 px transparent window at the screen corner with the magic
 * combo of FLAG_NOT_TOUCHABLE | FLAG_WATCH_OUTSIDE_TOUCH. Android delivers an
 * ACTION_OUTSIDE event to this window the first time the user puts a finger
 * down outside it (i.e. anywhere else on screen). We pause execution, then
 * arm a resume timer; further touches reset it; after [resumeAfterMs] of
 * idle, we resume.
 *
 * Caveats:
 * - Only the first ACTION_DOWN of an interaction fires ACTION_OUTSIDE; a slow
 *   drag or a long hold won't deliver continuous events. We compensate by
 *   keeping the resume timer at ~1.5s, so a held finger stays paused.
 * - Our own dispatched taps don't fire ACTION_OUTSIDE (synthetic gestures
 *   don't notify other windows), so the macro never pauses itself.
 * - Crosshair-overlay drags during pause remain handled inside CrosshairView.
 */
@SuppressLint("ClickableViewAccessibility")
class PauseOnTouchDetector(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var sentinelView: View? = null
    private var lastTouchAt = 0L
    private var autoPaused = false
    private var resumeAfterMs = 1500L

    fun show(resumeDelayMs: Long = 1500L) {
        if (sentinelView != null) return
        resumeAfterMs = resumeDelayMs

        val view = View(service)
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                onUserTouchedScreen()
            }
            false
        }
        sentinelView = view
        try {
            wm.addView(view, params)
        } catch (_: Exception) {
            sentinelView = null
        }
    }

    fun dismiss() {
        sentinelView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        sentinelView = null
        handler.removeCallbacksAndMessages(null)
        autoPaused = false
    }

    private fun onUserTouchedScreen() {
        lastTouchAt = SystemClock.uptimeMillis()
        // Only auto-pause an actively running session. Don't intervene if the
        // user paused manually or already stopped.
        if (CommandBus.runState.value == RunState.RUNNING && !autoPaused) {
            autoPaused = true
            CommandBus.send(TapCommand.Pause)
        }
        scheduleResume()
    }

    private fun scheduleResume() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            val idle = SystemClock.uptimeMillis() - lastTouchAt
            if (autoPaused && idle >= resumeAfterMs && CommandBus.runState.value == RunState.PAUSED) {
                autoPaused = false
                CommandBus.send(TapCommand.Resume)
            }
        }, resumeAfterMs + 50)
    }
}
