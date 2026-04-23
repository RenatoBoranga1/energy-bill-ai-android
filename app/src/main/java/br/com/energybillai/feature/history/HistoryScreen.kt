package br.com.energybillai.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.common.UiState
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.AppTextField
import br.com.energybillai.core.designsystem.EmptyStatePane
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.StatusPill
import br.com.energybillai.core.ui.toCurrencyLabel
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.core.ui.toMonthLabel
import br.com.energybillai.domain.model.BillExtractionStatus
import br.com.energybillai.domain.model.BillSummary
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

data class HistoryUiState(
    val content: UiState<List<BillSummary>> = UiState.Loading,
    val query: String = "",
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeSessionUseCase: ObserveSessionUseCase,
    private val observeHistoryUseCase: ObserveHistoryUseCase,
    private val refreshHistoryUseCase: RefreshHistoryUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HistoryUiState())
    val state = mutableState.asStateFlow()
    private var currentUserId: String? = null
    private var observeJob: Job? = null
    private var cachedBills: List<BillSummary> = emptyList()

    init {
        viewModelScope.launch {
            observeSessionUseCase().collectLatest { session ->
                val userId = session?.user?.id
                if (userId.isNullOrBlank()) {
                    currentUserId = null
                    cachedBills = emptyList()
                    observeJob?.cancel()
                    mutableState.value = HistoryUiState(content = UiState.Empty)
                } else if (userId != currentUserId) {
                    currentUserId = userId
                    observeHistory(userId)
                    refresh()
                }
            }
        }
    }

    fun updateQuery(value: String) {
        mutableState.value = mutableState.value.copy(query = value)
        publish()
    }

    fun refresh() {
        val userId = currentUserId ?: return
        viewModelScope.launch {
            refreshHistoryUseCase(userId)
        }
    }

    private fun observeHistory(userId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            observeHistoryUseCase(userId).collectLatest { bills ->
                cachedBills = bills
                publish()
            }
        }
    }

    private fun publish() {
        val filtered = cachedBills.filter { bill ->
            val query = mutableState.value.query.trim().lowercase()
            query.isBlank() || bill.referenceMonth.orEmpty().lowercase().contains(query) || bill.provider.orEmpty().lowercase().contains(query)
        }
        mutableState.value = mutableState.value.copy(
            content = if (filtered.isEmpty()) UiState.Empty else UiState.Success(filtered),
        )
    }
}

@Composable
fun HistoryScreen(
    onOpenBillDetail: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnergyScreen(title = "Historico") {
        AppTextField(
            value = state.query,
            onValueChange = viewModel::updateQuery,
            label = "Buscar por mes ou concessionaria",
        )
        when (val content = state.content) {
            UiState.Loading -> LoadingPane("Carregando historico", "Sincronizando contas confirmadas e pendentes.")
            UiState.Empty -> EmptyStatePane("Nenhuma conta encontrada", "Envie e confirme uma conta para alimentar o historico.")
            is UiState.Error -> EmptyStatePane("Falha ao carregar", content.error.message)
            is UiState.Success -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content.data.forEach { bill ->
                        AppCard(
                            title = bill.referenceMonth.toMonthLabel(),
                            supporting = bill.provider ?: "Concessionaria nao identificada",
                            onClick = { onOpenBillDetail(bill.billId) },
                        ) {
                            StatusPill(
                                text = bill.extractionStatus.name.replace("_", " "),
                                color = if (bill.extractionStatus.name == "CONFIRMED") androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                            )
                            androidx.compose.material3.Text(text = "Consumo: ${bill.consumptionKwh.toKwhLabel()}")
                            androidx.compose.material3.Text(text = "Valor: ${bill.totalValue.toCurrencyLabel()}")
                        }
                    }
                }
            }
            UiState.Idle -> Unit
        }
    }
}
