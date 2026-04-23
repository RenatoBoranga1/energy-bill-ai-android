package br.com.energybillai.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
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
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.MetricChip
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.designsystem.SectionHeader
import br.com.energybillai.core.designsystem.SimpleLineChart
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.core.ui.toMonthLabel
import br.com.energybillai.core.ui.toPercentLabel
import br.com.energybillai.core.ui.toTrendLabel
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

    EnergyScreen(title = "Análises", showBack = true, onBack = onNavigateBack) {
        AppBrandLockup(
            subtitle = "Indicadores claros para entender evolução, variações e pontos de atenção do consumo.",
        )

        when (val content = state) {
            UiState.Loading -> LoadingPane("Carregando análises", "Calculando médias, variações e extremos do período.")
            is UiState.Error -> ErrorStatePane("Análises indisponíveis", content.error.message, onRetry = viewModel::refresh)
            UiState.Empty -> EmptyStatePane("Sem análises", "Confirme uma conta com histórico suficiente para ver os indicadores.")
            is UiState.Success -> {
                val data = content.data

                HeroCard(
                    eyebrow = "Leitura do consumo",
                    title = data.referenceMonth.toMonthLabel(),
                    supporting = data.trendSummary,
                )

                SectionHeader(
                    title = "Indicadores do período",
                    supporting = "Esses números ajudam a entender ritmo de consumo, oscilações recentes e estabilidade da conta.",
                )
                AppCard(
                    title = "Resumo executivo",
                    eyebrow = "Indicadores",
                    supporting = "Os dados abaixo consideram o histórico confirmado da conta atual.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricChip(
                                label = "Média diária",
                                value = data.averageDailyKwh.toKwhLabel(),
                                modifier = Modifier.weight(1f),
                                highlighted = true,
                            )
                            MetricChip(
                                label = "Média mensal",
                                value = data.averageMonthlyKwh.toKwhLabel(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricChip(
                                label = "Variação",
                                value = data.latestMonthOverMonthVariationPct.toPercentLabel(),
                                modifier = Modifier.weight(1f),
                            )
                            MetricChip(
                                label = "Tendência",
                                value = data.trendDirection.toTrendLabel(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                AppCard(
                    title = "Evolução mensal",
                    eyebrow = "Linha do tempo",
                    supporting = data.seasonalitySummary,
                ) {
                    SimpleLineChart(points = data.series.map { it.consumptionKwh })
                    data.series.takeLast(6).forEach { point ->
                        androidx.compose.material3.Text(text = "${point.referenceMonth.toMonthLabel()} • ${point.consumptionKwh.toKwhLabel()}")
                    }
                }

                if (data.anomalies.isNotEmpty()) {
                    AppCard(
                        title = "Pontos de atenção",
                        eyebrow = "Alertas",
                        supporting = "Meses com comportamento acima ou abaixo do padrão merecem uma segunda leitura.",
                    ) {
                        data.anomalies.forEach { anomaly ->
                            androidx.compose.material3.Text(
                                text = "${anomaly.referenceMonth.toMonthLabel()} • ${anomaly.deviationPct.toPercentLabel()} • ${anomaly.reason}",
                            )
                        }
                    }
                }

                if (data.insights.isNotEmpty()) {
                    AppCard(
                        title = "Recomendações",
                        eyebrow = "Insights",
                        supporting = "Mensagens objetivas para ajudar na tomada de decisão.",
                    ) {
                        data.insights.forEach { insight ->
                            androidx.compose.material3.Text(text = "• ${insight.message}")
                        }
                    }
                }

                PrimaryActionButton(
                    text = "Abrir projeções",
                    onClick = { onOpenForecast(data.billId) },
                )
            }

            UiState.Idle -> Unit
        }
    }
}
