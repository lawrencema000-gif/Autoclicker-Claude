package com.autoclicker.claude.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.autoclicker.claude.data.ClickMode
import com.autoclicker.claude.data.ProfileRepository
import com.autoclicker.claude.data.TapProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@SuppressLint("ClickableViewAccessibility")
class ProfilePickerOverlay(
    private val service: AccessibilityService,
    private val onProfileSelected: (TapProfile) -> Unit
) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var panelView: PanelView? = null
    private val repo = ProfileRepository(service)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var profiles: List<TapProfile> = emptyList()

    fun show() {
        if (panelView != null) return
        scope.launch {
            profiles = repo.profiles.first().sortedByDescending { it.updatedAt }
            showPanel()
        }
    }

    private fun showPanel() {
        val metrics = service.resources.displayMetrics
        val density = metrics.density
        val panelW = (metrics.widthPixels * 0.72f).toInt()
        val panelH = metrics.heightPixels

        val view = PanelView(service)
        val params = WindowManager.LayoutParams(
            panelW, panelH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
        }

        var scrollY = 0f
        var lastTouchY = 0f
        var isScrolling = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchY = event.y
                    isScrolling = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - lastTouchY
                    if (kotlin.math.abs(dy) > 8f) isScrolling = true
                    if (isScrolling) {
                        scrollY = (scrollY - dy).coerceAtLeast(0f)
                            .coerceAtMost((profiles.size * 80f * density - panelH + 120f * density).coerceAtLeast(0f))
                        lastTouchY = event.y
                        view.setScrollOffset(scrollY)
                        view.invalidate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!isScrolling) {
                        val tapIndex = view.getProfileIndexAt(event.y)
                        if (tapIndex == -1) {
                            dismiss() // tapped close area
                        } else if (tapIndex in profiles.indices) {
                            val selected = profiles[tapIndex]
                            dismiss()
                            onProfileSelected(selected)
                        }
                    }
                }
            }
            true
        }

        panelView = view
        wm.addView(view, params)
    }

    fun dismiss() {
        panelView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panelView = null
    }

    private inner class PanelView(context: Context) : View(context) {
        private val density = context.resources.displayMetrics.density
        private var scrollOffset = 0f

        private val bgPaint = Paint().apply { color = Color.argb(240, 15, 23, 42); isAntiAlias = true }
        private val scrimPaint = Paint().apply { color = Color.argb(120, 0, 0, 0) }
        private val titlePaint = Paint().apply { color = Color.WHITE; textSize = 18f * density; isFakeBoldText = true; isAntiAlias = true }
        private val closePaint = Paint().apply { color = Color.parseColor("#F87171"); textSize = 14f * density; textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true }
        private val namePaint = Paint().apply { color = Color.WHITE; textSize = 14f * density; isAntiAlias = true; isFakeBoldText = true }
        private val detailPaint = Paint().apply { color = Color.parseColor("#94A3B8"); textSize = 10f * density; isAntiAlias = true }
        private val cardPaint = Paint().apply { color = Color.parseColor("#1E293B"); isAntiAlias = true }
        private val playPaint = Paint().apply { color = Color.parseColor("#34D399"); isAntiAlias = true; style = Paint.Style.FILL }
        private val playTextPaint = Paint().apply { color = Color.WHITE; textSize = 12f * density; textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true }
        private val emptyPaint = Paint().apply { color = Color.parseColor("#64748B"); textSize = 13f * density; textAlign = Paint.Align.CENTER; isAntiAlias = true }

        fun setScrollOffset(offset: Float) { scrollOffset = offset }

        fun getProfileIndexAt(y: Float): Int {
            val headerH = 56f * density
            if (y < headerH) return -1 // close area
            val adjustedY = y + scrollOffset - headerH
            val cardH = 70f * density
            val gap = 10f * density
            return (adjustedY / (cardH + gap)).toInt()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val pad = 16f * density
            val headerH = 56f * density
            val cardH = 70f * density
            val gap = 10f * density

            // Background panel
            canvas.drawRect(0f, 0f, w, h, bgPaint)

            // Header
            canvas.drawText("Profiles", pad, 36f * density, titlePaint)
            val closeRect = RectF(w - 48f * density, 8f * density, w - 8f * density, 48f * density)
            canvas.drawText("X", closeRect.centerX(), closeRect.centerY() + 5f * density, closePaint)

            if (profiles.isEmpty()) {
                canvas.drawText("No saved profiles", w / 2f, h / 2f, emptyPaint)
                return
            }

            // Clip to content area
            canvas.save()
            canvas.clipRect(0f, headerH, w, h)

            profiles.forEachIndexed { index, profile ->
                val cardTop = headerH + index * (cardH + gap) - scrollOffset
                if (cardTop + cardH < headerH || cardTop > h) return@forEachIndexed // skip off-screen

                val cardRect = RectF(pad, cardTop, w - pad, cardTop + cardH)
                canvas.drawRoundRect(cardRect, 12f * density, 12f * density, cardPaint)

                // Profile name
                canvas.drawText(profile.name, pad + 12f * density, cardTop + 28f * density, namePaint)

                // Details
                val modeStr = when (profile.mode) {
                    ClickMode.SINGLE_POINT -> "Single"
                    ClickMode.MULTI_POINT -> "Multi"
                    ClickMode.PATTERN_MODE -> "Pattern"
                    else -> "Other"
                }
                val detail = "$modeStr | ${profile.steps.size} steps | ${profile.intervalMs}ms"
                canvas.drawText(detail, pad + 12f * density, cardTop + 48f * density, detailPaint)

                // Play button
                val playRect = RectF(w - pad - 44f * density, cardTop + 15f * density, w - pad - 4f * density, cardTop + cardH - 15f * density)
                canvas.drawRoundRect(playRect, 8f * density, 8f * density, playPaint)
                canvas.drawText("▶", playRect.centerX(), playRect.centerY() + 5f * density, playTextPaint)
            }

            canvas.restore()
        }
    }
}
