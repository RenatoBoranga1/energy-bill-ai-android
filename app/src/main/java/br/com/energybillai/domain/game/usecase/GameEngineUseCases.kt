package br.com.energybillai.domain.game.usecase

import br.com.energybillai.core.common.AppError
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.domain.game.model.Achievement
import br.com.energybillai.domain.game.model.AchievementType
import br.com.energybillai.domain.game.model.ChallengeProgressSnapshot
import br.com.energybillai.domain.game.model.EnergyChallenge
import br.com.energybillai.domain.game.model.EnergyChallengeStatus
import br.com.energybillai.domain.game.model.EnergyChallengeType
import br.com.energybillai.domain.game.model.GameEngineResult
import br.com.energybillai.domain.game.model.GameProgress
import br.com.energybillai.domain.game.model.MeterReading
import br.com.energybillai.domain.game.model.MeterReadingValidation
import br.com.energybillai.domain.game.model.SavingsProjection
import br.com.energybillai.domain.game.model.UserScore
import br.com.energybillai.domain.game.model.WeeklyConsumption
import br.com.energybillai.domain.game.repository.GameRepository
import br.com.energybillai.domain.game.repository.MeterReadingRepository
import br.com.energybillai.domain.model.BillForecast
import br.com.energybillai.domain.model.BillSummary
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class EnergySavingsCalculator @Inject constructor() {

    fun resolveReferenceTariff(
        history: List<BillSummary>,
        forecast: BillForecast?,
        manualTariffBrlPerKwh: Double? = null,
    ): Double? {
        if (manualTariffBrlPerKwh != null && manualTariffBrlPerKwh > 0) return manualTariffBrlPerKwh
        forecast?.referenceTariffBrlPerKwh?.takeIf { it > 0 }?.let { return it }
        return history
            .asSequence()
            .mapNotNull { bill ->
                val consumption = bill.consumptionKwh
                val totalValue = bill.totalValue
                if (consumption != null && totalValue != null && consumption > 0) totalValue / consumption else null
            }
            .firstOrNull()
    }

    fun buildReductionProjection(
        baseConsumptionKwh: Double?,
        tariffBrlPerKwh: Double?,
        dailyReductionKwh: Double? = null,
        monthlyReductionPercent: Double? = null,
        periodDays: Int = 30,
    ): SavingsProjection? {
        val baseConsumption = baseConsumptionKwh ?: return null
        val tariff = tariffBrlPerKwh ?: return null
        if (baseConsumption <= 0 || tariff <= 0 || periodDays <= 0) return null

        val targetReductionKwh = when {
            dailyReductionKwh != null -> dailyReductionKwh * periodDays
            monthlyReductionPercent != null -> baseConsumption * (monthlyReductionPercent / 100.0)
            else -> return null
        }.coerceAtLeast(0.0)

        val estimatedSavingsBrl = targetReductionKwh * tariff
        val estimatedClosingValue = max(0.0, (baseConsumption * tariff) - estimatedSavingsBrl)

        return SavingsProjection(
            dailyReductionKwh = dailyReductionKwh,
            monthlyReductionPercent = monthlyReductionPercent,
            estimatedMonthlySavingsKwh = targetReductionKwh,
            estimatedMonthlySavingsBrl = estimatedSavingsBrl,
            estimatedClosingValueBrl = estimatedClosingValue,
        )
    }

    fun estimateCost(consumptionKwh: Double?, tariffBrlPerKwh: Double?): Double? {
        if (consumptionKwh == null || tariffBrlPerKwh == null || consumptionKwh < 0 || tariffBrlPerKwh <= 0) return null
        return consumptionKwh * tariffBrlPerKwh
    }
}

class MeterReadingValidator @Inject constructor() {

    fun validate(
        readingDate: LocalDate,
        confirmedValue: Long,
        previousReading: MeterReading?,
        expectedConsumptionKwh: Double? = null,
    ): MeterReadingValidation {
        if (confirmedValue < 0) {
            return MeterReadingValidation(
                isAccepted = false,
                isInitialReading = previousReading == null,
                isSuspicious = false,
                blockingMessage = "A leitura confirmada nao pode ser negativa.",
                previousReading = previousReading,
            )
        }

        if (previousReading == null) {
            return MeterReadingValidation(
                isAccepted = true,
                isInitialReading = true,
                isSuspicious = false,
                warningMessage = "Esta sera usada como leitura inicial. O consumo semanal sera calculado a partir da proxima leitura.",
            )
        }

        val previousDate = parseDate(previousReading.readingDate) ?: return MeterReadingValidation(
            isAccepted = false,
            isInitialReading = false,
            isSuspicious = false,
            blockingMessage = "Nao foi possivel validar a nova leitura com a anterior salva.",
            previousReading = previousReading,
        )

        val daysBetween = ChronoUnit.DAYS.between(previousDate, readingDate).toInt()
        if (daysBetween <= 0) {
            return MeterReadingValidation(
                isAccepted = false,
                isInitialReading = false,
                isSuspicious = false,
                blockingMessage = "A nova leitura precisa ter data posterior a leitura anterior.",
                previousReading = previousReading,
            )
        }

        val delta = confirmedValue - previousReading.confirmedValue
        if (delta < 0) {
            return MeterReadingValidation(
                isAccepted = false,
                isInitialReading = false,
                isSuspicious = true,
                blockingMessage = "A leitura confirmada ficou menor que a anterior. Revise o numero do medidor antes de salvar.",
                previousReading = previousReading,
            )
        }

        val weeklyConsumption = delta.toDouble()
        val avgDaily = weeklyConsumption / daysBetween
        val suspicious = when {
            expectedConsumptionKwh != null && expectedConsumptionKwh > 0 && weeklyConsumption > expectedConsumptionKwh * 2.2 -> true
            avgDaily > 35.0 -> true
            daysBetween <= 10 && weeklyConsumption > 250.0 -> true
            else -> false
        }
        val warning = when {
            suspicious -> "Essa leitura gerou um consumo acima do padrao esperado. Vale conferir o numero do medidor antes de confirmar."
            avgDaily > 18.0 -> "O consumo diario desta leitura ficou elevado. O app vai acompanhar esse comportamento nas proximas semanas."
            else -> null
        }

        return MeterReadingValidation(
            isAccepted = true,
            isInitialReading = false,
            isSuspicious = suspicious,
            warningMessage = warning,
            previousReading = previousReading,
            weeklyConsumptionKwh = weeklyConsumption,
            daysBetweenReadings = daysBetween,
        )
    }
}

class ChallengeGenerator @Inject constructor(
    private val energySavingsCalculator: EnergySavingsCalculator,
) {

    fun generate(
        userId: String,
        history: List<BillSummary>,
        forecast: BillForecast?,
        today: LocalDate = LocalDate.now(),
        manualTariffBrlPerKwh: Double? = null,
    ): EnergyChallenge {
        val sortedHistory = history
            .filter { it.consumptionKwh != null && it.totalValue != null }
            .sortedBy { parseMonthKey(it.referenceMonth) ?: YearMonth.of(1970, 1) }
        val latest = sortedHistory.lastOrNull()
        val previous = sortedHistory.getOrNull(sortedHistory.lastIndex - 1)
        val recentAverage = sortedHistory.takeLast(3).mapNotNull { it.consumptionKwh }.averageOrNull()
        val tariff = energySavingsCalculator.resolveReferenceTariff(
            history = history,
            forecast = forecast,
            manualTariffBrlPerKwh = manualTariffBrlPerKwh,
        ) ?: 0.90

        val forecastPoint = forecast?.generatedForecasts?.firstOrNull()
        val monthChallenge = sortedHistory.size >= 2 || forecastPoint != null
        val startDate = today
        val endDate = if (monthChallenge) today.withDayOfMonth(today.lengthOfMonth()) else today.plusDays(6)
        val challengeDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        val expectedMonthlyConsumption = forecastPoint?.predictedKwh
            ?: latest?.consumptionKwh
            ?: recentAverage
            ?: 220.0
        val baselineConsumption = (expectedMonthlyConsumption / startDate.lengthOfMonth()) * challengeDays
        val baselineValue = baselineConsumption * tariff
        val averageDaily = (recentAverage ?: latest?.consumptionKwh ?: 220.0) / 30.0
        val elevatedVsPrevious = latest?.consumptionKwh != null &&
            previous?.consumptionKwh != null &&
            latest.consumptionKwh > previous.consumptionKwh * 1.12
        val forecastPressure = forecastPoint?.predictedKwh != null &&
            latest?.consumptionKwh != null &&
            forecastPoint.predictedKwh > latest.consumptionKwh * 1.05

        val type = when {
            !monthChallenge -> EnergyChallengeType.REDUCE_WEEKLY_CONSUMPTION
            elevatedVsPrevious -> EnergyChallengeType.REDUCE_VS_PREVIOUS_MONTH
            forecastPressure -> EnergyChallengeType.STAY_BELOW_FORECAST
            averageDaily >= 8.5 -> EnergyChallengeType.REDUCE_DAILY_KWH
            else -> EnergyChallengeType.SAVE_TARGET_BRL
        }

        val targetKwh = when (type) {
            EnergyChallengeType.REDUCE_DAILY_KWH -> challengeDays.toDouble()
            EnergyChallengeType.REDUCE_WEEKLY_CONSUMPTION -> 7.0
            EnergyChallengeType.REDUCE_VS_PREVIOUS_MONTH -> {
                val previousConsumption = previous?.consumptionKwh ?: expectedMonthlyConsumption
                val previousEquivalent = (previousConsumption / startDate.lengthOfMonth()) * challengeDays
                max(baselineConsumption - (previousEquivalent * 0.95), baselineConsumption * 0.05)
            }
            EnergyChallengeType.STAY_BELOW_FORECAST -> max(baselineConsumption * 0.05, 12.0)
            EnergyChallengeType.SAVE_TARGET_BRL -> null
        }
        val targetBrl = when (type) {
            EnergyChallengeType.SAVE_TARGET_BRL -> max(baselineValue * 0.08, tariff * 15.0)
            else -> targetKwh?.times(tariff)
        }
        val title = when (type) {
            EnergyChallengeType.REDUCE_DAILY_KWH -> "Reduza 1 kWh por dia"
            EnergyChallengeType.REDUCE_VS_PREVIOUS_MONTH -> "Fique 5% abaixo do mes anterior"
            EnergyChallengeType.STAY_BELOW_FORECAST -> "Fique abaixo da previsao"
            EnergyChallengeType.REDUCE_WEEKLY_CONSUMPTION -> "Controle o consumo desta semana"
            EnergyChallengeType.SAVE_TARGET_BRL -> "Economize em reais neste ciclo"
        }
        val description = when (type) {
            EnergyChallengeType.REDUCE_DAILY_KWH -> {
                "Se voce reduzir cerca de 1 kWh por dia ate ${endDate.toPtBr()}, a proxima conta tende a aliviar."
            }
            EnergyChallengeType.REDUCE_VS_PREVIOUS_MONTH -> {
                "Seu consumo recente subiu. O desafio agora e fechar o periodo atual pelo menos 5% abaixo do mes anterior."
            }
            EnergyChallengeType.STAY_BELOW_FORECAST -> {
                "A previsao aponta pressao de alta. O objetivo e manter o consumo abaixo do cenario previsto para o fechamento do mes."
            }
            EnergyChallengeType.REDUCE_WEEKLY_CONSUMPTION -> {
                "Nesta semana, tente consumir menos do que seu padrao recente para comecar a construir economia real."
            }
            EnergyChallengeType.SAVE_TARGET_BRL -> {
                "Seu objetivo e aliviar a conta em reais ate o fechamento do ciclo, mantendo o consumo dentro de uma faixa mais eficiente."
            }
        }

        return EnergyChallenge(
            id = UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            description = description,
            type = type,
            targetKwh = targetKwh,
            targetBrl = targetBrl,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            status = EnergyChallengeStatus.SUGGESTED,
            progressPercent = 0.0,
            rewardXp = if (monthChallenge) 150 else 50,
            baselineConsumptionKwh = baselineConsumption,
            baselineValueBrl = baselineValue,
            currentSavedKwh = 0.0,
            currentSavedBrl = 0.0,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString(),
        )
    }
}

class ProgressTracker @Inject constructor(
    private val energySavingsCalculator: EnergySavingsCalculator,
) {

    fun updateChallengeProgress(
        challenge: EnergyChallenge?,
        validation: MeterReadingValidation,
        currentReadingDate: LocalDate,
        referenceTariffBrlPerKwh: Double?,
        previousPeriodConsumptionKwh: Double? = null,
    ): ChallengeProgressSnapshot {
        if (challenge == null || !validation.isAccepted || validation.isInitialReading || validation.weeklyConsumptionKwh == null) {
            return ChallengeProgressSnapshot(
                updatedChallenge = challenge,
                weeklyConsumption = null,
                savingsProjection = null,
                progressMessage = validation.warningMessage ?: "Leitura inicial salva com sucesso.",
            )
        }

        val challengeStart = parseDate(challenge.startDate) ?: currentReadingDate
        val challengeEnd = parseDate(challenge.endDate) ?: currentReadingDate
        val challengeDays = max(1L, ChronoUnit.DAYS.between(challengeStart, challengeEnd) + 1).toInt()
        val periodDays = validation.daysBetweenReadings ?: 7
        val baselineConsumption = challenge.baselineConsumptionKwh
        val baselineDaily = baselineConsumption?.div(challengeDays)
        val expectedForPeriod = baselineDaily?.times(periodDays)
        val targetKwh = challenge.targetKwh
            ?: if (referenceTariffBrlPerKwh != null && challenge.targetBrl != null && referenceTariffBrlPerKwh > 0) {
                challenge.targetBrl / referenceTariffBrlPerKwh
            } else {
                null
            }
        val savedThisPeriod = if (expectedForPeriod != null) {
            expectedForPeriod - validation.weeklyConsumptionKwh
        } else {
            null
        }
        val cumulativeSaved = ((challenge.currentSavedKwh ?: 0.0) + (savedThisPeriod ?: 0.0)).coerceAtLeast(0.0)
        val cumulativeSavedBrl = energySavingsCalculator.estimateCost(cumulativeSaved, referenceTariffBrlPerKwh)
        val progressPercent = when {
            targetKwh != null && targetKwh > 0 -> ((cumulativeSaved / targetKwh) * 100.0).coerceIn(0.0, 100.0)
            challenge.targetBrl != null && challenge.targetBrl > 0 && cumulativeSavedBrl != null ->
                ((cumulativeSavedBrl / challenge.targetBrl) * 100.0).coerceIn(0.0, 100.0)
            else -> 0.0
        }

        val updatedChallenge = challenge.copy(
            progressPercent = progressPercent,
            currentSavedKwh = cumulativeSaved,
            currentSavedBrl = cumulativeSavedBrl,
            status = when {
                progressPercent >= 100.0 -> EnergyChallengeStatus.COMPLETED
                currentReadingDate.isAfter(challengeEnd) -> EnergyChallengeStatus.FAILED
                else -> EnergyChallengeStatus.ACTIVE
            },
            updatedAt = Instant.now().toString(),
        )

        val weeklyConsumption = WeeklyConsumption(
            startDate = validation.previousReading?.readingDate ?: currentReadingDate.toString(),
            endDate = currentReadingDate.toString(),
            consumptionKwh = validation.weeklyConsumptionKwh,
            averageDailyKwh = validation.weeklyConsumptionKwh / periodDays,
            estimatedCostBrl = energySavingsCalculator.estimateCost(validation.weeklyConsumptionKwh, referenceTariffBrlPerKwh),
            vsExpectedPercent = percentChange(validation.weeklyConsumptionKwh, expectedForPeriod),
            vsPreviousWeekPercent = percentChange(validation.weeklyConsumptionKwh, previousPeriodConsumptionKwh),
            isSuspicious = validation.isSuspicious,
        )

        val projection = when {
            expectedForPeriod != null && baselineConsumption != null && referenceTariffBrlPerKwh != null -> {
                val actualDaily = validation.weeklyConsumptionKwh / periodDays
                val targetDailySaving = (baselineDaily ?: 0.0) - actualDaily
                if (targetDailySaving > 0) {
                    energySavingsCalculator.buildReductionProjection(
                        baseConsumptionKwh = baselineConsumption,
                        tariffBrlPerKwh = referenceTariffBrlPerKwh,
                        dailyReductionKwh = targetDailySaving,
                        periodDays = challengeDays,
                    )
                } else {
                    null
                }
            }
            else -> null
        }

        val progressMessage = when {
            updatedChallenge.status == EnergyChallengeStatus.COMPLETED -> "Boa! Voce concluiu o desafio e ja consolidou economia real no periodo."
            savedThisPeriod != null && savedThisPeriod > 0 -> "Boa! Esta leitura ficou abaixo da referencia esperada e avancou o desafio."
            savedThisPeriod != null && savedThisPeriod < 0 -> "Nesta leitura o consumo passou do alvo. Ainda da tempo de recuperar o ritmo nas proximas semanas."
            else -> "Leitura salva. O app vai continuar acompanhando sua meta."
        }

        return ChallengeProgressSnapshot(
            updatedChallenge = updatedChallenge,
            weeklyConsumption = weeklyConsumption,
            savingsProjection = projection,
            progressMessage = progressMessage,
        )
    }
}

class UserScoreManager @Inject constructor() {

    fun resolveStreak(previousScore: UserScore?, readingDate: LocalDate): Int {
        val lastReadingDate = previousScore?.lastWeeklyReadingDate?.let(::parseDate) ?: return 1
        val gap = ChronoUnit.DAYS.between(lastReadingDate, readingDate).toInt()
        return when {
            gap <= 0 -> previousScore.weeklyStreak
            gap in 5..10 -> previousScore.weeklyStreak + 1
            gap in 1..4 -> previousScore.weeklyStreak
            else -> 1
        }
    }

    fun applyXp(previousScore: UserScore?, gainedXp: Int, readingDate: LocalDate): UserScore {
        val totalXp = (previousScore?.totalXp ?: 0) + gainedXp
        val streak = resolveStreak(previousScore, readingDate)
        val levelInfo = resolveLevel(totalXp)
        return UserScore(
            userId = previousScore?.userId.orEmpty(),
            totalXp = totalXp,
            currentLevel = levelInfo.level,
            levelName = levelInfo.name,
            xpToNextLevel = levelInfo.xpToNextLevel,
            weeklyStreak = streak,
            lastWeeklyReadingDate = readingDate.toString(),
            updatedAt = Instant.now().toString(),
        )
    }

    fun initialScore(userId: String): UserScore {
        return UserScore(
            userId = userId,
            totalXp = 0,
            currentLevel = 1,
            levelName = "Iniciante",
            xpToNextLevel = 100,
            weeklyStreak = 0,
            lastWeeklyReadingDate = null,
            updatedAt = Instant.now().toString(),
        )
    }

    private fun resolveLevel(totalXp: Int): LevelInfo {
        return when {
            totalXp >= 900 -> LevelInfo(level = 5, name = "Lenda Sustentável", xpToNextLevel = 0)
            totalXp >= 500 -> LevelInfo(level = 4, name = "Mestre da Economia", xpToNextLevel = 900 - totalXp)
            totalXp >= 250 -> LevelInfo(level = 3, name = "Guardião da Energia", xpToNextLevel = 500 - totalXp)
            totalXp >= 100 -> LevelInfo(level = 2, name = "Economizador", xpToNextLevel = 250 - totalXp)
            else -> LevelInfo(level = 1, name = "Iniciante", xpToNextLevel = 100 - totalXp)
        }
    }

    private data class LevelInfo(
        val level: Int,
        val name: String,
        val xpToNextLevel: Int,
    )
}

class RewardSystem @Inject constructor() {

    fun calculateXp(
        challengeBefore: EnergyChallenge?,
        challengeAfter: EnergyChallenge?,
        previousStreak: Int,
        newStreak: Int,
    ): Int {
        val readingXp = 10
        val challengeXp = when {
            challengeBefore?.status != EnergyChallengeStatus.COMPLETED &&
                challengeAfter?.status == EnergyChallengeStatus.COMPLETED &&
                challengeDurationDays(challengeAfter) > 7 -> 150
            challengeBefore?.status != EnergyChallengeStatus.COMPLETED &&
                challengeAfter?.status == EnergyChallengeStatus.COMPLETED -> 50
            else -> 0
        }
        val streakXp = if (previousStreak < 4 && newStreak >= 4) 100 else 0
        return readingXp + challengeXp + streakXp
    }

    fun resolveAchievements(
        userId: String,
        existingAchievements: List<Achievement>,
        readingWasInitial: Boolean,
        currentScore: UserScore,
        updatedChallenge: EnergyChallenge?,
        weeklyConsumption: WeeklyConsumption?,
    ): List<Achievement> {
        val existingTypes = existingAchievements.filter { it.isUnlocked }.map { it.type }.toSet()
        val now = Instant.now().toString()
        val unlocked = mutableListOf<Achievement>()

        fun unlock(type: AchievementType, title: String, description: String) {
            if (type !in existingTypes) {
                unlocked += Achievement(
                    id = "${userId}_${type.name}",
                    userId = userId,
                    type = type,
                    title = title,
                    description = description,
                    unlockedAt = now,
                    isUnlocked = true,
                )
            }
        }

        if (readingWasInitial || existingAchievements.none { it.type == AchievementType.FIRST_READING && it.isUnlocked }) {
            unlock(
                type = AchievementType.FIRST_READING,
                title = "Primeira leitura",
                description = "Você registrou sua primeira leitura do medidor e começou a acompanhar o consumo semanal.",
            )
        }
        if ((updatedChallenge?.currentSavedKwh ?: 0.0) > 0.0 || (weeklyConsumption?.vsExpectedPercent ?: 0.0) < 0) {
            unlock(
                type = AchievementType.FIRST_SAVINGS,
                title = "Primeira economia",
                description = "Voce ja conseguiu ficar abaixo da referencia e comecou a gerar economia real.",
            )
        }
        if (currentScore.weeklyStreak >= 4) {
            unlock(
                type = AchievementType.FOUR_WEEKS_STREAK,
                title = "4 semanas seguidas",
                description = "Voce manteve uma sequencia de quatro semanas acompanhando o medidor.",
            )
        }
        if ((updatedChallenge?.currentSavedKwh ?: 0.0) >= ((updatedChallenge?.baselineConsumptionKwh ?: 0.0) * 0.05)) {
            unlock(
                type = AchievementType.REDUCED_FIVE_PERCENT,
                title = "Reduziu 5%",
                description = "Seu progresso ja representa uma reducao relevante em relacao ao padrao de referencia.",
            )
        }
        if ((updatedChallenge?.currentSavedBrl ?: 0.0) >= 50.0) {
            unlock(
                type = AchievementType.SAVED_FIFTY_BRL,
                title = "Economizou R$ 50",
                description = "Sua economia acumulada já ultrapassou R$ 50 no desafio atual.",
            )
        }
        if (updatedChallenge?.status == EnergyChallengeStatus.COMPLETED && challengeDurationDays(updatedChallenge) > 7) {
            unlock(
                type = AchievementType.MONTHLY_GOAL_COMPLETED,
                title = "Meta mensal concluida",
                description = "Voce concluiu um desafio mensal de economia.",
            )
        }

        return unlocked
    }

    fun buildMotivationalMessage(
        challengeSnapshot: ChallengeProgressSnapshot,
        updatedScore: UserScore,
        unlockedAchievements: List<Achievement>,
    ): String {
        return when {
            unlockedAchievements.isNotEmpty() -> "Boa! Voce desbloqueou ${unlockedAchievements.first().title.lowercase()} e chegou ao nivel ${updatedScore.currentLevel}."
            challengeSnapshot.updatedChallenge?.status == EnergyChallengeStatus.COMPLETED -> "Excelente! Seu desafio foi concluido e a economia ja esta consolidada."
            challengeSnapshot.weeklyConsumption?.vsExpectedPercent != null &&
                challengeSnapshot.weeklyConsumption.vsExpectedPercent < 0 -> {
                "Boa! Voce registrou sua leitura semanal e ficou abaixo da referencia esperada."
            }
            else -> challengeSnapshot.progressMessage
        }
    }
}

class GameEngine @Inject constructor(
    private val gameRepository: GameRepository,
    private val meterReadingRepository: MeterReadingRepository,
    private val challengeGenerator: ChallengeGenerator,
    private val progressTracker: ProgressTracker,
    private val rewardSystem: RewardSystem,
    private val userScoreManager: UserScoreManager,
    private val energySavingsCalculator: EnergySavingsCalculator,
    private val meterReadingValidator: MeterReadingValidator,
) {

    suspend fun ensureSuggestedChallenge(
        userId: String,
        history: List<BillSummary>,
        forecast: BillForecast?,
        today: LocalDate = LocalDate.now(),
        manualTariffBrlPerKwh: Double? = null,
    ): EnergyChallenge {
        val activeChallenge = gameRepository.observeActiveChallenge(userId).first()
        if (activeChallenge != null && canKeepChallengeActive(activeChallenge, today)) {
            return activeChallenge
        }

        val suggestedChallenge = gameRepository.observeSuggestedChallenge(userId).first()
        if (suggestedChallenge != null && canKeepChallengeSuggested(suggestedChallenge, today)) {
            return suggestedChallenge
        }

        val dismissedChallenge = gameRepository.observeChallenges(userId).first()
            .firstOrNull { it.status == EnergyChallengeStatus.CANCELED && canKeepDismissedChallenge(it, today) }
        if (dismissedChallenge != null) {
            return dismissedChallenge
        }

        gameRepository.clearActiveChallenges(userId)
        gameRepository.clearSuggestedChallenges(userId)
        val challenge = challengeGenerator.generate(
            userId = userId,
            history = history,
            forecast = forecast,
            today = today,
            manualTariffBrlPerKwh = manualTariffBrlPerKwh,
        )
        gameRepository.upsertChallenge(challenge)
        return challenge
    }

    suspend fun acceptSuggestedChallenge(
        userId: String,
        challengeId: String,
        today: LocalDate = LocalDate.now(),
    ): EnergyChallenge? {
        val activeChallenge = gameRepository.observeActiveChallenge(userId).first()
        if (activeChallenge != null && canKeepChallengeActive(activeChallenge, today)) {
            return activeChallenge
        }

        val challenge = gameRepository.getChallengeById(challengeId) ?: return null
        if (challenge.userId != userId) return null

        if (challenge.status == EnergyChallengeStatus.SUGGESTED) {
            gameRepository.clearActiveChallenges(userId)
            gameRepository.acceptSuggestedChallenge(challengeId)
        }

        return gameRepository.getChallengeById(challengeId)
    }

    suspend fun declineSuggestedChallenge(
        userId: String,
        challengeId: String,
    ) {
        val challenge = gameRepository.getChallengeById(challengeId) ?: return
        if (challenge.userId != userId) return
        if (challenge.status == EnergyChallengeStatus.SUGGESTED) {
            gameRepository.updateChallengeStatus(
                challengeId = challengeId,
                status = EnergyChallengeStatus.CANCELED,
            )
        }
    }

    suspend fun ensureActiveChallenge(
        userId: String,
        history: List<BillSummary>,
        forecast: BillForecast?,
        today: LocalDate = LocalDate.now(),
        manualTariffBrlPerKwh: Double? = null,
    ): EnergyChallenge {
        val activeChallenge = gameRepository.observeActiveChallenge(userId).first()
        if (activeChallenge != null && canKeepChallengeActive(activeChallenge, today)) {
            return activeChallenge
        }

        val suggestedChallenge = ensureSuggestedChallenge(
            userId = userId,
            history = history,
            forecast = forecast,
            today = today,
            manualTariffBrlPerKwh = manualTariffBrlPerKwh,
        )

        return when (suggestedChallenge.status) {
            EnergyChallengeStatus.ACTIVE -> suggestedChallenge
            EnergyChallengeStatus.SUGGESTED -> {
                acceptSuggestedChallenge(
                    userId = userId,
                    challengeId = suggestedChallenge.id,
                    today = today,
                ) ?: suggestedChallenge.copy(status = EnergyChallengeStatus.ACTIVE)
            }
            else -> suggestedChallenge
        }
    }

    suspend fun processConfirmedReading(
        userId: String,
        readingDate: LocalDate,
        confirmedValue: Long,
        imageUri: String?,
        extractedOcrValue: Long?,
        confidenceScore: Double?,
        observation: String?,
        history: List<BillSummary>,
        forecast: BillForecast?,
        manualTariffBrlPerKwh: Double? = null,
    ): AppResult<GameEngineResult> {
        val recentReadings = meterReadingRepository.getLatestReadings(userId, limit = 2)
        val previousReading = recentReadings.firstOrNull()
        val currentChallenge = ensureActiveChallenge(
            userId = userId,
            history = history,
            forecast = forecast,
            today = readingDate,
            manualTariffBrlPerKwh = manualTariffBrlPerKwh,
        )
        val challengeDays = challengeDurationDays(currentChallenge)
        val expectedWeeklyConsumption = currentChallenge.baselineConsumptionKwh?.div(challengeDays)?.times(7)
        val validation = meterReadingValidator.validate(
            readingDate = readingDate,
            confirmedValue = confirmedValue,
            previousReading = previousReading,
            expectedConsumptionKwh = expectedWeeklyConsumption,
        )
        if (!validation.isAccepted) {
            return AppResult.Error(
                AppError(
                    code = "invalid_meter_reading",
                    message = validation.blockingMessage ?: "Nao foi possivel validar a leitura confirmada.",
                ),
            )
        }

        val referenceTariff = energySavingsCalculator.resolveReferenceTariff(
            history = history,
            forecast = forecast,
            manualTariffBrlPerKwh = manualTariffBrlPerKwh,
        )
        val newReading = MeterReading(
            id = UUID.randomUUID().toString(),
            userId = userId,
            readingDate = readingDate.toString(),
            meterValueKwh = confirmedValue,
            imageUri = imageUri,
            extractedOcrValue = extractedOcrValue,
            confirmedValue = confirmedValue,
            confidenceScore = confidenceScore,
            observation = observation,
            isInitialReading = validation.isInitialReading,
            isSuspicious = validation.isSuspicious,
            createdAt = Instant.now().toString(),
        )
        meterReadingRepository.saveReading(newReading)

        val previousPeriodConsumption = if (recentReadings.size >= 2) {
            val latest = recentReadings[0]
            val beforeLatest = recentReadings[1]
            (latest.confirmedValue - beforeLatest.confirmedValue).toDouble().takeIf { it >= 0 }
        } else {
            null
        }

        val challengeSnapshot = progressTracker.updateChallengeProgress(
            challenge = currentChallenge,
            validation = validation,
            currentReadingDate = readingDate,
            referenceTariffBrlPerKwh = referenceTariff,
            previousPeriodConsumptionKwh = previousPeriodConsumption,
        )
        challengeSnapshot.updatedChallenge?.let { gameRepository.upsertChallenge(it) }

        val scoreBefore = gameRepository.getUserScore(userId) ?: userScoreManager.initialScore(userId)
        val newStreak = userScoreManager.resolveStreak(scoreBefore, readingDate)
        val gainedXp = rewardSystem.calculateXp(
            challengeBefore = currentChallenge,
            challengeAfter = challengeSnapshot.updatedChallenge,
            previousStreak = scoreBefore.weeklyStreak,
            newStreak = newStreak,
        )
        val updatedScore = userScoreManager.applyXp(
            previousScore = scoreBefore,
            gainedXp = gainedXp,
            readingDate = readingDate,
        )
        gameRepository.upsertUserScore(updatedScore)

        val existingAchievements = gameRepository.observeAchievements(userId).first()
        val unlockedAchievements = rewardSystem.resolveAchievements(
            userId = userId,
            existingAchievements = existingAchievements,
            readingWasInitial = validation.isInitialReading,
            currentScore = updatedScore,
            updatedChallenge = challengeSnapshot.updatedChallenge,
            weeklyConsumption = challengeSnapshot.weeklyConsumption,
        )
        unlockedAchievements.forEach { gameRepository.upsertAchievement(it) }

        val message = rewardSystem.buildMotivationalMessage(
            challengeSnapshot = challengeSnapshot,
            updatedScore = updatedScore,
            unlockedAchievements = unlockedAchievements,
        )

        return AppResult.Success(
            GameEngineResult(
                savedReading = newReading,
                activeChallenge = challengeSnapshot.updatedChallenge,
                weeklyConsumption = challengeSnapshot.weeklyConsumption,
                userScore = updatedScore,
                unlockedAchievements = unlockedAchievements,
                gainedXp = gainedXp,
                savingsProjection = challengeSnapshot.savingsProjection,
                message = message,
            ),
        )
    }
}

class EnsureSuggestedChallengeUseCase @Inject constructor(
    private val gameEngine: GameEngine,
) {
    suspend operator fun invoke(
        userId: String,
        history: List<BillSummary>,
        forecast: BillForecast?,
        today: LocalDate = LocalDate.now(),
        manualTariffBrlPerKwh: Double? = null,
    ): EnergyChallenge {
        return gameEngine.ensureSuggestedChallenge(
            userId = userId,
            history = history,
            forecast = forecast,
            today = today,
            manualTariffBrlPerKwh = manualTariffBrlPerKwh,
        )
    }
}

class EnsureActiveChallengeUseCase @Inject constructor(
    private val gameEngine: GameEngine,
) {
    suspend operator fun invoke(
        userId: String,
        history: List<BillSummary>,
        forecast: BillForecast?,
        today: LocalDate = LocalDate.now(),
        manualTariffBrlPerKwh: Double? = null,
    ): EnergyChallenge {
        return gameEngine.ensureActiveChallenge(
            userId = userId,
            history = history,
            forecast = forecast,
            today = today,
            manualTariffBrlPerKwh = manualTariffBrlPerKwh,
        )
    }
}

class ProcessMeterReadingUseCase @Inject constructor(
    private val gameEngine: GameEngine,
) {
    suspend operator fun invoke(
        userId: String,
        readingDate: LocalDate,
        confirmedValue: Long,
        imageUri: String?,
        extractedOcrValue: Long?,
        confidenceScore: Double?,
        observation: String?,
        history: List<BillSummary>,
        forecast: BillForecast?,
        manualTariffBrlPerKwh: Double? = null,
    ): AppResult<GameEngineResult> {
        return gameEngine.processConfirmedReading(
            userId = userId,
            readingDate = readingDate,
            confirmedValue = confirmedValue,
            imageUri = imageUri,
            extractedOcrValue = extractedOcrValue,
            confidenceScore = confidenceScore,
            observation = observation,
            history = history,
            forecast = forecast,
            manualTariffBrlPerKwh = manualTariffBrlPerKwh,
        )
    }
}

class BuildSavingsSimulationsUseCase @Inject constructor(
    private val energySavingsCalculator: EnergySavingsCalculator,
) {
    operator fun invoke(
        baseConsumptionKwh: Double?,
        referenceTariffBrlPerKwh: Double?,
        periodDays: Int = 30,
    ): List<SavingsProjection> {
        return listOfNotNull(
            energySavingsCalculator.buildReductionProjection(
                baseConsumptionKwh = baseConsumptionKwh,
                tariffBrlPerKwh = referenceTariffBrlPerKwh,
                dailyReductionKwh = 1.0,
                periodDays = periodDays,
            ),
            energySavingsCalculator.buildReductionProjection(
                baseConsumptionKwh = baseConsumptionKwh,
                tariffBrlPerKwh = referenceTariffBrlPerKwh,
                monthlyReductionPercent = 5.0,
                periodDays = periodDays,
            ),
            energySavingsCalculator.buildReductionProjection(
                baseConsumptionKwh = baseConsumptionKwh,
                tariffBrlPerKwh = referenceTariffBrlPerKwh,
                monthlyReductionPercent = 10.0,
                periodDays = periodDays,
            ),
        )
    }
}

class ResolveReferenceTariffUseCase @Inject constructor(
    private val energySavingsCalculator: EnergySavingsCalculator,
) {
    operator fun invoke(
        history: List<BillSummary>,
        forecast: BillForecast?,
        manualTariffBrlPerKwh: Double? = null,
    ): Double? {
        return energySavingsCalculator.resolveReferenceTariff(
            history = history,
            forecast = forecast,
            manualTariffBrlPerKwh = manualTariffBrlPerKwh,
        )
    }
}

class ObserveMeterReadingsUseCase @Inject constructor(
    private val meterReadingRepository: MeterReadingRepository,
) {
    operator fun invoke(userId: String): Flow<List<MeterReading>> {
        return meterReadingRepository.observeReadings(userId)
    }
}

class ObserveGameProgressUseCase @Inject constructor(
    private val gameRepository: GameRepository,
    private val meterReadingRepository: MeterReadingRepository,
) {
    operator fun invoke(userId: String): Flow<GameProgress> {
        return combine(
            gameRepository.observeSuggestedChallenge(userId),
            gameRepository.observeActiveChallenge(userId),
            meterReadingRepository.observeReadings(userId),
            gameRepository.observeUserScore(userId),
            gameRepository.observeAchievements(userId),
        ) { suggestedChallenge, activeChallenge, readings, userScore, achievements ->
            val latestReading = readings.firstOrNull()
            val pendingWeeklyReading = latestReading?.readingDate?.let(::parseDate)?.let { latestDate ->
                ChronoUnit.DAYS.between(latestDate, LocalDate.now()) >= 7
            } ?: true

            val motivationalMessage = when {
                activeChallenge?.status == EnergyChallengeStatus.COMPLETED -> "Desafio concluido. Hora de aceitar a proxima meta."
                suggestedChallenge != null -> "Encontramos uma nova oportunidade de economia para voce. Vale conferir a sugestao antes de comecar."
                pendingWeeklyReading -> "Sua leitura semanal esta pendente. Registrar o medidor ajuda a manter o desafio atualizado."
                activeChallenge != null -> "Seu desafio atual ja esta em andamento. Continue acompanhando para manter o ritmo."
                else -> "Vamos comecar definindo sua proxima meta de economia."
            }

            GameProgress(
                suggestedChallenge = suggestedChallenge,
                activeChallenge = activeChallenge,
                latestWeeklyConsumption = buildLatestWeeklyConsumption(readings),
                userScore = userScore,
                achievements = achievements,
                pendingWeeklyReading = pendingWeeklyReading,
                motivationalMessage = motivationalMessage,
            )
        }
    }
}

private fun buildLatestWeeklyConsumption(readings: List<MeterReading>): WeeklyConsumption? {
    if (readings.size < 2) return null
    val latest = readings[0]
    val previous = readings[1]
    val latestDate = parseDate(latest.readingDate) ?: return null
    val previousDate = parseDate(previous.readingDate) ?: return null
    val days = max(1L, ChronoUnit.DAYS.between(previousDate, latestDate)).toInt()
    val consumption = (latest.confirmedValue - previous.confirmedValue).toDouble()
    if (consumption < 0) return null
    return WeeklyConsumption(
        startDate = previous.readingDate,
        endDate = latest.readingDate,
        consumptionKwh = consumption,
        averageDailyKwh = consumption / days,
        estimatedCostBrl = null,
        vsExpectedPercent = null,
        vsPreviousWeekPercent = null,
        isSuspicious = latest.isSuspicious,
    )
}

private fun canKeepChallengeActive(challenge: EnergyChallenge, today: LocalDate): Boolean {
    val endDate = parseDate(challenge.endDate) ?: return false
    return challenge.status == EnergyChallengeStatus.ACTIVE && !today.isAfter(endDate)
}

private fun canKeepChallengeSuggested(challenge: EnergyChallenge, today: LocalDate): Boolean {
    val endDate = parseDate(challenge.endDate) ?: return false
    return challenge.status == EnergyChallengeStatus.SUGGESTED && !today.isAfter(endDate)
}

private fun canKeepDismissedChallenge(challenge: EnergyChallenge, today: LocalDate): Boolean {
    val endDate = parseDate(challenge.endDate) ?: return false
    return challenge.status == EnergyChallengeStatus.CANCELED && !today.isAfter(endDate)
}

private fun challengeDurationDays(challenge: EnergyChallenge?): Int {
    if (challenge == null) return 0
    val start = parseDate(challenge.startDate) ?: return 0
    val end = parseDate(challenge.endDate) ?: return 0
    return max(1L, ChronoUnit.DAYS.between(start, end) + 1).toInt()
}

private fun parseDate(rawValue: String): LocalDate? {
    return runCatching { LocalDate.parse(rawValue.take(10)) }.getOrNull()
}

private fun parseMonthKey(rawValue: String?): YearMonth? {
    if (rawValue.isNullOrBlank()) return null
    return runCatching { YearMonth.parse(rawValue) }.getOrNull()
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

private fun percentChange(current: Double?, previous: Double?): Double? {
    if (current == null || previous == null || previous == 0.0) return null
    return ((current - previous) / previous) * 100.0
}

private fun LocalDate.toPtBr(): String = "${dayOfMonth.toString().padStart(2, '0')}/${monthValue.toString().padStart(2, '0')}"
