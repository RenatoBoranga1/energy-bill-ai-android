package br.com.energybillai.feature.review

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
import br.com.energybillai.core.common.AppError
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.common.UiState
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.AppTextField
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.InlineWarning
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.ui.toMonthLabel
import br.com.energybillai.domain.model.BillReview
import br.com.energybillai.domain.model.HistoricalConsumption
import br.com.energybillai.domain.model.ReviewedBillData
import br.com.energybillai.domain.usecase.ConfirmBillUseCase
import br.com.energybillai.domain.usecase.GetBillUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewFormState(
    val billId: String = "",
    val isSubmitting: Boolean = false,
    val loadingState: UiState<BillReview> = UiState.Loading,
    val error: AppError? = null,
    val concessionaria: String = "",
    val mesReferencia: String = "",
    val consumoKwh: String = "",
    val diasFaturados: String = "",
    val valorTotal: String = "",
    val bandeiraTarifaria: String = "",
    val unidadeConsumidora: String = "",
    val vencimento: String = "",
    val historicoConsumo: List<HistoricalConsumption> = emptyList(),
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getBillUseCase: GetBillUseCase,
    private val confirmBillUseCase: ConfirmBillUseCase,
) : ViewModel() {
    private val billId: String = checkNotNull(savedStateHandle["billId"])
    private val mutableState = MutableStateFlow(ReviewFormState(billId = billId))
    val state = mutableState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loadingState = UiState.Loading, error = null)
            when (val result = getBillUseCase(billId)) {
                is AppResult.Success -> {
                    val reviewed = result.data.structuredData.reviewed
                    mutableState.value = mutableState.value.copy(
                        loadingState = UiState.Success(result.data),
                        concessionaria = reviewed.concessionaria.orEmpty(),
                        mesReferencia = reviewed.mesReferencia.orEmpty(),
                        consumoKwh = reviewed.consumoKwh?.toString().orEmpty(),
                        diasFaturados = reviewed.diasFaturados?.toString().orEmpty(),
                        valorTotal = reviewed.valorTotal?.toString().orEmpty(),
                        bandeiraTarifaria = reviewed.bandeiraTarifaria.orEmpty(),
                        unidadeConsumidora = reviewed.unidadeConsumidora.orEmpty(),
                        vencimento = reviewed.vencimento.orEmpty(),
                        historicoConsumo = reviewed.historicoConsumo,
                    )
                }
                is AppResult.Error -> mutableState.value = mutableState.value.copy(loadingState = UiState.Error(result.error))
            }
        }
    }

    fun updateConcessionaria(value: String) { mutableState.value = mutableState.value.copy(concessionaria = value) }
    fun updateMesReferencia(value: String) { mutableState.value = mutableState.value.copy(mesReferencia = value) }
    fun updateConsumoKwh(value: String) { mutableState.value = mutableState.value.copy(consumoKwh = value) }
    fun updateDiasFaturados(value: String) { mutableState.value = mutableState.value.copy(diasFaturados = value) }
    fun updateValorTotal(value: String) { mutableState.value = mutableState.value.copy(valorTotal = value) }
    fun updateBandeiraTarifaria(value: String) { mutableState.value = mutableState.value.copy(bandeiraTarifaria = value) }
    fun updateUnidadeConsumidora(value: String) { mutableState.value = mutableState.value.copy(unidadeConsumidora = value) }
    fun updateVencimento(value: String) { mutableState.value = mutableState.value.copy(vencimento = value) }

    fun submit() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSubmitting = true, error = null)
            val payload = ReviewedBillData(
                concessionaria = mutableState.value.concessionaria.ifBlank { null },
                mesReferencia = mutableState.value.mesReferencia.ifBlank { null },
                consumoKwh = mutableState.value.consumoKwh.toDoubleOrNull(),
                diasFaturados = mutableState.value.diasFaturados.toIntOrNull(),
                valorTotal = mutableState.value.valorTotal.toDoubleOrNull(),
                bandeiraTarifaria = mutableState.value.bandeiraTarifaria.ifBlank { null },
                unidadeConsumidora = mutableState.value.unidadeConsumidora.ifBlank { null },
                vencimento = mutableState.value.vencimento.ifBlank { null },
                historicoConsumo = mutableState.value.historicoConsumo,
            )
            when (val result = confirmBillUseCase(billId, payload)) {
                is AppResult.Success -> {
                    mutableState.value = mutableState.value.copy(
                        isSubmitting = false,
                        loadingState = UiState.Success(result.data),
                    )
                }
                is AppResult.Error -> mutableState.value = mutableState.value.copy(isSubmitting = false, error = result.error)
            }
        }
    }
}

@Composable
fun ReviewScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnergyScreen(title = "Revisao da extracao", showBack = true, onBack = onNavigateBack) {
        when (val content = state.loadingState) {
            UiState.Loading -> LoadingPane("Preparando revisao", "Carregando documento extraido e campos sinalizados.")
            is UiState.Error -> InlineWarning(text = content.error.message)
            is UiState.Success -> {
                val review = content.data
                state.error?.let { InlineWarning(text = it.message) }
                if (review.structuredData.warnings.isNotEmpty()) {
                    review.structuredData.warnings.forEach { InlineWarning(text = it) }
                }
                AppCard(
                    title = review.bill.mesReferencia.toMonthLabel(),
                    supporting = "Campos em destaque merecem atencao extra quando a confianca da IA for baixa.",
                ) {
                    AppTextField(value = state.concessionaria, onValueChange = viewModel::updateConcessionaria, label = "Concessionaria")
                    AppTextField(value = state.mesReferencia, onValueChange = viewModel::updateMesReferencia, label = "Mes de referencia")
                    AppTextField(value = state.consumoKwh, onValueChange = viewModel::updateConsumoKwh, label = "Consumo (kWh)")
                    AppTextField(value = state.diasFaturados, onValueChange = viewModel::updateDiasFaturados, label = "Dias faturados")
                    AppTextField(value = state.valorTotal, onValueChange = viewModel::updateValorTotal, label = "Valor total")
                    AppTextField(value = state.bandeiraTarifaria, onValueChange = viewModel::updateBandeiraTarifaria, label = "Bandeira tarifaria")
                    AppTextField(value = state.unidadeConsumidora, onValueChange = viewModel::updateUnidadeConsumidora, label = "Unidade consumidora")
                    AppTextField(value = state.vencimento, onValueChange = viewModel::updateVencimento, label = "Vencimento (YYYY-MM-DD)")
                }
                if (review.fieldsForReview.isNotEmpty()) {
                    AppCard(title = "Campos sinalizados para revisao") {
                        review.fieldsForReview.forEach { field ->
                            val score = review.structuredData.confidence[field]
                            androidx.compose.material3.Text(text = "$field • confianca ${score ?: 0.0}")
                        }
                    }
                }
                if (state.historicoConsumo.isNotEmpty()) {
                    AppCard(title = "Historico extraido") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.historicoConsumo.forEach { item ->
                                androidx.compose.material3.Text(text = "${item.referenceMonth.toMonthLabel()} • ${item.consumptionKwh} kWh")
                            }
                        }
                    }
                }
                PrimaryActionButton(
                    text = "Confirmar conta",
                    onClick = viewModel::submit,
                    loading = state.isSubmitting,
                )
            }
            UiState.Empty -> Unit
            UiState.Idle -> Unit
        }
    }
}
