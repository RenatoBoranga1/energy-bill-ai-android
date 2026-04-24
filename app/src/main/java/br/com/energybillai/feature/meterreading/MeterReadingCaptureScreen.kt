package br.com.energybillai.feature.meterreading

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.AppError
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.designsystem.AppBrandLockup
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.EmptyStatePane
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.ErrorStatePane
import br.com.energybillai.core.designsystem.HeroCard
import br.com.energybillai.core.designsystem.InlineWarning
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.ocr.MeterReadingOcrProcessor
import br.com.energybillai.core.ocr.MeterReadingOcrResult
import br.com.energybillai.data.local.game.MeterReadingDraft
import br.com.energybillai.data.local.game.MeterReadingDraftStore
import br.com.energybillai.data.local.game.MeterReadingImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MeterReadingCaptureUiState(
    val isProcessing: Boolean = false,
    val completedDraftId: String? = null,
    val error: AppError? = null,
)

@HiltViewModel
class MeterReadingCaptureViewModel @Inject constructor(
    private val imageStore: MeterReadingImageStore,
    private val draftStore: MeterReadingDraftStore,
    private val ocrProcessor: MeterReadingOcrProcessor,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MeterReadingCaptureUiState())
    val state = mutableState.asStateFlow()

    fun processSelectedImage(sourceUri: Uri) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                isProcessing = true,
                error = null,
                completedDraftId = null,
            )

            val persistedImageUri = when (val persisted = imageStore.persistImage(sourceUri)) {
                is AppResult.Success -> persisted.data
                is AppResult.Error -> {
                    mutableState.value = mutableState.value.copy(
                        isProcessing = false,
                        error = persisted.error,
                    )
                    return@launch
                }
            }

            val analyzedResult = when (val scan = ocrProcessor.analyze(persistedImageUri)) {
                is AppResult.Success -> scan.data
                is AppResult.Error -> {
                    MeterReadingOcrResult(
                        detectedValue = null,
                        confidenceScore = 0.0,
                        rawText = "",
                        candidates = emptyList(),
                        warningMessage = "Não conseguimos identificar a leitura automaticamente. Você ainda pode digitar o valor manualmente.",
                    )
                }
            }

            val draft = MeterReadingDraft(
                imageUri = persistedImageUri.toString(),
                extractedValue = analyzedResult.detectedValue,
                confidenceScore = analyzedResult.confidenceScore,
                rawText = analyzedResult.rawText,
                warningMessage = analyzedResult.warningMessage,
            )
            draftStore.save(draft)

            mutableState.value = MeterReadingCaptureUiState(
                isProcessing = false,
                completedDraftId = draft.id,
            )
        }
    }

    fun onCaptureError(message: String) {
        mutableState.value = mutableState.value.copy(
            isProcessing = false,
            error = AppError(
                code = "meter_reading_capture_error",
                message = message,
            ),
        )
    }

    fun consumeNavigation() {
        mutableState.value = mutableState.value.copy(completedDraftId = null)
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }
}

@Composable
fun MeterReadingCaptureScreen(
    onNavigateBack: () -> Unit,
    onOpenReview: (String) -> Unit,
    viewModel: MeterReadingCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    DisposableEffect(lifecycleOwner, cameraController) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.unbind()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted = granted
        if (!granted) {
            viewModel.onCaptureError(
                "O acesso à câmera foi negado. Você ainda pode selecionar uma foto da galeria.",
            )
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.processSelectedImage(uri)
        }
    }

    LaunchedEffect(state.completedDraftId) {
        val draftId = state.completedDraftId ?: return@LaunchedEffect
        onOpenReview(draftId)
        viewModel.consumeNavigation()
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    EnergyScreen(
        title = "Leitura do medidor",
        showBack = true,
        onBack = onNavigateBack,
    ) {
        AppBrandLockup(
            subtitle = "Registre sua leitura semanal para acompanhar o consumo real e alimentar seus desafios de economia.",
        )
        HeroCard(
            eyebrow = "Leitura semanal",
            title = "Fotografe o medidor com calma",
            supporting = "Vamos tentar identificar o número automaticamente, mas você sempre confere e confirma antes de salvar.",
        )

        if (!cameraPermissionGranted) {
            InlineWarning(
                text = "Se preferir, libere a câmera para capturar a leitura agora. Também é possível escolher uma foto da galeria.",
            )
        }

        state.error?.let { error ->
            ErrorStatePane(
                title = "Não foi possível preparar a leitura",
                message = error.message,
                onRetry = viewModel::clearError,
            )
        }

        AppCard(
            title = "Centralize o visor do medidor",
            eyebrow = "Captura",
            supporting = "Evite reflexos, aproxime a câmera e mantenha apenas os números do medidor em destaque.",
        ) {
            if (cameraPermissionGranted) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    factory = { previewContext ->
                        PreviewView(previewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            controller = cameraController
                        }
                    },
                )
            } else {
                EmptyStatePane(
                    title = "Câmera indisponível",
                    message = "Autorize a câmera para capturar a leitura diretamente pelo app.",
                )
            }

            if (state.isProcessing) {
                AppCard(
                    title = "Analisando a foto",
                    supporting = "Estamos preparando a imagem e identificando a leitura para a revisão manual.",
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (cameraPermissionGranted) {
                PrimaryActionButton(
                    text = "Capturar leitura",
                    onClick = {
                        val outputFile = context.createMeterReadingCaptureFile()
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                        cameraController.takePicture(
                            outputOptions,
                            mainExecutor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    viewModel.processSelectedImage(outputFile.toUri())
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    viewModel.onCaptureError(
                                        exception.message ?: "Não foi possível capturar a foto do medidor.",
                                    )
                                }
                            },
                        )
                    },
                    enabled = !state.isProcessing,
                    loading = state.isProcessing,
                )
            } else {
                PrimaryActionButton(
                    text = "Permitir câmera",
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    enabled = !state.isProcessing,
                )
            }

            TextButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !state.isProcessing,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Escolher imagem da galeria")
            }
        }

        AppCard(
            title = "Dicas para acertar na primeira tentativa",
            eyebrow = "Boas práticas",
            supporting = "Uma foto mais limpa aumenta bastante a chance de reconhecer a leitura automaticamente.",
        ) {
            TipRow("Use boa iluminação e evite sombras sobre o visor.")
            TipRow("Posicione o celular de frente para o medidor, sem inclinar.")
            TipRow("Se a leitura automática falhar, você poderá digitar o valor manualmente na próxima tela.")
        }
    }
}

@Composable
private fun TipRow(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.TipsAndUpdates,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Context.createMeterReadingCaptureFile(): File {
    val directory = File(cacheDir, "captured-meter").apply { mkdirs() }
    return File(directory, "meter-${System.currentTimeMillis()}.jpg")
}
