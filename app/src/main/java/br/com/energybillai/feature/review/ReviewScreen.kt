package br.com.energybillai.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import br.com.energybillai.core.designsystem.AppBrandLockup
import br.com.energybillai.core.designsystem.AppTextField
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.HeroCard
import br.com.energybillai.core.designsystem.InlineWarning
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.SectionHeader
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.ui.toMonthLabel
import br.com.energybillai.domain.model.BillExtractionStatus
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
import kotlin.math.roundToInt

data class ReviewFormState(
    val billId: String = "",
    val isSubmitting: Boolean = false,
    val loadingState: UiState<BillReview> = UiState.Loading,
    val error: AppError? = null,
    val successMessage: String? = null,
    val confirmationCompletedAt: Long? = null,
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
            mutableState.value = mutableState.value.copy(
                loadingState = UiState.Loading,
                error = null,
                successMessage = null,
                confirmationCompletedAt = null,
            )
            when (val result = getBillUseCase(billId)) {
                is AppResult.Success -> {
                    mutableState.value = mutableState.value.withReview(result.data)
                }

                is AppResult.Error -> mutableState.value = mutableState.value.copy(loadingState = UiState.Error(result.error))
            }
        }
    }

    fun updateConcessionaria(value: String) {
        mutableState.value = mutableState.value.copy(concessionaria = value)
    }

    fun updateMesReferencia(value: String) {
        mutableState.value = mutableState.value.copy(mesReferencia = value)
    }

    fun updateConsumoKwh(value: String) {
        mutableState.value = mutableState.value.copy(consumoKwh = value)
    }

    fun updateDiasFaturados(value: String) {
        mutableState.value = mutableState.value.copy(diasFaturados = value)
    }

    fun updateValorTotal(value: String) {
        mutableState.value = mutableState.value.copy(valorTotal = value)
    }

    fun updateBandeiraTarifaria(value: String) {
        mutableState.value = mutableState.value.copy(bandeiraTarifaria = value)
    }

    fun updateUnidadeConsumidora(value: String) {
        mutableState.value = mutableState.value.copy(unidadeConsumidora = value)
    }

    fun updateVencimento(value: String) {
        mutableState.value = mutableState.value.copy(vencimento = value)
    }

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
                    mutableState.value = mutableState.value
                        .withReview(result.data)
                        .copy(
                            isSubmitting = false,
                            error = null,
                            successMessage = "Conta confirmada com sucesso. Abrindo os detalhes atualizados.",
                            confirmationCompletedAt = System.currentTimeMillis(),
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
    onOpenConfirmedBill: (String) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.confirmationCompletedAt) {
        if (state.confirmationCompletedAt != null) {
            onOpenConfirmedBill(state.billId)
        }
    }

    EnergyScreen(title = "Revisão da extração", showBack = true, onBack = onNavigateBack) {
        AppBrandLockup(
            subtitle = "Confira os campos extraídos e aprove somente o que fizer sentido para o seu histórico.",
        )
        when (val content = state.loadingState) {
            UiState.Loading -> LoadingPane("Preparando revisão", "Carregando documento extraído e campos sinalizados.")
            is UiState.Error -> InlineWarning(text = content.error.message)
            is UiState.Success -> {
                val review = content.data
                val alreadyConfirmed = review.extractionStatus == BillExtractionStatus.CONFIRMED && !review.reviewRequired

                HeroCard(
                    eyebrow = if (alreadyConfirmed) "Conta consolidada" else "Conferência recomendada",
                    title = review.bill.mesReferencia.toMonthLabel(),
                    supporting = if (alreadyConfirmed) {
                        "Os dados abaixo já foram confirmados e estão prontos para análises e projeções."
                    } else {
                        "Revise os campos com baixa confiança, corrija o que for preciso e confirme quando estiver seguro."
                    },
                )

                state.error?.let { InlineWarning(text = it.message) }
                state.successMessage?.let {
                    AppCard(
                        title = "Conta confirmada",
                        supporting = it,
                    ) {}
                }
                if (review.structuredData.warnings.isNotEmpty()) {
                    review.structuredData.warnings.forEach { warning ->
                        InlineWarning(text = warning.toFriendlyReviewWarning())
                    }
                }
                if (alreadyConfirmed) {
                    AppCard(
                        title = "Revisão concluída",
                        supporting = "Esta conta já foi confirmada e está pronta para análises, projeções e histórico.",
                    ) {}
                }
                SectionHeader(
                    title = "Campos principais",
                    supporting = "Os apoios abaixo mostram quais campos ainda merecem revisão manual antes da confirmação.",
                )
                AppCard(
                    title = review.bill.mesReferencia.toMonthLabel(),
                    eyebrow = "Dados extraídos",
                    supporting = if (alreadyConfirmed) {
                        "Os dados abaixo foram consolidados na confirmação manual."
                    } else {
                        "Campos com baixa confiança continuam editáveis antes da confirmação final."
                    },
                ) {
                    AppTextField(
                        value = state.concessionaria,
                        onValueChange = viewModel::updateConcessionaria,
                        label = "Concessionária",
                        supporting = review.fieldSupport("concessionaria"),
                    )
                    AppTextField(
                        value = state.mesReferencia,
                        onValueChange = viewModel::updateMesReferencia,
                        label = "Mês de referência",
                        supporting = review.fieldSupport("mes_referencia"),
                    )
                    AppTextField(
                        value = state.consumoKwh,
                        onValueChange = viewModel::updateConsumoKwh,
                        label = "Consumo (kWh)",
                        supporting = review.fieldSupport("consumo_kwh"),
                    )
                    AppTextField(
                        value = state.diasFaturados,
                        onValueChange = viewModel::updateDiasFaturados,
                        label = "Dias faturados",
                        supporting = review.fieldSupport("dias_faturados"),
                    )
                    AppTextField(
                        value = state.valorTotal,
                        onValueChange = viewModel::updateValorTotal,
                        label = "Valor total",
                        supporting = review.fieldSupport("valor_total"),
                    )
                    AppTextField(
                        value = state.bandeiraTarifaria,
                        onValueChange = viewModel::updateBandeiraTarifaria,
                        label = "Bandeira tarifária",
                        supporting = review.fieldSupport("bandeira_tarifaria"),
                    )
                    AppTextField(
                        value = state.unidadeConsumidora,
                        onValueChange = viewModel::updateUnidadeConsumidora,
                        label = "Unidade consumidora",
                        supporting = review.fieldSupport("unidade_consumidora"),
                    )
                    AppTextField(
                        value = state.vencimento,
                        onValueChange = viewModel::updateVencimento,
                        label = "Vencimento (AAAA-MM-DD)",
                        supporting = review.fieldSupport("vencimento"),
                    )
                }
                if (review.fieldsForReview.isNotEmpty()) {
                    AppCard(title = "Campos que merecem revisão", eyebrow = "Atenção") {
                        review.fieldsForReview.forEach { field ->
                            androidx.compose.material3.Text(
                                text = "${field.toFriendlyFieldLabel()} • confiança ${review.structuredData.confidence[field].toConfidenceLabel()}",
                            )
                        }
                    }
                }
                if (state.historicoConsumo.isNotEmpty()) {
                    AppCard(
                        title = "Histórico extraído",
                        eyebrow = "Leitura da conta",
                        supporting = "Cada linha representa o consumo faturado e os dias entre leituras do respectivo mês.",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.historicoConsumo.forEach { item ->
                                androidx.compose.material3.Text(
                                    text = "${item.referenceMonth.toMonthLabel()} - ${item.consumptionKwh} kWh em ${item.billedDays ?: 0} dias",
                                )
                            }
                        }
                    }
                }
                PrimaryActionButton(
                    text = if (alreadyConfirmed) "Abrir detalhes da conta" else "Confirmar conta",
                    onClick = {
                        if (alreadyConfirmed) {
                            onOpenConfirmedBill(review.billId)
                        } else {
                            viewModel.submit()
                        }
                    },
                    loading = state.isSubmitting,
                )
            }

            UiState.Empty -> Unit
            UiState.Idle -> Unit
        }
    }
}

private fun ReviewFormState.withReview(review: BillReview): ReviewFormState {
    val reviewed = review.structuredData.reviewed
    return copy(
        loadingState = UiState.Success(review),
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

private fun BillReview.fieldSupport(fieldName: String): String? {
    if (fieldName !in fieldsForReview) return null
    return "Revisão recomendada. Confiança estimada: ${structuredData.confidence[fieldName].toConfidenceLabel()}."
}

private fun String.toFriendlyFieldLabel(): String {
    return when (this) {
        "concessionaria" -> "Concessionária"
        "mes_referencia" -> "Mês de referência"
        "consumo_kwh" -> "Consumo em kWh"
        "dias_faturados" -> "Dias faturados"
        "valor_total" -> "Valor total"
        "bandeira_tarifaria" -> "Bandeira tarifária"
        "unidade_consumidora" -> "Unidade consumidora"
        "vencimento" -> "Vencimento"
        "historico_consumo" -> "Histórico de consumo"
        else -> replace("_", " ")
    }
}

private fun Double?.toConfidenceLabel(): String {
    if (this == null) return "0%"
    return "${(this * 100).roundToInt()}%"
}

private fun String.toFriendlyReviewWarning(): String {
    return when {
        startsWith("Confira ") -> this
        contains("Nao foi possivel extrair texto legivel") -> "Não conseguimos ler bem o documento. Se necessário, revise os campos manualmente."
        else -> this
    }
}
