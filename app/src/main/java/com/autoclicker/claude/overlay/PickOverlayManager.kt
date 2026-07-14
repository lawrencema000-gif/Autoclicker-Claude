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

@SuppressLint("ClickableViewAccessibility")
class PickOverlayManager(private val service: AccessibilityService) {

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density
    private var overlayView: PickOverlayView? = null
    private var multiPick = false
    private val pickedPoints = mutableListOf<Pair<Float, Float>>()
    private var currentTouch: Pair<Float, Float>? = null

    fun show(multi: Boolean) {
        if (overlayView != null) return
        multiPick = multi
        pickedPoints.clear()
        currentTouch = null
        CommandBus.setPickModeActive(true)

        val view = PickOverlayView(service)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    currentTouch = Pair(event.rawX, event.rawY)
                    view.invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    val x = event.rawX
                    val y = event.rawY
                    val metrics = service.resources.displayMetrics

                    // 0. CANCEL button hit? (always available — lets a user who
                    //    entered pick mode by accident back out without placing a point)
                    val cancelRect = getCancelRect(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
                    if (cancelRect.contains(x, y)) {
                        pickedPoints.clear()
                        CommandBus.clearPickResults()
                        dismiss()
                        return@setOnTouchListener true
                    }

                    // 1. DONE button hit?
                    if (multiPick && pickedPoints.isNotEmpty()) {
                        val btnRect = getDoneButtonRect(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
                        if (btnRect.contains(x, y)) {
                            dismiss()
                            return@setOnTouchListener true
                        }
                        // 2. CLEAR-ALL button hit?
                        val clearRect = getClearAllRect(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
                        if (clearRect.contains(x, y)) {
                            pickedPoints.clear()
                            CommandBus.clearPickResults()
                            triggerServiceHaptic()
                            currentTouch = null
                            view.invalidate()
                            return@setOnTouchListener true
                        }
                    }

                    // 3. Tap on an EXISTING point removes it (within ~36dp radius).
                    val density = metrics.density
                    val removeRadius = 36f * density
                    val hitIndex = pickedPoints.indexOfLast { (px, py) ->
                        val dx = x - px; val dy = y - py
                        (dx * dx + dy * dy) <= removeRadius * removeRadius
                    }
                    if (hitIndex >= 0) {
                        pickedPoints.removeAt(hitIndex)
                        // Tell the listener side which index was removed so it can
                        // drop the corresponding ClickPoint from the in-flight list.
                        CommandBus.emitPickRemove(hitIndex)
                        triggerServiceHaptic()
                        currentTouch = null
                        view.invalidate()
                        return@setOnTouchListener true
                    }

                    // 4. Otherwise add a new point at the tap position.
                    val cx = x.coerceIn(0f, metrics.widthPixels.toFloat())
                    val cy = y.coerceIn(0f, metrics.heightPixels.toFloat())
                    pickedPoints.add(Pair(cx, cy))
                    CommandBus.emitPickResult(cx, cy)
                    currentTouch = null
                    triggerServiceHaptic()

                    if (!multiPick) {
                        dismiss()
                    } else {
                        view.invalidate()
                    }
                }
            }
            true
        }

        overlayView = view
        try { wm.addView(view, params) } catch (_: Exception) {
            overlayView = null
            CommandBus.setPickModeActive(false)
        }
    }

    fun dismiss() {
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        pickedPoints.clear()
        currentTouch = null
        CommandBus.setPickModeActive(false)
        CommandBus.send(com.autoclicker.claude.data.TapCommand.ExitPickMode)
    }

    private fun triggerServiceHaptic() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = service.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vm.defaultVibrator.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = service.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                v.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    private fun getDoneButtonRect(screenW: Float, screenH: Float): RectF {
        val btnW = 96f * density
        val btnH = 44f * density
        val margin = 20f * density
        val bottomGap = 48f * density
        return RectF(
            screenW - btnW - margin,
            screenH - btnH - margin - bottomGap,
            screenW - margin,
            screenH - margin - bottomGap + btnH
        )
    }

    private fun getClearAllRect(screenW: Float, screenH: Float): RectF {
        val btnW = 96f * density
        val btnH = 44f * density
        val margin = 20f * density
        val bottomGap = 48f * density
        return RectF(
            margin,
            screenH - btnH - margin - bottomGap,
            margin + btnW,
            screenH - margin - bottomGap + btnH
        )
    }

    // Top-right cancel pill, clear of the status bar.
    private fun getCancelRect(screenW: Float, screenH: Float): RectF {
        val btnW = 96f * density
        val btnH = 40f * density
        val margin = 16f * density
        val topGap = 44f * density
        return RectF(
            screenW - btnW - margin,
            topGap,
            screenW - margin,
            topGap + btnH
        )
    }

    private inner class PickOverlayView(context: Context) : View(context) {
        private val d = context.resources.displayMetrics.density
        private val bgPaint = Paint().apply { color = Color.argb(80, 0, 0, 0) }
        private val crossPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            strokeWidth = 1.5f * d
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        private val crossFillPaint = Paint().apply {
            color = Color.parseColor("#2238BDF8")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val markerFillPaint = Paint().apply {
            color = Color.parseColor("#4438BDF8")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val markerStrokePaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 2f * d
            isAntiAlias = true
        }
        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 15f * d
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        private val coordPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            textSize = 12f * d
            isAntiAlias = true
        }
        private val btnPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val btnTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 15f * d
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        private val instrPaint = Paint().apply {
            color = Color.parseColor("#CCFFFFFF")
            textSize = 13f * d
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Semi-transparent overlay
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Draw picked point markers
            pickedPoints.forEachIndexed { index, (px, py) ->
                canvas.drawCircle(px, py, 14f * d, markerFillPaint)
                canvas.drawCircle(px, py, 14f * d, markerStrokePaint)
                canvas.drawText("${index + 1}", px, py + 5f * d, textPaint)
            }

            // Draw current touch crosshair
            currentTouch?.let { (cx, cy) ->
                canvas.drawCircle(cx, cy, 20f * d, crossFillPaint)
                canvas.drawCircle(cx, cy, 20f * d, crossPaint)
                canvas.drawLine(cx - 30f * d, cy, cx + 30f * d, cy, crossPaint)
                canvas.drawLine(cx, cy - 30f * d, cx, cy + 30f * d, crossPaint)
                canvas.drawText(
                    "(${cx.toInt()}, ${cy.toInt()})",
                    cx + 28f * d, cy - 10f * d, coordPaint
                )
            }

            // Draw DONE + CLEAR buttons for multi-pick
            if (multiPick && pickedPoints.isNotEmpty()) {
                val btnRect = getDoneButtonRect(width.toFloat(), height.toFloat())
                canvas.drawRoundRect(btnRect, 10f * d, 10f * d, btnPaint)
                canvas.drawText("DONE (${pickedPoints.size})", btnRect.centerX(), btnRect.centerY() + 5f * d, btnTextPaint)

                val clearRect = getClearAllRect(width.toFloat(), height.toFloat())
                val clearBgPaint = Paint(btnPaint).apply { color = Color.parseColor("#475569") }
                canvas.drawRoundRect(clearRect, 10f * d, 10f * d, clearBgPaint)
                canvas.drawText("CLEAR ALL", clearRect.centerX(), clearRect.centerY() + 5f * d, btnTextPaint)
            }

            // Cancel pill (top-right) — always available
            val cancelRect = getCancelRect(width.toFloat(), height.toFloat())
            val cancelBgPaint = Paint(btnPaint).apply { color = Color.parseColor("#334155") }
            canvas.drawRoundRect(cancelRect, 10f * d, 10f * d, cancelBgPaint)
            canvas.drawText("✕ Cancel", cancelRect.centerX(), cancelRect.centerY() + 5f * d, btnTextPaint)

            // Instruction text
            val instrText = if (multiPick) "Tap where you want each tap. Tap a number to remove it. DONE when ready."
            else "Tap the exact spot on screen you want tapped"
            canvas.drawText(instrText, width / 2f, 50f * d, instrPaint)
        }
    }
}
