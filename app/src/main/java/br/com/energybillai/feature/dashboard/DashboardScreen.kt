package br.com.energybillai.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.common.UiState
import br.com.energybillai.core.designsystem.ActionTile
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.AppBrandLockup
import br.com.energybillai.core.designsystem.EmptyStatePane
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.ErrorStatePane
import br.com.energybillai.core.designsystem.HeroCard
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.MetricChip
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.designsystem.SectionHeader
import br.com.energybillai.core.designsystem.SimpleLineChart
import br.com.energybillai.core.designsystem.StatusPill
import br.com.energybillai.core.ui.toCurrencyLabel
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.core.ui.toMonthLabel
import br.com.energybillai.core.ui.toTrendLabel
import br.com.energybillai.core.ui.toUiLabel
import br.com.energybillai.domain.model.BillAnalytics
import br.com.energybillai.domain.model.BillForecast
import br.com.energybillai.domain.model.BillExtractionStatus
import br.com.energybillai.domain.model.BillSummary
import br.com.energybillai.domain.usecase.GetAnalyticsUseCase
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

data class DashboardUiModel(
    val latestBill: BillSummary,
    val analytics: BillAnalytics?,
    val forecast: BillForecast?,
    val reviewQueueCount: Int,
    val confirmedCount: Int,
)

data class DashboardUiState(
    val content: UiState<DashboardUiModel> = UiState.Loading,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeSessionUseCase: ObserveSessionUseCase,
    private val observeHistoryUseCase: ObserveHistoryUseCase,
    private val refreshHistoryUseCase: RefreshHistoryUseCase,
    private val getAnalyticsUseCase: GetAnalyticsUseCase,
    private val getForecastUseCase: GetForecastUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DashboardUiState())
    val state = mutableState.asStateFlow()
    private var historyJob: Job? = null
    private var currentUserId: String? = null

    init {
        viewModelScope.launch {
            observeSessionUseCase().collectLatest { session ->
                val userId = session?.user?.id
                if (userId.isNullOrBlank()) {
                    historyJob?.cancel()
                    currentUserId = null
                    mutableState.value = DashboardUiState(content = UiState.Empty)
                } else if (currentUserId != userId) {
                    currentUserId = userId
                    observeHistory(userId)
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
            observeHistoryUseCase(userId).collectLatest { bills ->
                if (bills.isEmpty()) {
                    mutableState.value = DashboardUiState(content = UiState.Empty)
                } else {
                    val latest = bills.first()
                    val analytics = if (latest.extractionStatus == BillExtractionStatus.CONFIRMED && !latest.reviewRequired) {
                        when (val result = getAnalyticsUseCase(latest.billId)) {
                            is AppResult.Success -> result.data
                            is AppResult.Error -> null
                        }
                    } else {
                        null
                    }
                    val forecast = if (latest.extractionStatus == BillExtractionStatus.CONFIRMED && !latest.reviewRequired) {
                        when (val result = getForecastUseCase(latest.billId)) {
                            is AppResult.Success -> result.data
                            is AppResult.Error -> null
                        }
                    } else {
                        null
                    }
                    mutableState.value = DashboardUiState(
                        content = UiState.Success(
                            DashboardUiModel(
                                latestBill = latest,
                                analytics = analytics,
                                forecast = forecast,
                                reviewQueueCount = bills.count { it.reviewRequired },
                                confirmedCount = bills.count { it.extractionStatus == BillExtractionStatus.CONFIRMED },
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    onOpenHistory: () -> Unit,
    onOpenUpload: () -> Unit,
    onOpenBillDetail: (String) -> Unit,
    onOpenAnalytics: (String) -> Unit,
    onOpenForecast: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnergyScreen(title = "Início", modifier = modifier) {
        AppBrandLockup(
            subtitle = "Painel executivo com consumo, revisão assistida e projeções para os próximos ciclos.",
        )
        when (val content = state.content) {
            UiState.Loading -> LoadingPane("Carregando painel", "Sincronizando histórico, análises e projeções.")
            UiState.Empty -> {
                HeroCard(
                    eyebrow = "Comece agora",
                    title = "Envie sua primeira conta",
                    supporting = "Assim que a primeira conta for confirmada, o app libera indicadores, projeções e insights automaticamente.",
                )
                PrimaryActionButton(text = "Fazer upload da conta", onClick = onOpenUpload)
            }
            is UiState.Error -> ErrorStatePane("Não foi possível carregar o painel", content.error.message, onRetry = viewModel::refresh)
            is UiState.Success -> {
                val latest = content.data.latestBill
                val analytics = content.data.analytics
                val forecast = content.data.forecast
                val nextForecast = forecast?.generatedForecasts?.firstOrNull()
                HeroCard(
                    eyebrow = if (latest.reviewRequired) "Revisão pendente" else "Conta consolidada",
                    title = "Referência ${latest.referenceMonth.toMonthLabel()}",
                    supporting = analytics?.trendSummary
                        ?: "Acompanhe o ciclo completo da fatura, da revisão humana até a projeção dos próximos meses.",
                )
                SectionHeader(
                    title = "Panorama do ciclo atual",
                    supporting = "Os indicadores abaixo refletem a última conta priorizada para acompanhamento.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricChip(
                        label = "Consumo",
                        value = latest.consumptionKwh.toKwhLabel(),
                        modifier = Modifier.weight(1f),
                        highlighted = true,
                        supporting = "Conta principal",
                    )
                    MetricChip(
                        label = "Valor",
                        value = latest.totalValue.toCurrencyLabel(),
                        modifier = Modifier.weight(1f),
                        supporting = "Fatura total",
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricChip(
                        label = "Confirmadas",
                        value = content.data.confirmedCount.toString(),
                        modifier = Modifier.weight(1f),
                        supporting = "Base consolidada",
                    )
                    MetricChip(
                        label = "Em revisão",
                        value = content.data.reviewQueueCount.toString(),
                        modifier = Modifier.weight(1f),
                        supporting = "Demandam conferência",
                    )
                }
                AppCard(
                    title = "Status e tendência",
                    eyebrow = "Monitoramento",
                    supporting = analytics?.seasonalitySummary ?: "O gráfico abaixo resume a variação mais recente do consumo.",
                ) {
                    StatusPill(
                        text = latest.extractionStatus.toUiLabel(),
                        color = if (latest.extractionStatus.name == "CONFIRMED") androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                    )
                    if (analytics != null) {
                        SimpleLineChart(points = analytics.series.map { it.consumptionKwh })
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricChip(
                                label = "Média diária",
                                value = analytics.averageDailyKwh?.let { "${String.format("%.1f", it)} kWh/dia" } ?: "N/D",
                                modifier = Modifier.weight(1f),
                            )
                            MetricChip(
                                label = "Tendência",
                                value = analytics.trendDirection.toTrendLabel(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (nextForecast != null) {
                    AppCard(
                        title = "Projeção do próximo mês",
                        eyebrow = "Planejamento",
                        supporting = forecast?.referenceTariffBrlPerKwh?.let { rate ->
                            "Estimativa construída com tarifa média de ${rate.toCurrencyLabel()} por kWh."
                        } ?: "A estimativa em reais foi calculada com base nas contas confirmadas mais recentes.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricChip(
                                label = nextForecast.mesReferencia.toMonthLabel(),
                                value = nextForecast.predictedKwh.toKwhLabel(),
                                modifier = Modifier.weight(1f),
                                highlighted = true,
                                supporting = "Faixa principal",
                            )
                            MetricChip(
                                label = "Estimativa",
                                value = nextForecast.estimatedValueBrl.toCurrencyLabel(),
                                modifier = Modifier.weight(1f),
                                supporting = "Impacto financeiro",
                            )
                        }
                        androidx.compose.material3.Text(
                            text = "Faixa esperada: ${nextForecast.lowerBoundKwh.toKwhLabel()} a ${nextForecast.upperBoundKwh.toKwhLabel()}",
                        )
                        forecast.insights.firstOrNull()?.let { insight ->
                            androidx.compose.material3.Text(
                                text = insight.message,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                SectionHeader(
                    title = "Ações rápidas",
                    supporting = "Os atalhos abaixo levam direto para os fluxos mais importantes do produto.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(
                        title = "Histórico",
                        supporting = "Revise contas, procure duplicidades e acompanhe a evolução mensal.",
                        onClick = onOpenHistory,
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        title = "Enviar conta",
                        supporting = "Envie um novo PDF ou imagem da conta para extração inteligente.",
                        onClick = onOpenUpload,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(
                        title = "Detalhes",
                        supporting = "Abra a conta atual com revisão, histórico e exclusão segura.",
                        onClick = { onOpenBillDetail(latest.billId) },
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        title = "Análises",
                        supporting = if (analytics != null) "Explore médias, variações e padrões do consumo." else "Disponível após a confirmação.",
                        onClick = { onOpenAnalytics(latest.billId) },
                        modifier = Modifier.weight(1f),
                    )
                }
                AppCard(
                    title = "Explorar projeções",
                    eyebrow = "Planejamento",
                    supporting = "Abra a visão completa de 8 meses para apoiar decisões e planejamento financeiro.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryActionButton(
                            text = "Abrir projeções",
                            onClick = { onOpenForecast(latest.billId) },
                            modifier = Modifier.weight(1f),
                            enabled = latest.extractionStatus.name == "CONFIRMED" && !latest.reviewRequired,
                        )
                        PrimaryActionButton(
                            text = "Atualizar painel",
                            onClick = viewModel::refresh,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            UiState.Idle -> Unit
        }
    }
}
