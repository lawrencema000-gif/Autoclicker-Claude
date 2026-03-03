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
import com.autoclicker.claude.util.AntiDetection
import com.autoclicker.claude.util.PatternGenerator
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class AutoClickService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var executionJob: Job? = null
    private val paused = AtomicBoolean(false)
    private var tapCount = 0
    private var startTimeMs = 0L
    private var lastTapX = -1f
    private var lastTapY = -1f

    private var pickOverlay: PickOverlayManager? = null
    private var floatingToolbar: FloatingToolbarManager? = null
    private var screenOffReceiver: BroadcastReceiver? = null

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
                            antiDetection = cmd.settings.antiDetection,
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
        lastTapX = -1f
        lastTapY = -1f

        val anti = profile.antiDetection

        // Generate pattern steps if pattern mode
        val steps = if (profile.mode == ClickMode.PATTERN_MODE && profile.patternConfig != null) {
            PatternGenerator.generate(profile.patternConfig, profile.intervalMs, profile.rules.let {
                profile.intervalMs // use interval as hold fallback
            }).map { it.copy(holdDuration = 50L) }
        } else {
            profile.steps
        }

        val effectiveProfile = profile.copy(steps = steps)

        CommandBus.setRunState(RunState.RUNNING)
        CommandBus.updateStats(ExecutionStats(profileName = profile.name))

        if (profile.rules.stopOnScreenOff) {
            registerScreenOffReceiver()
        }

        floatingToolbar?.show()
        TapForegroundService.start(this, profile.name)

        executionJob = scope.launch {
            try {
                if (effectiveProfile.rules.startDelayMs > 0) {
                    delay(effectiveProfile.rules.startDelayMs)
                }

                val maxLoops = when {
                    effectiveProfile.rules.maxLoops > 0 -> effectiveProfile.rules.maxLoops
                    effectiveProfile.loopCount > 0 -> effectiveProfile.loopCount
                    else -> Int.MAX_VALUE
                }

                for (loop in 1..maxLoops) {
                    if (!isActive) break

                    for ((stepIdx, step) in effectiveProfile.steps.withIndex()) {
                        if (!isActive) break
                        waitWhilePaused()
                        if (shouldStop(effectiveProfile.rules)) break

                        // Compute delay with anti-detection jitter
                        val baseDelay = computeDelay(step.delayBefore, effectiveProfile.rules)
                        val jitteredDelay = AntiDetection.jitterInterval(baseDelay, anti)
                        if (jitteredDelay > 0) delay(jitteredDelay)

                        // Micro-pause for human simulation
                        if (AntiDetection.shouldMicroPause(anti)) {
                            delay(AntiDetection.getMicroPauseDuration(anti))
                        }

                        for (rep in 0 until max(1, step.repeatCount)) {
                            if (!isActive || shouldStop(effectiveProfile.rules)) break
                            waitWhilePaused()

                            when (step.action) {
                                ActionType.TAP -> {
                                    var (tx, ty) = AntiDetection.randomizePosition(step.x, step.y, anti)
                                    val (fx, fy) = AntiDetection.avoidRepetition(tx, ty, lastTapX, lastTapY, anti)
                                    tx = fx; ty = fy
                                    val hold = AntiDetection.humanizeHold(step.holdDuration, anti)
                                    dispatchTap(tx, ty, hold)
                                    lastTapX = tx; lastTapY = ty
                                    tapCount++
                                }
                                ActionType.SWIPE -> {
                                    val (sx, sy) = AntiDetection.randomizePosition(step.x, step.y, anti)
                                    val (ex, ey) = AntiDetection.randomizePosition(step.swipeToX, step.swipeToY, anti)
                                    dispatchSwipe(sx, sy, ex, ey, step.swipeDuration)
                                    tapCount++
                                }
                                ActionType.LONG_PRESS -> {
                                    val (lx, ly) = AntiDetection.randomizePosition(step.x, step.y, anti)
                                    val hold = AntiDetection.humanizeHold(step.holdDuration.coerceAtLeast(400L), anti)
                                    dispatchTap(lx, ly, hold)
                                    lastTapX = lx; lastTapY = ly
                                    tapCount++
                                }
                                ActionType.DELAY -> {
                                    delay(AntiDetection.jitterInterval(step.delayBefore, anti))
                                }
                                ActionType.PATTERN -> {
                                    // Pattern steps are pre-expanded, treated as TAP
                                    val (px, py) = AntiDetection.randomizePosition(step.x, step.y, anti)
                                    val hold = AntiDetection.humanizeHold(step.holdDuration, anti)
                                    dispatchTap(px, py, hold)
                                    tapCount++
                                }
                            }

                            CommandBus.updateStats(
                                ExecutionStats(
                                    totalTaps = tapCount,
                                    elapsedMs = System.currentTimeMillis() - startTimeMs,
                                    currentStep = stepIdx + 1,
                                    currentLoop = loop,
                                    profileName = effectiveProfile.name
                                )
                            )
                            floatingToolbar?.refresh()

                            if (rep < step.repeatCount - 1) {
                                val interRepeat = AntiDetection.jitterInterval(effectiveProfile.intervalMs, anti)
                                delay(interRepeat)
                            }
                        }

                        if (stepIdx < effectiveProfile.steps.size - 1) {
                            val interStep = AntiDetection.jitterInterval(effectiveProfile.intervalMs, anti)
                            delay(interStep)
                        }
                    }

                    if (shouldStop(effectiveProfile.rules)) break

                    if (loop < maxLoops && effectiveProfile.rules.pauseBetweenLoops > 0) {
                        delay(effectiveProfile.rules.pauseBetweenLoops)
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
        while (paused.get()) { delay(100) }
    }

    private fun shouldStop(rules: ClickRule): Boolean {
        if (rules.maxTaps > 0 && tapCount >= rules.maxTaps) return true
        if (rules.maxDurationMs > 0 && (System.currentTimeMillis() - startTimeMs) >= rules.maxDurationMs) return true
        return false
    }

    private fun computeDelay(baseDelay: Long, rules: ClickRule): Long {
        return if (rules.randomizeDelay) {
            baseDelay + kotlin.random.Random.nextLong(rules.randomDelayMin, rules.randomDelayMax + 1)
        } else {
            baseDelay
        }
    }

    private suspend fun dispatchTap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L)))
            .build()
        suspendCancellableCoroutine { cont ->
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(Unit) {} }
                override fun onCancelled(g: GestureDescription?) { if (cont.isActive) cont.resume(Unit) {} }
            }, null)
            if (!dispatched && cont.isActive) cont.resume(Unit) {}
        }
    }

    private suspend fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L)))
            .build()
        suspendCancellableCoroutine { cont ->
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(Unit) {} }
                override fun onCancelled(g: GestureDescription?) { if (cont.isActive) cont.resume(Unit) {} }
            }, null)
            if (!dispatched && cont.isActive) cont.resume(Unit) {}
        }
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiver != null) return
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) stopExecution()
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
        screenOffReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        screenOffReceiver = null
    }
}
