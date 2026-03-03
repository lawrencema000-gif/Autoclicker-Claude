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

    val profiles: Flow<List<TapProfile>> = context.dataStore.data.map { prefs ->
        val json = prefs[profilesKey] ?: "[]"
        val type = object : TypeToken<List<TapProfile>>() {}.type
        gson.fromJson(json, type)
    }

    val defaultSettings: Flow<DefaultSettings> = context.dataStore.data.map { prefs ->
        val json = prefs[defaultSettingsKey]
        if (json != null) gson.fromJson(json, DefaultSettings::class.java) else DefaultSettings()
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
        context.dataStore.edit { prefs ->
            val current = loadProfiles(prefs)
            current.add(profile)
            prefs[profilesKey] = gson.toJson(current)
        }
    }

    suspend fun updateProfile(profile: TapProfile) {
        context.dataStore.edit { prefs ->
            val current = loadProfiles(prefs)
            val index = current.indexOfFirst { it.id == profile.id }
            if (index >= 0) {
                current[index] = profile.copy(updatedAt = System.currentTimeMillis())
                prefs[profilesKey] = gson.toJson(current)
            }
        }
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
        val imported = gson.fromJson(json, TapProfile::class.java).copy(
            id = UUID.randomUUID().toString(),
            name = "${gson.fromJson(json, TapProfile::class.java).name} (Imported)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        addProfile(imported)
        return imported
    }

    private fun loadProfiles(prefs: Preferences): MutableList<TapProfile> {
        val json = prefs[profilesKey] ?: "[]"
        val type = object : TypeToken<MutableList<TapProfile>>() {}.type
        return gson.fromJson(json, type)
    }
}
