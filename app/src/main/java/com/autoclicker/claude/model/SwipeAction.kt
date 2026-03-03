package com.autoclicker.claude.model

data class SwipeAction(
    var startX: Float = 0f,
    var startY: Float = 0f,
    var endX: Float = 0f,
    var endY: Float = 0f,
    var duration: Long = 300L,
    var delay: Long = 500L,
    var label: String = ""
)
