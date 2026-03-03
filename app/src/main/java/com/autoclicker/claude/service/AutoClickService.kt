package com.autoclicker.claude.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.autoclicker.claude.model.ClickMode
import com.autoclicker.claude.model.ClickPoint
import com.autoclicker.claude.model.ClickScript
import com.autoclicker.claude.model.SwipeAction

class AutoClickService : AccessibilityService() {

    companion object {
        var instance: AutoClickService? = null
            private set
        var isRunning = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentScript: ClickScript? = null
    private var currentRepeat = 0
    private var currentIndex = 0
    private var isPaused = false
    private var totalClicks = 0L
    private var onClickCountUpdate: ((Long) -> Unit)? = null
    private var onStatusUpdate: ((Boolean) -> Unit)? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopClicking()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClicking()
        instance = null
    }

    fun setOnClickCountUpdate(listener: ((Long) -> Unit)?) {
        onClickCountUpdate = listener
    }

    fun setOnStatusUpdate(listener: ((Boolean) -> Unit)?) {
        onStatusUpdate = listener
    }

    fun startClicking(script: ClickScript) {
        if (isRunning) return
        currentScript = script
        currentRepeat = 0
        currentIndex = 0
        totalClicks = 0
        isRunning = true
        isPaused = false
        onStatusUpdate?.invoke(true)
        executeNext()
    }

    fun stopClicking() {
        isRunning = false
        isPaused = false
        handler.removeCallbacksAndMessages(null)
        onStatusUpdate?.invoke(false)
    }

    fun pauseClicking() {
        isPaused = true
        handler.removeCallbacksAndMessages(null)
    }

    fun resumeClicking() {
        if (!isRunning) return
        isPaused = false
        executeNext()
    }

    fun getTotalClicks(): Long = totalClicks

    private fun executeNext() {
        if (!isRunning || isPaused) return
        val script = currentScript ?: return

        when (script.mode) {
            ClickMode.SINGLE -> executeSingleMode(script)
            ClickMode.MULTI -> executeMultiMode(script)
        }
    }

    private fun executeSingleMode(script: ClickScript) {
        if (script.clickPoints.isEmpty()) return
        val point = script.clickPoints[0]

        performClick(point.x, point.y, point.duration) {
            totalClicks++
            onClickCountUpdate?.invoke(totalClicks)

            if (script.repeatCount > 0) {
                currentRepeat++
                if (currentRepeat >= script.repeatCount) {
                    stopClicking()
                    return@performClick
                }
            }

            if (isRunning && !isPaused) {
                handler.postDelayed({ executeNext() }, point.delay)
            }
        }
    }

    private fun executeMultiMode(script: ClickScript) {
        val totalActions = script.clickPoints.size + script.swipeActions.size

        if (totalActions == 0) return

        if (currentIndex < script.clickPoints.size) {
            val point = script.clickPoints[currentIndex]
            performClick(point.x, point.y, point.duration) {
                totalClicks++
                onClickCountUpdate?.invoke(totalClicks)
                currentIndex++

                if (currentIndex >= totalActions) {
                    currentIndex = 0
                    if (script.repeatCount > 0) {
                        currentRepeat++
                        if (currentRepeat >= script.repeatCount) {
                            stopClicking()
                            return@performClick
                        }
                    }
                }

                if (isRunning && !isPaused) {
                    handler.postDelayed({ executeNext() }, point.delay)
                }
            }
        } else {
            val swipeIndex = currentIndex - script.clickPoints.size
            if (swipeIndex < script.swipeActions.size) {
                val swipe = script.swipeActions[swipeIndex]
                performSwipe(swipe) {
                    currentIndex++
                    if (currentIndex >= totalActions) {
                        currentIndex = 0
                        if (script.repeatCount > 0) {
                            currentRepeat++
                            if (currentRepeat >= script.repeatCount) {
                                stopClicking()
                                return@performSwipe
                            }
                        }
                    }

                    if (isRunning && !isPaused) {
                        handler.postDelayed({ executeNext() }, swipe.delay)
                    }
                }
            }
        }
    }

    private fun performClick(x: Float, y: Float, duration: Long, onComplete: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val path = Path().apply { moveTo(x, y) }
        val clickDuration = if (duration < 1) 1L else duration

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, clickDuration))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                if (isRunning && !isPaused) onComplete()
            }
        }, handler)
    }

    private fun performSwipe(swipe: SwipeAction, onComplete: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val path = Path().apply {
            moveTo(swipe.startX, swipe.startY)
            lineTo(swipe.endX, swipe.endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, swipe.duration))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                if (isRunning && !isPaused) onComplete()
            }
        }, handler)
    }
}
