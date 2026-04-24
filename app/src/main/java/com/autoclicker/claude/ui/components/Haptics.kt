package com.autoclicker.claude.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class HapticType { TAP, SUCCESS, WARNING, ERROR }

object Haptics {
    fun trigger(context: Context, type: HapticType = HapticType.TAP) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val effect = when (type) {
                HapticType.TAP -> VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                HapticType.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 20, 40, 30), -1)
                HapticType.WARNING -> VibrationEffect.createWaveform(longArrayOf(0, 50, 80, 50), -1)
                HapticType.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40, 60, 40), -1)
            }
            vibrator.vibrate(effect)
        } catch (_: Exception) {}
    }
}
