package br.com.energybillai.feature.game

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.common.UiState
import br.com.energybillai.core.designsystem.AchievementBadge
import br.com.energybillai.core.designsystem.AchievementBadgeState
import br.com.energybillai.core.designsystem.AchievementBadgeUiModel
import br.com.energybillai.core.designsystem.AppBrandLockup
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.EmptyStatePane
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.ErrorStatePane
import br.com.energybillai.core.designsystem.HeroCard
import br.com.energybillai.core.designsystem.InlineWarning
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.MetricChip
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.designsystem.SectionHeader
import br.com.energybillai.core.designsystem.SimpleLineChart
import br.com.energybillai.core.designsystem.StatusPill
import br.com.energybillai.core.designsystem.WeeklyReadingMarkerState
import br.com.energybillai.core.designsystem.WeeklyReadingMarkerUiModel
import br.com.energybillai.core.designsystem.WeeklyReadingStreakCard
import br.com.energybillai.core.ui.toCurrencyLabel
import br.com.energybillai.core.ui.toDateLabel
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.domain.game.model.Achievement
import br.com.energybillai.domain.game.model.AchievementType
import br.com.energybillai.domain.game.model.EnergyChallenge
import br.com.energybillai.domain.game.model.EnergyChallengeStatus
import br.com.energybillai.domain.game.model.GameProgress
import br.com.energybillai.domain.game.model.MeterReading
import br.com.energybillai.domain.game.model.SavingsProjection
import br.com.energybillai.domain.game.model.UserScore
import br.com.energybillai.domain.game.model.WeeklyConsumption
import br.com.energybillai.domain.game.usecase.AcceptSuggestedChallengeUseCase
import br.com.energybillai.domain.game.usecase.BuildSavingsSimulationsUseCase
import br.com.energybillai.domain.game.usecase.DeclineSuggestedChallengeUseCase
import br.com.energybillai.domain.game.usecase.EnsureSuggestedChallengeUseCase
import br.com.energybillai.domain.game.usecase.ObserveGameProgressUseCase
import br.com.energybillai.domain.game.usecase.ObserveMeterReadingsUseCase
import br.com.energybillai.domain.game.usecase.ResolveReferenceTariffUseCase
import br.com.energybillai.domain.model.BillExtractionStatus
import br.com.energybillai.domain.model.BillForecast
import br.com.energybillai.domain.model.BillSummary
import br.com.energybillai.domain.usecase.GetForecastUseCase
import br.com.energybillai.domain.usecase.ObserveHistoryUseCase
import br.com.energybillai.domain.usecase.ObserveSessionUseCase
import br.com.energybillai.domain.usecase.RefreshHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class EnergyGameUiModel(
    val suggestedChallenge: EnergyChallenge?,
    val activeChallenge: EnergyChallenge?,
    val userScore: UserScore?,
    val achievements: List<Achievement>,
    val readings: List<MeterReading>,
    val latestWeeklyConsumption: WeeklyConsumption?,
    val pendingWeeklyReading: Boolean,
    val motivationalMessage: String?,
    val savingsSimulations: List<SavingsProjection>,
    val referenceTariffBrlPerKwh: Double?,
    val historyAvailable: Boolean,
)

data class EnergyGameUiState(
    val content: UiState<EnergyGameUiModel> = UiState.Loading,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class EnergyGameViewModel @Inject constructor(
    observeSessionUseCase: ObserveSessionUseCase,
    private val observeHistoryUseCase: ObserveHistoryUseCase,
    private val refreshHistoryUseCase: RefreshHistoryUseCase,
    private val getForecastUseCase: GetForecastUseCase,
    private val acceptSuggestedChallengeUseCase: AcceptSuggestedChallengeUseCase,
    private val declineSuggestedChallengeUseCase: DeclineSuggestedChallengeUseCase,
    private val ensureSuggestedChallengeUseCase: EnsureSuggestedChallengeUseCase,
    private val observeGameProgressUseCase: ObserveGameProgressUseCase,
    private val observeMeterReadingsUseCase: ObserveMeterReadingsUseCase,
    private val buildSavingsSimulationsUseCase: BuildSavingsSimulationsUseCase,
    private val resolveReferenceTariffUseCase: ResolveReferenceTariffUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EnergyGameUiState())
    val state = mutableState.asStateFlow()

    private var currentUserId: String? = null
    private var historyJob: Job? = null
    private var gameProgressJob: Job? = null
    private var meterReadingsJob: Job? = null

    private var latestHistory: List<BillSummary> = emptyList()
    private var latestForecast: BillForecast? = null
    private var latestForecastBillId: String? = null
    private var latestGameProgress: GameProgress? = null
    private var latestReadings: List<MeterReading> = emptyList()

    init {
        viewModelScope.launch {
            observeSessionUseCase().collectLatest { session ->
                val userId = session?.user?.id
                if (userId.isNullOrBlank()) {
                    currentUserId = null
                    historyJob?.cancel()
                    gameProgressJob?.cancel()
                    meterReadingsJob?.cancel()
                    resetLocalState()
                    mutableState.value = EnergyGameUiState(content = UiState.Empty)
                } else if (currentUserId != userId) {
                    currentUserId = userId
                    resetLocalState()
                    observeHistory(userId)
                    observeGameProgress(userId)
                    observeMeterReadings(userId)
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isRefreshing = true)
            when (refreshHistoryUseCase(userId)) {
                is AppResult.Success -> mutableState.value = mutableState.value.copy(isRefreshing = false)
                is AppResult.Error -> mutableState.value = mutableState.value.copy(isRefreshing = false)
            }
        }
    }

    fun acceptSuggestedChallenge() {
        val userId = currentUserId ?: return
        val suggestedChallengeId = latestGameProgress?.suggestedChallenge?.id ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isRefreshing = true)
            acceptSuggestedChallengeUseCase(
                userId = userId,
                challengeId = suggestedChallengeId,
            )
            mutableState.value = mutableState.value.copy(isRefreshing = false)
        }
    }

    fun declineSuggestedChallenge() {
        val userId = currentUserId ?: return
        val suggestedChallengeId = latestGameProgress?.suggestedChallenge?.id ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isRefreshing = true)
            declineSuggestedChallengeUseCase(
                userId = userId,
                challengeId = suggestedChallengeId,
            )
            mutableState.value = mutableState.value.copy(isRefreshing = false)
        }
    }

    private fun observeHistory(userId: String) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            observeHistoryUseCase(userId).collectLatest { history ->
                latestHistory = history
                val latestConfirmedBillId = history
                    .firstOrNull { it.extractionStatus == BillExtractionStatus.CONFIRMED && !it.reviewRequired }
                    ?.billId

                if (latestConfirmedBillId != latestForecastBillId) {
                    latestForecastBillId = latestConfirmedBillId
                    latestForecast = latestConfirmedBillId?.let { billId ->
                        when (val forecastResult = getForecastUseCase(billId)) {
                            is AppResult.Success -> forecastResult.data
                            is AppResult.Error -> null
                        }
                    }
                }

                if (history.isNotEmpty()) {
                    ensureSuggestedChallengeUseCase(
                        userId = userId,
                        history = history,
                        forecast = latestForecast,
                    )
                }
                publishState()
            }
        }
    }

    private fun observeGameProgress(userId: String) {
        gameProgressJob?.cancel()
        gameProgressJob = viewModelScope.launch {
            observeGameProgressUseCase(userId).collectLatest { progress ->
                latestGameProgress = progress
                publishState()
            }
        }
    }

    private fun observeMeterReadings(userId: String) {
        meterReadingsJob?.cancel()
        meterReadingsJob = viewModelScope.launch {
            observeMeterReadingsUseCase(userId).collectLatest { readings ->
                latestReadings = readings
                publishState()
            }
        }
    }

    private fun publishState() {
        val hasHistory = latestHistory.isNotEmpty()
        val hasGameData = latestReadings.isNotEmpty() ||
            latestGameProgress?.activeChallenge != null ||
            latestGameProgress?.latestWeeklyConsumption != null ||
            latestGameProgress?.userScore != null ||
            !latestGameProgress?.achievements.isNullOrEmpty()
        if (!hasHistory && !hasGameData) {
            mutableState.value = mutableState.value.copy(content = UiState.Empty)
            return
        }

        val referenceTariff = resolveReferenceTariffUseCase(
            history = latestHistory,
            forecast = latestForecast,
        ) ?: if (latestReadings.isNotEmpty()) 0.90 else null

        val suggestedChallenge = latestGameProgress?.suggestedChallenge
        val activeChallenge = latestGameProgress?.activeChallenge
        val displayedChallenge = activeChallenge ?: suggestedChallenge
        val baseConsumption = displayedChallenge?.baselineConsumptionKwh
            ?: latestHistory.firstOrNull()?.consumptionKwh
        val savingsSimulations = buildSavingsSimulationsUseCase(
            baseConsumptionKwh = baseConsumption,
            referenceTariffBrlPerKwh = referenceTariff,
        )

        mutableState.value = mutableState.value.copy(
            content = UiState.Success(
                EnergyGameUiModel(
                    suggestedChallenge = suggestedChallenge,
                    activeChallenge = activeChallenge,
                    userScore = latestGameProgress?.userScore,
                    achievements = latestGameProgress?.achievements.orEmpty(),
                    readings = latestReadings,
                    latestWeeklyConsumption = latestGameProgress?.latestWeeklyConsumption,
                    pendingWeeklyReading = latestGameProgress?.pendingWeeklyReading ?: true,
                    motivationalMessage = latestGameProgress?.motivationalMessage,
                    savingsSimulations = savingsSimulations,
                    referenceTariffBrlPerKwh = referenceTariff,
                    historyAvailable = hasHistory,
                ),
            ),
        )
    }

    private fun resetLocalState() {
        latestHistory = emptyList()
        latestForecast = null
        latestForecastBillId = null
        latestGameProgress = null
        latestReadings = emptyList()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnergyGameScreen(
    showBack: Boolean = true,
    onNavigateBack: () -> Unit,
    onOpenMeterReading: () -> Unit,
    onOpenUpload: () -> Unit,
    viewModel: EnergyGameViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val simulationBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted = granted
    }

    EnergyScreen(
        title = "Desafios",
        showBack = showBack,
        onBack = onNavigateBack,
    ) {
        AppBrandLockup(
            subtitle = "Missoes guiadas por consumo real, leituras semanais e economia estimada em reais.",
        )
        when (val content = state.content) {
            UiState.Loading -> {
                LoadingPane(
                    title = "Preparando suas missoes",
                    message = "Reunindo historico, leituras semanais e previsoes para montar uma jornada mais inteligente de economia.",
                )
            }

            UiState.Empty -> {
                HeroCard(
                    eyebrow = "Comece pela conta",
                    title = "Envie a primeira fatura para liberar os desafios",
                    supporting = "Assim o app entende seu padrao de consumo, estima economia em reais e cria metas semanais mais coerentes.",
                )
                PrimaryActionButton(
                    text = "Enviar primeira conta",
                    onClick = onOpenUpload,
                )
            }

            is UiState.Error -> {
                ErrorStatePane(
                    title = "Nao foi possivel carregar os desafios",
                    message = content.error.message,
                    onRetry = viewModel::refresh,
                )
            }

            is UiState.Success -> {
                EnergyGameContent(
                    model = content.data,
                    isRefreshing = state.isRefreshing,
                    shouldPromptNotificationPermission = !notificationPermissionGranted,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onRefresh = viewModel::refresh,
                    onAcceptSuggestedChallenge = viewModel::acceptSuggestedChallenge,
                    onDeclineSuggestedChallenge = viewModel::declineSuggestedChallenge,
                    onShowSimulation = {
                        coroutineScope.launch {
                            simulationBringIntoViewRequester.bringIntoView()
                        }
                    },
                    onOpenMeterReading = onOpenMeterReading,
                    onOpenUpload = onOpenUpload,
                    simulationBringIntoViewRequester = simulationBringIntoViewRequester,
                )
            }

            UiState.Idle -> Unit
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnergyGameContent(
    model: EnergyGameUiModel,
    isRefreshing: Boolean,
    shouldPromptNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRefresh: () -> Unit,
    onAcceptSuggestedChallenge: () -> Unit,
    onDeclineSuggestedChallenge: () -> Unit,
    onShowSimulation: () -> Unit,
    onOpenMeterReading: () -> Unit,
    onOpenUpload: () -> Unit,
    simulationBringIntoViewRequester: BringIntoViewRequester,
) {
    val displayedChallenge = model.activeChallenge ?: model.suggestedChallenge
    val achievementBadges = buildAchievementBadges(model)
    val streakMarkers = buildWeeklyMarkers(
        readings = model.readings,
        pendingReading = model.pendingWeeklyReading,
    )

    HeroCard(
        eyebrow = when {
            model.activeChallenge != null && model.pendingWeeklyReading -> "Leitura pendente"
            model.suggestedChallenge != null -> "Missao sugerida"
            else -> "Sua central de economia"
        },
        title = displayedChallenge?.title ?: "Transforme dados do medidor em economia real",
        supporting = model.motivationalMessage
            ?: displayedChallenge?.description
            ?: "Aceite uma missao, registre o medidor toda semana e acompanhe sua evolucao em kWh, reais e XP.",
    )

    if (shouldPromptNotificationPermission) {
        AppCard(
            title = "Ative os lembretes",
            eyebrow = "Nao perca o ritmo",
            supporting = "O app pode avisar quando a leitura semanal estiver pendente e quando sua meta estiver perto de ser concluida.",
        ) {
            PrimaryActionButton(
                text = "Ativar lembretes",
                onClick = onRequestNotificationPermission,
            )
        }
    }

    model.suggestedChallenge?.let { suggestedChallenge ->
        AppCard(
            title = "Voce recebeu uma nova missao",
            eyebrow = "Oportunidade de economia",
            supporting = suggestedChallenge.toSuggestionPitch(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricChip(
                    label = "Economia estimada",
                    value = suggestedChallenge.toPotentialSavingsLabel(),
                    modifier = Modifier.weight(1f),
                    highlighted = true,
                )
                MetricChip(
                    label = "Recompensa",
                    value = "${suggestedChallenge.rewardXp} XP",
                    modifier = Modifier.weight(1f),
                    supporting = "ao concluir",
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricChip(
                    label = "Meta principal",
                    value = suggestedChallenge.toPrimaryGoalLabel(),
                    modifier = Modifier.weight(1f),
                )
                MetricChip(
                    label = "Prazo",
                    value = suggestedChallenge.toRemainingDaysLabel(),
                    modifier = Modifier.weight(1f),
                    supporting = suggestedChallenge.endDate.toDateLabel(),
                )
            }
            PrimaryActionButton(
                text = "Aceitar desafio",
                onClick = onAcceptSuggestedChallenge,
                enabled = !isRefreshing,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onShowSimulation,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Ver simulacao")
                }
                TextButton(
                    onClick = onDeclineSuggestedChallenge,
                    modifier = Modifier.weight(1f),
                    enabled = !isRefreshing,
                ) {
                    Text("Agora nao")
                }
            }
        }
    }

    if (model.pendingWeeklyReading && model.activeChallenge != null) {
        InlineWarning(
            text = "Sua leitura semanal esta pendente. Registrar o medidor agora ajuda a manter a missao atualizada e protege sua sequencia.",
        )
    }

    WeeklyReadingStreakCard(
        streakWeeks = model.userScore?.weeklyStreak ?: 0,
        lastReadingLabel = model.readings.firstOrNull()?.readingDate?.toDateLabel(),
        nextReadingLabel = model.readings.firstOrNull()?.readingDate?.let(::buildNextReadingLabel) ?: "Agora",
        pendingReading = model.pendingWeeklyReading,
        markers = streakMarkers,
    )

    SectionHeader(
        title = "Seu ritmo no jogo",
        supporting = "Tudo aqui e alimentado pelas leituras confirmadas do medidor e pelo historico das contas.",
    )

    AppCard(
        title = "Placar do jogador",
        eyebrow = "XP e evolucao",
        supporting = "A cada leitura semanal e meta cumprida, voce acumula XP, sobe de nivel e desbloqueia novas medalhas.",
    ) {
        MetricChip(
            label = "Nivel atual",
            value = model.userScore?.levelName ?: "Iniciante",
            supporting = model.userScore?.let { "${it.totalXp} XP acumulados" } ?: "Comece pela primeira leitura",
            highlighted = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricChip(
                label = "Sequencia",
                value = "${model.userScore?.weeklyStreak ?: 0} semanas",
                modifier = Modifier.weight(1f),
            )
            MetricChip(
                label = "Proximo nivel",
                value = "${model.userScore?.xpToNextLevel ?: 100} XP",
                modifier = Modifier.weight(1f),
                supporting = "faltam para subir",
            )
        }
        PrimaryActionButton(
            text = "Registrar leitura semanal",
            onClick = onOpenMeterReading,
        )
        TextButton(
            onClick = onRefresh,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            enabled = !isRefreshing,
        ) {
            Text(if (isRefreshing) "Atualizando..." else "Atualizar desafios")
        }
    }

    model.activeChallenge?.let { challenge ->
        AppCard(
            title = "Missao em andamento",
            eyebrow = "Desafio ativo",
            supporting = challenge.description,
        ) {
            StatusPill(
                text = challenge.status.toDisplayLabel(),
                color = when (challenge.status) {
                    EnergyChallengeStatus.SUGGESTED -> MaterialTheme.colorScheme.tertiary
                    EnergyChallengeStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    EnergyChallengeStatus.FAILED -> MaterialTheme.colorScheme.error
                    EnergyChallengeStatus.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
                    EnergyChallengeStatus.ACTIVE -> MaterialTheme.colorScheme.secondary
                },
            )
            LinearProgressIndicator(
                progress = { (challenge.progressPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )
            Text(
                text = "Progresso atual: ${challenge.progressPercent.roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricChip(
                    label = "Meta em kWh",
                    value = challenge.targetKwh.toKwhLabel(),
                    modifier = Modifier.weight(1f),
                )
                MetricChip(
                    label = "Meta em R$",
                    value = challenge.targetBrl.toCurrencyLabel(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricChip(
                    label = "Economia atual",
                    value = challenge.currentSavedBrl.toCurrencyLabel(),
                    modifier = Modifier.weight(1f),
                    supporting = challenge.currentSavedKwh.toKwhLabel(),
                )
                MetricChip(
                    label = "Prazo restante",
                    value = challenge.toRemainingDaysLabel(),
                    modifier = Modifier.weight(1f),
                    supporting = challenge.endDate.toDateLabel(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricChip(
                    label = "Recompensa",
                    value = "${challenge.rewardXp} XP",
                    modifier = Modifier.weight(1f),
                    supporting = "ao concluir",
                )
                MetricChip(
                    label = "Falta para bater a meta",
                    value = challenge.toRemainingTargetLabel(),
                    modifier = Modifier.weight(1f),
                )
            }
            challenge.toRemainingMoneyHelper(model.referenceTariffBrlPerKwh)?.let { helper ->
                Text(
                    text = helper,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    model.latestWeeklyConsumption?.let { weeklyConsumption ->
        AppCard(
            title = "Leitura da semana",
            eyebrow = "Consumo real",
            supporting = "A comparacao abaixo mostra o que aconteceu entre a ultima leitura confirmada e a atual.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricChip(
                    label = "Consumo semanal",
                    value = weeklyConsumption.consumptionKwh.toKwhLabel(),
                    modifier = Modifier.weight(1f),
                    highlighted = true,
                )
                MetricChip(
                    label = "Media diaria",
                    value = weeklyConsumption.averageDailyKwh.toKwhLabel(),
                    modifier = Modifier.weight(1f),
                )
            }
            weeklyConsumption.estimatedCostBrl?.let { estimatedCost ->
                MetricChip(
                    label = "Custo estimado",
                    value = estimatedCost.toCurrencyLabel(),
                    supporting = "para este intervalo",
                )
            }
            if (weeklyConsumption.isSuspicious) {
                InlineWarning(
                    text = "Esta leitura ficou acima do padrao esperado. Vale revisar equipamentos de maior gasto ou conferir o numero antes da proxima semana.",
                )
            }
        }
    }

    if (model.readings.size >= 2) {
        AppCard(
            title = "Historico semanal",
            eyebrow = "Evolucao do medidor",
            supporting = "A curva abaixo usa as diferencas entre leituras confirmadas para mostrar como seu consumo tem se comportado.",
        ) {
            val weeklySeries = buildWeeklySeries(model.readings)
            if (weeklySeries.isNotEmpty()) {
                SimpleLineChart(points = weeklySeries)
            }
        }
    }

    AppCard(
        title = "Simulador de economia",
        eyebrow = "Cenarios de ganho",
        modifier = Modifier.bringIntoViewRequester(simulationBringIntoViewRequester),
        supporting = model.referenceTariffBrlPerKwh?.let { tariff ->
            "Os cenarios abaixo usam uma tarifa media de ${tariff.toCurrencyLabel()} por kWh."
        } ?: "Assim que houver tarifa suficiente, o app vai traduzir as metas em economia estimada em reais.",
    ) {
        if (model.savingsSimulations.isEmpty()) {
            Text(
                text = "Ainda faltam dados para montar simulacoes personalizadas. Confirme mais contas e continue registrando o medidor semanalmente.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            model.savingsSimulations.forEach { scenario ->
                MetricChip(
                    label = scenario.toScenarioLabel(),
                    value = scenario.estimatedMonthlySavingsBrl.toCurrencyLabel(),
                    supporting = "Economia potencial de ${scenario.estimatedMonthlySavingsKwh.toKwhLabel()} no fechamento do mes",
                    highlighted = scenario.monthlyReductionPercent == 10.0,
                )
            }
        }
    }

    AppCard(
        title = "Suas conquistas",
        eyebrow = "Medalhas",
        supporting = "Cada medalha representa uma etapa real da sua evolucao no acompanhamento de energia.",
    ) {
        achievementBadges.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { badge ->
                    AchievementBadge(
                        model = badge,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Column(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }

    AppCard(
        title = "Leituras recentes",
        eyebrow = "Controle semanal",
        supporting = "Cada registro confirmado melhora a qualidade das suas metas e ajuda a detectar desvios mais cedo.",
    ) {
        if (model.readings.isEmpty()) {
            Text(
                text = "Nenhuma leitura semanal registrada ainda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            model.readings.take(6).forEach { reading ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = reading.readingDate.toDateLabel(),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "${reading.confirmedValue} kWh no medidor",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (reading.isInitialReading) {
                        StatusPill(
                            text = "Leitura inicial",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    } else if (reading.isSuspicious) {
                        StatusPill(
                            text = "Revisar",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        if (!model.historyAvailable) {
            TextButton(
                onClick = onOpenUpload,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Enviar conta para melhorar as metas")
            }
        }
    }
}

private fun EnergyChallengeStatus.toDisplayLabel(): String {
    return when (this) {
        EnergyChallengeStatus.SUGGESTED -> "Sugestao"
        EnergyChallengeStatus.ACTIVE -> "Em andamento"
        EnergyChallengeStatus.COMPLETED -> "Concluido"
        EnergyChallengeStatus.FAILED -> "Nao concluido"
        EnergyChallengeStatus.CANCELED -> "Cancelado"
    }
}

private fun EnergyChallenge.toSuggestionPitch(): String {
    return when {
        targetBrl != null && targetKwh != null ->
            "Voce pode economizar ${targetBrl.toCurrencyLabel()} neste ciclo se reduzir cerca de ${targetKwh.toKwhLabel()} ao longo da meta."
        targetBrl != null ->
            "Voce pode aliviar a conta em cerca de ${targetBrl.toCurrencyLabel()} se mantiver esta missao ate o fim do ciclo."
        targetKwh != null ->
            "Sua meta sugerida agora e tirar ${targetKwh.toKwhLabel()} do consumo previsto neste ciclo."
        else ->
            description
    }
}

private fun EnergyChallenge.toPotentialSavingsLabel(): String {
    return targetBrl?.toCurrencyLabel()
        ?: currentSavedBrl?.takeIf { it > 0 }?.toCurrencyLabel()
        ?: "Em definicao"
}

private fun EnergyChallenge.toPrimaryGoalLabel(): String {
    return when {
        targetKwh != null -> targetKwh.toKwhLabel()
        targetBrl != null -> targetBrl.toCurrencyLabel()
        else -> title
    }
}

private fun EnergyChallenge.toRemainingDaysLabel(today: LocalDate = LocalDate.now()): String {
    val parsedEndDate = parseChallengeDate(endDate) ?: return "Prazo indefinido"
    val remainingDays = ChronoUnit.DAYS.between(today, parsedEndDate).coerceAtLeast(0)
    return when (remainingDays) {
        0L -> "Ultimo dia"
        1L -> "1 dia"
        else -> "$remainingDays dias"
    }
}

private fun EnergyChallenge.toRemainingTargetLabel(): String {
    return when {
        targetKwh != null -> ((targetKwh - (currentSavedKwh ?: 0.0)).coerceAtLeast(0.0)).toKwhLabel()
        targetBrl != null -> ((targetBrl - (currentSavedBrl ?: 0.0)).coerceAtLeast(0.0)).toCurrencyLabel()
        else -> "Em andamento"
    }
}

private fun EnergyChallenge.toRemainingMoneyHelper(referenceTariffBrlPerKwh: Double?): String? {
    val remainingKwh = targetKwh?.let { target ->
        (target - (currentSavedKwh ?: 0.0)).coerceAtLeast(0.0)
    }
    val remainingBrl = when {
        targetBrl != null -> (targetBrl - (currentSavedBrl ?: 0.0)).coerceAtLeast(0.0)
        remainingKwh != null && referenceTariffBrlPerKwh != null -> remainingKwh * referenceTariffBrlPerKwh
        else -> null
    } ?: return null

    return when {
        remainingBrl <= 0.01 -> "Meta financeira praticamente concluida. Uma nova leitura pode confirmar sua recompensa."
        remainingKwh != null -> "Faltam ${remainingKwh.toKwhLabel()} para concluir a meta, algo perto de ${remainingBrl.toCurrencyLabel()}."
        else -> "Falta cerca de ${remainingBrl.toCurrencyLabel()} para fechar a meta desta missao."
    }
}

private fun SavingsProjection.toScenarioLabel(): String {
    return when {
        dailyReductionKwh != null -> "Reduzindo ${dailyReductionKwh.toKwhLabel()} por dia"
        monthlyReductionPercent != null -> "Reduzindo ${monthlyReductionPercent.roundToInt()}% no mes"
        else -> "Cenario estimado"
    }
}

private fun buildWeeklySeries(readings: List<MeterReading>): List<Double> {
    val orderedReadings = readings.sortedBy { it.readingDate }
    if (orderedReadings.size < 2) return emptyList()
    return orderedReadings
        .zipWithNext { previous, current ->
            (current.confirmedValue - previous.confirmedValue).toDouble().coerceAtLeast(0.0)
        }
        .filter { it > 0.0 }
}

private fun buildAchievementBadges(model: EnergyGameUiModel): List<AchievementBadgeUiModel> {
    val unlockedTypes = model.achievements.filter { it.isUnlocked }.map { it.type }.toSet()
    val streak = model.userScore?.weeklyStreak ?: 0
    val savedBrl = model.activeChallenge?.currentSavedBrl ?: 0.0
    val progressPercent = model.activeChallenge?.progressPercent ?: 0.0

    fun stateFor(
        type: AchievementType,
        inProgress: Boolean,
    ): AchievementBadgeState {
        return when {
            unlockedTypes.contains(type) -> AchievementBadgeState.UNLOCKED
            inProgress -> AchievementBadgeState.IN_PROGRESS
            else -> AchievementBadgeState.LOCKED
        }
    }

    return listOf(
        AchievementBadgeUiModel(
            title = "Primeira leitura",
            description = "Confirme a primeira leitura do medidor para ligar o jogo.",
            icon = Icons.Outlined.TrackChanges,
            state = stateFor(
                type = AchievementType.FIRST_READING,
                inProgress = model.readings.isNotEmpty(),
            ),
            progressLabel = if (!unlockedTypes.contains(AchievementType.FIRST_READING) && model.readings.isNotEmpty()) "1/1" else null,
        ),
        AchievementBadgeUiModel(
            title = "Primeira economia",
            description = "Registre uma leitura abaixo da referencia e gere economia real.",
            icon = Icons.Outlined.Savings,
            state = stateFor(
                type = AchievementType.FIRST_SAVINGS,
                inProgress = savedBrl > 0.0,
            ),
            progressLabel = if (!unlockedTypes.contains(AchievementType.FIRST_SAVINGS) && savedBrl > 0.0) {
                savedBrl.toCurrencyLabel()
            } else {
                null
            },
        ),
        AchievementBadgeUiModel(
            title = "4 semanas seguidas",
            description = "Mantenha sua sequencia semanal sem perder a leitura do medidor.",
            icon = Icons.Outlined.LocalFireDepartment,
            state = stateFor(
                type = AchievementType.FOUR_WEEKS_STREAK,
                inProgress = streak > 0,
            ),
            progressLabel = if (!unlockedTypes.contains(AchievementType.FOUR_WEEKS_STREAK) && streak > 0) "$streak/4" else null,
        ),
        AchievementBadgeUiModel(
            title = "Reduziu 5%",
            description = "Mostra que seu consumo ficou abaixo do padrao esperado.",
            icon = Icons.Outlined.Bolt,
            state = stateFor(
                type = AchievementType.REDUCED_FIVE_PERCENT,
                inProgress = progressPercent > 0.0,
            ),
            progressLabel = if (!unlockedTypes.contains(AchievementType.REDUCED_FIVE_PERCENT) && progressPercent > 0.0) {
                "${progressPercent.roundToInt()}%"
            } else {
                null
            },
        ),
        AchievementBadgeUiModel(
            title = "Economizou R$ 50",
            description = "Acumule economia suficiente para bater uma meta financeira relevante.",
            icon = Icons.Outlined.Savings,
            state = stateFor(
                type = AchievementType.SAVED_FIFTY_BRL,
                inProgress = savedBrl > 0.0,
            ),
            progressLabel = if (!unlockedTypes.contains(AchievementType.SAVED_FIFTY_BRL) && savedBrl > 0.0) {
                savedBrl.toCurrencyLabel()
            } else {
                null
            },
        ),
        AchievementBadgeUiModel(
            title = "Meta mensal concluida",
            description = "Feche uma missao inteira e desbloqueie a recompensa principal do ciclo.",
            icon = Icons.Outlined.EmojiEvents,
            state = stateFor(
                type = AchievementType.MONTHLY_GOAL_COMPLETED,
                inProgress = model.activeChallenge != null,
            ),
            progressLabel = if (!unlockedTypes.contains(AchievementType.MONTHLY_GOAL_COMPLETED) && model.activeChallenge != null) {
                "${progressPercent.roundToInt()}%"
            } else {
                null
            },
        ),
    )
}

private fun buildWeeklyMarkers(
    readings: List<MeterReading>,
    pendingReading: Boolean,
): List<WeeklyReadingMarkerUiModel> {
    val today = LocalDate.now()
    val readingDates = readings.mapNotNull { parseChallengeDate(it.readingDate) }

    return listOf(3, 2, 1, 0).mapIndexed { index, weeksAgo ->
        val slotEnd = today.minusDays((weeksAgo * 7L))
        val slotStart = slotEnd.minusDays(6)
        val hasReading = readingDates.any { date ->
            !date.isBefore(slotStart) && !date.isAfter(slotEnd)
        }
        val state = when {
            hasReading -> WeeklyReadingMarkerState.COMPLETE
            index == 3 && pendingReading -> WeeklyReadingMarkerState.PENDING
            slotEnd.isBefore(today.minusDays(6)) -> WeeklyReadingMarkerState.MISSED
            else -> WeeklyReadingMarkerState.UPCOMING
        }

        WeeklyReadingMarkerUiModel(
            label = when (index) {
                0 -> "4 sem"
                1 -> "3 sem"
                2 -> "2 sem"
                else -> "Atual"
            },
            state = state,
        )
    }
}

private fun buildNextReadingLabel(rawDate: String): String {
    val parsedDate = parseChallengeDate(rawDate) ?: return "Agora"
    return parsedDate.plusDays(7).toString().toDateLabel()
}

private fun parseChallengeDate(rawValue: String): LocalDate? {
    return runCatching { LocalDate.parse(rawValue.take(10)) }.getOrNull()
}
