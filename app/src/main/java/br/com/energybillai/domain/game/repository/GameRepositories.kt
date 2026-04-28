package br.com.energybillai.domain.game.repository

import br.com.energybillai.domain.game.model.Achievement
import br.com.energybillai.domain.game.model.AchievementType
import br.com.energybillai.domain.game.model.EnergyChallenge
import br.com.energybillai.domain.game.model.EnergyChallengeStatus
import br.com.energybillai.domain.game.model.MeterReading
import br.com.energybillai.domain.game.model.UserScore
import kotlinx.coroutines.flow.Flow

interface GameRepository {
    fun observeSuggestedChallenge(userId: String): Flow<EnergyChallenge?>
    fun observeActiveChallenge(userId: String): Flow<EnergyChallenge?>
    fun observeChallenges(userId: String): Flow<List<EnergyChallenge>>
    suspend fun getChallengeById(challengeId: String): EnergyChallenge?
    suspend fun upsertChallenge(challenge: EnergyChallenge)
    suspend fun acceptSuggestedChallenge(challengeId: String)
    suspend fun updateChallengeStatus(challengeId: String, status: EnergyChallengeStatus)
    suspend fun clearSuggestedChallenges(userId: String)
    suspend fun clearActiveChallenges(userId: String)

    fun observeUserScore(userId: String): Flow<UserScore?>
    suspend fun getUserScore(userId: String): UserScore?
    suspend fun upsertUserScore(score: UserScore)

    fun observeAchievements(userId: String): Flow<List<Achievement>>
    suspend fun getAchievement(userId: String, type: AchievementType): Achievement?
    suspend fun upsertAchievement(achievement: Achievement)
}

interface MeterReadingRepository {
    fun observeReadings(userId: String): Flow<List<MeterReading>>
    suspend fun getReadingById(readingId: String): MeterReading?
    suspend fun getLatestConfirmedReading(userId: String): MeterReading?
    suspend fun getLatestReadings(userId: String, limit: Int): List<MeterReading>
    suspend fun saveReading(reading: MeterReading)
    suspend fun deleteReading(readingId: String)
}

interface GameReminderRepository {
    suspend fun ensureScheduled()
    suspend fun cancelAll()
}
