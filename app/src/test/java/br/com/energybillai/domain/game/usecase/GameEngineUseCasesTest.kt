package br.com.energybillai.domain.game.usecase

import com.google.common.truth.Truth.assertThat
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.domain.game.model.Achievement
import br.com.energybillai.domain.game.model.AchievementType
import br.com.energybillai.domain.game.model.EnergyChallenge
import br.com.energybillai.domain.game.model.EnergyChallengeStatus
import br.com.energybillai.domain.game.model.EnergyChallengeType
import br.com.energybillai.domain.game.model.MeterReading
import br.com.energybillai.domain.game.model.MeterReadingValidation
import br.com.energybillai.domain.game.model.UserScore
import br.com.energybillai.domain.game.repository.GameRepository
import br.com.energybillai.domain.game.repository.MeterReadingRepository
import br.com.energybillai.domain.model.BillExtractionStatus
import br.com.energybillai.domain.model.BillForecast
import br.com.energybillai.domain.model.BillSummary
import br.com.energybillai.domain.model.ForecastPoint
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GameEngineUseCasesTest {

    private val calculator = EnergySavingsCalculator()

    @Test
    fun `calculator resolves tariff from history when forecast is absent`() {
        val history = listOf(
            billSummary(month = "2026-03", consumption = 200.0, total = 180.0),
            billSummary(month = "2026-04", consumption = 250.0, total = 225.0),
        )

        val result = calculator.resolveReferenceTariff(
            history = history,
            forecast = null,
        )

        assertThat(result).isEqualTo(0.9)
    }

    @Test
    fun `calculator builds monthly savings projection for daily reduction`() {
        val projection = calculator.buildReductionProjection(
            baseConsumptionKwh = 300.0,
            tariffBrlPerKwh = 0.9,
            dailyReductionKwh = 1.0,
            periodDays = 30,
        )

        assertThat(projection).isNotNull()
        assertThat(projection?.estimatedMonthlySavingsKwh).isEqualTo(30.0)
        assertThat(projection?.estimatedMonthlySavingsBrl).isEqualTo(27.0)
        assertThat(projection?.estimatedClosingValueBrl).isEqualTo(243.0)
    }

    @Test
    fun `validator blocks reading lower than previous`() {
        val validator = MeterReadingValidator()
        val previousReading = MeterReading(
            id = "r1",
            userId = "user",
            readingDate = "2026-04-01",
            meterValueKwh = 1500,
            imageUri = null,
            extractedOcrValue = 1500,
            confirmedValue = 1500,
            confidenceScore = 0.91,
            observation = null,
            isInitialReading = true,
            isSuspicious = false,
            createdAt = "2026-04-01T10:00:00Z",
        )

        val result = validator.validate(
            readingDate = LocalDate.parse("2026-04-08"),
            confirmedValue = 1490,
            previousReading = previousReading,
        )

        assertThat(result.isAccepted).isFalse()
        assertThat(result.blockingMessage).contains("menor que a anterior")
    }

    @Test
    fun `validator accepts first reading as initial reading`() {
        val validator = MeterReadingValidator()

        val result = validator.validate(
            readingDate = LocalDate.parse("2026-04-08"),
            confirmedValue = 18234,
            previousReading = null,
        )

        assertThat(result.isAccepted).isTrue()
        assertThat(result.isInitialReading).isTrue()
        assertThat(result.warningMessage).contains("leitura inicial")
    }

    @Test
    fun `validator marks suspicious reading when consumption is far above expected`() {
        val validator = MeterReadingValidator()
        val previousReading = MeterReading(
            id = "r1",
            userId = "user",
            readingDate = "2026-04-01",
            meterValueKwh = 1000,
            imageUri = null,
            extractedOcrValue = 1000,
            confirmedValue = 1000,
            confidenceScore = 0.85,
            observation = null,
            isInitialReading = true,
            isSuspicious = false,
            createdAt = "2026-04-01T10:00:00Z",
        )

        val result = validator.validate(
            readingDate = LocalDate.parse("2026-04-08"),
            confirmedValue = 1300,
            previousReading = previousReading,
            expectedConsumptionKwh = 80.0,
        )

        assertThat(result.isAccepted).isTrue()
        assertThat(result.isSuspicious).isTrue()
        assertThat(result.warningMessage).contains("acima do padrao")
    }

    @Test
    fun `challenge generator creates forecast challenge when forecast is above current pattern`() {
        val generator = ChallengeGenerator(calculator)
        val history = listOf(
            billSummary(month = "2026-02", consumption = 245.0, total = 215.0),
            billSummary(month = "2026-03", consumption = 248.0, total = 219.0),
            billSummary(month = "2026-04", consumption = 250.0, total = 228.0),
        )
        val forecast = BillForecast(
            billId = "bill-1",
            referenceMonth = "2026-04",
            modelUsed = "prophet",
            horizonMonths = 8,
            historyPointsUsed = 10,
            explanation = "Forecast test",
            referenceTariffBrlPerKwh = 0.91,
            generatedForecasts = listOf(
                ForecastPoint(
                    id = "f1",
                    billId = "bill-1",
                    mesReferencia = "2026-05",
                    predictedKwh = 320.0,
                    lowerBoundKwh = 300.0,
                    upperBoundKwh = 340.0,
                    estimatedValueBrl = 291.2,
                    lowerBoundValueBrl = 273.0,
                    upperBoundValueBrl = 309.4,
                    modelUsed = "prophet",
                    createdAt = "2026-04-01T00:00:00Z",
                ),
            ),
            insights = emptyList(),
        )

        val challenge = generator.generate(
            userId = "user",
            history = history,
            forecast = forecast,
            today = LocalDate.parse("2026-04-24"),
        )

        assertThat(challenge.status).isEqualTo(EnergyChallengeStatus.SUGGESTED)
        assertThat(challenge.type).isEqualTo(EnergyChallengeType.STAY_BELOW_FORECAST)
        assertThat(challenge.rewardXp).isEqualTo(150)
        assertThat(challenge.targetKwh).isGreaterThan(0.0)
    }

    @Test
    fun `progress tracker completes challenge when savings beat target`() {
        val tracker = ProgressTracker(calculator)
        val challenge = EnergyChallenge(
            id = "challenge",
            userId = "user",
            title = "Reduza 1 kWh por dia",
            description = "teste",
            type = EnergyChallengeType.REDUCE_DAILY_KWH,
            targetKwh = 7.0,
            targetBrl = 6.3,
            startDate = "2026-04-01",
            endDate = "2026-04-07",
            status = EnergyChallengeStatus.ACTIVE,
            progressPercent = 0.0,
            rewardXp = 50,
            baselineConsumptionKwh = 70.0,
            baselineValueBrl = 63.0,
            currentSavedKwh = 0.0,
            currentSavedBrl = 0.0,
            createdAt = "2026-04-01T00:00:00Z",
            updatedAt = "2026-04-01T00:00:00Z",
        )
        val validation = MeterReadingValidation(
            isAccepted = true,
            isInitialReading = false,
            isSuspicious = false,
            previousReading = MeterReading(
                id = "prev",
                userId = "user",
                readingDate = "2026-04-01",
                meterValueKwh = 1000,
                imageUri = null,
                extractedOcrValue = null,
                confirmedValue = 1000,
                confidenceScore = null,
                observation = null,
                isInitialReading = true,
                isSuspicious = false,
                createdAt = "2026-04-01T00:00:00Z",
            ),
            weeklyConsumptionKwh = 60.0,
            daysBetweenReadings = 7,
        )

        val snapshot = tracker.updateChallengeProgress(
            challenge = challenge,
            validation = validation,
            currentReadingDate = LocalDate.parse("2026-04-08"),
            referenceTariffBrlPerKwh = 0.90,
            previousPeriodConsumptionKwh = 68.0,
        )

        assertThat(snapshot.updatedChallenge?.status).isEqualTo(EnergyChallengeStatus.COMPLETED)
        assertThat(snapshot.updatedChallenge?.progressPercent).isEqualTo(100.0)
        assertThat(snapshot.weeklyConsumption?.consumptionKwh).isEqualTo(60.0)
    }

    @Test
    fun `progress tracker keeps initial reading without progress snapshot`() {
        val tracker = ProgressTracker(calculator)
        val challenge = challenge(
            targetKwh = 7.0,
            baselineConsumptionKwh = 70.0,
        )
        val validation = MeterReadingValidation(
            isAccepted = true,
            isInitialReading = true,
            isSuspicious = false,
            warningMessage = "Leitura inicial salva com sucesso.",
        )

        val snapshot = tracker.updateChallengeProgress(
            challenge = challenge,
            validation = validation,
            currentReadingDate = LocalDate.parse("2026-04-08"),
            referenceTariffBrlPerKwh = 0.9,
        )

        assertThat(snapshot.updatedChallenge?.progressPercent).isEqualTo(0.0)
        assertThat(snapshot.weeklyConsumption).isNull()
        assertThat(snapshot.progressMessage).contains("Leitura inicial")
    }

    @Test
    fun `user score manager updates streak and level`() {
        val manager = UserScoreManager()
        val previous = UserScore(
            userId = "user",
            totalXp = 95,
            currentLevel = 1,
            levelName = "Iniciante",
            xpToNextLevel = 5,
            weeklyStreak = 3,
            lastWeeklyReadingDate = "2026-04-01",
            updatedAt = "2026-04-01T00:00:00Z",
        )

        val updated = manager.applyXp(
            previousScore = previous,
            gainedXp = 20,
            readingDate = LocalDate.parse("2026-04-08"),
        )

        assertThat(updated.weeklyStreak).isEqualTo(4)
        assertThat(updated.currentLevel).isEqualTo(2)
        assertThat(updated.levelName).isEqualTo("Economizador")
        assertThat(updated.totalXp).isEqualTo(115)
    }

    @Test
    fun `reward system unlocks streak and savings achievements`() {
        val rewardSystem = RewardSystem()
        val score = UserScore(
            userId = "user",
            totalXp = 320,
            currentLevel = 3,
            levelName = "Guardião da Energia",
            xpToNextLevel = 180,
            weeklyStreak = 4,
            lastWeeklyReadingDate = "2026-04-08",
            updatedAt = "2026-04-08T00:00:00Z",
        )

        val achievements = rewardSystem.resolveAchievements(
            userId = "user",
            existingAchievements = emptyList(),
            readingWasInitial = false,
            currentScore = score,
            updatedChallenge = challenge(
                status = EnergyChallengeStatus.COMPLETED,
                targetKwh = 10.0,
                baselineConsumptionKwh = 100.0,
                currentSavedKwh = 8.0,
                currentSavedBrl = 55.0,
            ),
            weeklyConsumption = null,
        )

        assertThat(achievements.map { it.type }).contains(AchievementType.FOUR_WEEKS_STREAK)
        assertThat(achievements.map { it.type }).contains(AchievementType.SAVED_FIFTY_BRL)
        assertThat(achievements.map { it.type }).contains(AchievementType.MONTHLY_GOAL_COMPLETED)
    }

    @Test
    fun `process meter reading saves initial reading and unlocks first reading`() = runTest {
        val gameRepository = FakeGameRepository()
        val meterReadingRepository = FakeMeterReadingRepository()
        val gameEngine = GameEngine(
            gameRepository = gameRepository,
            meterReadingRepository = meterReadingRepository,
            challengeGenerator = ChallengeGenerator(calculator),
            progressTracker = ProgressTracker(calculator),
            rewardSystem = RewardSystem(),
            userScoreManager = UserScoreManager(),
            energySavingsCalculator = calculator,
            meterReadingValidator = MeterReadingValidator(),
        )
        val history = listOf(
            billSummary(month = "2026-04", consumption = 252.0, total = 228.15),
        )

        val result = gameEngine.processConfirmedReading(
            userId = "user",
            readingDate = LocalDate.parse("2026-04-24"),
            confirmedValue = 18234,
            imageUri = "file:///meter.jpg",
            extractedOcrValue = 18230,
            confidenceScore = 0.82,
            observation = null,
            history = history,
            forecast = null,
        )

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        val success = result as AppResult.Success
        assertThat(success.data.savedReading.isInitialReading).isTrue()
        assertThat(success.data.unlockedAchievements.map { it.type }).contains(AchievementType.FIRST_READING)
        assertThat(meterReadingRepository.readings.first().confirmedValue).isEqualTo(18234L)
    }

    @Test
    fun `observe game progress flags pending weekly reading when latest reading is old`() = runTest {
        val gameRepository = FakeGameRepository()
        val meterReadingRepository = FakeMeterReadingRepository()
        val observeGameProgressUseCase = ObserveGameProgressUseCase(
            gameRepository = gameRepository,
            meterReadingRepository = meterReadingRepository,
        )
        val oldDate = LocalDate.now().minusDays(10)

        meterReadingRepository.saveReading(
            MeterReading(
                id = "reading-1",
                userId = "user",
                readingDate = oldDate.toString(),
                meterValueKwh = 18000,
                imageUri = null,
                extractedOcrValue = null,
                confirmedValue = 18000,
                confidenceScore = null,
                observation = null,
                isInitialReading = true,
                isSuspicious = false,
                createdAt = "${oldDate}T00:00:00Z",
            ),
        )

        val progress = observeGameProgressUseCase("user").first()

        assertThat(progress.pendingWeeklyReading).isTrue()
        assertThat(progress.motivationalMessage).contains("pendente")
    }

    @Test
    fun `observe game progress exposes suggested challenge before acceptance`() = runTest {
        val gameRepository = FakeGameRepository()
        val meterReadingRepository = FakeMeterReadingRepository()
        val observeGameProgressUseCase = ObserveGameProgressUseCase(
            gameRepository = gameRepository,
            meterReadingRepository = meterReadingRepository,
        )

        gameRepository.upsertChallenge(
            challenge(status = EnergyChallengeStatus.SUGGESTED),
        )

        val progress = observeGameProgressUseCase("user").first()

        assertThat(progress.suggestedChallenge?.status).isEqualTo(EnergyChallengeStatus.SUGGESTED)
        assertThat(progress.activeChallenge).isNull()
        assertThat(progress.motivationalMessage).contains("oportunidade")
    }

    @Test
    fun `accept suggested challenge promotes mission to active`() = runTest {
        val gameRepository = FakeGameRepository()
        val meterReadingRepository = FakeMeterReadingRepository()
        val gameEngine = GameEngine(
            gameRepository = gameRepository,
            meterReadingRepository = meterReadingRepository,
            challengeGenerator = ChallengeGenerator(calculator),
            progressTracker = ProgressTracker(calculator),
            rewardSystem = RewardSystem(),
            userScoreManager = UserScoreManager(),
            energySavingsCalculator = calculator,
            meterReadingValidator = MeterReadingValidator(),
        )
        val acceptSuggestedChallengeUseCase = AcceptSuggestedChallengeUseCase(gameEngine)
        val suggestedChallenge = challenge(status = EnergyChallengeStatus.SUGGESTED)

        gameRepository.upsertChallenge(suggestedChallenge)

        val acceptedChallenge = acceptSuggestedChallengeUseCase(
            userId = "user",
            challengeId = suggestedChallenge.id,
            today = LocalDate.parse("2026-04-24"),
        )

        val progress = ObserveGameProgressUseCase(
            gameRepository = gameRepository,
            meterReadingRepository = meterReadingRepository,
        )("user").first()

        assertThat(acceptedChallenge?.status).isEqualTo(EnergyChallengeStatus.ACTIVE)
        assertThat(progress.suggestedChallenge).isNull()
        assertThat(progress.activeChallenge?.status).isEqualTo(EnergyChallengeStatus.ACTIVE)
    }

    @Test
    fun `decline suggested challenge hides mission from current cycle`() = runTest {
        val gameRepository = FakeGameRepository()
        val meterReadingRepository = FakeMeterReadingRepository()
        val gameEngine = GameEngine(
            gameRepository = gameRepository,
            meterReadingRepository = meterReadingRepository,
            challengeGenerator = ChallengeGenerator(calculator),
            progressTracker = ProgressTracker(calculator),
            rewardSystem = RewardSystem(),
            userScoreManager = UserScoreManager(),
            energySavingsCalculator = calculator,
            meterReadingValidator = MeterReadingValidator(),
        )
        val declineSuggestedChallengeUseCase = DeclineSuggestedChallengeUseCase(gameEngine)
        val suggestedChallenge = challenge(status = EnergyChallengeStatus.SUGGESTED)

        gameRepository.upsertChallenge(suggestedChallenge)
        declineSuggestedChallengeUseCase(
            userId = "user",
            challengeId = suggestedChallenge.id,
        )

        val progress = ObserveGameProgressUseCase(
            gameRepository = gameRepository,
            meterReadingRepository = meterReadingRepository,
        )("user").first()

        assertThat(progress.suggestedChallenge).isNull()
        assertThat(gameRepository.getChallengeById(suggestedChallenge.id)?.status)
            .isEqualTo(EnergyChallengeStatus.CANCELED)
    }

    @Test
    fun `process meter reading auto accepts suggested challenge as fallback`() = runTest {
        val gameRepository = FakeGameRepository()
        val meterReadingRepository = FakeMeterReadingRepository()
        val gameEngine = GameEngine(
            gameRepository = gameRepository,
            meterReadingRepository = meterReadingRepository,
            challengeGenerator = ChallengeGenerator(calculator),
            progressTracker = ProgressTracker(calculator),
            rewardSystem = RewardSystem(),
            userScoreManager = UserScoreManager(),
            energySavingsCalculator = calculator,
            meterReadingValidator = MeterReadingValidator(),
        )
        val history = listOf(
            billSummary(month = "2026-04", consumption = 252.0, total = 228.15),
        )

        val suggested = gameEngine.ensureSuggestedChallenge(
            userId = "user",
            history = history,
            forecast = null,
            today = LocalDate.parse("2026-04-24"),
        )

        assertThat(suggested.status).isEqualTo(EnergyChallengeStatus.SUGGESTED)

        val result = gameEngine.processConfirmedReading(
            userId = "user",
            readingDate = LocalDate.parse("2026-04-24"),
            confirmedValue = 18234,
            imageUri = "file:///meter.jpg",
            extractedOcrValue = 18230,
            confidenceScore = 0.82,
            observation = null,
            history = history,
            forecast = null,
        )

        val success = result as AppResult.Success
        assertThat(success.data.activeChallenge?.status).isEqualTo(EnergyChallengeStatus.ACTIVE)
    }

    @Test
    fun `ensure suggested challenge respects dismissed challenge during same cycle`() = runTest {
        val gameRepository = FakeGameRepository()
        val meterReadingRepository = FakeMeterReadingRepository()
        val gameEngine = GameEngine(
            gameRepository = gameRepository,
            meterReadingRepository = meterReadingRepository,
            challengeGenerator = ChallengeGenerator(calculator),
            progressTracker = ProgressTracker(calculator),
            rewardSystem = RewardSystem(),
            userScoreManager = UserScoreManager(),
            energySavingsCalculator = calculator,
            meterReadingValidator = MeterReadingValidator(),
        )
        val history = listOf(
            billSummary(month = "2026-04", consumption = 252.0, total = 228.15),
        )

        val suggested = gameEngine.ensureSuggestedChallenge(
            userId = "user",
            history = history,
            forecast = null,
            today = LocalDate.parse("2026-04-24"),
        )
        gameEngine.declineSuggestedChallenge(
            userId = "user",
            challengeId = suggested.id,
        )

        val afterDecline = gameEngine.ensureSuggestedChallenge(
            userId = "user",
            history = history,
            forecast = null,
            today = LocalDate.parse("2026-04-24"),
        )

        assertThat(afterDecline.id).isEqualTo(suggested.id)
        assertThat(afterDecline.status).isEqualTo(EnergyChallengeStatus.CANCELED)
    }

    private fun billSummary(month: String, consumption: Double, total: Double): BillSummary {
        return BillSummary(
            billId = month,
            documentId = month,
            referenceMonth = month,
            provider = "CPFL",
            consumptionKwh = consumption,
            totalValue = total,
            extractionStatus = BillExtractionStatus.CONFIRMED,
            reviewRequired = false,
        )
    }

    private fun challenge(
        status: EnergyChallengeStatus = EnergyChallengeStatus.ACTIVE,
        targetKwh: Double? = 7.0,
        baselineConsumptionKwh: Double? = 70.0,
        currentSavedKwh: Double? = 0.0,
        currentSavedBrl: Double? = 0.0,
    ): EnergyChallenge {
        return EnergyChallenge(
            id = "challenge",
            userId = "user",
            title = "Reduza 1 kWh por dia",
            description = "teste",
            type = EnergyChallengeType.REDUCE_DAILY_KWH,
            targetKwh = targetKwh,
            targetBrl = 6.3,
            startDate = "2026-04-01",
            endDate = "2026-04-30",
            status = status,
            progressPercent = 0.0,
            rewardXp = 150,
            baselineConsumptionKwh = baselineConsumptionKwh,
            baselineValueBrl = 63.0,
            currentSavedKwh = currentSavedKwh,
            currentSavedBrl = currentSavedBrl,
            createdAt = "2026-04-01T00:00:00Z",
            updatedAt = "2026-04-01T00:00:00Z",
        )
    }

    private class FakeGameRepository : GameRepository {
        private val challenges = MutableStateFlow<List<EnergyChallenge>>(emptyList())
        private val userScores = MutableStateFlow<Map<String, UserScore>>(emptyMap())
        private val achievements = MutableStateFlow<Map<String, List<Achievement>>>(emptyMap())

        override fun observeSuggestedChallenge(userId: String): Flow<EnergyChallenge?> {
            return challenges.map { items ->
                items.filter { it.userId == userId }
                    .filter { it.status == EnergyChallengeStatus.SUGGESTED }
                    .maxByOrNull { it.updatedAt }
            }
        }

        override fun observeActiveChallenge(userId: String): Flow<EnergyChallenge?> {
            return challenges.map { items ->
                items.filter { it.userId == userId }
                    .filter { it.status == EnergyChallengeStatus.ACTIVE }
                    .maxByOrNull { it.updatedAt }
            }
        }

        override fun observeChallenges(userId: String): Flow<List<EnergyChallenge>> {
            return challenges.map { items ->
                items.filter { it.userId == userId }
            }
        }

        override suspend fun getChallengeById(challengeId: String): EnergyChallenge? {
            return challenges.value.firstOrNull { it.id == challengeId }
        }

        override suspend fun upsertChallenge(challenge: EnergyChallenge) {
            challenges.value = challenges.value
                .filterNot { it.id == challenge.id } + challenge
        }

        override suspend fun acceptSuggestedChallenge(challengeId: String) {
            challenges.value = challenges.value.map { challenge ->
                if (challenge.id == challengeId && challenge.status == EnergyChallengeStatus.SUGGESTED) {
                    challenge.copy(status = EnergyChallengeStatus.ACTIVE)
                } else {
                    challenge
                }
            }
        }

        override suspend fun updateChallengeStatus(challengeId: String, status: EnergyChallengeStatus) {
            challenges.value = challenges.value.map { challenge ->
                if (challenge.id == challengeId) challenge.copy(status = status) else challenge
            }
        }

        override suspend fun clearSuggestedChallenges(userId: String) {
            challenges.value = challenges.value.map { challenge ->
                if (challenge.userId == userId && challenge.status == EnergyChallengeStatus.SUGGESTED) {
                    challenge.copy(status = EnergyChallengeStatus.CANCELED)
                } else {
                    challenge
                }
            }
        }

        override suspend fun clearActiveChallenges(userId: String) {
            challenges.value = challenges.value.map { challenge ->
                if (challenge.userId == userId && challenge.status == EnergyChallengeStatus.ACTIVE) {
                    challenge.copy(status = EnergyChallengeStatus.CANCELED)
                } else {
                    challenge
                }
            }
        }

        override fun observeUserScore(userId: String): Flow<UserScore?> {
            return userScores.map { scores -> scores[userId] }
        }

        override suspend fun getUserScore(userId: String): UserScore? = userScores.value[userId]

        override suspend fun upsertUserScore(score: UserScore) {
            userScores.value = userScores.value + (score.userId to score)
        }

        override fun observeAchievements(userId: String): Flow<List<Achievement>> {
            return achievements.map { current -> current[userId].orEmpty() }
        }

        override suspend fun getAchievement(userId: String, type: AchievementType): Achievement? {
            return achievements.value[userId].orEmpty().firstOrNull { it.type == type }
        }

        override suspend fun upsertAchievement(achievement: Achievement) {
            val current = achievements.value[achievement.userId].orEmpty()
                .filterNot { it.id == achievement.id } + achievement
            achievements.value = achievements.value + (achievement.userId to current)
        }
    }

    private class FakeMeterReadingRepository : MeterReadingRepository {
        private val readingsFlow = MutableStateFlow<List<MeterReading>>(emptyList())

        val readings: List<MeterReading>
            get() = readingsFlow.value

        override fun observeReadings(userId: String): Flow<List<MeterReading>> {
            return readingsFlow.map { readings -> readings.filter { it.userId == userId } }
        }

        override suspend fun getReadingById(readingId: String): MeterReading? {
            return readingsFlow.value.firstOrNull { it.id == readingId }
        }

        override suspend fun getLatestConfirmedReading(userId: String): MeterReading? {
            return readingsFlow.value
                .filter { it.userId == userId }
                .maxByOrNull { LocalDate.parse(it.readingDate) }
        }

        override suspend fun getLatestReadings(userId: String, limit: Int): List<MeterReading> {
            return readingsFlow.value
                .filter { it.userId == userId }
                .sortedByDescending { LocalDate.parse(it.readingDate) }
                .take(limit)
        }

        override suspend fun saveReading(reading: MeterReading) {
            readingsFlow.value = (readingsFlow.value + reading)
                .sortedByDescending { LocalDate.parse(it.readingDate) }
        }

        override suspend fun deleteReading(readingId: String) {
            readingsFlow.value = readingsFlow.value.filterNot { it.id == readingId }
        }
    }
}
