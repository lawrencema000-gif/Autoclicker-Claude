package com.autoclicker.claude.util

import com.autoclicker.claude.data.ActionType
import com.autoclicker.claude.data.ClickPoint
import com.autoclicker.claude.data.PatternConfig
import com.autoclicker.claude.data.PatternType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object PatternGenerator {

    fun generate(config: PatternConfig, intervalMs: Long, holdMs: Long): List<ClickPoint> {
        return when (config.type) {
            PatternType.CIRCLE -> generateCircle(config, intervalMs, holdMs)
            PatternType.ZIGZAG -> generateZigzag(config, intervalMs, holdMs)
            PatternType.GRID -> generateGrid(config, intervalMs, holdMs)
            PatternType.SPIRAL -> generateSpiral(config, intervalMs, holdMs)
            PatternType.DIAMOND -> generateDiamond(config, intervalMs, holdMs)
            PatternType.RANDOM_AREA -> generateRandomArea(config, intervalMs, holdMs)
            PatternType.CUSTOM -> config.customPoints.mapIndexed { i, pt ->
                pt.copy(delayBefore = intervalMs, holdDuration = holdMs, order = i)
            }
        }
    }

    private fun generateCircle(config: PatternConfig, intervalMs: Long, holdMs: Long): List<ClickPoint> {
        val points = mutableListOf<ClickPoint>()
        for (i in 0 until config.pointCount) {
            val angle = 2.0 * PI * i / config.pointCount
            val x = config.centerX + config.radius * cos(angle).toFloat()
            val y = config.centerY + config.radius * sin(angle).toFloat()
            points.add(
                ClickPoint(
                    action = ActionType.TAP,
                    x = x, y = y,
                    delayBefore = intervalMs,
                    holdDuration = holdMs,
                    label = "Circle ${i + 1}",
                    order = i
                )
            )
        }
        return points
    }

    private fun generateZigzag(config: PatternConfig, intervalMs: Long, holdMs: Long): List<ClickPoint> {
        val points = mutableListOf<ClickPoint>()
        val stepX = config.areaWidth / (config.pointCount - 1).coerceAtLeast(1)
        val startX = config.centerX - config.areaWidth / 2
        val topY = config.centerY - config.areaHeight / 2
        val bottomY = config.centerY + config.areaHeight / 2

        for (i in 0 until config.pointCount) {
            val x = startX + stepX * i
            val y = if (i % 2 == 0) topY else bottomY
            points.add(
                ClickPoint(
                    action = ActionType.TAP,
                    x = x, y = y,
                    delayBefore = intervalMs,
                    holdDuration = holdMs,
                    label = "Zigzag ${i + 1}",
                    order = i
                )
            )
        }
        return points
    }

    private fun generateGrid(config: PatternConfig, intervalMs: Long, holdMs: Long): List<ClickPoint> {
        val points = mutableListOf<ClickPoint>()
        val startX = config.centerX - (config.gridCols - 1) * config.gridSpacing / 2
        val startY = config.centerY - (config.gridRows - 1) * config.gridSpacing / 2
        var idx = 0

        for (row in 0 until config.gridRows) {
            for (col in 0 until config.gridCols) {
                val x = startX + col * config.gridSpacing
                val y = startY + row * config.gridSpacing
                points.add(
                    ClickPoint(
                        action = ActionType.TAP,
                        x = x, y = y,
                        delayBefore = intervalMs,
                        holdDuration = holdMs,
                        label = "Grid [${row + 1},${col + 1}]",
                        order = idx++
                    )
                )
            }
        }
        return points
    }

    private fun generateSpiral(config: PatternConfig, intervalMs: Long, holdMs: Long): List<ClickPoint> {
        val points = mutableListOf<ClickPoint>()
        val totalPoints = config.pointCount
        val maxAngle = 2.0 * PI * config.spiralRevolutions

        for (i in 0 until totalPoints) {
            val t = i.toDouble() / (totalPoints - 1).coerceAtLeast(1)
            val angle = maxAngle * t
            val r = config.radius * t
            val x = config.centerX + (r * cos(angle)).toFloat()
            val y = config.centerY + (r * sin(angle)).toFloat()
            points.add(
                ClickPoint(
                    action = ActionType.TAP,
                    x = x, y = y,
                    delayBefore = intervalMs,
                    holdDuration = holdMs,
                    label = "Spiral ${i + 1}",
                    order = i
                )
            )
        }
        return points
    }

    private fun generateDiamond(config: PatternConfig, intervalMs: Long, holdMs: Long): List<ClickPoint> {
        val r = config.radius
        val cx = config.centerX
        val cy = config.centerY
        val perSide = (config.pointCount / 4).coerceAtLeast(1)
        val points = mutableListOf<ClickPoint>()
        var idx = 0

        // Top -> Right -> Bottom -> Left
        val corners = listOf(
            Pair(cx, cy - r),       // top
            Pair(cx + r, cy),       // right
            Pair(cx, cy + r),       // bottom
            Pair(cx - r, cy)        // left
        )

        for (side in 0 until 4) {
            val (sx, sy) = corners[side]
            val (ex, ey) = corners[(side + 1) % 4]
            for (p in 0 until perSide) {
                val t = p.toFloat() / perSide
                val x = sx + (ex - sx) * t
                val y = sy + (ey - sy) * t
                points.add(
                    ClickPoint(
                        action = ActionType.TAP,
                        x = x, y = y,
                        delayBefore = intervalMs,
                        holdDuration = holdMs,
                        label = "Diamond ${idx + 1}",
                        order = idx++
                    )
                )
            }
        }
        return points
    }

    private fun generateRandomArea(config: PatternConfig, intervalMs: Long, holdMs: Long): List<ClickPoint> {
        val points = mutableListOf<ClickPoint>()
        val halfW = config.areaWidth / 2
        val halfH = config.areaHeight / 2

        for (i in 0 until config.pointCount) {
            val x = config.centerX + Random.nextFloat() * config.areaWidth - halfW
            val y = config.centerY + Random.nextFloat() * config.areaHeight - halfH
            points.add(
                ClickPoint(
                    action = ActionType.TAP,
                    x = x, y = y,
                    delayBefore = intervalMs,
                    holdDuration = holdMs,
                    label = "Random ${i + 1}",
                    order = i
                )
            )
        }
        return points
    }
}
