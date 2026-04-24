package br.com.energybillai.feature.meterreading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.AppError
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.designsystem.AppBrandLockup
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.AppTextField
import br.com.energybillai.core.designsystem.EmptyStatePane
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.ErrorStatePane
import br.com.energybillai.core.designsystem.HeroCard
import br.com.energybillai.core.designsystem.InlineWarning
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.MetricChip
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.ui.toCurrencyLabel
import br.com.energybillai.core.ui.toKwhLabel
import br.com.energybillai.data.local.game.MeterReadingDraft
import br.com.energybillai.data.local.game.MeterReadingDraftStore
import br.com.energybillai.domain.game.model.GameEngineResult
import br.com.energybillai.domain.game.usecase.ProcessMeterReadingUseCase
import br.com.energybillai.domain.usecase.GetCurrentSessionUseCase
import br.com.energybillai.domain.usecase.ObserveHistoryUseCase
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class MeterReadingReviewUiState(
    val isLoading: Boolean = true,
    val draft: MeterReadingDraft? = null,
    val confirmedValue: String = "",
    val note: String = "",
    val isSaving: Boolean = false,
    val result: GameEngineResult? = null,
    val error: AppError? = null,
)

@HiltViewModel
class MeterReadingReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val draftStore: MeterReadingDraftStore,
    private val processMeterReadingUseCase: ProcessMeterReadingUseCase,
    private val observeHistoryUseCase: ObserveHistoryUseCase,
    private val getCurrentSessionUseCase: GetCurrentSessionUseCase,
) : ViewModel() {
    private val draftId: String? = savedStateHandle["draftId"]

    private val mutableState = MutableStateFlow(MeterReadingReviewUiState())
    val state = mutableState.asStateFlow()

    init {
        loadDraft()
    }

    private fun loadDraft() {
        val draft = draftId?.let(draftStore::get)
        mutableState.value = if (draft == null) {
            MeterReadingReviewUiState(
                isLoading = false,
                error = AppError(
                    code = "meter_reading_draft_missing",
                    message = "A imagem temporária não foi encontrada. Faça uma nova leitura do medidor.",
                ),
            )
        } else {
            MeterReadingReviewUiState(
                isLoading = false,
                draft = draft,
                confirmedValue = draft.extractedValue?.toString().orEmpty(),
            )
        }
    }

    fun updateConfirmedValue(value: String) {
        mutableState.value = mutableState.value.copy(
            confirmedValue = value.filter(Char::isDigit),
            error = null,
        )
    }

    fun updateNote(value: String) {
        mutableState.value = mutableState.value.copy(note = value, error = null)
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    fun discardDraft() {
        draftId?.let(draftStore::remove)
    }

    fun confirmReading() {
        val currentState = mutableState.value
        val draft = currentState.draft ?: return
        val confirmedValue = currentState.confirmedValue.toLongOrNull()
        if (confirmedValue == null) {
            mutableState.value = currentState.copy(
                error = AppError(
                    code = "meter_reading_missing_value",
                    message = "Digite a leitura confirmada do medidor antes de salvar.",
                ),
            )
            return
        }

        val session = getCurrentSessionUseCase()
        if (session == null) {
            mutableState.value = currentState.copy(
                error = AppError(
                    code = "meter_reading_auth_required",
                    message = "Sua sessão não está disponível. Entre novamente para continuar.",
                ),
            )
            return
        }

        viewModelScope.launch {
            mutableState.value = currentState.copy(
                isSaving = true,
                error = null,
            )
            val localHistory = observeHistoryUseCase(session.user.id).first()
            when (
                val result = processMeterReadingUseCase(
                    userId = session.user.id,
                    readingDate = LocalDate.now(),
                    confirmedValue = confirmedValue,
                    imageUri = draft.imageUri,
                    extractedOcrValue = draft.extractedValue,
                    confidenceScore = draft.confidenceScore,
                    observation = currentState.note.takeIf { it.isNotBlank() },
                    history = localHistory,
                    forecast = null,
                )
            ) {
                is AppResult.Success -> {
                    draftId?.let(draftStore::remove)
                    mutableState.value = currentState.copy(
                        isSaving = false,
                        result = result.data,
                        error = null,
                    )
                }

                is AppResult.Error -> {
                    mutableState.value = currentState.copy(
                        isSaving = false,
                        error = result.error,
                    )
                }
            }
        }
    }
}

@Composable
fun MeterReadingReviewScreen(
    onNavigateBack: () -> Unit,
    onRetakePhoto: () -> Unit,
    onDone: () -> Unit,
    viewModel: MeterReadingReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val result = state.result
    val draft = state.draft

    EnergyScreen(
        title = "Revisar leitura",
        showBack = true,
        onBack = onNavigateBack,
    ) {
        when {
            state.isLoading -> {
                LoadingPane(
                    title = "Carregando leitura",
                    message = "Preparando a imagem capturada para sua conferência final.",
                )
            }

            result != null -> {
                MeterReadingSavedContent(
                    result = result,
                    onCaptureAnother = onRetakePhoto,
                    onDone = onDone,
                )
            }

            draft == null -> {
                EmptyStatePane(
                    title = "Leitura não encontrada",
                    message = state.error?.message ?: "Faça uma nova captura para continuar.",
                )
            }

            else -> {
                MeterReadingReviewContent(
                    draft = draft,
                    confirmedValue = state.confirmedValue,
                    note = state.note,
                    isSaving = state.isSaving,
                    error = state.error,
                    onConfirmedValueChange = viewModel::updateConfirmedValue,
                    onNoteChange = viewModel::updateNote,
                    onConfirm = viewModel::confirmReading,
                    onRetakePhoto = {
                        viewModel.discardDraft()
                        onRetakePhoto()
                    },
                    onDismissError = viewModel::clearError,
                )
            }
        }
    }
}

@Composable
private fun MeterReadingReviewContent(
    draft: MeterReadingDraft,
    confirmedValue: String,
    note: String,
    isSaving: Boolean,
    error: AppError?,
    onConfirmedValueChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onRetakePhoto: () -> Unit,
    onDismissError: () -> Unit,
) {
    AppBrandLockup(
        subtitle = "Confira a leitura do medidor antes de salvar. Nada é gravado automaticamente sem a sua revisão.",
    )
    HeroCard(
        eyebrow = "Confirmação",
        title = "Revise a leitura detectada",
        supporting = "Se o número estiver diferente do visor, ajuste manualmente. Essa confirmação é a base dos desafios e da economia estimada.",
    )

    AppCard(
        title = "Imagem capturada",
        eyebrow = "Prévia",
        supporting = "Use a foto abaixo para comparar o valor do medidor com o campo editável.",
    ) {
        AsyncImage(
            model = draft.imageUri,
            contentDescription = "Foto do medidor",
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        )
    }

    AppCard(
        title = "Leitura detectada",
        eyebrow = "Leitura automática",
        supporting = "O app faz uma sugestão inicial, mas a decisão final sempre é sua.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricChip(
                label = "Sugestão",
                value = draft.extractedValue?.toString() ?: "Não identificada",
                highlighted = draft.extractedValue != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MetricChip(
            label = "Confiança",
            value = draft.confidenceScore.toConfidenceLabel(),
            supporting = draft.confidenceScore.toConfidenceSupporting(),
        )
        if (!draft.warningMessage.isNullOrBlank()) {
            InlineWarning(text = draft.warningMessage)
        }
    }

    error?.let { currentError ->
        ErrorStatePane(
            title = "Não foi possível salvar a leitura",
            message = currentError.message,
            onRetry = onDismissError,
        )
    }

    AppCard(
        title = "Confirme os dados",
        eyebrow = "Revisão final",
        supporting = "A leitura confirmada será usada para calcular o consumo semanal, atualizar o desafio e registrar seu progresso.",
    ) {
        AppTextField(
            value = confirmedValue,
            onValueChange = onConfirmedValueChange,
            label = "Leitura confirmada do medidor",
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            supporting = "Digite apenas números, exatamente como aparecem no visor.",
        )
        AppTextField(
            value = note,
            onValueChange = onNoteChange,
            label = "Observação (opcional)",
            singleLine = false,
            supporting = "Use este campo para registrar algo fora do normal, como reflexo, visor danificado ou consumo atípico.",
        )
        PrimaryActionButton(
            text = "Salvar leitura semanal",
            onClick = onConfirm,
            loading = isSaving,
            enabled = !isSaving,
        )
        TextButton(
            onClick = onRetakePhoto,
            enabled = !isSaving,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Refazer foto")
        }
    }
}

@Composable
private fun MeterReadingSavedContent(
    result: GameEngineResult,
    onCaptureAnother: () -> Unit,
    onDone: () -> Unit,
) {
    AppBrandLockup(
        subtitle = "Leitura registrada com sucesso. O app já atualizou seu progresso de economia e seu histórico semanal.",
    )
    HeroCard(
        eyebrow = "Tudo certo",
        title = "Leitura semanal salva",
        supporting = result.message,
    )
    AppCard(
        title = "Resumo da leitura",
        eyebrow = "Impacto imediato",
        supporting = "Acompanhe como essa leitura mexeu com o seu desafio e com a sua evolução no app.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricChip(
                label = "XP ganho",
                value = "${result.gainedXp} XP",
                highlighted = result.gainedXp > 0,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MetricChip(
            label = "Nível",
            value = result.userScore.levelName,
            supporting = "Nível ${result.userScore.currentLevel}",
        )
        result.weeklyConsumption?.let { weeklyConsumption ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricChip(
                    label = "Consumo semanal",
                    value = weeklyConsumption.consumptionKwh.toKwhLabel(),
                    supporting = "Média de ${weeklyConsumption.averageDailyKwh.toKwhLabel()} por dia",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            MetricChip(
                label = "Custo estimado",
                value = weeklyConsumption.estimatedCostBrl.toCurrencyLabel(),
            )
        }
        result.savingsProjection?.let { projection ->
            MetricChip(
                label = "Economia estimada",
                value = projection.estimatedMonthlySavingsBrl.toCurrencyLabel(),
                supporting = "Cenário atual: ${projection.estimatedMonthlySavingsKwh.toKwhLabel()} de redução potencial no mês",
            )
        }
        result.activeChallenge?.let { challenge ->
            MetricChip(
                label = "Desafio ativo",
                value = "${challenge.progressPercent.toInt()}%",
                supporting = challenge.title,
                highlighted = challenge.progressPercent >= 50.0,
            )
        }
        if (result.unlockedAchievements.isNotEmpty()) {
            Text(
                text = "Conquistas desbloqueadas",
                style = MaterialTheme.typography.titleSmall,
            )
            result.unlockedAchievements.forEach { achievement ->
                Text(
                    text = "• ${achievement.title}: ${achievement.description}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    PrimaryActionButton(
        text = "Registrar outra leitura",
        onClick = onCaptureAnother,
    )
    TextButton(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Voltar ao app")
    }
}

private fun Double?.toConfidenceLabel(): String {
    return when {
        this == null -> "Manual"
        this >= 0.85 -> "Alta"
        this >= 0.60 -> "Média"
        this > 0.0 -> "Baixa"
        else -> "Não identificada"
    }
}

private fun Double?.toConfidenceSupporting(): String {
    return when {
        this == null -> "Sem leitura automática"
        this >= 0.85 -> "Os números parecem bem nítidos"
        this >= 0.60 -> "Vale conferir antes de salvar"
        this > 0.0 -> "Revisão manual recomendada"
        else -> "Digite a leitura manualmente"
    }
}
