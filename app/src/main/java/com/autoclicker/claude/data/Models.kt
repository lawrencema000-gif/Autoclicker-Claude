package com.autoclicker.claude.data

import com.google.gson.annotations.SerializedName
import java.util.UUID

enum class ActionType {
    @SerializedName("tap") TAP,
    @SerializedName("swipe") SWIPE,
    @SerializedName("hold") LONG_PRESS,
    @SerializedName("wait") DELAY
}

enum class ClickMode {
    @SerializedName("single") SINGLE_POINT,
    @SerializedName("multi") MULTI_POINT
}

enum class StopCondition {
    @SerializedName("never") NEVER,
    @SerializedName("after_taps") AFTER_TAPS,
    @SerializedName("after_seconds") AFTER_SECONDS
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

data class ClickRule(
    val maxTaps: Int = 0,
    val maxDurationMs: Long = 0,
    val stopOnScreenOff: Boolean = true,
    val randomizeDelay: Boolean = false,
    val randomDelayMin: Long = 50,
    val randomDelayMax: Long = 200,
    val startDelayMs: Long = 0,
    val pauseBetweenLoops: Long = 0
)

data class DefaultSettings(
    val intervalMs: Long = 100L,
    val holdDurationMs: Long = 10L,
    val swipeDurationMs: Long = 350L,
    val stopCondition: StopCondition = StopCondition.NEVER,
    val stopValue: Int = 0
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
