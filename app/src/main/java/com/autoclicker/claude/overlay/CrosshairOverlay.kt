package com.autoclicker.claude.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.autoclicker.claude.data.ClickPoint
import com.autoclicker.claude.data.CommandBus
import com.autoclicker.claude.data.RunState

@SuppressLint("ClickableViewAccessibility")
class CrosshairOverlay(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: CrosshairView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var points: MutableList<ClickPoint> = mutableListOf()
    private var activeIndex = -1

    fun show(targetPoints: List<ClickPoint>) {
        dismiss()
        if (targetPoints.isEmpty()) return
        points = targetPoints.toMutableList()

        val view = CrosshairView(service)
        // IMPORTANT: a full-screen touchable overlay CONSUMES every touch even when
        // its onTouch returns false — returning false does NOT pass the event to the
        // window below. So while the session is running we keep the overlay
        // NOT_TOUCHABLE (pure pass-through: the target app and the pause-on-touch
        // detector both keep working). Only when paused do we make it touchable so
        // the user can drag points. setInteractive() flips this at runtime.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        overlayView = view
        layoutParams = params
        try { wm.addView(view, params) } catch (_: Exception) { overlayView = null }
    }

    /**
     * Toggle whether the overlay intercepts touches. Called by the service on
     * pause (interactive=true, so points can be dragged) and resume/run
     * (interactive=false, so touches pass through to the underlying app).
     */
    fun setInteractive(interactive: Boolean) {
        val p = layoutParams ?: return
        val v = overlayView ?: return
        val flag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val newFlags = if (interactive) p.flags and flag.inv() else p.flags or flag
        if (newFlags == p.flags) return
        p.flags = newFlags
        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
    }

    fun updateActiveIndex(index: Int) {
        activeIndex = index
        overlayView?.invalidate()
    }

    fun updatePoint(index: Int, x: Float, y: Float) {
        if (index !in points.indices) return
        points[index] = points[index].copy(x = x, y = y)
        overlayView?.invalidate()
    }

    fun dismiss() {
        overlayView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlayView = null
        layoutParams = null
        points.clear()
        activeIndex = -1
    }

    private inner class CrosshairView(context: Context) : View(context) {
        private val density = context.resources.displayMetrics.density

        private val crosshairPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            strokeWidth = 1.5f * density
            style = Paint.Style.STROKE
            isAntiAlias = true
            pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
        }
        private val circlePaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            isAntiAlias = true
        }
        private val circleFillPaint = Paint().apply {
            color = Color.parseColor("#1A38BDF8")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val activePaint = Paint().apply {
            color = Color.parseColor("#34D399")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            isAntiAlias = true
        }
        private val activeFillPaint = Paint().apply {
            color = Color.parseColor("#2234D399")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val draggingPaint = Paint().apply {
            color = Color.parseColor("#F59E0B")
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            isAntiAlias = true
        }
        private val draggingFillPaint = Paint().apply {
            color = Color.parseColor("#33F59E0B")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val numberPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            textSize = 10f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        private val activeNumberPaint = Paint().apply {
            color = Color.parseColor("#34D399")
            textSize = 10f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        private val linePaint = Paint().apply {
            color = Color.parseColor("#2238BDF8")
            strokeWidth = 1f * density
            isAntiAlias = true
            pathEffect = DashPathEffect(floatArrayOf(4f * density, 6f * density), 0f)
        }
        private val hintPaint = Paint().apply {
            color = Color.parseColor("#F59E0B")
            textSize = 10f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        private val crossLen = 14f * density
        private val circleR = 18f * density
        private val touchSlop = 36f * density

        private var draggingIndex = -1
        private var dragStartX = 0f
        private var dragStartY = 0f
        private var dragOffsetX = 0f
        private var dragOffsetY = 0f

        init {
            setOnTouchListener { _, event -> handleTouch(event) }
        }

        private fun handleTouch(event: MotionEvent): Boolean {
            // Drag policy: if the user touches a crosshair point, ALWAYS grab it
            // and auto-pause if running. This avoids the StateFlow propagation race
            // where pause-on-touch fires Pause but the runState change hasn't
            // arrived by the time CrosshairView's ACTION_DOWN is processed.
            // Touches that miss every point are passed through.
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val hit = points.indexOfFirst { pt ->
                        val dx = event.x - pt.x
                        val dy = event.y - pt.y
                        (dx * dx + dy * dy) <= touchSlop * touchSlop
                    }
                    if (hit >= 0) {
                        if (CommandBus.runState.value == RunState.RUNNING) {
                            CommandBus.send(com.autoclicker.claude.data.TapCommand.Pause)
                        }
                        draggingIndex = hit
                        dragStartX = event.x
                        dragStartY = event.y
                        dragOffsetX = event.x - points[hit].x
                        dragOffsetY = event.y - points[hit].y
                        invalidate()
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (draggingIndex < 0) return false
                    val newX = (event.x - dragOffsetX).coerceIn(0f, width.toFloat())
                    val newY = (event.y - dragOffsetY).coerceIn(0f, height.toFloat())
                    points[draggingIndex] = points[draggingIndex].copy(x = newX, y = newY)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (draggingIndex < 0) return false
                    val finalX = points[draggingIndex].x
                    val finalY = points[draggingIndex].y
                    CommandBus.send(
                        com.autoclicker.claude.data.TapCommand.MoveCrosshair(draggingIndex, finalX, finalY)
                    )
                    draggingIndex = -1
                    invalidate()
                    return true
                }
            }
            return false
        }

        override fun onDraw(canvas: Canvas) {
            if (points.isEmpty()) return

            for (i in 0 until points.size - 1) {
                canvas.drawLine(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y, linePaint)
            }

            points.forEachIndexed { idx, pt ->
                val isDragging = idx == draggingIndex
                val isActive = idx == activeIndex && !isDragging
                val cPaint = when {
                    isDragging -> draggingPaint
                    isActive -> activePaint
                    else -> circlePaint
                }
                val fPaint = when {
                    isDragging -> draggingFillPaint
                    isActive -> activeFillPaint
                    else -> circleFillPaint
                }
                val nPaint = if (isActive) activeNumberPaint else numberPaint
                val r = if (isActive || isDragging) circleR + 4f * density else circleR

                canvas.drawCircle(pt.x, pt.y, r, fPaint)
                canvas.drawCircle(pt.x, pt.y, r, cPaint)
                canvas.drawLine(pt.x - crossLen, pt.y, pt.x + crossLen, pt.y, cPaint)
                canvas.drawLine(pt.x, pt.y - crossLen, pt.x, pt.y + crossLen, cPaint)
                val dotPaint = Paint(cPaint).apply { style = Paint.Style.FILL }
                canvas.drawCircle(pt.x, pt.y, 2.5f * density, dotPaint)
                canvas.drawText("${idx + 1}", pt.x, pt.y - r - 4f * density, nPaint)
            }

            // Affordance hint: how to move the tap spot. Different message while
            // running (pause first) vs paused (drag now).
            if (draggingIndex < 0 && points.isNotEmpty()) {
                val hint = when (CommandBus.runState.value) {
                    RunState.PAUSED -> "Drag a point to move it, then Resume"
                    RunState.RUNNING -> "This is where it taps · Pause to move it"
                    else -> null
                }
                if (hint != null) {
                    canvas.drawText(
                        hint,
                        points[0].x,
                        (points[0].y + circleR + 18f * density).coerceAtMost(height.toFloat() - 8f * density),
                        hintPaint
                    )
                }
            }
        }
    }
}
