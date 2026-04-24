package br.com.energybillai.core.ocr

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import br.com.energybillai.core.common.AppError
import br.com.energybillai.core.common.AppResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class MeterReadingOcrResult(
    val detectedValue: Long?,
    val confidenceScore: Double,
    val rawText: String,
    val candidates: List<Long>,
    val warningMessage: String? = null,
)

@Singleton
class MeterReadingOcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun analyze(imageUri: Uri): AppResult<MeterReadingOcrResult> {
        val image = runCatching { InputImage.fromFilePath(context, imageUri) }.getOrElse { throwable ->
            return AppResult.Error(
                AppError(
                    code = "meter_reading_image_error",
                    message = throwable.message ?: "NÃ£o foi possÃ­vel abrir a imagem do medidor.",
                ),
            )
        }

        val recognizedText = runCatching { recognizer.process(image).await().text }.getOrElse { throwable ->
            return AppResult.Error(
                AppError(
                    code = "meter_reading_scan_error",
                    message = throwable.message ?: "NÃ£o foi possÃ­vel analisar a imagem do medidor.",
                ),
            )
        }

        return AppResult.Success(parseRecognizedText(recognizedText))
    }

    companion object {
        @VisibleForTesting
        internal fun parseRecognizedText(rawText: String): MeterReadingOcrResult {
            val candidates = buildCandidateStrings(rawText)
            val scoredCandidates = candidates
                .map { candidate -> candidate to scoreCandidate(candidate, rawText) }
                .sortedWith(
                    compareByDescending<Pair<String, Double>> { it.second }
                        .thenByDescending { it.first.length }
                        .thenByDescending { it.first.toLongOrNull() ?: 0L },
                )

            val bestCandidate = scoredCandidates.firstOrNull()?.first?.toLongOrNull()
            val confidence = scoredCandidates.firstOrNull()?.second ?: 0.0
            val warningMessage = when {
                bestCandidate == null -> "NÃ£o conseguimos identificar o nÃºmero do medidor automaticamente. VocÃª pode digitar a leitura manualmente."
                confidence < 0.55 -> "A leitura automÃ¡tica ficou pouco confiÃ¡vel. Confira os nÃºmeros com atenÃ§Ã£o antes de salvar."
                scoredCandidates.size > 1 -> "Encontramos mais de um nÃºmero possÃ­vel na imagem. Vale revisar o campo antes de confirmar."
                else -> null
            }

            return MeterReadingOcrResult(
                detectedValue = bestCandidate,
                confidenceScore = confidence,
                rawText = rawText,
                candidates = scoredCandidates.mapNotNull { it.first.toLongOrNull() },
                warningMessage = warningMessage,
            )
        }

        private fun buildCandidateStrings(rawText: String): List<String> {
            val directMatches = Regex("""\d{4,10}""")
                .findAll(rawText)
                .map { it.value }
                .toList()

            val lineBasedMatches = rawText
                .lines()
                .map { line -> line.filter(Char::isDigit) }
                .filter { digits -> digits.length in 4..10 }

            return (directMatches + lineBasedMatches)
                .map { it.trimStart('0').ifBlank { "0" } }
                .filter { it.length in 1..10 }
                .distinct()
        }

        private fun scoreCandidate(candidate: String, rawText: String): Double {
            val lengthScore = when (candidate.length) {
                6, 7 -> 0.96
                5, 8 -> 0.88
                4, 9 -> 0.74
                else -> 0.56
            }
            val occurrences = Regex("""\b${Regex.escape(candidate)}\b""")
                .findAll(rawText)
                .count()
            val occurrenceBonus = when {
                occurrences >= 2 -> 0.08
                occurrences == 1 -> 0.04
                else -> 0.0
            }
            val allZeroPenalty = if (candidate.all { it == '0' }) 0.45 else 0.0
            val leadingZerosPenalty = when {
                candidate.startsWith("000") -> 0.12
                candidate.startsWith("00") -> 0.06
                else -> 0.0
            }
            return (lengthScore + occurrenceBonus - allZeroPenalty - leadingZerosPenalty)
                .coerceIn(0.0, 1.0)
        }
    }
}
