package br.com.energybillai.data.local.game

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gameReminderDataStore by preferencesDataStore(name = "energy_game_reminders")

private object GameReminderKeys {
    val lastWeeklyReminderDate = stringPreferencesKey("last_weekly_reminder_date")
    val lastChallengeReminderDate = stringPreferencesKey("last_challenge_reminder_date")
    val lastChallengeReminderId = stringPreferencesKey("last_challenge_reminder_id")
}

data class GameReminderSnapshot(
    val lastWeeklyReminderDate: String? = null,
    val lastChallengeReminderDate: String? = null,
    val lastChallengeReminderId: String? = null,
)

@Singleton
class GameReminderStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun snapshot(): GameReminderSnapshot {
        return context.gameReminderDataStore.data
            .catch { emit(emptyPreferences()) }
            .map { preferences ->
                GameReminderSnapshot(
                    lastWeeklyReminderDate = preferences[GameReminderKeys.lastWeeklyReminderDate],
                    lastChallengeReminderDate = preferences[GameReminderKeys.lastChallengeReminderDate],
                    lastChallengeReminderId = preferences[GameReminderKeys.lastChallengeReminderId],
                )
            }
            .first()
    }

    suspend fun markWeeklyReminder(date: String) {
        context.gameReminderDataStore.edit { preferences ->
            preferences[GameReminderKeys.lastWeeklyReminderDate] = date
        }
    }

    suspend fun markChallengeReminder(date: String, challengeId: String) {
        context.gameReminderDataStore.edit { preferences ->
            preferences[GameReminderKeys.lastChallengeReminderDate] = date
            preferences[GameReminderKeys.lastChallengeReminderId] = challengeId
        }
    }

    suspend fun clear() {
        context.gameReminderDataStore.edit { it.clear() }
    }
}
