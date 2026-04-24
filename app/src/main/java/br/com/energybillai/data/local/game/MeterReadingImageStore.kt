package br.com.energybillai.data.local.game

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import br.com.energybillai.core.common.AppError
import br.com.energybillai.core.common.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeterReadingImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun persistImage(sourceUri: Uri): AppResult<Uri> {
        val targetDirectory = File(context.filesDir, "meter-readings").apply { mkdirs() }
        val extension = resolveExtension(sourceUri)
        val targetFile = File(targetDirectory, "meter-reading-${System.currentTimeMillis()}.$extension")

        return runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Falha ao abrir a imagem selecionada.")
            cleanupIfTemporaryFile(sourceUri)
            targetFile.toUri()
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = {
                AppResult.Error(
                    AppError(
                        code = "meter_reading_persist_image_error",
                        message = "NÃ£o foi possÃ­vel preparar a imagem da leitura. Tente novamente com outra foto.",
                    ),
                )
            },
        )
    }

    private fun resolveExtension(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        return when {
            mimeType.contains("png", ignoreCase = true) -> "png"
            else -> "jpg"
        }
    }

    private fun cleanupIfTemporaryFile(sourceUri: Uri) {
        if (sourceUri.scheme != "file") return
        val sourcePath = sourceUri.path ?: return
        val cachePath = context.cacheDir.absolutePath
        if (sourcePath.startsWith(cachePath)) {
            runCatching { File(sourcePath).delete() }
        }
    }
}
