package com.autoclicker.claude.model

data class ClickPoint(
    var x: Float = 0f,
    var y: Float = 0f,
    var delay: Long = 300L,
    var duration: Long = 1L,
    var label: String = ""
)
