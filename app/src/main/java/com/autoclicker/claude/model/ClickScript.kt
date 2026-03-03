package com.autoclicker.claude.model

data class ClickScript(
    var name: String = "Untitled",
    var clickPoints: MutableList<ClickPoint> = mutableListOf(),
    var swipeActions: MutableList<SwipeAction> = mutableListOf(),
    var repeatCount: Int = -1, // -1 = infinite
    var globalInterval: Long = 300L,
    var globalDuration: Long = 0L, // 0 = no limit
    var mode: ClickMode = ClickMode.SINGLE
)

enum class ClickMode {
    SINGLE,
    MULTI
}
