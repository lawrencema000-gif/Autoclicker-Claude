package com.autoclicker.claude.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.autoclicker.claude.data.CommandBus
import com.autoclicker.claude.data.RunState
import com.autoclicker.claude.data.TapCommand

@SuppressLint("ClickableViewAccessibility")
class FloatingBubbleManager(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubbleView: BubbleView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartParamX = 0
    private var dragStartParamY = 0
    private val dragThreshold = 10f

    fun show() {
        if (bubbleView != null) return
        val density = service.resources.displayMetrics.density
        val sizePx = (48 * density).toInt()
        val screenW = service.resources.displayMetrics.widthPixels
        val screenH = service.resources.displayMetrics.heightPixels
        val margin = (16 * density).toInt()

        val view = BubbleView(service)
        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Clamp to screen bounds (so bubble can't appear off-screen on rotation)
            x = (screenW - sizePx - margin).coerceIn(0, screenW - sizePx)
            y = (screenH / 2).coerceIn(0, screenH - sizePx)
        }

        // Start pulse animation for first 3 seconds to draw user's attention
        view.startPulseAnimation()

        var touchDownTime = 0L

        view.setOnTouchListener { v, event ->
            val p = layoutParams ?: return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    touchDownTime = System.currentTimeMillis()
                    dragStartX = event.rawX; dragStartY = event.rawY
                    dragStartParamX = p.x; dragStartParamY = p.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragStartX; val dy = event.rawY - dragStartY
                    if (!isDragging && (dx * dx + dy * dy) > dragThreshold * dragThreshold) isDragging = true
                    if (isDragging) {
                        val screenW = service.resources.displayMetrics.widthPixels
                        val screenH = service.resources.displayMetrics.heightPixels
                        p.x = (dragStartParamX + dx).toInt().coerceIn(0, screenW - p.width)
                        p.y = (dragStartParamY + dy).toInt().coerceIn(0, screenH - p.height)
                        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val holdDuration = System.currentTimeMillis() - touchDownTime
                    if (!isDragging) {
                        if (holdDuration >= 500) {
                            // Long press: enter pick mode
                            CommandBus.send(TapCommand.EnterPickMode(true))
                        } else {
                            // Short tap: toggle play/pause or re-run
                            when (CommandBus.runState.value) {
                                RunState.IDLE -> CommandBus.lastProfile.value?.let { CommandBus.send(TapCommand.StartProfile(it)) }
                                RunState.RUNNING -> CommandBus.send(TapCommand.Pause)
                                RunState.PAUSED -> CommandBus.send(TapCommand.Resume)
                            }
                        }
                        view.invalidate()
                    }
                    isDragging = false
                }
            }
            true
        }

        bubbleView = view
        layoutParams = params
        wm.addView(view, params)
    }

    fun dismiss() {
        bubbleView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubbleView = null
        layoutParams = null
    }

    fun refresh() {
        bubbleView?.invalidate()
    }

    private inner class BubbleView(context: Context) : View(context) {
        private val density = context.resources.displayMetrics.density

        private val bgPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        private val iconPaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        private val shadowPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.argb(40, 0, 0, 0)
        }
        private val ringPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }

        private var pulseStartTime = 0L
        private val pulseDurationMs = 3000L // pulse for 3 seconds
        private val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { invalidate() }
        }

        fun startPulseAnimation() {
            pulseStartTime = System.currentTimeMillis()
            animator.start()
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = width / 2f - 2f * density

            // Pulse ring (only during first 3 seconds)
            val elapsed = System.currentTimeMillis() - pulseStartTime
            if (pulseStartTime > 0 && elapsed < pulseDurationMs) {
                val phase = ((elapsed % 1200L) / 1200f)
                val pulseR = r + (phase * 14f * density)
                val pulseAlpha = ((1f - phase) * 180f).toInt().coerceIn(0, 255)
                ringPaint.color = Color.argb(pulseAlpha, 0x38, 0xBD, 0xF8)
                canvas.drawCircle(cx, cy, pulseR, ringPaint)
            } else if (pulseStartTime > 0) {
                // Pulse complete — stop animator
                animator.cancel()
                pulseStartTime = 0L
            }

            // Shadow
            canvas.drawCircle(cx, cy + 2f * density, r, shadowPaint)

            // Background color based on state
            bgPaint.color = when (CommandBus.runState.value) {
                RunState.IDLE -> Color.parseColor("#0369A1")      // blue
                RunState.RUNNING -> Color.parseColor("#059669")   // green
                RunState.PAUSED -> Color.parseColor("#D97706")    // amber
            }
            canvas.drawCircle(cx, cy, r, bgPaint)

            // Icon
            val icon = when (CommandBus.runState.value) {
                RunState.IDLE -> "▶"
                RunState.RUNNING -> "⏸"
                RunState.PAUSED -> "▶"
            }
            canvas.drawText(icon, cx, cy + 6f * density, iconPaint)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            animator.cancel()
        }
    }
}

