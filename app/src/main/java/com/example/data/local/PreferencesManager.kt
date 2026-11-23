package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cricscore_prefs")

data class DefaultMatchPreferences(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val defaultOvers: Int = 10,
    val defaultPlayers: Int = 11,
    val defaultWideReball: Boolean = true,
    val defaultWideRuns: Int = 1,
    val defaultNoBallReball: Boolean = true,
    val defaultNoBallRuns: Int = 1,
    val defaultLastManStands: Boolean = false,
    val askShotDirection: Boolean = true
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_OVERS = intPreferencesKey("default_overs")
        val DEFAULT_PLAYERS = intPreferencesKey("default_players")
        val DEFAULT_WIDE_REBALL = booleanPreferencesKey("default_wide_reball")
        val DEFAULT_WIDE_RUNS = intPreferencesKey("default_wide_runs")
        val DEFAULT_NOBALL_REBALL = booleanPreferencesKey("default_noball_reball")
        val DEFAULT_NOBALL_RUNS = intPreferencesKey("default_noball_runs")
        val DEFAULT_LAST_MAN = booleanPreferencesKey("default_last_man")
        val ASK_SHOT_DIRECTION = booleanPreferencesKey("ask_shot_direction")
    }

    val preferencesFlow: Flow<DefaultMatchPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.LIGHT.name
        DefaultMatchPreferences(
            themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.LIGHT),
            defaultOvers = prefs[Keys.DEFAULT_OVERS] ?: 10,
            defaultPlayers = prefs[Keys.DEFAULT_PLAYERS] ?: 11,
            defaultWideReball = prefs[Keys.DEFAULT_WIDE_REBALL] ?: true,
            defaultWideRuns = prefs[Keys.DEFAULT_WIDE_RUNS] ?: 1,
            defaultNoBallReball = prefs[Keys.DEFAULT_NOBALL_REBALL] ?: true,
            defaultNoBallRuns = prefs[Keys.DEFAULT_NOBALL_RUNS] ?: 1,
            defaultLastManStands = prefs[Keys.DEFAULT_LAST_MAN] ?: false,
            askShotDirection = prefs[Keys.ASK_SHOT_DIRECTION] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAskShotDirection(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ASK_SHOT_DIRECTION] = enabled }
    }

    suspend fun updateDefaultRules(
        overs: Int,
        players: Int,
        wideReball: Boolean,
        wideRuns: Int,
        noBallReball: Boolean,
        noBallRuns: Int,
        lastManStands: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_OVERS] = overs.coerceIn(1, 99)
            prefs[Keys.DEFAULT_PLAYERS] = players.coerceIn(2, 15)
            prefs[Keys.DEFAULT_WIDE_REBALL] = wideReball
            prefs[Keys.DEFAULT_WIDE_RUNS] = wideRuns.coerceIn(0, 4)
            prefs[Keys.DEFAULT_NOBALL_REBALL] = noBallReball
            prefs[Keys.DEFAULT_NOBALL_RUNS] = noBallRuns.coerceIn(0, 4)
            prefs[Keys.DEFAULT_LAST_MAN] = lastManStands
        }
    }
}
