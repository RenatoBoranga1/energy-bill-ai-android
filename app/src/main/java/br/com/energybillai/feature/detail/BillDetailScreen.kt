package br.com.energybillai.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.AppError
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.common.UiState
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.AppBrandLockup
import br.com.energybillai.core.designsystem.EmptyStatePane
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.InlineWarning
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.designsystem.StatusPill
import br.com.energybillai.core.ui.toCurrencyLabel
import br.com.energybillai.core.ui.toDateLabel
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.core.ui.toMonthLabel
import br.com.energybillai.core.ui.toUiLabel
import br.com.energybillai.domain.model.BillReview
import br.com.energybillai.domain.usecase.DeleteBillUseCase
import br.com.energybillai.domain.usecase.GetBillUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BillDetailUiState(
    val content: UiState<BillReview> = UiState.Loading,
    val isDeleting: Boolean = false,
    val deleteError: AppError? = null,
    val deletedAt: Long? = null,
)

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getBillUseCase: GetBillUseCase,
    private val deleteBillUseCase: DeleteBillUseCase,
) : ViewModel() {
    private val billId: String = checkNotNull(savedStateHandle["billId"])
    private val mutableState = MutableStateFlow(BillDetailUiState())
    val state = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(content = UiState.Loading, deleteError = null)
            mutableState.value = when (val result = getBillUseCase(billId)) {
                is AppResult.Success -> mutableState.value.copy(content = UiState.Success(result.data))
                is AppResult.Error -> mutableState.value.copy(content = UiState.Error(result.error))
            }
        }
    }

    fun deleteBill() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isDeleting = true, deleteError = null)
            mutableState.value = when (val result = deleteBillUseCase(billId)) {
                is AppResult.Success -> mutableState.value.copy(isDeleting = false, deletedAt = System.currentTimeMillis())
                is AppResult.Error -> mutableState.value.copy(isDeleting = false, deleteError = result.error)
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
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.deletedAt) {
        if (state.deletedAt != null) {
            onNavigateBack()
        }
    }

    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { androidx.compose.material3.Text("Excluir conta") },
            text = {
                androidx.compose.material3.Text(
                    "Use esta opção para remover uma conta duplicada ou enviada por engano. O arquivo original e os dados derivados também serão apagados.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteBill()
                    },
                    enabled = !state.isDeleting,
                ) {
                    androidx.compose.material3.Text("Excluir")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                    androidx.compose.material3.Text("Cancelar")
                }
            },
        )
    }

    EnergyScreen(title = "Detalhes da conta", showBack = true, onBack = onNavigateBack) {
        AppBrandLockup(
            subtitle = "Visualize os dados consolidados da conta, acompanhe alertas e gerencie duplicidades com segurança.",
        )
        state.deleteError?.let { InlineWarning(text = it.message) }
        when (val content = state.content) {
            UiState.Loading -> LoadingPane("Carregando conta", "Buscando dados completos da extração.")
            UiState.Empty -> EmptyStatePane("Conta indisponível", "Não foi possível localizar os detalhes desta fatura.")
            is UiState.Error -> EmptyStatePane("Falha ao carregar", content.error.message)
            is UiState.Success -> {
                val bill = content.data
                AppCard(
                    title = bill.bill.mesReferencia.toMonthLabel(),
                    supporting = bill.bill.concessionaria ?: "Concessionária não identificada",
                ) {
                    StatusPill(
                        text = bill.extractionStatus.toUiLabel(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    )
                    androidx.compose.material3.Text(text = "Consumo: ${bill.bill.consumoKwh.toKwhLabel()}")
                    androidx.compose.material3.Text(text = "Valor total: ${bill.bill.valorTotal.toCurrencyLabel()}")
                    androidx.compose.material3.Text(text = "Dias faturados: ${bill.bill.diasFaturados ?: "--"}")
                    androidx.compose.material3.Text(text = "Vencimento: ${bill.bill.vencimento.toDateLabel()}")
                    androidx.compose.material3.Text(text = "Unidade consumidora: ${bill.bill.unidadeConsumidora ?: "--"}")
                }
                if (bill.structuredData.warnings.isNotEmpty()) {
                    AppCard(title = "Avisos da extração") {
                        bill.structuredData.warnings.forEach { warning ->
                            androidx.compose.material3.Text(text = "- $warning")
                        }
                    }
                }
                if (bill.bill.consumptionHistory.isNotEmpty()) {
                    AppCard(title = "Histórico identificado") {
                        bill.bill.consumptionHistory.forEach { item ->
                            androidx.compose.material3.Text(
                                text = "${item.referenceMonth.toMonthLabel()} - ${item.consumptionKwh.toKwhLabel()}",
                            )
                        }
                    }
                }
                AppCard(title = "Explorar conta") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryActionButton(text = "Abrir análises", onClick = { onOpenAnalytics(bill.billId) })
                        PrimaryActionButton(text = "Abrir projeções", onClick = { onOpenForecast(bill.billId) })
                    }
                }
                AppCard(
                    title = "Gerenciar conta",
                    supporting = "Se esta conta foi enviada em duplicidade, você pode removê-la com segurança daqui.",
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !state.isDeleting,
                    ) {
                        if (state.isDeleting) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            androidx.compose.material3.Text("Excluir conta")
                        }
                    }
                }
            }

            UiState.Idle -> Unit
        }
    }
}
