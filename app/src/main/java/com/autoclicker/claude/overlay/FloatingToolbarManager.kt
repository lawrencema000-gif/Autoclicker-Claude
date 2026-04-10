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
import kotlin.math.ln
import kotlin.math.exp

private data class ButtonDef(val rect: RectF, val label: String, val color: Int)

@SuppressLint("ClickableViewAccessibility")
class FloatingToolbarManager(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var toolbarView: ToolbarView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartParamX = 0
    private var dragStartParamY = 0
    private val dragThreshold = 10f

    private var expanded = false

    fun show() {
        if (toolbarView != null) return
        expanded = false

        val density = service.resources.displayMetrics.density
        val widthPx = (52 * density).toInt()
        val heightPx = (160 * density).toInt()

        val view = ToolbarView(service)
        val params = WindowManager.LayoutParams(
            widthPx, heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
                    dragStartX = event.rawX; dragStartY = event.rawY
                    dragStartParamX = p.x; dragStartParamY = p.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragStartX; val dy = event.rawY - dragStartY
                    if (!isDragging && (dx * dx + dy * dy) > dragThreshold * dragThreshold) isDragging = true
                    if (isDragging) {
                        p.x = (dragStartParamX + dx).toInt(); p.y = (dragStartParamY + dy).toInt()
                        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) (v as ToolbarView).handleTap(event.x, event.y)
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
        toolbarView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        toolbarView = null; layoutParams = null; expanded = false
    }

    fun refresh() { toolbarView?.invalidate() }

    private fun toggleExpanded() {
        expanded = !expanded
        val density = service.resources.displayMetrics.density
        val p = layoutParams ?: return
        if (expanded) {
            p.width = (180 * density).toInt()
            p.height = (320 * density).toInt()
        } else {
            p.width = (52 * density).toInt()
            p.height = (160 * density).toInt()
        }
        try { wm.updateViewLayout(toolbarView, p) } catch (_: Exception) {}
        toolbarView?.invalidate()
    }

    private inner class ToolbarView(context: Context) : View(context) {
        private val density = context.resources.displayMetrics.density
        private val bgPaint = Paint().apply { color = Color.argb(235, 15, 23, 42); isAntiAlias = true }
        private val btnPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val btnTextPaint = Paint().apply { color = Color.WHITE; textSize = 11f * density; textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true }
        private val statsPaint = Paint().apply { color = Color.parseColor("#94A3B8"); textSize = 9f * density; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        private val tapCountPaint = Paint().apply { color = Color.WHITE; textSize = 13f * density; textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true }
        private val dragHintPaint = Paint().apply { color = Color.argb(80, 255, 255, 255); strokeWidth = 2f * density; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
        private val sectionPaint = Paint().apply { color = Color.parseColor("#64748B"); textSize = 8f * density; textAlign = Paint.Align.LEFT; isAntiAlias = true }
        private val sliderTrackPaint = Paint().apply { color = Color.parseColor("#334155"); strokeWidth = 3f * density; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
        private val sliderThumbPaint = Paint().apply { color = Color.parseColor("#38BDF8"); isAntiAlias = true; style = Paint.Style.FILL }
        private val sliderValuePaint = Paint().apply { color = Color.parseColor("#38BDF8"); textSize = 10f * density; textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true }

        private val buttons = mutableListOf<ButtonDef>()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            buttons.clear()
            val w = width.toFloat(); val h = height.toFloat()
            val pad = 6f * density; val btnH = 30f * density; val gap = 4f * density

            canvas.drawRoundRect(0f, 0f, w, h, 16f * density, 16f * density, bgPaint)

            // Drag handle
            val dotY = 8f * density; val dotR = 1.5f * density
            canvas.drawCircle(w / 2f - 6f * density, dotY, dotR, dragHintPaint)
            canvas.drawCircle(w / 2f, dotY, dotR, dragHintPaint)
            canvas.drawCircle(w / 2f + 6f * density, dotY, dotR, dragHintPaint)

            var y = 16f * density
            val stats = CommandBus.stats.value

            if (!expanded) {
                // COMPACT VIEW
                canvas.drawText("${stats.totalTaps}", w / 2f, y + 12f * density, tapCountPaint)
                y += 16f * density
                val elapsed = stats.elapsedMs / 1000
                val timeText = if (elapsed >= 3600) String.format("%d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)
                else String.format("%d:%02d", elapsed / 60, elapsed % 60)
                canvas.drawText(timeText, w / 2f, y + 10f * density, statsPaint)
                y += 16f * density

                // Play/Pause
                val isPaused = CommandBus.runState.value == RunState.PAUSED
                val playColor = if (isPaused) Color.parseColor("#38BDF8") else Color.parseColor("#34D399")
                val playLabel = if (isPaused) "▶" else "⏸"
                val playRect = RectF(pad, y, w - pad, y + btnH)
                buttons.add(ButtonDef(playRect, playLabel, playColor))
                btnPaint.color = playColor
                canvas.drawRoundRect(playRect, 8f * density, 8f * density, btnPaint)
                canvas.drawText(playLabel, playRect.centerX(), playRect.centerY() + 4f * density, btnTextPaint)
                y += btnH + gap

                // Stop
                val stopRect = RectF(pad, y, w - pad, y + btnH)
                buttons.add(ButtonDef(stopRect, "⏹", Color.parseColor("#F87171")))
                btnPaint.color = Color.parseColor("#F87171")
                canvas.drawRoundRect(stopRect, 8f * density, 8f * density, btnPaint)
                canvas.drawText("⏹", stopRect.centerX(), stopRect.centerY() + 4f * density, btnTextPaint)
                y += btnH + gap

                // Gear (expand)
                val gearRect = RectF(pad, y, w - pad, y + btnH)
                buttons.add(ButtonDef(gearRect, "⚙", Color.parseColor("#475569")))
                btnPaint.color = Color.parseColor("#475569")
                canvas.drawRoundRect(gearRect, 8f * density, 8f * density, btnPaint)
                canvas.drawText("⚙", gearRect.centerX(), gearRect.centerY() + 4f * density, btnTextPaint)

            } else {
                // EXPANDED VIEW
                // Stats row
                canvas.drawText("${stats.totalTaps} taps", w / 2f, y + 12f * density, tapCountPaint)
                y += 16f * density
                val elapsed = stats.elapsedMs / 1000
                val timeText = if (elapsed >= 3600) String.format("%d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60)
                else String.format("%d:%02d", elapsed / 60, elapsed % 60)
                canvas.drawText("$timeText  L${stats.currentLoop}", w / 2f, y + 10f * density, statsPaint)
                y += 18f * density

                // Control buttons row
                val halfW = (w - pad * 3) / 2
                val isPaused = CommandBus.runState.value == RunState.PAUSED
                val playColor = if (isPaused) Color.parseColor("#38BDF8") else Color.parseColor("#34D399")
                val playLabel = if (isPaused) "▶" else "⏸"

                val playRect = RectF(pad, y, pad + halfW, y + btnH)
                buttons.add(ButtonDef(playRect, playLabel, playColor))
                btnPaint.color = playColor
                canvas.drawRoundRect(playRect, 8f * density, 8f * density, btnPaint)
                canvas.drawText(playLabel, playRect.centerX(), playRect.centerY() + 4f * density, btnTextPaint)

                val stopRect = RectF(pad * 2 + halfW, y, w - pad, y + btnH)
                buttons.add(ButtonDef(stopRect, "⏹", Color.parseColor("#F87171")))
                btnPaint.color = Color.parseColor("#F87171")
                canvas.drawRoundRect(stopRect, 8f * density, 8f * density, btnPaint)
                canvas.drawText("⏹", stopRect.centerX(), stopRect.centerY() + 4f * density, btnTextPaint)
                y += btnH + 8f * density

                // Speed section
                canvas.drawText("SPEED", pad, y + 8f * density, sectionPaint)
                y += 14f * density

                // Speed presets row
                val presets = listOf("Turbo" to 10L, "Fast" to 100L, "Normal" to 500L, "Slow" to 2000L)
                val presetW = (w - pad * 2 - gap * 3) / 4
                val currentInterval = CommandBus.liveIntervalOverride.value.takeIf { it > 0 }
                    ?: CommandBus.defaultSettingsState.value.intervalMs
                presets.forEachIndexed { i, (label, ms) ->
                    val px = pad + i * (presetW + gap)
                    val rect = RectF(px, y, px + presetW, y + 24f * density)
                    val selected = currentInterval == ms
                    val bgCol = if (selected) Color.parseColor("#38BDF8") else Color.parseColor("#1E293B")
                    buttons.add(ButtonDef(rect, "speed_$ms", bgCol))
                    btnPaint.color = bgCol
                    canvas.drawRoundRect(rect, 6f * density, 6f * density, btnPaint)
                    val txtCol = if (selected) Color.parseColor("#0F172A") else Color.parseColor("#94A3B8")
                    val textP = Paint(btnTextPaint).apply {
                        textSize = 7f * density
                        color = txtCol
                    }
                    canvas.drawText(label, rect.centerX(), rect.centerY() + 3f * density, textP)
                }
                y += 24f * density + 8f * density

                // Speed slider
                val sliderLeft = pad + 8f * density
                val sliderRight = w - pad - 8f * density
                val sliderY = y + 8f * density
                canvas.drawLine(sliderLeft, sliderY, sliderRight, sliderY, sliderTrackPaint)
                // Log scale: 10ms to 5000ms
                val logMin = ln(10.0); val logMax = ln(5000.0)
                val logCurrent = ln(currentInterval.toDouble().coerceIn(10.0, 5000.0))
                val thumbFraction = ((logCurrent - logMin) / (logMax - logMin)).toFloat()
                val thumbX = sliderLeft + thumbFraction * (sliderRight - sliderLeft)
                canvas.drawCircle(thumbX, sliderY, 6f * density, sliderThumbPaint)
                // Slider hit area
                val sliderRect = RectF(sliderLeft - 10f, y - 4f * density, sliderRight + 10f, y + 20f * density)
                buttons.add(ButtonDef(sliderRect, "slider", 0))
                // Value label
                val intervalLabel = if (currentInterval < 1000) "${currentInterval}ms" else "${currentInterval / 1000}s"
                canvas.drawText(intervalLabel, w / 2f, y + 24f * density, sliderValuePaint)
                y += 32f * density

                // Action buttons
                canvas.drawText("ACTIONS", pad, y + 8f * density, sectionPaint)
                y += 14f * density

                // Pick Points button
                val pickRect = RectF(pad, y, w - pad, y + btnH)
                buttons.add(ButtonDef(pickRect, "pick", Color.parseColor("#0EA5E9")))
                btnPaint.color = Color.parseColor("#0EA5E9")
                canvas.drawRoundRect(pickRect, 8f * density, 8f * density, btnPaint)
                canvas.drawText("Pick Points", pickRect.centerX(), pickRect.centerY() + 4f * density, btnTextPaint)
                y += btnH + gap

                // Profiles button
                val profilesRect = RectF(pad, y, w - pad, y + btnH)
                buttons.add(ButtonDef(profilesRect, "profiles", Color.parseColor("#7C3AED")))
                btnPaint.color = Color.parseColor("#7C3AED")
                canvas.drawRoundRect(profilesRect, 8f * density, 8f * density, btnPaint)
                canvas.drawText("Profiles", profilesRect.centerX(), profilesRect.centerY() + 4f * density, btnTextPaint)
                y += btnH + gap

                // Collapse button
                val collapseRect = RectF(pad, y, w - pad, y + btnH)
                buttons.add(ButtonDef(collapseRect, "collapse", Color.parseColor("#334155")))
                btnPaint.color = Color.parseColor("#334155")
                canvas.drawRoundRect(collapseRect, 8f * density, 8f * density, btnPaint)
                canvas.drawText("Close", collapseRect.centerX(), collapseRect.centerY() + 4f * density, btnTextPaint)
            }
        }

        fun handleTap(x: Float, y: Float) {
            for (btn in buttons) {
                if (btn.rect.contains(x, y)) {
                    when {
                        btn.label == "⏸" -> CommandBus.send(TapCommand.Pause)
                        btn.label == "▶" -> CommandBus.send(TapCommand.Resume)
                        btn.label == "⏹" -> CommandBus.send(TapCommand.Stop)
                        btn.label == "⚙" || btn.label == "collapse" -> toggleExpanded()
                        btn.label.startsWith("speed_") -> {
                            val ms = btn.label.removePrefix("speed_").toLongOrNull() ?: return
                            CommandBus.updateInterval(ms)
                        }
                        btn.label == "slider" -> {
                            // Map tap X to interval value (log scale)
                            val sliderLeft = btn.rect.left + 10f
                            val sliderRight = btn.rect.right - 10f
                            val fraction = ((x - sliderLeft) / (sliderRight - sliderLeft)).coerceIn(0f, 1f)
                            val logMin = ln(10.0); val logMax = ln(5000.0)
                            val ms = exp(logMin + fraction * (logMax - logMin)).toLong().coerceIn(10, 5000)
                            CommandBus.updateInterval(ms)
                        }
                        btn.label == "pick" -> {
                            CommandBus.send(TapCommand.Stop)
                            CommandBus.send(TapCommand.EnterPickMode(true))
                            toggleExpanded()
                        }
                        btn.label == "profiles" -> {
                            CommandBus.send(TapCommand.ShowProfilePicker)
                            toggleExpanded()
                        }
                    }
                    invalidate()
                    return
                }
            }
        }
    }
}
