package br.com.energybillai.feature.forecast

import androidx.compose.foundation.layout.Arrangement
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
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.AppBrandLockup
import br.com.energybillai.core.designsystem.EmptyStatePane
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.ErrorStatePane
import br.com.energybillai.core.designsystem.ForecastBandChart
import br.com.energybillai.core.designsystem.HeroCard
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.MetricChip
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.designsystem.SectionHeader
import br.com.energybillai.core.ui.toCurrencyLabel
import br.com.energybillai.core.ui.toForecastModelLabel
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.core.ui.toMonthLabel
import br.com.energybillai.domain.model.BillForecast
import br.com.energybillai.domain.model.ForecastPoint
import br.com.energybillai.domain.usecase.GetForecastUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ForecastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getForecastUseCase: GetForecastUseCase,
) : ViewModel() {
    private val billId: String = checkNotNull(savedStateHandle["billId"])
    private val mutableState = MutableStateFlow<UiState<BillForecast>>(UiState.Loading)
    val state = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = UiState.Loading
            mutableState.value = when (val result = getForecastUseCase(billId)) {
                is AppResult.Success -> UiState.Success(result.data)
                is AppResult.Error -> UiState.Error(result.error)
            }
        }
    }
}

@Composable
fun ForecastScreen(
    onNavigateBack: () -> Unit,
    onOpenAnalytics: (String) -> Unit,
    viewModel: ForecastViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnergyScreen(title = "Projeções", showBack = true, onBack = onNavigateBack) {
        AppBrandLockup(
            subtitle = "Estimativas de consumo e custo para apoiar planejamento, economia e previsibilidade.",
        )
        when (val content = state) {
            UiState.Loading -> LoadingPane("Carregando projeções", "Calculando os próximos meses com base no histórico confirmado.")
            is UiState.Error -> ErrorStatePane("Projeções indisponíveis", content.error.message, onRetry = viewModel::refresh)
            UiState.Empty -> EmptyStatePane("Sem projeções", "Confirme contas suficientes para habilitar a visão dos próximos ciclos.")
            is UiState.Success -> {
                val data = content.data
                val nextForecast = data.generatedForecasts.firstOrNull()

                if (nextForecast != null) {
                    HeroCard(
                        eyebrow = "Próximo ciclo estimado",
                        title = nextForecast.mesReferencia.toMonthLabel(),
                        supporting = "Este é o cenário mais provável para o próximo mês, considerando seu histórico recente e a faixa de variação esperada.",
                    )
                    AppCard(title = "Resumo do próximo ciclo", eyebrow = "Planejamento") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricChip(
                                label = "Consumo previsto",
                                value = nextForecast.predictedKwh.toKwhLabel(),
                                modifier = Modifier.weight(1f),
                                highlighted = true,
                                supporting = "Mes seguinte",
                            )
                            MetricChip(
                                label = "Valor estimado",
                                value = nextForecast.estimatedValueBrl.toCurrencyLabel(),
                                modifier = Modifier.weight(1f),
                                supporting = "Com tarifa media",
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricChip(
                                label = "Faixa inferior",
                                value = nextForecast.lowerBoundKwh.toKwhLabel(),
                                modifier = Modifier.weight(1f),
                            )
                            MetricChip(
                                label = "Faixa superior",
                                value = nextForecast.upperBoundKwh.toKwhLabel(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        data.referenceTariffBrlPerKwh?.let { tariff ->
                            androidx.compose.material3.Text(text = "Tarifa média de referência: ${tariff.toCurrencyLabel()} por kWh")
                        }
                    }
                }

                SectionHeader(
                    title = "Projeção para ${data.horizonMonths} meses",
                    supporting = "A curva abaixo mostra o cenário central e a faixa esperada para cada mês, com base no ${data.modelUsed.toForecastModelLabel().lowercase()}.",
                )
                AppCard(
                    title = "Curva projetada",
                    eyebrow = "Visão geral",
                    supporting = data.explanation,
                ) {
                    ForecastBandChart(
                        predictions = data.generatedForecasts.map { Triple(it.predictedKwh, it.lowerBoundKwh, it.upperBoundKwh) },
                    )
                }

                AppCard(
                    title = "Calendário previsto",
                    eyebrow = "Mês a mês",
                    supporting = "Cada linha resume o consumo esperado, a faixa provável e a estimativa financeira do respectivo mês.",
                ) {
                    data.generatedForecasts.forEach { item ->
                        ForecastRow(item = item)
                    }
                }

                if (data.insights.isNotEmpty()) {
                    AppCard(title = "Leituras da projeção", eyebrow = "Insights") {
                        data.insights.forEach { insight ->
                            androidx.compose.material3.Text(text = "- ${insight.message}")
                        }
                    }
                }

                PrimaryActionButton(text = "Voltar às análises", onClick = { onOpenAnalytics(data.billId) })
            }

            UiState.Idle -> Unit
        }
    }
}

@Composable
private fun ForecastRow(item: ForecastPoint) {
    AppCard(
        title = item.mesReferencia.toMonthLabel(),
        supporting = "Faixa esperada de ${item.lowerBoundKwh.toKwhLabel()} a ${item.upperBoundKwh.toKwhLabel()}",
        eyebrow = "Mês projetado",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricChip(
                label = "Previsto",
                value = item.predictedKwh.toKwhLabel(),
                modifier = Modifier.weight(1f),
            )
            MetricChip(
                label = "Estimativa",
                value = item.estimatedValueBrl.toCurrencyLabel(),
                modifier = Modifier.weight(1f),
            )
        }
        val lowerValue = item.lowerBoundValueBrl.toCurrencyLabel()
        val upperValue = item.upperBoundValueBrl.toCurrencyLabel()
        androidx.compose.material3.Text(
            text = "Impacto financeiro estimado entre $lowerValue e $upperValue.",
        )
    }
}
