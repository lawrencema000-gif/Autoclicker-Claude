package com.autoclicker.claude.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "autoclicker_data")

class ProfileRepository(private val context: Context) {
    private val gson = Gson()
    private val profilesKey = stringPreferencesKey("profiles_json")
    private val defaultSettingsKey = stringPreferencesKey("default_settings_json")
    private val lastProfileKey = stringPreferencesKey("last_profile_id")
    private val onboardingKey = booleanPreferencesKey("onboarding_complete")
    private val sessionModeKey = stringPreferencesKey("session_mode")
    private val sessionPatternConfigKey = stringPreferencesKey("session_pattern_config")
    private val sessionCustomPointsKey = stringPreferencesKey("session_custom_points")
    private val lastProfileJsonKey = stringPreferencesKey("last_profile_json")
    private val homeTipSeenKey = booleanPreferencesKey("home_tip_seen")
    private val bubbleTipSeenKey = booleanPreferencesKey("bubble_tip_seen")
    private val historyKey = stringPreferencesKey("session_history_json")
    private val bubbleEnabledKey = booleanPreferencesKey("bubble_enabled")
    private val volumeTriggerKey = booleanPreferencesKey("volume_trigger_enabled")
    private val pauseOnTouchKey = booleanPreferencesKey("pause_on_touch_enabled")

    val profiles: Flow<List<TapProfile>> = context.dataStore.data.map { prefs ->
        val json = prefs[profilesKey] ?: "[]"
        try {
            val type = object : TypeToken<List<TapProfile>>() {}.type
            val parsed: List<TapProfile>? = gson.fromJson(json, type)
            parsed?.map { sanitize(it) } ?: emptyList()
        } catch (_: Exception) {
            // A single corrupt value must not permanently kill the flow (which
            // would make the entire Scripts tab and every reader go blank).
            emptyList()
        }
    }

    val defaultSettings: Flow<DefaultSettings> = context.dataStore.data.map { prefs ->
        val json = prefs[defaultSettingsKey]
        try {
            if (json != null) gson.fromJson(json, DefaultSettings::class.java) ?: DefaultSettings()
            else DefaultSettings()
        } catch (_: Exception) {
            DefaultSettings()
        }
    }

    val lastProfileId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[lastProfileKey]
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[onboardingKey] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[onboardingKey] = complete }
    }

    suspend fun saveDefaultSettings(settings: DefaultSettings) {
        context.dataStore.edit { it[defaultSettingsKey] = gson.toJson(settings) }
    }

    suspend fun setLastProfileId(id: String) {
        context.dataStore.edit { it[lastProfileKey] = id }
    }

    suspend fun addProfile(profile: TapProfile) {
        val stamped = stampSourceScreen(profile)
        context.dataStore.edit { prefs ->
            val current = loadProfiles(prefs)
            current.add(stamped)
            prefs[profilesKey] = gson.toJson(current)
        }
    }

    suspend fun updateProfile(profile: TapProfile) {
        val stamped = stampSourceScreen(profile)
        context.dataStore.edit { prefs ->
            val current = loadProfiles(prefs)
            val index = current.indexOfFirst { it.id == profile.id }
            if (index >= 0) {
                current[index] = stamped.copy(updatedAt = System.currentTimeMillis())
                prefs[profilesKey] = gson.toJson(current)
            }
        }
    }

    /** Stamp the current screen size on a profile if it hasn't been set yet,
     *  so the runtime knows how to scale coords if this profile is later run
     *  on a different-sized device. */
    private fun stampSourceScreen(profile: TapProfile): TapProfile {
        if (profile.sourceScreenWidth > 0 && profile.sourceScreenHeight > 0) return profile
        val metrics = context.resources.displayMetrics
        return profile.copy(
            sourceScreenWidth = metrics.widthPixels,
            sourceScreenHeight = metrics.heightPixels
        )
    }

    suspend fun deleteProfile(id: String) {
        context.dataStore.edit { prefs ->
            val current = loadProfiles(prefs)
            current.removeAll { it.id == id }
            prefs[profilesKey] = gson.toJson(current)
        }
    }

    suspend fun duplicateProfile(id: String): TapProfile? {
        var duplicated: TapProfile? = null
        context.dataStore.edit { prefs ->
            val current = loadProfiles(prefs)
            val source = current.find { it.id == id }
            if (source != null) {
                duplicated = source.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${source.name} (Copy)",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                current.add(duplicated!!)
                prefs[profilesKey] = gson.toJson(current)
            }
        }
        return duplicated
    }

    fun exportProfileJson(profile: TapProfile): String = gson.toJson(profile)

    suspend fun importProfileJson(json: String): TapProfile {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("File is empty")
        }
        if (!trimmed.startsWith("{")) {
            throw IllegalArgumentException("Not a valid script file (must be JSON)")
        }
        val parsed = try {
            (gson.fromJson(trimmed, TapProfile::class.java)
                ?: throw IllegalArgumentException("Script is corrupted or from an unsupported version"))
                .let { sanitize(it) }
        } catch (e: com.google.gson.JsonSyntaxException) {
            throw IllegalArgumentException("Script file is corrupted")
        }
        if (parsed.name.isBlank()) {
            throw IllegalArgumentException("Script is missing a name")
        }
        val imported = parsed.copy(
            id = UUID.randomUUID().toString(),
            name = "${parsed.name} (Imported)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        addProfile(imported)
        return imported
    }

    // Session state persistence
    suspend fun saveSessionState(mode: ClickMode, patternConfig: PatternConfig, customPoints: List<ClickPoint>) {
        context.dataStore.edit { prefs ->
            prefs[sessionModeKey] = gson.toJson(mode)
            prefs[sessionPatternConfigKey] = gson.toJson(patternConfig)
            prefs[sessionCustomPointsKey] = gson.toJson(customPoints)
        }
    }

    fun getSessionMode(): Flow<ClickMode> = context.dataStore.data.map { prefs ->
        val json = prefs[sessionModeKey] ?: return@map ClickMode.SINGLE_POINT
        try { gson.fromJson(json, ClickMode::class.java) ?: ClickMode.SINGLE_POINT }
        catch (_: Exception) { ClickMode.SINGLE_POINT }
    }

    fun getSessionPatternConfig(): Flow<PatternConfig> = context.dataStore.data.map { prefs ->
        val json = prefs[sessionPatternConfigKey] ?: return@map PatternConfig()
        try { gson.fromJson(json, PatternConfig::class.java) ?: PatternConfig() }
        catch (_: Exception) { PatternConfig() }
    }

    fun getSessionCustomPoints(): Flow<List<ClickPoint>> = context.dataStore.data.map { prefs ->
        val json = prefs[sessionCustomPointsKey] ?: return@map emptyList()
        try {
            val type = object : TypeToken<List<ClickPoint>>() {}.type
            gson.fromJson<List<ClickPoint>>(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    // Last profile persistence (for volume trigger after restart)
    suspend fun saveLastProfile(profile: TapProfile) {
        context.dataStore.edit { it[lastProfileJsonKey] = gson.toJson(profile) }
    }

    fun getLastProfile(): Flow<TapProfile?> = context.dataStore.data.map { prefs ->
        val json = prefs[lastProfileJsonKey] ?: return@map null
        try { gson.fromJson(json, TapProfile::class.java)?.let { sanitize(it) } }
        catch (_: Exception) { null }
    }

    // Session history (persisted so it survives process death / restart)
    val history: Flow<List<HistoryEntry>> = context.dataStore.data.map { prefs ->
        val json = prefs[historyKey] ?: return@map emptyList()
        try {
            val type = object : TypeToken<List<HistoryEntry>>() {}.type
            gson.fromJson<List<HistoryEntry>>(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun saveHistory(entries: List<HistoryEntry>) {
        context.dataStore.edit { it[historyKey] = gson.toJson(entries.take(50)) }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it.remove(historyKey) }
    }

    // Quick Controls toggle persistence
    val bubbleEnabled: Flow<Boolean> = context.dataStore.data.map { it[bubbleEnabledKey] ?: false }
    val volumeTriggerEnabled: Flow<Boolean> = context.dataStore.data.map { it[volumeTriggerKey] ?: false }
    val pauseOnTouchEnabled: Flow<Boolean> = context.dataStore.data.map { it[pauseOnTouchKey] ?: false }
    suspend fun setBubbleEnabled(v: Boolean) { context.dataStore.edit { it[bubbleEnabledKey] = v } }
    suspend fun setVolumeTriggerEnabled(v: Boolean) { context.dataStore.edit { it[volumeTriggerKey] = v } }
    suspend fun setPauseOnTouchEnabled(v: Boolean) { context.dataStore.edit { it[pauseOnTouchKey] = v } }

    val homeTipSeen: Flow<Boolean> = context.dataStore.data.map { it[homeTipSeenKey] ?: false }
    suspend fun setHomeTipSeen() { context.dataStore.edit { it[homeTipSeenKey] = true } }

    val bubbleTipSeen: Flow<Boolean> = context.dataStore.data.map { it[bubbleTipSeenKey] ?: false }
    suspend fun setBubbleTipSeen() { context.dataStore.edit { it[bubbleTipSeenKey] = true } }

    private fun loadProfiles(prefs: Preferences): MutableList<TapProfile> {
        val json = prefs[profilesKey] ?: "[]"
        val type = object : TypeToken<MutableList<TapProfile>>() {}.type
        return try {
            gson.fromJson<MutableList<TapProfile>>(json, type)?.map { sanitize(it) }?.toMutableList()
                ?: mutableListOf()
        } catch (_: Exception) { mutableListOf() }
    }

    /**
     * Gson deserializes by reflection and bypasses Kotlin's constructor, so any
     * non-null field absent from older stored JSON comes back as null and would
     * crash (NPE) the first time it's dereferenced. Coalesce every nullable-in-
     * practice field back to its default. This is the app's forward/backward
     * compatibility guard for schema additions across versions.
     */
    @Suppress("USELESS_ELVIS")
    private fun sanitize(p: TapProfile): TapProfile = p.copy(
        name = p.name ?: "Untitled Script",
        description = p.description ?: "",
        category = p.category ?: "",
        mode = p.mode ?: ClickMode.SINGLE_POINT,
        steps = (p.steps ?: emptyList()).map { s ->
            s.copy(action = s.action ?: ActionType.TAP)
        },
        rules = p.rules ?: ClickRule(),
        antiDetection = p.antiDetection ?: AntiDetectionConfig()
    )
}
