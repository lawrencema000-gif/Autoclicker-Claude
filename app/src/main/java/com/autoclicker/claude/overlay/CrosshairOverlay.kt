package com.autoclicker.claude.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.autoclicker.claude.data.ClickPoint
import com.autoclicker.claude.data.CommandBus

@SuppressLint("ClickableViewAccessibility")
class CrosshairOverlay(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: CrosshairView? = null
    private var points: List<ClickPoint> = emptyList()
    private var activeIndex = -1

    fun show(targetPoints: List<ClickPoint>) {
        dismiss()
        if (targetPoints.isEmpty()) return
        points = targetPoints

        val view = CrosshairView(service)
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
        wm.addView(view, params)
    }

    fun updateActiveIndex(index: Int) {
        activeIndex = index
        overlayView?.invalidate()
    }

    fun dismiss() {
        overlayView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlayView = null
        points = emptyList()
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
            color = Color.parseColor("#1A38BDF8") // very transparent
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

        private val crossLen = 14f * density
        private val circleR = 18f * density

        override fun onDraw(canvas: Canvas) {
            if (points.isEmpty()) return

            // Draw connecting lines between points (execution order)
            for (i in 0 until points.size - 1) {
                canvas.drawLine(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y, linePaint)
            }

            // Draw each crosshair
            points.forEachIndexed { idx, pt ->
                val isActive = idx == activeIndex
                val cPaint = if (isActive) activePaint else circlePaint
                val fPaint = if (isActive) activeFillPaint else circleFillPaint
                val nPaint = if (isActive) activeNumberPaint else numberPaint
                val r = if (isActive) circleR + 4f * density else circleR

                // Target circle
                canvas.drawCircle(pt.x, pt.y, r, fPaint)
                canvas.drawCircle(pt.x, pt.y, r, cPaint)

                // Crosshair lines
                canvas.drawLine(pt.x - crossLen, pt.y, pt.x + crossLen, pt.y, cPaint)
                canvas.drawLine(pt.x, pt.y - crossLen, pt.x, pt.y + crossLen, cPaint)

                // Center dot
                val dotPaint = Paint(cPaint).apply { style = Paint.Style.FILL }
                canvas.drawCircle(pt.x, pt.y, 2.5f * density, dotPaint)

                // Step number (above the crosshair)
                canvas.drawText("${idx + 1}", pt.x, pt.y - r - 4f * density, nPaint)
            }
        }
    }
}
