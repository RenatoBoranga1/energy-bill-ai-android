package br.com.energybillai.feature.upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.AppError
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.AppBrandLockup
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.HeroCard
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.designsystem.SectionHeader
import br.com.energybillai.core.network.ApiConfig
import br.com.energybillai.domain.model.UploadPayload
import br.com.energybillai.domain.usecase.ExtractBillUseCase
import br.com.energybillai.domain.usecase.GetCurrentSessionUseCase
import br.com.energybillai.domain.usecase.UploadDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val MAX_UPLOAD_SIZE_BYTES = 15L * 1024L * 1024L

enum class UploadStep {
    Idle,
    Selected,
    Uploading,
    Success,
    Error,
}

enum class UploadSource(
    val label: String,
) {
    Pdf("PDF"),
    GalleryImage("Imagem da galeria"),
    Camera("Foto da câmera"),
}

data class SelectedAsset(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val bytes: ByteArray,
    val source: UploadSource,
)

data class UploadUiState(
    val step: UploadStep = UploadStep.Idle,
    val selectedAsset: SelectedAsset? = null,
    val error: AppError? = null,
    val completedBillId: String? = null,
) {
    val isUploading: Boolean = step == UploadStep.Uploading
}

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val uploadDocumentUseCase: UploadDocumentUseCase,
    private val extractBillUseCase: ExtractBillUseCase,
    private val getCurrentSessionUseCase: GetCurrentSessionUseCase,
    apiConfig: ApiConfig,
) : ViewModel() {
    private val mutableState = MutableStateFlow(UploadUiState())
    val state = mutableState.asStateFlow()
    val apiBaseUrl: String = apiConfig.baseUrl

    fun selectAsset(asset: SelectedAsset) {
        mutableState.value = UploadUiState(
            step = UploadStep.Selected,
            selectedAsset = asset,
        )
    }

    fun rejectSelection(error: AppError) {
        mutableState.value = UploadUiState(
            step = UploadStep.Error,
            error = error,
        )
    }

    fun clearSelection() {
        mutableState.value = UploadUiState()
    }

    fun resetNavigation() {
        mutableState.value = mutableState.value.copy(completedBillId = null)
    }

    fun submitSelectedAsset() {
        val asset = mutableState.value.selectedAsset
        if (asset == null) {
            mutableState.value = UploadUiState(
                step = UploadStep.Error,
                error = AppError(
                    code = "upload_no_file_selected",
                    message = "Selecione um PDF ou imagem antes de enviar.",
                ),
            )
            return
        }
        if (mutableState.value.isUploading) return
        if (getCurrentSessionUseCase()?.tokens?.accessToken.isNullOrBlank()) {
            mutableState.value = mutableState.value.copy(
                step = UploadStep.Error,
                error = AppError(
                    code = "auth_required",
                    message = "Faça login novamente para enviar a conta. Sua sessão não possui token de acesso.",
                    statusCode = 401,
                ),
            )
            return
        }

        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                step = UploadStep.Uploading,
                error = null,
                completedBillId = null,
            )
            val payload = UploadPayload(
                fileName = asset.fileName,
                mimeType = asset.mimeType,
                bytes = asset.bytes,
            )
            when (val upload = uploadDocumentUseCase(payload)) {
                is AppResult.Success -> extractUploadedDocument(upload.data.id, asset)
                is AppResult.Error -> mutableState.value = mutableState.value.copy(
                    step = UploadStep.Error,
                    error = upload.error,
                )
            }
        }
    }

    private suspend fun extractUploadedDocument(documentId: String, asset: SelectedAsset) {
        when (val extracted = extractBillUseCase(documentId)) {
            is AppResult.Success -> mutableState.value = mutableState.value.copy(
                step = UploadStep.Success,
                selectedAsset = asset,
                completedBillId = extracted.data.billId,
                error = null,
            )
            is AppResult.Error -> mutableState.value = mutableState.value.copy(
                step = UploadStep.Error,
                selectedAsset = asset,
                error = extracted.error,
            )
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
    val cameraUriState = remember { mutableStateOf<Uri?>(null) }

    fun handleSelectedUri(uri: Uri?, source: UploadSource) {
        if (uri == null) return
        when (val resolved = uri.resolveUploadAsset(context = context, source = source)) {
            is AppResult.Success -> viewModel.selectAsset(resolved.data)
            is AppResult.Error -> viewModel.rejectSelection(resolved.error)
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handleSelectedUri(uri, UploadSource.Pdf)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        handleSelectedUri(uri, UploadSource.GalleryImage)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            handleSelectedUri(cameraUriState.value, UploadSource.Camera)
        }
    }

    EnergyScreen(title = "Enviar conta") {
        AppBrandLockup(
            subtitle = "Envie PDF, imagem ou foto da conta com revisão assistida antes da confirmação.",
        )
        HeroCard(
            eyebrow = "Novo documento",
            title = "Envie PDF, imagem ou foto da conta",
            supporting = "A leitura automática organiza os campos da fatura e você confirma tudo com tranquilidade antes de salvar.",
        )

        SectionHeader(
            title = "Escolha a origem do arquivo",
            supporting = "Selecione a melhor origem para o documento. O app preserva nome, tamanho e tipo reais do arquivo antes do envio.",
        )
        SourcePickerCard(
            isBusy = state.isUploading,
            onPickPdf = { documentLauncher.launch(arrayOf("application/pdf")) },
            onPickImage = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCapturePhoto = {
                val uri = context.createCaptureUri()
                cameraUriState.value = uri
                cameraLauncher.launch(uri)
            },
        )

        state.selectedAsset?.let { asset ->
            SelectedFileCard(
                asset = asset,
                isUploading = state.isUploading,
                onClear = viewModel::clearSelection,
                onSubmit = viewModel::submitSelectedAsset,
            )
        }

        if (state.step == UploadStep.Uploading) {
            UploadProgressCard()
        }

        val completedBillId = state.completedBillId
        if (state.step == UploadStep.Success && completedBillId != null) {
            UploadSuccessCard(
                onOpenReview = {
                    onOpenReview(completedBillId)
                    viewModel.resetNavigation()
                },
            )
        }

        state.error?.let { error ->
            UploadErrorCard(
                error = error,
                apiBaseUrl = viewModel.apiBaseUrl,
                canRetry = state.selectedAsset != null && !state.isUploading,
                onRetry = viewModel::submitSelectedAsset,
            )
        }
    }
}

@Composable
private fun SourcePickerCard(
    isBusy: Boolean,
    onPickPdf: () -> Unit,
    onPickImage: () -> Unit,
    onCapturePhoto: () -> Unit,
) {
    AppCard(
        title = "Selecionar origem",
        eyebrow = "Entrada",
        supporting = "Use o seletor do Android para PDF ou imagem. O app lê o arquivo com segurança, sem depender de caminho físico.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryActionButton(
                text = "Escolher PDF da conta",
                onClick = onPickPdf,
                enabled = !isBusy,
            )
            PrimaryActionButton(
                text = "Selecionar imagem da galeria",
                onClick = onPickImage,
                enabled = !isBusy,
            )
            PrimaryActionButton(
                text = "Capturar foto da fatura",
                onClick = onCapturePhoto,
                enabled = !isBusy,
            )
        }
    }
}

@Composable
private fun SelectedFileCard(
    asset: SelectedAsset,
    isUploading: Boolean,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
) {
    AppCard(
        title = "Arquivo selecionado",
        eyebrow = "Prévia",
        supporting = "Confira o documento antes de enviar para upload e extração.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileTypeIcon(asset = asset)
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = asset.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${asset.mimeType.toFriendlyType()} - ${asset.sizeBytes.toFileSizeLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = asset.source.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        PrimaryActionButton(
            text = "Enviar e extrair",
            onClick = onSubmit,
            loading = isUploading,
            enabled = !isUploading,
        )
        TextButton(
            onClick = onClear,
            enabled = !isUploading,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(text = "Trocar arquivo")
        }
    }
}

@Composable
private fun FileTypeIcon(asset: SelectedAsset) {
    val imageVector = when {
        asset.mimeType == "application/pdf" -> Icons.Outlined.PictureAsPdf
        asset.mimeType.startsWith("image/") -> Icons.Outlined.Image
        else -> Icons.Outlined.Description
    }
    val containerColor = when {
        asset.mimeType == "application/pdf" -> MaterialTheme.colorScheme.errorContainer
        asset.mimeType.startsWith("image/") -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val iconColor = when {
        asset.mimeType == "application/pdf" -> MaterialTheme.colorScheme.onErrorContainer
        asset.mimeType.startsWith("image/") -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    RoundedIconContainer(
        imageVector = imageVector,
        tint = iconColor,
        containerColor = containerColor,
    )
}

@Composable
private fun RoundedIconContainer(
    imageVector: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    containerColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        modifier = Modifier.size(58.dp),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun UploadProgressCard() {
    AppCard(
        title = "Enviando e extraindo",
        eyebrow = "Processamento",
        supporting = "Estamos enviando o arquivo e solicitando a extração inteligente no backend.",
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Mantenha esta tela aberta até a conclusão.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UploadSuccessCard(
    onOpenReview: () -> Unit,
) {
    AppCard(
        title = "Extração concluída",
        eyebrow = "Sucesso",
        supporting = "Os dados foram interpretados e agora precisam da sua revisão antes de entrar no histórico.",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Documento processado com sucesso.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        PrimaryActionButton(
            text = "Revisar campos extraídos",
            onClick = onOpenReview,
        )
    }
}

@Composable
private fun UploadErrorCard(
    error: AppError,
    apiBaseUrl: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    AppCard(
        title = "Falha na comunicação",
        eyebrow = "Diagnóstico",
        supporting = error.message,
    ) {
        Text(
            text = "API configurada: $apiBaseUrl",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Checklist rápido: backend ligado, comando com --host 0.0.0.0, tablet na mesma rede Wi‑Fi e firewall liberando a porta 8000.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canRetry) {
            PrimaryActionButton(
                text = "Tentar novamente",
                onClick = onRetry,
            )
        }
    }
}

private data class DocumentMetadata(
    val displayName: String?,
    val sizeBytes: Long?,
)

private fun Uri.resolveUploadAsset(
    context: Context,
    source: UploadSource,
): AppResult<SelectedAsset> {
    val resolver = context.contentResolver
    val metadata = resolver.queryOpenableMetadata(this)
    val displayName = metadata.displayName
        ?.takeIf { it.isNotBlank() }
        ?: source.fallbackFileName()
    val mimeType = resolver.getType(this)
        ?: displayName.inferMimeType(source)

    if (!mimeType.isSupportedUploadMimeType()) {
        return AppResult.Error(
            AppError(
                code = "upload_unsupported_type",
                message = "Formato não suportado. Envie um PDF, JPG, JPEG ou PNG.",
            ),
        )
    }

    metadata.sizeBytes?.let { size ->
        if (size > MAX_UPLOAD_SIZE_BYTES) {
            return AppResult.Error(
                AppError(
                    code = "upload_file_too_large",
                    message = "Arquivo muito grande. O limite atual é ${MAX_UPLOAD_SIZE_BYTES.toFileSizeLabel()}.",
                ),
            )
        }
    }

    val bytes = runCatching {
        resolver.openInputStream(this)?.use { input -> input.readBytes() }
    }.getOrNull()

    if (bytes == null) {
        return AppResult.Error(
            AppError(
                code = "upload_file_read_error",
                message = "Não foi possível ler o arquivo selecionado. Tente escolher o PDF novamente.",
            ),
        )
    }
    if (bytes.isEmpty()) {
        return AppResult.Error(
            AppError(
                code = "upload_empty_file",
                message = "O arquivo selecionado está vazio.",
            ),
        )
    }
    if (bytes.size.toLong() > MAX_UPLOAD_SIZE_BYTES) {
        return AppResult.Error(
            AppError(
                code = "upload_file_too_large",
                message = "Arquivo muito grande. O limite atual é ${MAX_UPLOAD_SIZE_BYTES.toFileSizeLabel()}.",
            ),
        )
    }

    return AppResult.Success(
        SelectedAsset(
            fileName = displayName,
            mimeType = mimeType,
            sizeBytes = metadata.sizeBytes?.takeIf { it > 0 } ?: bytes.size.toLong(),
            bytes = bytes,
            source = source,
        ),
    )
}

private fun android.content.ContentResolver.queryOpenableMetadata(uri: Uri): DocumentMetadata {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    return runCatching {
        query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                DocumentMetadata(displayName = null, sizeBytes = null)
            } else {
                val name = cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME)
                val size = cursor.getLongOrNull(OpenableColumns.SIZE)
                DocumentMetadata(displayName = name, sizeBytes = size)
            }
        }
    }.getOrNull() ?: DocumentMetadata(displayName = null, sizeBytes = null)
}

private fun android.database.Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    if (index < 0 || isNull(index)) return null
    return getString(index)
}

private fun android.database.Cursor.getLongOrNull(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    if (index < 0 || isNull(index)) return null
    return runCatching { getLong(index) }.getOrNull()
}

private fun String.isSupportedUploadMimeType(): Boolean {
    return this == "application/pdf" || this == "image/jpeg" || this == "image/png" || startsWith("image/")
}

private fun String.toFriendlyType(): String {
    return when (this) {
        "application/pdf" -> "PDF"
        "image/jpeg" -> "Imagem JPEG"
        "image/png" -> "Imagem PNG"
        else -> substringAfter('/').replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
        }
    }
}

private fun String.inferMimeType(source: UploadSource): String {
    return when {
        source == UploadSource.Pdf -> "application/pdf"
        endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        endsWith(".png", ignoreCase = true) -> "image/png"
        endsWith(".jpg", ignoreCase = true) || endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
        else -> "image/jpeg"
    }
}

private fun UploadSource.fallbackFileName(): String {
    val timestamp = System.currentTimeMillis()
    return when (this) {
        UploadSource.Pdf -> "conta-$timestamp.pdf"
        UploadSource.GalleryImage -> "conta-$timestamp.jpg"
        UploadSource.Camera -> "foto-conta-$timestamp.jpg"
    }
}

private fun Long.toFileSizeLabel(): String {
    val kb = this / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    } else {
        String.format(Locale.getDefault(), "%.1f KB", kb.coerceAtLeast(0.1))
    }
}

private fun Context.createCaptureUri(): Uri {
    val directory = File(cacheDir, "captured").apply { mkdirs() }
    val file = File(directory, "bill-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
}
