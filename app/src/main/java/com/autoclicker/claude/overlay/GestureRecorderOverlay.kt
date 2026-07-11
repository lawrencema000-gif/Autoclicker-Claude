package com.autoclicker.claude.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.autoclicker.claude.data.ActionType
import com.autoclicker.claude.data.ClickPoint
import com.autoclicker.claude.data.CommandBus

@SuppressLint("ClickableViewAccessibility")
class GestureRecorderOverlay(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: RecorderView? = null

    private val recordedSteps = mutableListOf<ClickPoint>()
    private val currentPath = mutableListOf<Pair<Float, Float>>()
    private var touchDownTime = 0L
    private var onRecordingComplete: ((List<ClickPoint>) -> Unit)? = null

    fun show(onComplete: (List<ClickPoint>) -> Unit) {
        if (overlayView != null) return
        recordedSteps.clear()
        onRecordingComplete = onComplete

        val view = RecorderView(service)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        view.setOnTouchListener { _, event ->
            val density = service.resources.displayMetrics.density
            val screenW = service.resources.displayMetrics.widthPixels.toFloat()
            val screenH = service.resources.displayMetrics.heightPixels.toFloat()

            // Density-scaled button rects, kept identical to the ones drawn in onDraw.
            val btnW = 160f * density; val btnH = 60f * density
            val margin = 30f * density; val bottomGap = 60f * density
            val doneRect = RectF(screenW - btnW - margin, screenH - btnH - margin - bottomGap, screenW - margin, screenH - margin - bottomGap + btnH)
            val cancelRect = RectF(margin, screenH - btnH - margin - bottomGap, margin + btnW, screenH - margin - bottomGap + btnH)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Only treat a bottom-right tap as DONE when the button is
                    // actually drawn (i.e. at least one step recorded); otherwise
                    // a first tap in that region would end recording immediately.
                    if (recordedSteps.isNotEmpty() && doneRect.contains(event.rawX, event.rawY)) {
                        dismiss()
                        onRecordingComplete?.invoke(recordedSteps.toList())
                        return@setOnTouchListener true
                    }
                    if (cancelRect.contains(event.rawX, event.rawY)) {
                        dismiss()
                        onRecordingComplete?.invoke(emptyList())
                        return@setOnTouchListener true
                    }
                    currentPath.clear()
                    currentPath.add(Pair(event.rawX, event.rawY))
                    touchDownTime = System.currentTimeMillis()
                    view.invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    currentPath.add(Pair(event.rawX, event.rawY))
                    view.invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    if (currentPath.isEmpty()) return@setOnTouchListener true
                    val duration = System.currentTimeMillis() - touchDownTime
                    val startX = currentPath.first().first
                    val startY = currentPath.first().second
                    val endX = event.rawX
                    val endY = event.rawY

                    val distance = Math.hypot((endX - startX).toDouble(), (endY - startY).toDouble())

                    if (distance < 20) {
                        // It's a tap
                        recordedSteps.add(ClickPoint(
                            action = if (duration > 300) ActionType.LONG_PRESS else ActionType.TAP,
                            x = startX, y = startY,
                            holdDuration = duration.coerceAtLeast(50),
                            delayBefore = 100L,
                            label = "Tap ${recordedSteps.size + 1}",
                            order = recordedSteps.size
                        ))
                    } else {
                        // It's a swipe
                        recordedSteps.add(ClickPoint(
                            action = ActionType.SWIPE,
                            x = startX, y = startY,
                            swipeToX = endX, swipeToY = endY,
                            swipeDuration = duration.coerceIn(50, 5000),
                            delayBefore = 100L,
                            label = "Swipe ${recordedSteps.size + 1}",
                            order = recordedSteps.size
                        ))
                    }
                    currentPath.clear()
                    view.invalidate()
                }
            }
            true
        }

        overlayView = view
        try { wm.addView(view, params) } catch (_: Exception) { overlayView = null }
    }

    fun dismiss() {
        overlayView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlayView = null
        currentPath.clear()
    }

    private inner class RecorderView(context: Context) : View(context) {
        private val d = context.resources.displayMetrics.density
        private val bgPaint = Paint().apply { color = Color.argb(60, 0, 0, 0) }
        private val pathPaint = Paint().apply {
            color = Color.parseColor("#FF5722")
            strokeWidth = 6f * d
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val dotPaint = Paint().apply { color = Color.parseColor("#FF5722"); isAntiAlias = true; style = Paint.Style.FILL }
        private val tapMarkerPaint = Paint().apply { color = Color.parseColor("#4438BDF8"); isAntiAlias = true; style = Paint.Style.FILL }
        private val tapStrokePaint = Paint().apply { color = Color.parseColor("#38BDF8"); isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 3f * d }
        private val instrPaint = Paint().apply { color = Color.parseColor("#CCFFFFFF"); textSize = 14f * d; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        private val btnPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val btnTextPaint = Paint().apply { color = Color.WHITE; textSize = 15f * d; textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true }
        private val countPaint = Paint().apply { color = Color.WHITE; textSize = 12f * d; textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Draw recorded steps as markers
            recordedSteps.forEachIndexed { i, step ->
                when (step.action) {
                    ActionType.TAP, ActionType.LONG_PRESS -> {
                        canvas.drawCircle(step.x, step.y, 12f * d, tapMarkerPaint)
                        canvas.drawCircle(step.x, step.y, 12f * d, tapStrokePaint)
                        canvas.drawText("${i + 1}", step.x, step.y + 5f * d, countPaint)
                    }
                    ActionType.SWIPE -> {
                        canvas.drawCircle(step.x, step.y, 6f * d, dotPaint)
                        canvas.drawLine(step.x, step.y, step.swipeToX, step.swipeToY, pathPaint)
                        canvas.drawCircle(step.swipeToX, step.swipeToY, 6f * d, dotPaint)
                    }
                    else -> {}
                }
            }

            // Draw current gesture path
            if (currentPath.size > 1) {
                for (i in 0 until currentPath.size - 1) {
                    canvas.drawLine(currentPath[i].first, currentPath[i].second, currentPath[i + 1].first, currentPath[i + 1].second, pathPaint)
                }
            }
            if (currentPath.isNotEmpty()) {
                canvas.drawCircle(currentPath.last().first, currentPath.last().second, 4f * d, dotPaint)
            }

            // Instructions
            canvas.drawText("Record gestures: tap or swipe on screen", width / 2f, 40f * d, instrPaint)
            canvas.drawText("${recordedSteps.size} steps recorded", width / 2f, 60f * d, instrPaint)

            // DONE / CANCEL buttons (density-scaled, matching the touch hit-tests)
            val w = width.toFloat(); val h = height.toFloat()
            val btnW = 160f * d; val btnH = 60f * d; val margin = 30f * d; val bottomGap = 60f * d
            if (recordedSteps.isNotEmpty()) {
                val doneRect = RectF(w - btnW - margin, h - btnH - margin - bottomGap, w - margin, h - margin - bottomGap + btnH)
                btnPaint.color = Color.parseColor("#059669")
                canvas.drawRoundRect(doneRect, 8f * d, 8f * d, btnPaint)
                canvas.drawText("DONE", doneRect.centerX(), doneRect.centerY() + 5f * d, btnTextPaint)
            }
            val cancelRect = RectF(margin, h - btnH - margin - bottomGap, margin + btnW, h - margin - bottomGap + btnH)
            btnPaint.color = Color.parseColor("#DC2626")
            canvas.drawRoundRect(cancelRect, 8f * d, 8f * d, btnPaint)
            canvas.drawText("CANCEL", cancelRect.centerX(), cancelRect.centerY() + 5f * d, btnTextPaint)
        }
    }
}
