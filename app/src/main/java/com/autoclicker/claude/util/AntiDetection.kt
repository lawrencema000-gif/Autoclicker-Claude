package com.autoclicker.claude.util

import com.autoclicker.claude.data.AntiDetectionConfig
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object AntiDetection {

    /**
     * Randomize a coordinate within a small radius to avoid detection.
     * Uses proper Gaussian-like distribution so most taps cluster near center.
     */
    fun randomizePosition(x: Float, y: Float, config: AntiDetectionConfig): Pair<Float, Float> {
        if (!config.randomPositionOffset) return Pair(x, y)

        val r = config.positionOffsetRadius
        // Box-Muller transform for true Gaussian distribution
        val u1 = Random.nextFloat().coerceAtLeast(0.0001f)
        val u2 = Random.nextFloat()
        val magnitude = sqrt(-2f * kotlin.math.ln(u1)) * r * 0.33f // 99.7% within radius
        val angle = 2f * Math.PI.toFloat() * u2
        val dx = (magnitude * cos(angle)).coerceIn(-r, r)
        val dy = (magnitude * sin(angle)).coerceIn(-r, r)

        return Pair(
            (x + dx).coerceAtLeast(0f),
            (y + dy).coerceAtLeast(0f)
        )
    }

    /**
     * Add jitter to the interval to appear more human.
     * Uses acceleration curve: intervals gradually speed up then slow down.
     */
    private var intervalCycleCount = 0
    fun jitterInterval(baseMs: Long, config: AntiDetectionConfig): Long {
        if (!config.intervalJitter) return baseMs

        // Acceleration curve: sinusoidal variation simulates human rhythm
        intervalCycleCount++
        val cycleFactor = sin(intervalCycleCount * 0.1f) * 0.5f + 0.5f // 0..1
        val maxJitter = baseMs * config.jitterPercent / 100
        val jitter = (maxJitter * (cycleFactor * 2f - 1f)).toLong()
        val noise = Random.nextLong(-maxJitter / 4, maxJitter / 4 + 1)

        return (baseMs + jitter + noise).coerceAtLeast(1L)
    }

    /**
     * Humanize hold duration by varying it slightly.
     * Simulates natural finger pressure variation.
     */
    fun humanizeHold(baseMs: Long, config: AntiDetectionConfig): Long {
        if (!config.humanizeHoldDuration) return baseMs
        val variation = config.holdVariationMs
        // Skew toward slightly longer holds (humans tend to hold a bit longer)
        val skew = (Random.nextFloat() * Random.nextFloat() * variation).toLong()
        val delta = Random.nextLong(-variation / 2, skew + 1)
        return (baseMs + delta).coerceAtLeast(1L)
    }

    /**
     * Decide if a micro-pause should happen (simulates human distraction).
     * Probability increases slightly over time (fatigue simulation).
     */
    private var tapsSinceLastPause = 0
    fun shouldMicroPause(config: AntiDetectionConfig): Boolean {
        if (!config.enabled) return false
        tapsSinceLastPause++
        // Increasing probability: more likely to pause after many consecutive taps
        val fatigueMultiplier = 1f + (tapsSinceLastPause / 50f).coerceAtMost(2f)
        val shouldPause = Random.nextFloat() < config.microPauseProbability * fatigueMultiplier
        if (shouldPause) tapsSinceLastPause = 0
        return shouldPause
    }

    /**
     * Get random micro-pause duration.
     * Longer pauses are rarer (exponential distribution).
     */
    fun getMicroPauseDuration(config: AntiDetectionConfig): Long {
        val range = config.microPauseMaxMs - config.microPauseMinMs
        // Exponential: most pauses are short, occasional long ones
        val factor = -kotlin.math.ln(Random.nextFloat().coerceAtLeast(0.01f).toDouble())
        val duration = config.microPauseMinMs + (range * factor * 0.3).toLong()
        return duration.coerceIn(config.microPauseMinMs, config.microPauseMaxMs)
    }

    /**
     * When avoidExactRepetition is on, slightly shift position each time
     * so no two consecutive taps hit the exact same pixel.
     */
    fun avoidRepetition(x: Float, y: Float, lastX: Float, lastY: Float, config: AntiDetectionConfig): Pair<Float, Float> {
        if (!config.avoidExactRepetition) return Pair(x, y)
        if (x == lastX && y == lastY) {
            val nudge = Random.nextFloat() * 4f + 1f
            val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
            return Pair(
                (x + nudge * cos(angle)).coerceAtLeast(0f),
                (y + nudge * sin(angle)).coerceAtLeast(0f)
            )
        }
        return Pair(x, y)
    }

    /**
     * Generate a natural movement path between two points.
     * Adds slight curve and acceleration/deceleration to swipe gestures.
     */
    fun naturalSwipePath(x1: Float, y1: Float, x2: Float, y2: Float): List<Pair<Float, Float>> {
        val points = mutableListOf<Pair<Float, Float>>()
        val steps = 10
        // Perpendicular offset for curve
        val dx = x2 - x1; val dy = y2 - y1
        val curveMagnitude = sqrt((dx * dx + dy * dy).toDouble()).toFloat() * 0.1f * (Random.nextFloat() - 0.5f)
        val perpX = -dy; val perpY = dx
        val perpLen = sqrt((perpX * perpX + perpY * perpY).toDouble()).toFloat().coerceAtLeast(1f)

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            // Ease in-out: slow start, fast middle, slow end
            val eased = t * t * (3f - 2f * t) // smoothstep
            val cx = x1 + (x2 - x1) * eased + curveMagnitude * sin(t * Math.PI.toFloat()) * (perpX / perpLen)
            val cy = y1 + (y2 - y1) * eased + curveMagnitude * sin(t * Math.PI.toFloat()) * (perpY / perpLen)
            points.add(Pair(cx, cy))
        }
        return points
    }

    /** Reset cycle counters (call when a new session starts) */
    fun resetSession() {
        intervalCycleCount = 0
        tapsSinceLastPause = 0
    }
}
