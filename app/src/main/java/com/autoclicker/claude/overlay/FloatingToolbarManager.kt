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
    private var layoutParams: WindowManager.LayoutParams? = null

    // Drag state
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartParamX = 0
    private var dragStartParamY = 0
    private val dragThreshold = 10f

    fun show() {
        if (toolbarView != null) return

        val density = service.resources.displayMetrics.density
        val widthPx = (52 * density).toInt()
        val heightPx = (160 * density).toInt()

        val view = ToolbarView(service)
        val params = WindowManager.LayoutParams(
            widthPx, heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = service.resources.displayMetrics.heightPixels / 3
        }

        view.setOnTouchListener { v, event ->
            val p = layoutParams ?: return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    dragStartParamX = p.x
                    dragStartParamY = p.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragStartX
                    val dy = event.rawY - dragStartY
                    if (!isDragging && (dx * dx + dy * dy) > dragThreshold * dragThreshold) {
                        isDragging = true
                    }
                    if (isDragging) {
                        p.x = (dragStartParamX + dx).toInt()
                        p.y = (dragStartParamY + dy).toInt()
                        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        (v as ToolbarView).handleTap(event.x, event.y)
                    }
                    isDragging = false
                }
            }
            true
        }

        toolbarView = view
        layoutParams = params
        wm.addView(view, params)
    }

    fun dismiss() {
        toolbarView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        toolbarView = null
        layoutParams = null
    }

    fun refresh() {
        toolbarView?.invalidate()
    }

    private inner class ToolbarView(context: Context) : View(context) {

        private val density = context.resources.displayMetrics.density

        private val bgPaint = Paint().apply {
            color = Color.argb(230, 20, 25, 40)
            isAntiAlias = true
        }
        private val btnPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        private val btnTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 11f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        private val statsPaint = Paint().apply {
            color = Color.parseColor("#B0B8CC")
            textSize = 9f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        private val tapCountPaint = Paint().apply {
            color = Color.WHITE
            textSize = 13f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        private val dragHintPaint = Paint().apply {
            color = Color.argb(80, 255, 255, 255)
            strokeWidth = 2f * density
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        private val buttons = mutableListOf<ButtonDef>()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            buttons.clear()

            val w = width.toFloat()
            val h = height.toFloat()
            val pad = 6f * density
            val btnH = 32f * density
            val gap = 5f * density

            // Background pill
            canvas.drawRoundRect(0f, 0f, w, h, 16f * density, 16f * density, bgPaint)

            // Drag handle (3 small dots at top)
            val dotY = 8f * density
            val dotR = 1.5f * density
            canvas.drawCircle(w / 2f - 6f * density, dotY, dotR, dragHintPaint)
            canvas.drawCircle(w / 2f, dotY, dotR, dragHintPaint)
            canvas.drawCircle(w / 2f + 6f * density, dotY, dotR, dragHintPaint)

            var yOffset = 16f * density

            // Tap count
            val stats = CommandBus.stats.value
            canvas.drawText("${stats.totalTaps}", w / 2f, yOffset + 12f * density, tapCountPaint)
            yOffset += 16f * density

            // Time
            val elapsed = stats.elapsedMs / 1000
            val timeText = if (elapsed >= 3600) {
                String.format("%d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)
            } else {
                String.format("%d:%02d", elapsed / 60, elapsed % 60)
            }
            canvas.drawText(timeText, w / 2f, yOffset + 10f * density, statsPaint)
            yOffset += 16f * density

            // Play/Pause button
            val isPaused = CommandBus.runState.value == RunState.PAUSED
            val playColor = if (isPaused) Color.parseColor("#38BDF8") else Color.parseColor("#34D399")
            val playLabel = if (isPaused) "▶" else "⏸"
            val playRect = RectF(pad, yOffset, w - pad, yOffset + btnH)
            buttons.add(ButtonDef(playRect, playLabel, playColor))
            btnPaint.color = playColor
            canvas.drawRoundRect(playRect, 10f * density, 10f * density, btnPaint)
            canvas.drawText(playLabel, playRect.centerX(), playRect.centerY() + 4f * density, btnTextPaint)
            yOffset += btnH + gap

            // Stop button
            val stopRect = RectF(pad, yOffset, w - pad, yOffset + btnH)
            buttons.add(ButtonDef(stopRect, "⏹", Color.parseColor("#F87171")))
            btnPaint.color = Color.parseColor("#F87171")
            canvas.drawRoundRect(stopRect, 10f * density, 10f * density, btnPaint)
            canvas.drawText("⏹", stopRect.centerX(), stopRect.centerY() + 4f * density, btnTextPaint)
            yOffset += btnH + gap

            // Loop/Step
            canvas.drawText("L${stats.currentLoop} S${stats.currentStep}", w / 2f, yOffset + 10f * density, statsPaint)
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
