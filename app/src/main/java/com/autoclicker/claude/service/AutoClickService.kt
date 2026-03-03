package com.autoclicker.claude.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.autoclicker.claude.data.*
import com.autoclicker.claude.overlay.FloatingToolbarManager
import com.autoclicker.claude.overlay.PickOverlayManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.random.Random

class AutoClickService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var executionJob: Job? = null
    private val paused = AtomicBoolean(false)
    private var tapCount = 0
    private var startTimeMs = 0L

    private var pickOverlay: PickOverlayManager? = null
    private var floatingToolbar: FloatingToolbarManager? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var currentRules: ClickRule? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        CommandBus.setServiceConnected(true)

        pickOverlay = PickOverlayManager(this)
        floatingToolbar = FloatingToolbarManager(this)

        scope.launch {
            CommandBus.commands.collect { cmd ->
                when (cmd) {
                    is TapCommand.StartProfile -> startProfile(cmd.profile)
                    is TapCommand.QuickStart -> {
                        val profile = TapProfile(
                            name = "Quick Start",
                            mode = cmd.mode,
                            steps = cmd.points,
                            intervalMs = cmd.settings.intervalMs,
                            rules = ClickRule(
                                maxTaps = if (cmd.settings.stopCondition == StopCondition.AFTER_TAPS) cmd.settings.stopValue else 0,
                                maxDurationMs = if (cmd.settings.stopCondition == StopCondition.AFTER_SECONDS) cmd.settings.stopValue * 1000L else 0
                            )
                        )
                        startProfile(profile)
                    }
                    is TapCommand.Stop -> stopExecution()
                    is TapCommand.Pause -> pauseExecution()
                    is TapCommand.Resume -> resumeExecution()
                    is TapCommand.EnterPickMode -> pickOverlay?.show(cmd.multiPick)
                    is TapCommand.ExitPickMode -> { /* handled by PickOverlayManager */ }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopExecution() }

    override fun onUnbind(intent: Intent?): Boolean {
        stopExecution()
        pickOverlay?.dismiss()
        floatingToolbar?.dismiss()
        CommandBus.setServiceConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopExecution()
        pickOverlay?.dismiss()
        floatingToolbar?.dismiss()
        CommandBus.setServiceConnected(false)
        scope.cancel()
    }

    private fun startProfile(profile: TapProfile) {
        stopExecution()

        tapCount = 0
        startTimeMs = System.currentTimeMillis()
        paused.set(false)
        currentRules = profile.rules

        CommandBus.setRunState(RunState.RUNNING)
        CommandBus.updateStats(ExecutionStats(profileName = profile.name))

        // Register screen off receiver
        if (profile.rules.stopOnScreenOff) {
            registerScreenOffReceiver()
        }

        // Show floating toolbar
        floatingToolbar?.show()

        // Start foreground service
        TapForegroundService.start(this, profile.name)

        executionJob = scope.launch {
            try {
                // Start delay
                if (profile.rules.startDelayMs > 0) {
                    delay(profile.rules.startDelayMs)
                }

                val maxLoops = if (profile.loopCount <= 0) Int.MAX_VALUE else profile.loopCount

                for (loop in 1..maxLoops) {
                    if (!isActive) break

                    for ((stepIdx, step) in profile.steps.withIndex()) {
                        if (!isActive) break
                        waitWhilePaused()
                        if (shouldStop(profile.rules)) break

                        // Per-step delay
                        val stepDelay = computeDelay(step.delayBefore, profile.rules)
                        if (stepDelay > 0) delay(stepDelay)

                        // Execute action with repeat
                        for (rep in 0 until max(1, step.repeatCount)) {
                            if (!isActive || shouldStop(profile.rules)) break
                            waitWhilePaused()

                            when (step.action) {
                                ActionType.TAP -> {
                                    dispatchTap(step.x, step.y, step.holdDuration)
                                    tapCount++
                                }
                                ActionType.SWIPE -> {
                                    dispatchSwipe(step.x, step.y, step.swipeToX, step.swipeToY, step.swipeDuration)
                                    tapCount++
                                }
                                ActionType.LONG_PRESS -> {
                                    dispatchTap(step.x, step.y, step.holdDuration.coerceAtLeast(400L))
                                    tapCount++
                                }
                                ActionType.DELAY -> {
                                    delay(step.delayBefore)
                                }
                            }

                            // Update stats
                            CommandBus.updateStats(
                                ExecutionStats(
                                    totalTaps = tapCount,
                                    elapsedMs = System.currentTimeMillis() - startTimeMs,
                                    currentStep = stepIdx + 1,
                                    currentLoop = loop,
                                    profileName = profile.name
                                )
                            )
                            floatingToolbar?.refresh()

                            // Inter-repeat interval
                            if (rep < step.repeatCount - 1) {
                                delay(profile.intervalMs)
                            }
                        }

                        // Inter-step interval
                        if (stepIdx < profile.steps.size - 1) {
                            delay(profile.intervalMs)
                        }
                    }

                    if (shouldStop(profile.rules)) break

                    // Pause between loops
                    if (loop < maxLoops && profile.rules.pauseBetweenLoops > 0) {
                        delay(profile.rules.pauseBetweenLoops)
                    }
                }
            } finally {
                stopExecution()
            }
        }
    }

    private fun stopExecution() {
        executionJob?.cancel()
        executionJob = null
        paused.set(false)
        currentRules = null

        unregisterScreenOffReceiver()
        floatingToolbar?.dismiss()
        TapForegroundService.stop(this)

        CommandBus.setRunState(RunState.IDLE)
    }

    private fun pauseExecution() {
        paused.set(true)
        CommandBus.setRunState(RunState.PAUSED)
        floatingToolbar?.refresh()
        TapForegroundService.updateState(this, CommandBus.stats.value.profileName, true)
    }

    private fun resumeExecution() {
        paused.set(false)
        CommandBus.setRunState(RunState.RUNNING)
        floatingToolbar?.refresh()
        TapForegroundService.updateState(this, CommandBus.stats.value.profileName, false)
    }

    private suspend fun waitWhilePaused() {
        while (paused.get()) {
            delay(100)
        }
    }

    private fun shouldStop(rules: ClickRule): Boolean {
        if (rules.maxTaps > 0 && tapCount >= rules.maxTaps) return true
        if (rules.maxDurationMs > 0 && (System.currentTimeMillis() - startTimeMs) >= rules.maxDurationMs) return true
        return false
    }

    private fun computeDelay(baseDelay: Long, rules: ClickRule): Long {
        return if (rules.randomizeDelay) {
            baseDelay + Random.nextLong(rules.randomDelayMin, rules.randomDelayMax + 1)
        } else {
            baseDelay
        }
    }

    private suspend fun dispatchTap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        val duration = durationMs.coerceAtLeast(1L)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        suspendCancellableCoroutine { cont ->
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(Unit) {}
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(Unit) {}
                }
            }, null)
            if (!dispatched && cont.isActive) {
                cont.resume(Unit) {}
            }
        }
    }

    private suspend fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val duration = durationMs.coerceAtLeast(1L)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        suspendCancellableCoroutine { cont ->
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(Unit) {}
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(Unit) {}
                }
            }, null)
            if (!dispatched && cont.isActive) {
                cont.resume(Unit) {}
            }
        }
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiver != null) return
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    stopExecution()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, filter)
        }
    }

    private fun unregisterScreenOffReceiver() {
        screenOffReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        screenOffReceiver = null
    }
}
