package com.autoclicker.claude.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.autoclicker.claude.data.CommandBus
import com.autoclicker.claude.data.RunState
import com.autoclicker.claude.data.TapCommand

private data class ButtonDef(val rect: RectF, val label: String, val color: Int)

@SuppressLint("ClickableViewAccessibility")
class FloatingToolbarManager(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var toolbarView: ToolbarView? = null

    fun show() {
        if (toolbarView != null) return

        val view = ToolbarView(service)
        val params = WindowManager.LayoutParams(
            160, 400,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            x = 0
        }

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                view.handleTap(event.x, event.y)
            }
            true
        }

        toolbarView = view
        wm.addView(view, params)
    }

    fun dismiss() {
        toolbarView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        toolbarView = null
    }

    fun refresh() {
        toolbarView?.invalidate()
    }

    private inner class ToolbarView(context: Context) : View(context) {

        private val bgPaint = Paint().apply {
            color = Color.argb(220, 30, 35, 50)
            isAntiAlias = true
        }
        private val btnTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        private val statsPaint = Paint().apply {
            color = Color.parseColor("#8B95B0")
            textSize = 22f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        private val buttons = mutableListOf<ButtonDef>()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            buttons.clear()

            val w = width.toFloat()
            val h = height.toFloat()

            // Background
            canvas.drawRoundRect(0f, 0f, w, h, 24f, 24f, bgPaint)

            // Stats
            val stats = CommandBus.stats.value
            val tapsText = "${stats.totalTaps} taps"
            canvas.drawText(tapsText, w / 2f, 50f, statsPaint)

            val elapsed = stats.elapsedMs / 1000
            val timeText = if (elapsed >= 3600) {
                String.format("%d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)
            } else {
                String.format("%d:%02d", elapsed / 60, elapsed % 60)
            }
            canvas.drawText(timeText, w / 2f, 80f, statsPaint)

            // Play/Pause button
            val isPaused = CommandBus.runState.value == RunState.PAUSED
            val playPauseColor = if (isPaused) Color.parseColor("#38BDF8") else Color.parseColor("#34D399")
            val playPauseLabel = if (isPaused) "▶" else "⏸"
            val playBtn = ButtonDef(
                RectF(20f, 110f, w - 20f, 200f),
                playPauseLabel,
                playPauseColor
            )
            buttons.add(playBtn)

            val btnPaint = Paint().apply {
                color = playBtn.color
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(playBtn.rect, 16f, 16f, btnPaint)
            canvas.drawText(playBtn.label, playBtn.rect.centerX(), playBtn.rect.centerY() + 10f, btnTextPaint)

            // Stop button
            val stopBtn = ButtonDef(
                RectF(20f, 220f, w - 20f, 310f),
                "⏹",
                Color.parseColor("#F87171")
            )
            buttons.add(stopBtn)

            val stopPaint = Paint().apply {
                color = stopBtn.color
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(stopBtn.rect, 16f, 16f, stopPaint)
            canvas.drawText(stopBtn.label, stopBtn.rect.centerX(), stopBtn.rect.centerY() + 10f, btnTextPaint)

            // Loop counter
            val loopText = "Loop ${stats.currentLoop}"
            canvas.drawText(loopText, w / 2f, 350f, statsPaint)
            val stepText = "Step ${stats.currentStep}"
            canvas.drawText(stepText, w / 2f, 380f, statsPaint)
        }

        fun handleTap(x: Float, y: Float) {
            buttons.forEach { btn ->
                if (btn.rect.contains(x, y)) {
                    when {
                        btn.label == "⏸" -> CommandBus.send(TapCommand.Pause)
                        btn.label == "▶" -> CommandBus.send(TapCommand.Resume)
                        btn.label == "⏹" -> CommandBus.send(TapCommand.Stop)
                    }
                    invalidate()
                    return
                }
            }
        }
    }
}
