package br.com.energybillai.feature.detail

import androidx.compose.foundation.layout.Arrangement
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
import br.com.energybillai.core.designsystem.EmptyStatePane
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.designsystem.StatusPill
import br.com.energybillai.core.ui.toCurrencyLabel
import br.com.energybillai.core.ui.toDateLabel
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.core.ui.toMonthLabel
import br.com.energybillai.domain.model.BillReview
import br.com.energybillai.domain.usecase.GetBillUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getBillUseCase: GetBillUseCase,
) : ViewModel() {
    private val billId: String = checkNotNull(savedStateHandle["billId"])
    private val mutableState = MutableStateFlow<UiState<BillReview>>(UiState.Loading)
    val state = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = UiState.Loading
            mutableState.value = when (val result = getBillUseCase(billId)) {
                is AppResult.Success -> UiState.Success(result.data)
                is AppResult.Error -> UiState.Error(result.error)
            }
        }
    }
}

@Composable
fun BillDetailScreen(
    onNavigateBack: () -> Unit,
    onOpenAnalytics: (String) -> Unit,
    onOpenForecast: (String) -> Unit,
    viewModel: BillDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnergyScreen(title = "Detalhes da conta", showBack = true, onBack = onNavigateBack) {
        when (val content = state) {
            UiState.Loading -> LoadingPane("Carregando conta", "Buscando dados completos da extracao.")
            UiState.Empty -> EmptyStatePane("Conta indisponivel", "Nao foi possivel localizar os detalhes desta fatura.")
            is UiState.Error -> EmptyStatePane("Falha ao carregar", content.error.message)
            is UiState.Success -> {
                val bill = content.data
                AppCard(
                    title = bill.bill.mesReferencia.toMonthLabel(),
                    supporting = bill.bill.concessionaria ?: "Concessionaria nao identificada",
                ) {
                    StatusPill(text = bill.extractionStatus.name.replace("_", " "), color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    androidx.compose.material3.Text(text = "Consumo: ${bill.bill.consumoKwh.toKwhLabel()}")
                    androidx.compose.material3.Text(text = "Valor total: ${bill.bill.valorTotal.toCurrencyLabel()}")
                    androidx.compose.material3.Text(text = "Dias faturados: ${bill.bill.diasFaturados ?: "--"}")
                    androidx.compose.material3.Text(text = "Vencimento: ${bill.bill.vencimento.toDateLabel()}")
                    androidx.compose.material3.Text(text = "Unidade consumidora: ${bill.bill.unidadeConsumidora ?: "--"}")
                }
                if (bill.structuredData.warnings.isNotEmpty()) {
                    AppCard(title = "Avisos da extracao") {
                        bill.structuredData.warnings.forEach { warning ->
                            androidx.compose.material3.Text(text = "• $warning")
                        }
                    }
                }
                if (bill.bill.consumptionHistory.isNotEmpty()) {
                    AppCard(title = "Historico impresso") {
                        bill.bill.consumptionHistory.forEach { item ->
                            androidx.compose.material3.Text(text = "${item.referenceMonth.toMonthLabel()} • ${item.consumptionKwh.toKwhLabel()}")
                        }
                    }
                }
                AppCard(title = "Explorar conta") {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryActionButton(text = "Abrir analytics", onClick = { onOpenAnalytics(bill.billId) })
                        PrimaryActionButton(text = "Abrir forecast", onClick = { onOpenForecast(bill.billId) })
                    }
                }
            }
            UiState.Idle -> Unit
        }
    }
}
