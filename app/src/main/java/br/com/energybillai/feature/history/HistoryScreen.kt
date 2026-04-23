package br.com.energybillai.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.UiState
import br.com.energybillai.core.designsystem.AppBrandLockup
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.AppTextField
import br.com.energybillai.core.designsystem.EmptyStatePane
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.HeroCard
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.MetricChip
import br.com.energybillai.core.designsystem.SectionHeader
import br.com.energybillai.core.designsystem.StatusPill
import br.com.energybillai.core.ui.toCurrencyLabel
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.core.ui.toMonthLabel
import br.com.energybillai.core.ui.toUiLabel
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
        val query = mutableState.value.query.trim().lowercase()
        val filtered = cachedBills.filter { bill ->
            query.isBlank() ||
                bill.referenceMonth.orEmpty().lowercase().contains(query) ||
                bill.provider.orEmpty().lowercase().contains(query)
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

    EnergyScreen(title = "Histórico") {
        AppBrandLockup(
            subtitle = "Linha do tempo consolidada para auditoria rápida, revisão e gestão de duplicidades.",
        )
        AppTextField(
            value = state.query,
            onValueChange = viewModel::updateQuery,
            label = "Buscar por mês ou concessionária",
            supporting = "Use a busca para localizar ciclos, concessionárias ou revisar possíveis duplicidades.",
        )

        when (val content = state.content) {
            UiState.Loading -> LoadingPane("Carregando histórico", "Sincronizando contas confirmadas e pendências de revisão.")
            UiState.Empty -> EmptyStatePane("Nenhuma conta encontrada", "Envie e confirme uma conta para alimentar o histórico.")
            is UiState.Error -> EmptyStatePane("Falha ao carregar", content.error.message)
            is UiState.Success -> {
                val confirmedCount = content.data.count { it.extractionStatus == BillExtractionStatus.CONFIRMED }
                val reviewCount = content.data.count { it.reviewRequired }

                HeroCard(
                    eyebrow = "Base consolidada",
                    title = "${content.data.size} contas no histórico",
                    supporting = "Visualize a linha do tempo, abra detalhes da conta e elimine duplicidades com mais segurança.",
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricChip(
                        label = "Confirmadas",
                        value = confirmedCount.toString(),
                        modifier = Modifier.weight(1f),
                        highlighted = true,
                        supporting = "Prontas para análises",
                    )
                    MetricChip(
                        label = "Em revisão",
                        value = reviewCount.toString(),
                        modifier = Modifier.weight(1f),
                        supporting = "Demandam conferência",
                    )
                }

                SectionHeader(
                    title = "Linha do tempo das contas",
                    supporting = "Cada card resume referência, status e impacto financeiro antes de abrir os detalhes completos.",
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content.data.forEach { bill ->
                        AppCard(
                            title = bill.referenceMonth.toMonthLabel(),
                            eyebrow = if (bill.reviewRequired) "Revisão" else "Conta",
                            supporting = buildString {
                                append(bill.provider ?: "Concessionária não identificada")
                                append(". Abra os detalhes para revisar ou excluir duplicidades.")
                            },
                            onClick = { onOpenBillDetail(bill.billId) },
                        ) {
                            StatusPill(
                                text = bill.extractionStatus.toUiLabel(),
                                color = if (bill.extractionStatus == BillExtractionStatus.CONFIRMED) {
                                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.material3.MaterialTheme.colorScheme.secondary
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricChip(
                                    label = "Consumo",
                                    value = bill.consumptionKwh.toKwhLabel(),
                                    modifier = Modifier.weight(1f),
                                )
                                MetricChip(
                                    label = "Valor",
                                    value = bill.totalValue.toCurrencyLabel(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            UiState.Idle -> Unit
        }
    }
}
