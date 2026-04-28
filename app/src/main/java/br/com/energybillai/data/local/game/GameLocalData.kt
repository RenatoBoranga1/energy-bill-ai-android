package br.com.energybillai.data.local.game

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.energybillai.domain.game.model.Achievement
import br.com.energybillai.domain.game.model.AchievementType
import br.com.energybillai.domain.game.model.EnergyChallenge
import br.com.energybillai.domain.game.model.EnergyChallengeStatus
import br.com.energybillai.domain.game.model.EnergyChallengeType
import br.com.energybillai.domain.game.model.MeterReading
import br.com.energybillai.domain.game.model.UserScore
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "energy_challenges",
    indices = [Index(value = ["userId", "status"], name = "index_energy_challenges_userId_status")],
)
data class EnergyChallengeEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val type: String,
    val targetKwh: Double?,
    val targetBrl: Double?,
    val startDate: String,
    val endDate: String,
    val status: String,
    val progressPercent: Double,
    val rewardXp: Int,
    val baselineConsumptionKwh: Double?,
    val baselineValueBrl: Double?,
    val currentSavedKwh: Double?,
    val currentSavedBrl: Double?,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "meter_readings",
    indices = [Index(value = ["userId", "readingDate"], name = "index_meter_readings_userId_readingDate")],
)
data class MeterReadingEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val readingDate: String,
    val meterValueKwh: Long,
    val imageUri: String?,
    val extractedOcrValue: Long?,
    val confirmedValueKwh: Long,
    val confidenceScore: Double?,
    val observation: String?,
    val isInitialReading: Boolean,
    val isSuspicious: Boolean,
    val createdAt: String,
)

@Entity(tableName = "user_scores")
data class UserScoreEntity(
    @PrimaryKey val userId: String,
    val totalXp: Int,
    val currentLevel: Int,
    val levelName: String,
    val xpToNextLevel: Int,
    val weeklyStreak: Int,
    val lastWeeklyReadingDate: String?,
    val updatedAt: String,
)

@Entity(
    tableName = "achievements",
    indices = [Index(value = ["userId", "type"], name = "index_achievements_userId_type")],
)
data class AchievementEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val description: String,
    val unlockedAt: String?,
    val isUnlocked: Boolean,
)

@Dao
interface EnergyChallengeDao {
    @Query(
        """
        SELECT * FROM energy_challenges
        WHERE userId = :userId AND status = 'SUGGESTED'
        ORDER BY updatedAt DESC
        LIMIT 1
        """,
    )
    fun observeSuggestedChallenge(userId: String): Flow<EnergyChallengeEntity?>

    @Query(
        """
        SELECT * FROM energy_challenges
        WHERE userId = :userId AND status = 'ACTIVE'
        ORDER BY updatedAt DESC
        LIMIT 1
        """,
    )
    fun observeActiveChallenge(userId: String): Flow<EnergyChallengeEntity?>

    @Query("SELECT * FROM energy_challenges WHERE userId = :userId ORDER BY startDate DESC, updatedAt DESC")
    fun observeChallenges(userId: String): Flow<List<EnergyChallengeEntity>>

    @Query("SELECT * FROM energy_challenges WHERE id = :challengeId LIMIT 1")
    suspend fun getById(challengeId: String): EnergyChallengeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(challenge: EnergyChallengeEntity)

    @Query(
        """
        UPDATE energy_challenges
        SET status = 'ACTIVE', updatedAt = :updatedAt
        WHERE id = :challengeId AND status = 'SUGGESTED'
        """,
    )
    suspend fun acceptSuggestedChallenge(challengeId: String, updatedAt: String)

    @Query("UPDATE energy_challenges SET status = :status, updatedAt = :updatedAt WHERE id = :challengeId")
    suspend fun updateStatus(challengeId: String, status: String, updatedAt: String)

    @Query("UPDATE energy_challenges SET status = 'CANCELED', updatedAt = :updatedAt WHERE userId = :userId AND status = 'SUGGESTED'")
    suspend fun clearSuggestedChallenges(userId: String, updatedAt: String)

    @Query("UPDATE energy_challenges SET status = 'CANCELED', updatedAt = :updatedAt WHERE userId = :userId AND status = 'ACTIVE'")
    suspend fun clearActiveChallenges(userId: String, updatedAt: String)
}

@Dao
interface MeterReadingDao {
    @Query("SELECT * FROM meter_readings WHERE userId = :userId ORDER BY readingDate DESC, createdAt DESC")
    fun observeReadings(userId: String): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings WHERE id = :readingId LIMIT 1")
    suspend fun getById(readingId: String): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings WHERE userId = :userId ORDER BY readingDate DESC, createdAt DESC LIMIT 1")
    suspend fun getLatestConfirmedReading(userId: String): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings WHERE userId = :userId ORDER BY readingDate DESC, createdAt DESC LIMIT :limit")
    suspend fun getLatestReadings(userId: String, limit: Int): List<MeterReadingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reading: MeterReadingEntity)

    @Query("DELETE FROM meter_readings WHERE id = :readingId")
    suspend fun deleteById(readingId: String)
}

@Dao
interface UserScoreDao {
    @Query("SELECT * FROM user_scores WHERE userId = :userId LIMIT 1")
    fun observeByUserId(userId: String): Flow<UserScoreEntity?>

    @Query("SELECT * FROM user_scores WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): UserScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(score: UserScoreEntity)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements WHERE userId = :userId ORDER BY unlockedAt DESC, title ASC")
    fun observeAchievements(userId: String): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE userId = :userId AND type = :type LIMIT 1")
    suspend fun getByType(userId: String, type: String): AchievementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(achievement: AchievementEntity)
}

val GAME_DB_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `energy_challenges` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `targetKwh` REAL,
                `targetBrl` REAL,
                `startDate` TEXT NOT NULL,
                `endDate` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `progressPercent` REAL NOT NULL,
                `rewardXp` INTEGER NOT NULL,
                `baselineConsumptionKwh` REAL,
                `baselineValueBrl` REAL,
                `currentSavedKwh` REAL,
                `currentSavedBrl` REAL,
                `createdAt` TEXT NOT NULL,
                `updatedAt` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `meter_readings` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `readingDate` TEXT NOT NULL,
                `meterValueKwh` INTEGER NOT NULL,
                `imageUri` TEXT,
                `extractedOcrValue` INTEGER,
                `confirmedValueKwh` INTEGER NOT NULL,
                `confidenceScore` REAL,
                `observation` TEXT,
                `isInitialReading` INTEGER NOT NULL,
                `isSuspicious` INTEGER NOT NULL,
                `createdAt` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_scores` (
                `userId` TEXT NOT NULL,
                `totalXp` INTEGER NOT NULL,
                `currentLevel` INTEGER NOT NULL,
                `levelName` TEXT NOT NULL,
                `xpToNextLevel` INTEGER NOT NULL,
                `weeklyStreak` INTEGER NOT NULL,
                `lastWeeklyReadingDate` TEXT,
                `updatedAt` TEXT NOT NULL,
                PRIMARY KEY(`userId`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `achievements` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `unlockedAt` TEXT,
                `isUnlocked` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_energy_challenges_userId_status` ON `energy_challenges` (`userId`, `status`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_meter_readings_userId_readingDate` ON `meter_readings` (`userId`, `readingDate`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_achievements_userId_type` ON `achievements` (`userId`, `type`)")
    }
}

fun EnergyChallengeEntity.toDomain(): EnergyChallenge {
    return EnergyChallenge(
        id = id,
        userId = userId,
        title = title,
        description = description,
        type = runCatching { EnergyChallengeType.valueOf(type) }.getOrDefault(EnergyChallengeType.REDUCE_DAILY_KWH),
        targetKwh = targetKwh,
        targetBrl = targetBrl,
        startDate = startDate,
        endDate = endDate,
        status = runCatching { EnergyChallengeStatus.valueOf(status) }.getOrDefault(EnergyChallengeStatus.ACTIVE),
        progressPercent = progressPercent,
        rewardXp = rewardXp,
        baselineConsumptionKwh = baselineConsumptionKwh,
        baselineValueBrl = baselineValueBrl,
        currentSavedKwh = currentSavedKwh,
        currentSavedBrl = currentSavedBrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun EnergyChallenge.toEntity(): EnergyChallengeEntity {
    return EnergyChallengeEntity(
        id = id,
        userId = userId,
        title = title,
        description = description,
        type = type.name,
        targetKwh = targetKwh,
        targetBrl = targetBrl,
        startDate = startDate,
        endDate = endDate,
        status = status.name,
        progressPercent = progressPercent,
        rewardXp = rewardXp,
        baselineConsumptionKwh = baselineConsumptionKwh,
        baselineValueBrl = baselineValueBrl,
        currentSavedKwh = currentSavedKwh,
        currentSavedBrl = currentSavedBrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun MeterReadingEntity.toDomain(): MeterReading {
    return MeterReading(
        id = id,
        userId = userId,
        readingDate = readingDate,
        meterValueKwh = meterValueKwh,
        imageUri = imageUri,
        extractedOcrValue = extractedOcrValue,
        confirmedValue = confirmedValueKwh,
        confidenceScore = confidenceScore,
        observation = observation,
        isInitialReading = isInitialReading,
        isSuspicious = isSuspicious,
        createdAt = createdAt,
    )
}

fun MeterReading.toEntity(): MeterReadingEntity {
    return MeterReadingEntity(
        id = id,
        userId = userId,
        readingDate = readingDate,
        meterValueKwh = meterValueKwh,
        imageUri = imageUri,
        extractedOcrValue = extractedOcrValue,
        confirmedValueKwh = confirmedValue,
        confidenceScore = confidenceScore,
        observation = observation,
        isInitialReading = isInitialReading,
        isSuspicious = isSuspicious,
        createdAt = createdAt,
    )
}

fun UserScoreEntity.toDomain(): UserScore {
    return UserScore(
        userId = userId,
        totalXp = totalXp,
        currentLevel = currentLevel,
        levelName = levelName,
        xpToNextLevel = xpToNextLevel,
        weeklyStreak = weeklyStreak,
        lastWeeklyReadingDate = lastWeeklyReadingDate,
        updatedAt = updatedAt,
    )
}

fun UserScore.toEntity(): UserScoreEntity {
    return UserScoreEntity(
        userId = userId,
        totalXp = totalXp,
        currentLevel = currentLevel,
        levelName = levelName,
        xpToNextLevel = xpToNextLevel,
        weeklyStreak = weeklyStreak,
        lastWeeklyReadingDate = lastWeeklyReadingDate,
        updatedAt = updatedAt,
    )
}

fun AchievementEntity.toDomain(): Achievement {
    return Achievement(
        id = id,
        userId = userId,
        type = runCatching { AchievementType.valueOf(type) }.getOrDefault(AchievementType.FIRST_READING),
        title = title,
        description = description,
        unlockedAt = unlockedAt,
        isUnlocked = isUnlocked,
    )
}

fun Achievement.toEntity(): AchievementEntity {
    return AchievementEntity(
        id = id,
        userId = userId,
        type = type.name,
        title = title,
        description = description,
        unlockedAt = unlockedAt,
        isUnlocked = isUnlocked,
    )
}
