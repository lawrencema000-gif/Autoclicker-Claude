package com.autoclicker.claude.data

import com.google.gson.annotations.SerializedName
import java.util.UUID

enum class ActionType {
    @SerializedName("tap") TAP,
    @SerializedName("swipe") SWIPE,
    @SerializedName("hold") LONG_PRESS,
    @SerializedName("wait") DELAY,
    @SerializedName("pattern") PATTERN
}

enum class ClickMode {
    @SerializedName("single") SINGLE_POINT,
    @SerializedName("multi") MULTI_POINT,
    @SerializedName("pattern") PATTERN_MODE
}

enum class StopCondition {
    @SerializedName("never") NEVER,
    @SerializedName("after_taps") AFTER_TAPS,
    @SerializedName("after_seconds") AFTER_SECONDS,
    @SerializedName("after_loops") AFTER_LOOPS
}

enum class PatternType {
    @SerializedName("circle") CIRCLE,
    @SerializedName("zigzag") ZIGZAG,
    @SerializedName("grid") GRID,
    @SerializedName("spiral") SPIRAL,
    @SerializedName("diamond") DIAMOND,
    @SerializedName("random_area") RANDOM_AREA,
    @SerializedName("custom") CUSTOM
}

enum class SpeedPreset(val label: String, val intervalMs: Long) {
    TURBO("Turbo", 10),
    FAST("Fast", 100),
    NORMAL("Normal", 500),
    SLOW("Slow", 2000),
    CRAWL("Crawl", 30000),
    HOURLY("Hourly", 3600000);

    companion object {
        fun fromInterval(ms: Long): SpeedPreset? = entries.find { it.intervalMs == ms }
    }
}

enum class SpeedMode {
    @SerializedName("interval") INTERVAL,
    @SerializedName("rate") RATE
}

enum class TimeUnit(val label: String, val toMs: Long) {
    MILLISECONDS("ms", 1),
    SECONDS("s", 1000),
    MINUTES("min", 60000);
}

data class ClickPoint(
    val id: String = UUID.randomUUID().toString(),
    val action: ActionType = ActionType.TAP,
    val x: Float = 0f,
    val y: Float = 0f,
    val swipeToX: Float = 0f,
    val swipeToY: Float = 0f,
    @SerializedName("delay") val delayBefore: Long = 100L,
    @SerializedName("holdMs") val holdDuration: Long = 50L,
    val swipeDuration: Long = 300L,
    val repeatCount: Int = 1,
    val label: String = "",
    val order: Int = 0
)

data class PatternConfig(
    val type: PatternType = PatternType.CIRCLE,
    val centerX: Float = 540f,
    val centerY: Float = 960f,
    val radius: Float = 100f,
    val pointCount: Int = 8,
    val gridRows: Int = 3,
    val gridCols: Int = 3,
    val gridSpacing: Float = 80f,
    val spiralRevolutions: Float = 2f,
    val areaWidth: Float = 200f,
    val areaHeight: Float = 200f,
    val customPoints: List<ClickPoint> = emptyList()
)

data class AntiDetectionConfig(
    val enabled: Boolean = false,
    val randomPositionOffset: Boolean = false,
    val positionOffsetRadius: Float = 15f,
    val intervalJitter: Boolean = false,
    val jitterPercent: Int = 20,
    val humanizeHoldDuration: Boolean = false,
    val holdVariationMs: Long = 30L,
    val naturalMovement: Boolean = false,
    val avoidExactRepetition: Boolean = false,
    val microPauseProbability: Float = 0.05f,
    val microPauseMinMs: Long = 200,
    val microPauseMaxMs: Long = 1500
)

data class ClickRule(
    val maxTaps: Int = 0,
    val maxDurationMs: Long = 0,
    val maxLoops: Int = 0,
    val stopOnScreenOff: Boolean = true,
    val randomizeDelay: Boolean = false,
    val randomDelayMin: Long = 50,
    val randomDelayMax: Long = 200,
    val startDelayMs: Long = 0,
    val pauseBetweenLoops: Long = 0
)

data class DefaultSettings(
    val intervalMs: Long = 500L,
    val holdDurationMs: Long = 200L,
    val swipeDurationMs: Long = 300L,
    val stopCondition: StopCondition = StopCondition.NEVER,
    val stopValue: Int = 0,
    val speedMode: SpeedMode = SpeedMode.INTERVAL,
    val antiDetection: AntiDetectionConfig = AntiDetectionConfig()
)

data class TapProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New Script",
    val description: String = "",
    val mode: ClickMode = ClickMode.SINGLE_POINT,
    val steps: List<ClickPoint> = emptyList(),
    val intervalMs: Long = 100L,
    val loopCount: Int = 0,
    val rules: ClickRule = ClickRule(),
    val patternConfig: PatternConfig? = null,
    val antiDetection: AntiDetectionConfig = AntiDetectionConfig(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class RunState { IDLE, RUNNING, PAUSED }

data class ExecutionStats(
    val totalTaps: Int = 0,
    val elapsedMs: Long = 0,
    val currentStep: Int = 0,
    val currentLoop: Int = 0,
    val profileName: String = ""
)
