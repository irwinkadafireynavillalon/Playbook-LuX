package com.example.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "playlux_settings")

data class LoquendoServerConfig(
    val serverUrl: String = "",
    val apiKey: String = "",
    val method: String = "POST_JSON", // "POST_JSON" or "GET_QUERY"
    val voice: String = "Jorge"
)

data class PlayerSettings(
    val defaultSpeed: Float = 1.0f,
    val jumpSeconds: Int = 15, // 10, 15, 30
    val autoRewindSeconds: Int = 3, // 0, 2, 3, 5
    val skipSilence: Boolean = false,
    val normalizeVolume: Boolean = false,
    val normalizeGainDb: Float = 3.0f, // 0f..6f dB for LoudnessEnhancer
    val eqPreset: Int = 0, // 0=Normal, 1=Voz clara, 2=Graves, 3=Agudos, 4=Noche, 5=Personalizado
    val eqBands: List<Int> = listOf(0, 0, 0, 0, 0), // 5 bands in millibels (-1200..1200)
    val resumeOnBluetooth: Boolean = false,
    val autoNextChapter: Boolean = true,
    val monoAudio: Boolean = false,
    val balancePan: Float = 0f, // -1f Left .. +1f Right
    val bassBoostStrength: Int = 0, // 0..1000
    val virtualizerStrength: Int = 0, // 0..1000
    val fadeOnPauseResume: Boolean = true,
    val crossfadeMs: Int = 0 // 0, 500, 1000, 2000, 3000
)

data class EqPresetSpec(
    val id: Int,
    val name: String,
    val bands: List<Int>
)

class SettingsStore(private val context: Context) {
    companion object {
        private val KEY_THEME = stringPreferencesKey("app_theme")
        private val KEY_REDUCE_EFFECTS = booleanPreferencesKey("reduce_effects")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_GRID_VIEW = booleanPreferencesKey("vault_grid_view")

        private val KEY_LOQUENDO_URL = stringPreferencesKey("loquendo_server_url")
        private val KEY_LOQUENDO_KEY = stringPreferencesKey("loquendo_api_key")
        private val KEY_LOQUENDO_METHOD = stringPreferencesKey("loquendo_method")
        private val KEY_LOQUENDO_VOICE = stringPreferencesKey("loquendo_voice")

        private val KEY_PLAYER_DEFAULT_SPEED = floatPreferencesKey("player_default_speed")
        private val KEY_PLAYER_JUMP_SECONDS = intPreferencesKey("player_jump_seconds")
        private val KEY_PLAYER_AUTO_REWIND = intPreferencesKey("player_auto_rewind")
        private val KEY_PLAYER_SKIP_SILENCE = booleanPreferencesKey("player_skip_silence")
        private val KEY_PLAYER_NORMALIZE = booleanPreferencesKey("player_normalize_volume")
        private val KEY_PLAYER_NORMALIZE_GAIN_DB = floatPreferencesKey("player_normalize_gain_db")
        private val KEY_PLAYER_EQ_PRESET = intPreferencesKey("player_eq_preset")
        private val KEY_PLAYER_EQ_BANDS = stringPreferencesKey("player_eq_bands")
        private val KEY_PLAYER_RESUME_BT = booleanPreferencesKey("player_resume_bluetooth")
        private val KEY_PLAYER_AUTO_NEXT = booleanPreferencesKey("player_auto_next_chapter")
        private val KEY_PLAYER_MONO = booleanPreferencesKey("player_mono_audio")
        private val KEY_PLAYER_BALANCE = floatPreferencesKey("player_balance_pan")
        private val KEY_PLAYER_BASS_BOOST = intPreferencesKey("player_bass_boost")
        private val KEY_PLAYER_VIRTUALIZER = intPreferencesKey("player_virtualizer")
        private val KEY_PLAYER_FADE_PAUSE = booleanPreferencesKey("player_fade_pause_resume")
        private val KEY_PLAYER_CROSSFADE_MS = intPreferencesKey("player_crossfade_ms")

        val EQ_PRESETS = listOf(
            EqPresetSpec(0, "Normal", listOf(0, 0, 0, 0, 0)),
            EqPresetSpec(1, "Voz clara", listOf(-200, 150, 500, 400, 100)),
            EqPresetSpec(2, "Graves suaves", listOf(450, 250, 0, -100, -150)),
            EqPresetSpec(3, "Agudos nítidos", listOf(-150, 0, 200, 450, 550)),
            EqPresetSpec(4, "Modo noche", listOf(-400, -100, 350, 250, -250))
        )
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { preferences ->
        val themeStr = preferences[KEY_THEME] ?: AppThemeMode.DARK.name
        try {
            AppThemeMode.valueOf(themeStr)
        } catch (_: Exception) {
            AppThemeMode.DARK
        }
    }

    val reduceEffects: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_REDUCE_EFFECTS] ?: false
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val isGridView: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_GRID_VIEW] ?: true
    }

    val loquendoConfig: Flow<LoquendoServerConfig> = context.dataStore.data.map { preferences ->
        LoquendoServerConfig(
            serverUrl = preferences[KEY_LOQUENDO_URL] ?: "",
            apiKey = preferences[KEY_LOQUENDO_KEY] ?: "",
            method = preferences[KEY_LOQUENDO_METHOD] ?: "POST_JSON",
            voice = preferences[KEY_LOQUENDO_VOICE] ?: "Jorge"
        )
    }

    val playerSettings: Flow<PlayerSettings> = context.dataStore.data.map { preferences ->
        readPlayerSettings(preferences)
    }

    private fun readPlayerSettings(preferences: Preferences): PlayerSettings {
        val bandsRaw = preferences[KEY_PLAYER_EQ_BANDS]
        val parsedBands = bandsRaw?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.size == 5 }
            ?: listOf(0, 0, 0, 0, 0)

        return PlayerSettings(
            defaultSpeed = (preferences[KEY_PLAYER_DEFAULT_SPEED] ?: 1.0f).coerceIn(0.5f, 3.0f),
            jumpSeconds = (preferences[KEY_PLAYER_JUMP_SECONDS] ?: 15).let { if (it in listOf(10, 15, 30)) it else 15 },
            autoRewindSeconds = (preferences[KEY_PLAYER_AUTO_REWIND] ?: 3).coerceIn(0, 5),
            skipSilence = preferences[KEY_PLAYER_SKIP_SILENCE] ?: false,
            normalizeVolume = preferences[KEY_PLAYER_NORMALIZE] ?: false,
            normalizeGainDb = (preferences[KEY_PLAYER_NORMALIZE_GAIN_DB] ?: 3.0f).coerceIn(0f, 6f),
            eqPreset = preferences[KEY_PLAYER_EQ_PRESET] ?: 0,
            eqBands = parsedBands,
            resumeOnBluetooth = preferences[KEY_PLAYER_RESUME_BT] ?: false,
            autoNextChapter = preferences[KEY_PLAYER_AUTO_NEXT] ?: true,
            monoAudio = preferences[KEY_PLAYER_MONO] ?: false,
            balancePan = (preferences[KEY_PLAYER_BALANCE] ?: 0f).coerceIn(-1f, 1f),
            bassBoostStrength = (preferences[KEY_PLAYER_BASS_BOOST] ?: 0).coerceIn(0, 1000),
            virtualizerStrength = (preferences[KEY_PLAYER_VIRTUALIZER] ?: 0).coerceIn(0, 1000),
            fadeOnPauseResume = preferences[KEY_PLAYER_FADE_PAUSE] ?: true,
            crossfadeMs = (preferences[KEY_PLAYER_CROSSFADE_MS] ?: 0).coerceIn(0, 3000)
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME] = mode.name
        }
    }

    suspend fun setReduceEffects(reduce: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_REDUCE_EFFECTS] = reduce
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setGridView(isGrid: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GRID_VIEW] = isGrid
        }
    }

    suspend fun setLoquendoConfig(
        serverUrl: String,
        apiKey: String,
        method: String,
        voice: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOQUENDO_URL] = serverUrl.trim()
            preferences[KEY_LOQUENDO_KEY] = apiKey.trim()
            preferences[KEY_LOQUENDO_METHOD] = method
            preferences[KEY_LOQUENDO_VOICE] = voice.trim().ifBlank { "Jorge" }
        }
    }

    suspend fun updatePlayerSettings(transform: (PlayerSettings) -> PlayerSettings) {
        context.dataStore.edit { preferences ->
            val current = readPlayerSettings(preferences)
            val next = transform(current)
            preferences[KEY_PLAYER_DEFAULT_SPEED] = next.defaultSpeed.coerceIn(0.5f, 3.0f)
            preferences[KEY_PLAYER_JUMP_SECONDS] = next.jumpSeconds
            preferences[KEY_PLAYER_AUTO_REWIND] = next.autoRewindSeconds.coerceIn(0, 5)
            preferences[KEY_PLAYER_SKIP_SILENCE] = next.skipSilence
            preferences[KEY_PLAYER_NORMALIZE] = next.normalizeVolume
            preferences[KEY_PLAYER_NORMALIZE_GAIN_DB] = next.normalizeGainDb.coerceIn(0f, 6f)
            preferences[KEY_PLAYER_EQ_PRESET] = next.eqPreset
            preferences[KEY_PLAYER_EQ_BANDS] = next.eqBands.take(5).joinToString(",")
            preferences[KEY_PLAYER_RESUME_BT] = next.resumeOnBluetooth
            preferences[KEY_PLAYER_AUTO_NEXT] = next.autoNextChapter
            preferences[KEY_PLAYER_MONO] = next.monoAudio
            preferences[KEY_PLAYER_BALANCE] = next.balancePan.coerceIn(-1f, 1f)
            preferences[KEY_PLAYER_BASS_BOOST] = next.bassBoostStrength.coerceIn(0, 1000)
            preferences[KEY_PLAYER_VIRTUALIZER] = next.virtualizerStrength.coerceIn(0, 1000)
            preferences[KEY_PLAYER_FADE_PAUSE] = next.fadeOnPauseResume
            preferences[KEY_PLAYER_CROSSFADE_MS] = next.crossfadeMs.coerceIn(0, 3000)
        }
    }
}
