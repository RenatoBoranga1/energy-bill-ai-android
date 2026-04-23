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
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.EmptyStatePane
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.ErrorStatePane
import br.com.energybillai.core.designsystem.HeroCard
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.MetricChip
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.designsystem.SimpleLineChart
import br.com.energybillai.core.designsystem.StatusPill
import br.com.energybillai.core.ui.toCurrencyLabel
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.core.ui.toMonthLabel
import br.com.energybillai.domain.model.BillAnalytics
import br.com.energybillai.domain.model.BillExtractionStatus
import br.com.energybillai.domain.model.BillSummary
import br.com.energybillai.domain.usecase.GetAnalyticsUseCase
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
                    mutableState.value = DashboardUiState(
                        content = UiState.Success(
                            DashboardUiModel(
                                latestBill = latest,
                                analytics = analytics,
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

    EnergyScreen(title = "Resumo", modifier = modifier) {
        when (val content = state.content) {
            UiState.Loading -> LoadingPane("Carregando painel", "Sincronizando historico e analytics.")
            UiState.Empty -> {
                HeroCard(
                    eyebrow = "Comece agora",
                    title = "Envie sua primeira conta",
                    supporting = "Assim que a primeira conta for confirmada, o app mostra tendencias, previsoes e insights automaticamente.",
                )
                PrimaryActionButton(text = "Fazer upload da conta", onClick = onOpenUpload)
            }
            is UiState.Error -> ErrorStatePane("Nao foi possivel carregar o dashboard", content.error.message, onRetry = viewModel::refresh)
            is UiState.Success -> {
                val latest = content.data.latestBill
                HeroCard(
                    eyebrow = "Conta em destaque",
                    title = "Referencia ${latest.referenceMonth.toMonthLabel()}",
                    supporting = content.data.analytics?.trendSummary
                        ?: "Acompanhe o ciclo completo da fatura, da revisao humana ate a previsao dos proximos meses.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricChip(label = "Consumo", value = latest.consumptionKwh.toKwhLabel(), modifier = Modifier.weight(1f))
                    MetricChip(label = "Valor", value = latest.totalValue.toCurrencyLabel(), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricChip(label = "Confirmadas", value = content.data.confirmedCount.toString(), modifier = Modifier.weight(1f))
                    MetricChip(label = "Em revisao", value = content.data.reviewQueueCount.toString(), modifier = Modifier.weight(1f))
                }
                AppCard(title = "Status da conta") {
                    StatusPill(
                        text = latest.extractionStatus.name.replace("_", " "),
                        color = if (latest.extractionStatus.name == "CONFIRMED") androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                    )
                    if (content.data.analytics != null) {
                        SimpleLineChart(points = content.data.analytics.series.map { it.consumptionKwh })
                    }
                }
                AppCard(title = "Acoes rapidas") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryActionButton(text = "Historico", onClick = onOpenHistory, modifier = Modifier.weight(1f))
                        PrimaryActionButton(text = "Upload", onClick = onOpenUpload, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryActionButton(text = "Detalhes", onClick = { onOpenBillDetail(latest.billId) }, modifier = Modifier.weight(1f))
                        PrimaryActionButton(
                            text = "Analytics",
                            onClick = { onOpenAnalytics(latest.billId) },
                            modifier = Modifier.weight(1f),
                            enabled = content.data.analytics != null,
                        )
                    }
                    PrimaryActionButton(
                        text = "Forecast",
                        onClick = { onOpenForecast(latest.billId) },
                        enabled = latest.extractionStatus.name == "CONFIRMED" && !latest.reviewRequired,
                    )
                }
            }
            UiState.Idle -> Unit
        }
    }
}
