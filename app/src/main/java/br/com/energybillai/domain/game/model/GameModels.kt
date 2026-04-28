package br.com.energybillai.domain.game.model

enum class EnergyChallengeType {
    REDUCE_DAILY_KWH,
    REDUCE_VS_PREVIOUS_MONTH,
    STAY_BELOW_FORECAST,
    REDUCE_WEEKLY_CONSUMPTION,
    SAVE_TARGET_BRL,
}

enum class EnergyChallengeStatus {
    SUGGESTED,
    ACTIVE,
    COMPLETED,
    FAILED,
    CANCELED,
}

enum class AchievementType {
    FIRST_READING,
    FIRST_SAVINGS,
    FOUR_WEEKS_STREAK,
    REDUCED_FIVE_PERCENT,
    SAVED_FIFTY_BRL,
    MONTHLY_GOAL_COMPLETED,
}

data class EnergyChallenge(
    val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val type: EnergyChallengeType,
    val targetKwh: Double? = null,
    val targetBrl: Double? = null,
    val startDate: String,
    val endDate: String,
    val status: EnergyChallengeStatus,
    val progressPercent: Double,
    val rewardXp: Int,
    val baselineConsumptionKwh: Double? = null,
    val baselineValueBrl: Double? = null,
    val currentSavedKwh: Double? = null,
    val currentSavedBrl: Double? = null,
    val createdAt: String,
    val updatedAt: String,
)

data class MeterReading(
    val id: String,
    val userId: String,
    val readingDate: String,
    val meterValueKwh: Long,
    val imageUri: String?,
    val extractedOcrValue: Long?,
    val confirmedValue: Long,
    val confidenceScore: Double?,
    val observation: String?,
    val isInitialReading: Boolean,
    val isSuspicious: Boolean,
    val createdAt: String,
)

data class UserScore(
    val userId: String,
    val totalXp: Int,
    val currentLevel: Int,
    val levelName: String,
    val xpToNextLevel: Int,
    val weeklyStreak: Int,
    val lastWeeklyReadingDate: String? = null,
    val updatedAt: String,
)

data class Achievement(
    val id: String,
    val userId: String,
    val type: AchievementType,
    val title: String,
    val description: String,
    val unlockedAt: String?,
    val isUnlocked: Boolean,
)

data class WeeklyConsumption(
    val startDate: String,
    val endDate: String,
    val consumptionKwh: Double,
    val averageDailyKwh: Double,
    val estimatedCostBrl: Double?,
    val vsExpectedPercent: Double? = null,
    val vsPreviousWeekPercent: Double? = null,
    val isSuspicious: Boolean = false,
)

data class SavingsProjection(
    val dailyReductionKwh: Double? = null,
    val monthlyReductionPercent: Double? = null,
    val estimatedMonthlySavingsKwh: Double,
    val estimatedMonthlySavingsBrl: Double,
    val estimatedClosingValueBrl: Double? = null,
)

data class MeterReadingValidation(
    val isAccepted: Boolean,
    val isInitialReading: Boolean,
    val isSuspicious: Boolean,
    val blockingMessage: String? = null,
    val warningMessage: String? = null,
    val previousReading: MeterReading? = null,
    val weeklyConsumptionKwh: Double? = null,
    val daysBetweenReadings: Int? = null,
)

data class ChallengeProgressSnapshot(
    val updatedChallenge: EnergyChallenge?,
    val weeklyConsumption: WeeklyConsumption?,
    val savingsProjection: SavingsProjection?,
    val progressMessage: String,
)

data class GameEngineResult(
    val savedReading: MeterReading,
    val activeChallenge: EnergyChallenge?,
    val weeklyConsumption: WeeklyConsumption?,
    val userScore: UserScore,
    val unlockedAchievements: List<Achievement>,
    val gainedXp: Int,
    val savingsProjection: SavingsProjection?,
    val message: String,
)

data class GameProgress(
    val suggestedChallenge: EnergyChallenge? = null,
    val activeChallenge: EnergyChallenge? = null,
    val latestWeeklyConsumption: WeeklyConsumption? = null,
    val userScore: UserScore? = null,
    val achievements: List<Achievement> = emptyList(),
    val pendingWeeklyReading: Boolean = false,
    val motivationalMessage: String? = null,
)
