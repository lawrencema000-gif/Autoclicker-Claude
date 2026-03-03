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

                    // Check if DONE button was tapped
                    if (multiPick && pickedPoints.isNotEmpty()) {
                        val metrics = service.resources.displayMetrics
                        val btnRect = getDoneButtonRect(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
                        if (btnRect.contains(x, y)) {
                            dismiss()
                            return@setOnTouchListener true
                        }
                    }

                    pickedPoints.add(Pair(x, y))
                    CommandBus.emitPickResult(x, y)
                    currentTouch = null

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
        wm.addView(view, params)
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

    private fun getDoneButtonRect(screenW: Float, screenH: Float): RectF {
        val btnW = 180f
        val btnH = 70f
        val margin = 40f
        return RectF(
            screenW - btnW - margin,
            screenH - btnH - margin - 80f,
            screenW - margin,
            screenH - margin - 80f + btnH
        )
    }

    private inner class PickOverlayView(context: Context) : View(context) {
        private val bgPaint = Paint().apply { color = Color.argb(80, 0, 0, 0) }
        private val crossPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        private val crossFillPaint = Paint().apply {
            color = Color.parseColor("#2238BDF8")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val markerPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val markerStrokePaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        private val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        private val coordPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            textSize = 28f
            isAntiAlias = true
        }
        private val btnPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val btnTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Semi-transparent overlay
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Draw picked point markers
            pickedPoints.forEachIndexed { index, (px, py) ->
                canvas.drawCircle(px, py, 28f, Paint().apply {
                    color = Color.parseColor("#4438BDF8")
                    style = Paint.Style.FILL
                    isAntiAlias = true
                })
                canvas.drawCircle(px, py, 28f, markerStrokePaint)
                canvas.drawText("${index + 1}", px, py + 12f, textPaint)
            }

            // Draw current touch crosshair
            currentTouch?.let { (cx, cy) ->
                canvas.drawCircle(cx, cy, 40f, crossFillPaint)
                canvas.drawCircle(cx, cy, 40f, crossPaint)
                canvas.drawLine(cx - 60f, cy, cx + 60f, cy, crossPaint)
                canvas.drawLine(cx, cy - 60f, cx, cy + 60f, crossPaint)
                canvas.drawText(
                    "(${cx.toInt()}, ${cy.toInt()})",
                    cx + 55f, cy - 20f, coordPaint
                )
            }

            // Draw DONE button for multi-pick
            if (multiPick && pickedPoints.isNotEmpty()) {
                val btnRect = getDoneButtonRect(width.toFloat(), height.toFloat())
                canvas.drawRoundRect(btnRect, 20f, 20f, btnPaint)
                canvas.drawText(
                    "DONE",
                    btnRect.centerX(),
                    btnRect.centerY() + 12f,
                    btnTextPaint
                )
            }

            // Instruction text
            val instrText = if (multiPick) "Tap to add points, press DONE when finished"
            else "Tap anywhere to select a point"
            val instrPaint = Paint().apply {
                color = Color.parseColor("#CCFFFFFF")
                textSize = 30f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.drawText(instrText, width / 2f, 100f, instrPaint)
        }
    }
}
