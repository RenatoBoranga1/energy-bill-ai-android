package br.com.energybillai.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.common.UiState
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.EmptyStatePane
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.MetricChip
import br.com.energybillai.core.designsystem.SimpleLineChart
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.core.ui.toMonthLabel
import br.com.energybillai.core.ui.toPercentLabel
import br.com.energybillai.domain.model.BillAnalytics
import br.com.energybillai.domain.usecase.GetAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAnalyticsUseCase: GetAnalyticsUseCase,
) : ViewModel() {
    private val billId: String = checkNotNull(savedStateHandle["billId"])
    private val mutableState = MutableStateFlow<UiState<BillAnalytics>>(UiState.Loading)
    val state = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = UiState.Loading
            mutableState.value = when (val result = getAnalyticsUseCase(billId)) {
                is AppResult.Success -> UiState.Success(result.data)
                is AppResult.Error -> UiState.Error(result.error)
            }
        }
    }
}

@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit,
    onOpenForecast: (String) -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnergyScreen(title = "Analytics", showBack = true, onBack = onNavigateBack) {
        when (val content = state) {
            UiState.Loading -> LoadingPane("Carregando analytics", "Calculando variacoes, extremos e indicadores diarios.")
            is UiState.Error -> EmptyStatePane("Analytics indisponivel", content.error.message)
            UiState.Empty -> EmptyStatePane("Sem analytics", "Confirme uma conta com historico suficiente para ver indicadores completos.")
            is UiState.Success -> {
                val data = content.data
                AppCard(title = "Visao geral", supporting = data.trendSummary) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricChip(label = "Media diaria", value = data.averageDailyKwh.toKwhLabel(), modifier = androidx.compose.ui.Modifier.weight(1f))
                            MetricChip(label = "Media mensal", value = data.averageMonthlyKwh.toKwhLabel(), modifier = androidx.compose.ui.Modifier.weight(1f))
                        }
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricChip(label = "Variacao", value = data.latestMonthOverMonthVariationPct.toPercentLabel(), modifier = androidx.compose.ui.Modifier.weight(1f))
                            MetricChip(label = "Tendencia", value = data.trendDirection.replace('_', ' '), modifier = androidx.compose.ui.Modifier.weight(1f))
                        }
                    }
                }
                AppCard(title = "Serie de consumo", supporting = data.seasonalitySummary) {
                    SimpleLineChart(points = data.series.map { it.consumptionKwh })
                    data.series.takeLast(6).forEach { point ->
                        androidx.compose.material3.Text(text = "${point.referenceMonth.toMonthLabel()} • ${point.consumptionKwh.toKwhLabel()}")
                    }
                }
                if (data.anomalies.isNotEmpty()) {
                    AppCard(title = "Picos e anomalias") {
                        data.anomalies.forEach { anomaly ->
                            androidx.compose.material3.Text(text = "${anomaly.referenceMonth.toMonthLabel()} • ${anomaly.deviationPct.toPercentLabel()} • ${anomaly.reason}")
                        }
                    }
                }
                if (data.insights.isNotEmpty()) {
                    AppCard(title = "Insights") {
                        data.insights.forEach { insight ->
                            androidx.compose.material3.Text(text = "• ${insight.message}")
                        }
                    }
                }
                br.com.energybillai.core.designsystem.PrimaryActionButton(
                    text = "Ver forecast",
                    onClick = { onOpenForecast(data.billId) },
                )
            }
            UiState.Idle -> Unit
        }
    }
}
