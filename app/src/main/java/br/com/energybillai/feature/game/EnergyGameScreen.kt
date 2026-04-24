package br.com.energybillai.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.common.UiState
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
import br.com.energybillai.core.ui.toCurrencyLabel
import br.com.energybillai.core.ui.toDateLabel
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.domain.game.model.Achievement
import br.com.energybillai.domain.game.model.EnergyChallenge
import br.com.energybillai.domain.game.model.EnergyChallengeStatus
import br.com.energybillai.domain.game.model.GameProgress
import br.com.energybillai.domain.game.model.MeterReading
import br.com.energybillai.domain.game.model.SavingsProjection
import br.com.energybillai.domain.game.model.UserScore
import br.com.energybillai.domain.game.model.WeeklyConsumption
import br.com.energybillai.domain.game.usecase.BuildSavingsSimulationsUseCase
import br.com.energybillai.domain.game.usecase.EnsureActiveChallengeUseCase
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
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class EnergyGameUiModel(
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
    private val ensureActiveChallengeUseCase: EnsureActiveChallengeUseCase,
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
                    ensureActiveChallengeUseCase(
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

        val activeChallenge = latestGameProgress?.activeChallenge
        val baseConsumption = activeChallenge?.baselineConsumptionKwh
            ?: latestHistory.firstOrNull()?.consumptionKwh
        val savingsSimulations = buildSavingsSimulationsUseCase(
            baseConsumptionKwh = baseConsumption,
            referenceTariffBrlPerKwh = referenceTariff,
        )

        mutableState.value = mutableState.value.copy(
            content = UiState.Success(
                EnergyGameUiModel(
                    activeChallenge = activeChallenge,
                    userScore = latestGameProgress?.userScore,
                    achievements = latestGameProgress?.achievements.orEmpty().filter { it.isUnlocked },
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

@Composable
fun EnergyGameScreen(
    showBack: Boolean = true,
    onNavigateBack: () -> Unit,
    onOpenMeterReading: () -> Unit,
    onOpenUpload: () -> Unit,
    viewModel: EnergyGameViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnergyScreen(
        title = "Energy Game",
        showBack = showBack,
        onBack = onNavigateBack,
    ) {
        AppBrandLockup(
            subtitle = "Transforme leituras reais do medidor em metas semanais, economia estimada e progresso visivel.",
        )
        when (val content = state.content) {
            UiState.Loading -> {
                LoadingPane(
                    title = "Preparando seu desafio",
                    message = "Reunindo historico, leituras semanais e projeções para montar sua jornada de economia.",
                )
            }

            UiState.Empty -> {
                HeroCard(
                    eyebrow = "Comece pelo historico",
                    title = "Envie a primeira conta para ativar o jogo",
                    supporting = "Com a conta confirmada, o app define metas melhores, calcula economia em reais e acompanha sua leitura semanal com mais contexto.",
                )
                PrimaryActionButton(
                    text = "Enviar primeira conta",
                    onClick = onOpenUpload,
                )
            }

            is UiState.Error -> {
                ErrorStatePane(
                    title = "Nao foi possivel carregar o Energy Game",
                    message = content.error.message,
                    onRetry = viewModel::refresh,
                )
            }

            is UiState.Success -> {
                EnergyGameContent(
                    model = content.data,
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    onOpenMeterReading = onOpenMeterReading,
                    onOpenUpload = onOpenUpload,
                )
            }

            UiState.Idle -> Unit
        }
    }
}

@Composable
private fun EnergyGameContent(
    model: EnergyGameUiModel,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenMeterReading: () -> Unit,
    onOpenUpload: () -> Unit,
) {
    HeroCard(
        eyebrow = if (model.pendingWeeklyReading) "Leitura pendente" else "Desafio em andamento",
        title = model.activeChallenge?.title ?: "Sua jornada de economia ja comecou",
        supporting = model.motivationalMessage
            ?: model.activeChallenge?.description
            ?: "Registre leituras semanais para entender seu consumo real e acompanhar metas mais acionaveis.",
    )

    if (model.pendingWeeklyReading) {
        InlineWarning(
            text = "Sua leitura semanal esta pendente. Registrar o medidor agora ajuda a manter o desafio atualizado e evita perder o ritmo.",
        )
    }

    SectionHeader(
        title = "Resumo do jogador",
        supporting = "Nivel, pontos e desafios atualizados com base nas leituras confirmadas do medidor.",
    )
    AppCard(
        title = "Seu progresso",
        eyebrow = "Pontuacao",
        supporting = "A cada leitura confirmada e meta cumprida, voce acumula XP e conquista novos niveis.",
    ) {
        MetricChip(
            label = "Nivel atual",
            value = model.userScore?.levelName ?: "Iniciante",
            supporting = model.userScore?.let { "${it.totalXp} XP acumulados" } ?: "Comece pela primeira leitura",
            highlighted = true,
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
            Text(if (isRefreshing) "Atualizando..." else "Atualizar resumo")
        }
    }

    model.activeChallenge?.let { challenge ->
        AppCard(
            title = "Desafio ativo",
            eyebrow = "Meta da vez",
            supporting = challenge.description,
        ) {
            StatusPill(
                text = challenge.status.toDisplayLabel(),
                color = when (challenge.status) {
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
                    .height(8.dp),
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
                    label = "Janela do desafio",
                    value = challenge.endDate.toDateLabel(),
                    modifier = Modifier.weight(1f),
                    supporting = "encerramento previsto",
                )
            }
        }
    }

    model.latestWeeklyConsumption?.let { weeklyConsumption ->
        AppCard(
            title = "Ritmo da semana",
            eyebrow = "Consumo real",
            supporting = "A leitura mais recente mostra quanto voce consumiu desde o ultimo registro confirmado.",
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
                    supporting = "estimativa para este intervalo",
                )
            }
            if (weeklyConsumption.isSuspicious) {
                InlineWarning(
                    text = "A ultima leitura ficou acima do padrao esperado. Vale revisar equipamentos de maior gasto ou confirmar a leitura com calma.",
                )
            }
        }
    }

    if (model.readings.size >= 2) {
        AppCard(
            title = "Historico de consumo semanal",
            eyebrow = "Evolucao",
            supporting = "A curva abaixo usa as diferencas entre leituras confirmadas para mostrar como o consumo tem se comportado ao longo das semanas.",
        ) {
            val weeklySeries = buildWeeklySeries(model.readings)
            if (weeklySeries.isNotEmpty()) {
                SimpleLineChart(points = weeklySeries)
            }
        }
    }

    AppCard(
        title = "Simulador de economia",
        eyebrow = "Cenarios",
        supporting = model.referenceTariffBrlPerKwh?.let { tariff ->
            "As simulacoes abaixo usam tarifa de referencia de ${tariff.toCurrencyLabel()} por kWh."
        } ?: "Assim que houver referencia de tarifa suficiente, o app vai traduzir as metas em economia estimada em reais.",
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
        title = "Conquistas desbloqueadas",
        eyebrow = "Recompensas",
        supporting = "Cada conquista marca um marco real do seu acompanhamento de energia.",
    ) {
        if (model.achievements.isEmpty()) {
            Text(
                text = "Sua primeira conquista chega com a primeira leitura confirmada do medidor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            model.achievements.take(5).forEach { achievement ->
                Text(
                    text = "• ${achievement.title}: ${achievement.description}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    AppCard(
        title = "Leituras recentes",
        eyebrow = "Historico do medidor",
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
        EnergyChallengeStatus.ACTIVE -> "Em andamento"
        EnergyChallengeStatus.COMPLETED -> "Concluido"
        EnergyChallengeStatus.FAILED -> "Nao concluido"
        EnergyChallengeStatus.CANCELED -> "Cancelado"
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
