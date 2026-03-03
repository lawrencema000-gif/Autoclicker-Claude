package com.autoclicker.claude.util

import android.content.Context

object SettingsManager {
    private const val PREFS_NAME = "autoclicker_settings"

    fun getDefaultInterval(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("default_interval", 300L)
    }

    fun setDefaultInterval(context: Context, interval: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong("default_interval", interval).apply()
    }

    fun getDefaultDuration(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("default_duration", 1L)
    }

    fun setDefaultDuration(context: Context, duration: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong("default_duration", duration).apply()
    }

    fun getDefaultRepeatCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("default_repeat", -1)
    }

    fun setDefaultRepeatCount(context: Context, count: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt("default_repeat", count).apply()
    }

    fun isFirstLaunch(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val first = prefs.getBoolean("first_launch", true)
        if (first) prefs.edit().putBoolean("first_launch", false).apply()
        return first
    }
}
