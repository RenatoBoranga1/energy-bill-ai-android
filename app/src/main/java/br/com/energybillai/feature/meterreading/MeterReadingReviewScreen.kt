package br.com.energybillai.feature.meterreading

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import br.com.energybillai.core.ocr.MeterReadingOcrCandidate
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
                    message = "A imagem temporaria nao foi encontrada. Faca uma nova leitura do medidor.",
                ),
            )
        } else {
            MeterReadingReviewUiState(
                isLoading = false,
                draft = draft,
                confirmedValue = draft.extractedValue?.toString()
                    ?: draft.candidates.firstOrNull()?.value?.toString()
                    .orEmpty(),
            )
        }
    }

    fun updateConfirmedValue(value: String) {
        mutableState.value = mutableState.value.copy(
            confirmedValue = value.filter(Char::isDigit),
            error = null,
        )
    }

    fun pickCandidate(value: Long) {
        updateConfirmedValue(value.toString())
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
                    message = "Sua sessao nao esta disponivel. Entre novamente para continuar.",
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
                    message = "Preparando a imagem capturada para sua conferencia final.",
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
                    title = "Leitura nao encontrada",
                    message = state.error?.message ?: "Faca uma nova captura para continuar.",
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
                    onPickCandidate = viewModel::pickCandidate,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeterReadingReviewContent(
    draft: MeterReadingDraft,
    confirmedValue: String,
    note: String,
    isSaving: Boolean,
    error: AppError?,
    onConfirmedValueChange: (String) -> Unit,
    onPickCandidate: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onRetakePhoto: () -> Unit,
    onDismissError: () -> Unit,
) {
    AppBrandLockup(
        subtitle = "Confira a leitura do medidor antes de salvar. Nada e gravado automaticamente sem a sua revisao.",
    )
    HeroCard(
        eyebrow = "Confirmacao",
        title = "Revise a leitura detectada",
        supporting = "Se o numero estiver diferente do visor, ajuste manualmente. Essa confirmacao e a base dos desafios e da economia estimada.",
    )

    AppCard(
        title = "Imagem capturada",
        eyebrow = "Previa",
        supporting = "Use a foto abaixo para comparar o valor do medidor com o campo editavel.",
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
        eyebrow = "Leitura automatica",
        supporting = "O app faz uma sugestao inicial, mas a decisao final sempre e sua.",
    ) {
        MetricChip(
            label = "Sugestao principal",
            value = draft.extractedValue?.toString() ?: "Nao identificada",
            highlighted = draft.extractedValue != null,
            modifier = Modifier.fillMaxWidth(),
        )
        MetricChip(
            label = "Confianca",
            value = draft.confidenceScore.toConfidenceLabel(),
            supporting = draft.confidenceScore.toConfidenceSupporting(),
        )
        if (draft.candidates.isNotEmpty()) {
            Text(
                text = "Sugestoes encontradas",
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                draft.candidates.forEach { candidate ->
                    CandidateChip(
                        candidate = candidate,
                        isSelected = confirmedValue == candidate.value.toString(),
                        onClick = { onPickCandidate(candidate.value) },
                    )
                }
            }
        }
        if (!draft.warningMessage.isNullOrBlank()) {
            InlineWarning(text = draft.warningMessage)
        }
    }

    error?.let { currentError ->
        ErrorStatePane(
            title = "Nao foi possivel salvar a leitura",
            message = currentError.message,
            onRetry = onDismissError,
        )
    }

    AppCard(
        title = "Confirme os dados",
        eyebrow = "Revisao final",
        supporting = "A leitura confirmada sera usada para calcular o consumo semanal, atualizar o desafio e registrar seu progresso.",
    ) {
        AppTextField(
            value = confirmedValue,
            onValueChange = onConfirmedValueChange,
            label = "Leitura confirmada do medidor",
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            supporting = "Digite apenas numeros, exatamente como aparecem no visor.",
        )
        AppTextField(
            value = note,
            onValueChange = onNoteChange,
            label = "Observacao (opcional)",
            singleLine = false,
            supporting = "Use este campo para registrar algo fora do normal, como reflexo, visor danificado ou consumo atipico.",
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
private fun CandidateChip(
    candidate: MeterReadingOcrCandidate,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                    },
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = candidate.value.toString(),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = candidate.confidenceScore.toShortConfidenceLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        subtitle = "Leitura registrada com sucesso. O app ja atualizou seu progresso e sua economia estimada.",
    )
    HeroCard(
        eyebrow = "Tudo certo",
        title = "Leitura semanal salva",
        supporting = result.message,
    )
    AppCard(
        title = "Resumo da leitura",
        eyebrow = "Impacto imediato",
        supporting = "Acompanhe como essa leitura mexeu com o seu desafio e com a sua evolucao no app.",
    ) {
        MetricChip(
            label = "XP ganho",
            value = "${result.gainedXp} XP",
            highlighted = result.gainedXp > 0,
            modifier = Modifier.fillMaxWidth(),
        )
        MetricChip(
            label = "Nivel",
            value = result.userScore.levelName,
            supporting = "Nivel ${result.userScore.currentLevel}",
        )
        result.weeklyConsumption?.let { weeklyConsumption ->
            MetricChip(
                label = "Consumo semanal",
                value = weeklyConsumption.consumptionKwh.toKwhLabel(),
                supporting = "Media de ${weeklyConsumption.averageDailyKwh.toKwhLabel()} por dia",
                modifier = Modifier.fillMaxWidth(),
            )
            MetricChip(
                label = "Custo estimado",
                value = weeklyConsumption.estimatedCostBrl.toCurrencyLabel(),
            )
        }
        result.savingsProjection?.let { projection ->
            MetricChip(
                label = "Economia estimada",
                value = projection.estimatedMonthlySavingsBrl.toCurrencyLabel(),
                supporting = "Cenario atual: ${projection.estimatedMonthlySavingsKwh.toKwhLabel()} de reducao potencial no mes",
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
                    text = "- ${achievement.title}: ${achievement.description}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PrimaryActionButton(
            text = "Voltar aos desafios",
            onClick = onDone,
        )
        TextButton(
            onClick = onCaptureAnother,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Registrar nova leitura")
        }
    }
}

private fun Double?.toConfidenceLabel(): String {
    return when {
        this == null -> "Nao identificada"
        this >= 0.82 -> "Alta"
        this >= 0.60 -> "Boa"
        this > 0.0 -> "Baixa"
        else -> "Nao identificada"
    }
}

private fun Double?.toShortConfidenceLabel(): String {
    return when {
        this == null -> "sem score"
        this >= 0.82 -> "alta"
        this >= 0.60 -> "boa"
        this > 0.0 -> "baixa"
        else -> "baixa"
    }
}

private fun Double?.toConfidenceSupporting(): String {
    return when {
        this == null -> "Preencha a leitura manualmente."
        this >= 0.82 -> "A sugestao parece consistente com a foto capturada."
        this >= 0.60 -> "Vale conferir os digitos antes de salvar."
        this > 0.0 -> "Revisao manual recomendada."
        else -> "Preencha a leitura manualmente."
    }
}
