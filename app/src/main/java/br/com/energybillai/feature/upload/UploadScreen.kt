package br.com.energybillai.feature.upload

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.AppError
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.HeroCard
import br.com.energybillai.core.designsystem.InlineWarning
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.domain.model.UploadPayload
import br.com.energybillai.domain.usecase.ExtractBillUseCase
import br.com.energybillai.domain.usecase.UploadDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UploadUiState(
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val completedBillId: String? = null,
)

private data class SelectedAsset(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val uri: Uri?,
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val uploadDocumentUseCase: UploadDocumentUseCase,
    private val extractBillUseCase: ExtractBillUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(UploadUiState())
    val state = mutableState.asStateFlow()

    fun resetNavigation() {
        mutableState.value = mutableState.value.copy(completedBillId = null)
    }

    fun submit(asset: UploadPayload) {
        viewModelScope.launch {
            mutableState.value = UploadUiState(isSubmitting = true)
            when (val upload = uploadDocumentUseCase(asset)) {
                is AppResult.Success -> {
                    when (val extracted = extractBillUseCase(upload.data.id)) {
                        is AppResult.Success -> mutableState.value = UploadUiState(
                            isSubmitting = false,
                            completedBillId = extracted.data.billId,
                        )
                        is AppResult.Error -> mutableState.value = UploadUiState(isSubmitting = false, error = extracted.error)
                    }
                }
                is AppResult.Error -> mutableState.value = UploadUiState(isSubmitting = false, error = upload.error)
            }
        }
    }
}

@Composable
fun UploadScreen(
    onOpenReview: (String) -> Unit,
    viewModel: UploadViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedAsset by remember { mutableStateOf<SelectedAsset?>(null) }

    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedAsset = uri?.toAsset(context)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selectedAsset = uri?.toAsset(context)
    }
    val cameraUriState = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            selectedAsset = cameraUriState.value?.toAsset(context)
        }
    }

    LaunchedEffect(state.completedBillId) {
        state.completedBillId?.let { billId ->
            onOpenReview(billId)
            viewModel.resetNavigation()
        }
    }

    EnergyScreen(title = "Upload") {
        HeroCard(
            eyebrow = "Novo documento",
            title = "Envie PDF, imagem ou foto da conta",
            supporting = "O backend extrai os campos automaticamente, mas a confirmacao final continua humana para preservar confiabilidade.",
        )
        state.error?.let { InlineWarning(text = it.message) }
        AppCard(title = "Selecionar origem") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryActionButton(text = "Escolher PDF", onClick = { documentLauncher.launch(arrayOf("application/pdf")) })
                PrimaryActionButton(text = "Escolher imagem", onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                PrimaryActionButton(
                    text = "Tirar foto",
                    onClick = {
                        val uri = context.createCaptureUri()
                        cameraUriState.value = uri
                        cameraLauncher.launch(uri)
                    },
                )
            }
        }
        selectedAsset?.let { asset ->
            AppCard(
                title = "Arquivo selecionado",
                supporting = "Revise o nome e envie para iniciar a extracao inteligente.",
            ) {
                androidx.compose.material3.Text(text = asset.fileName)
                androidx.compose.material3.Text(text = asset.mimeType)
                PrimaryActionButton(
                    text = "Enviar e extrair",
                    onClick = {
                        viewModel.submit(
                            UploadPayload(
                                fileName = asset.fileName,
                                mimeType = asset.mimeType,
                                bytes = asset.bytes,
                            ),
                        )
                    },
                    loading = state.isSubmitting,
                )
            }
        }
    }
}

private fun Uri.toAsset(context: Context): SelectedAsset? {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(this) ?: return null
    val fileName = this.lastPathSegment?.substringAfterLast('/') ?: "conta"
    val bytes = resolver.openInputStream(this)?.use { it.readBytes() } ?: return null
    return SelectedAsset(
        fileName = fileName,
        mimeType = mimeType,
        bytes = bytes,
        uri = this,
    )
}

private fun Context.createCaptureUri(): Uri {
    val directory = File(cacheDir, "captured").apply { mkdirs() }
    val file = File(directory, "bill-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
}
