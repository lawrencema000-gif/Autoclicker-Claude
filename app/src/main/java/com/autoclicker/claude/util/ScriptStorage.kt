package com.autoclicker.claude.util

import android.content.Context
import com.autoclicker.claude.model.ClickScript
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ScriptStorage {
    private const val PREFS_NAME = "autoclicker_scripts"
    private const val KEY_SCRIPTS = "scripts"
    private val gson = Gson()

    fun saveScripts(context: Context, scripts: List<ClickScript>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SCRIPTS, gson.toJson(scripts)).apply()
    }

    fun loadScripts(context: Context): MutableList<ClickScript> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SCRIPTS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<ClickScript>>() {}.type
        return gson.fromJson(json, type)
    }

    fun exportScript(script: ClickScript): String {
        return gson.toJson(script)
    }

    fun importScript(json: String): ClickScript? {
        return try {
            gson.fromJson(json, ClickScript::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
