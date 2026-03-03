package com.autoclicker.claude.util

import com.autoclicker.claude.data.AntiDetectionConfig
import kotlin.random.Random

object AntiDetection {

    /**
     * Randomize a coordinate within a small radius to avoid detection.
     * Uses Gaussian-like distribution so most taps are near center.
     */
    fun randomizePosition(x: Float, y: Float, config: AntiDetectionConfig): Pair<Float, Float> {
        if (!config.randomPositionOffset) return Pair(x, y)

        val r = config.positionOffsetRadius
        // Box-Muller-like: use two uniform randoms for a more natural distribution
        val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
        val distance = r * Random.nextFloat() * Random.nextFloat() // bias toward center
        val dx = distance * kotlin.math.cos(angle)
        val dy = distance * kotlin.math.sin(angle)

        return Pair(
            (x + dx).coerceAtLeast(0f),
            (y + dy).coerceAtLeast(0f)
        )
    }

    /**
     * Add jitter to the interval to appear more human.
     */
    fun jitterInterval(baseMs: Long, config: AntiDetectionConfig): Long {
        if (!config.intervalJitter) return baseMs
        val maxJitter = baseMs * config.jitterPercent / 100
        val jitter = Random.nextLong(-maxJitter, maxJitter + 1)
        return (baseMs + jitter).coerceAtLeast(1L)
    }

    /**
     * Humanize hold duration by varying it slightly.
     */
    fun humanizeHold(baseMs: Long, config: AntiDetectionConfig): Long {
        if (!config.humanizeHoldDuration) return baseMs
        val variation = config.holdVariationMs
        val delta = Random.nextLong(-variation, variation + 1)
        return (baseMs + delta).coerceAtLeast(1L)
    }

    /**
     * Decide if a micro-pause should happen (simulates human distraction).
     */
    fun shouldMicroPause(config: AntiDetectionConfig): Boolean {
        if (!config.enabled) return false
        return Random.nextFloat() < config.microPauseProbability
    }

    /**
     * Get random micro-pause duration.
     */
    fun getMicroPauseDuration(config: AntiDetectionConfig): Long {
        return Random.nextLong(config.microPauseMinMs, config.microPauseMaxMs + 1)
    }

    /**
     * When avoidExactRepetition is on, slightly shift position each time
     * so no two consecutive taps hit the exact same pixel.
     */
    fun avoidRepetition(x: Float, y: Float, lastX: Float, lastY: Float, config: AntiDetectionConfig): Pair<Float, Float> {
        if (!config.avoidExactRepetition) return Pair(x, y)
        if (x == lastX && y == lastY) {
            val nudge = Random.nextFloat() * 3f + 1f
            val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
            return Pair(x + nudge * kotlin.math.cos(angle), y + nudge * kotlin.math.sin(angle))
        }
        return Pair(x, y)
    }
}
