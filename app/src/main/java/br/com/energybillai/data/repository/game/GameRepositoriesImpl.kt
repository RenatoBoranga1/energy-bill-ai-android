package br.com.energybillai.data.repository.game

import br.com.energybillai.data.local.game.AchievementDao
import br.com.energybillai.data.local.game.EnergyChallengeDao
import br.com.energybillai.data.local.game.MeterReadingDao
import br.com.energybillai.data.local.game.UserScoreDao
import br.com.energybillai.data.local.game.toDomain
import br.com.energybillai.data.local.game.toEntity
import br.com.energybillai.domain.game.model.Achievement
import br.com.energybillai.domain.game.model.AchievementType
import br.com.energybillai.domain.game.model.EnergyChallenge
import br.com.energybillai.domain.game.model.EnergyChallengeStatus
import br.com.energybillai.domain.game.model.MeterReading
import br.com.energybillai.domain.game.model.UserScore
import br.com.energybillai.domain.game.repository.GameRepository
import br.com.energybillai.domain.game.repository.MeterReadingRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DefaultGameRepository @Inject constructor(
    private val challengeDao: EnergyChallengeDao,
    private val userScoreDao: UserScoreDao,
    private val achievementDao: AchievementDao,
) : GameRepository {

    override fun observeSuggestedChallenge(userId: String): Flow<EnergyChallenge?> {
        return challengeDao.observeSuggestedChallenge(userId).map { it?.toDomain() }
    }

    override fun observeActiveChallenge(userId: String): Flow<EnergyChallenge?> {
        return challengeDao.observeActiveChallenge(userId).map { it?.toDomain() }
    }

    override fun observeChallenges(userId: String): Flow<List<EnergyChallenge>> {
        return challengeDao.observeChallenges(userId).map { items -> items.map { it.toDomain() } }
    }

    override suspend fun getChallengeById(challengeId: String): EnergyChallenge? {
        return challengeDao.getById(challengeId)?.toDomain()
    }

    override suspend fun upsertChallenge(challenge: EnergyChallenge) {
        challengeDao.upsert(challenge.toEntity())
    }

    override suspend fun acceptSuggestedChallenge(challengeId: String) {
        challengeDao.acceptSuggestedChallenge(
            challengeId = challengeId,
            updatedAt = Instant.now().toString(),
        )
    }

    override suspend fun updateChallengeStatus(challengeId: String, status: EnergyChallengeStatus) {
        challengeDao.updateStatus(
            challengeId = challengeId,
            status = status.name,
            updatedAt = Instant.now().toString(),
        )
    }

    override suspend fun clearSuggestedChallenges(userId: String) {
        challengeDao.clearSuggestedChallenges(
            userId = userId,
            updatedAt = Instant.now().toString(),
        )
    }

    override suspend fun clearActiveChallenges(userId: String) {
        challengeDao.clearActiveChallenges(
            userId = userId,
            updatedAt = Instant.now().toString(),
        )
    }

    override fun observeUserScore(userId: String): Flow<UserScore?> {
        return userScoreDao.observeByUserId(userId).map { it?.toDomain() }
    }

    override suspend fun getUserScore(userId: String): UserScore? {
        return userScoreDao.getByUserId(userId)?.toDomain()
    }

    override suspend fun upsertUserScore(score: UserScore) {
        userScoreDao.upsert(score.toEntity())
    }

    override fun observeAchievements(userId: String): Flow<List<Achievement>> {
        return achievementDao.observeAchievements(userId).map { items -> items.map { it.toDomain() } }
    }

    override suspend fun getAchievement(userId: String, type: AchievementType): Achievement? {
        return achievementDao.getByType(userId, type.name)?.toDomain()
    }

    override suspend fun upsertAchievement(achievement: Achievement) {
        achievementDao.upsert(achievement.toEntity())
    }
}

@Singleton
class DefaultMeterReadingRepository @Inject constructor(
    private val meterReadingDao: MeterReadingDao,
) : MeterReadingRepository {

    override fun observeReadings(userId: String): Flow<List<MeterReading>> {
        return meterReadingDao.observeReadings(userId).map { items -> items.map { it.toDomain() } }
    }

    override suspend fun getReadingById(readingId: String): MeterReading? {
        return meterReadingDao.getById(readingId)?.toDomain()
    }

    override suspend fun getLatestConfirmedReading(userId: String): MeterReading? {
        return meterReadingDao.getLatestConfirmedReading(userId)?.toDomain()
    }

    override suspend fun getLatestReadings(userId: String, limit: Int): List<MeterReading> {
        return meterReadingDao.getLatestReadings(userId, limit).map { it.toDomain() }
    }

    override suspend fun saveReading(reading: MeterReading) {
        meterReadingDao.upsert(reading.toEntity())
    }

    override suspend fun deleteReading(readingId: String) {
        meterReadingDao.deleteById(readingId)
    }
}
