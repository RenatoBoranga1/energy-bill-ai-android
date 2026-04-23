package br.com.energybillai.feature.forecast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import br.com.energybillai.core.designsystem.ForecastBandChart
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.core.ui.toMonthLabel
import br.com.energybillai.domain.model.BillForecast
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

    EnergyScreen(title = "Forecast", showBack = true, onBack = onNavigateBack) {
        when (val content = state) {
            UiState.Loading -> LoadingPane("Carregando forecast", "Projetando os proximos meses com a faixa prevista de consumo.")
            is UiState.Error -> EmptyStatePane("Forecast indisponivel", content.error.message)
            UiState.Empty -> EmptyStatePane("Sem forecast", "Confirme contas suficientes para habilitar a previsao.")
            is UiState.Success -> {
                val data = content.data
                AppCard(
                    title = "Projecao para ${data.horizonMonths} meses",
                    supporting = "${data.explanation} Metodo: ${data.modelUsed}.",
                ) {
                    ForecastBandChart(
                        predictions = data.generatedForecasts.map { Triple(it.predictedKwh, it.lowerBoundKwh, it.upperBoundKwh) },
                    )
                    data.generatedForecasts.forEach { item ->
                        androidx.compose.material3.Text(
                            text = "${item.mesReferencia.toMonthLabel()} • ${item.predictedKwh.toKwhLabel()} (${item.lowerBoundKwh.toKwhLabel()} - ${item.upperBoundKwh.toKwhLabel()})",
                        )
                    }
                }
                if (data.insights.isNotEmpty()) {
                    AppCard(title = "Leituras da previsao") {
                        data.insights.forEach { insight ->
                            androidx.compose.material3.Text(text = "• ${insight.message}")
                        }
                    }
                }
                PrimaryActionButton(text = "Voltar aos analytics", onClick = { onOpenAnalytics(data.billId) })
            }
            UiState.Idle -> Unit
        }
    }
}
