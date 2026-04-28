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

data class MeterReadingOcrCandidate(
    val value: Long,
    val confidenceScore: Double,
)

data class MeterReadingOcrResult(
    val detectedValue: Long?,
    val confidenceScore: Double,
    val rawText: String,
    val candidates: List<MeterReadingOcrCandidate>,
    val warningMessage: String? = null,
)

data class MeterReadingRoiHint(
    val normalizedLeft: Float = 0.18f,
    val normalizedTop: Float = 0.28f,
    val normalizedRight: Float = 0.82f,
    val normalizedBottom: Float = 0.62f,
)

@Singleton
class MeterReadingOcrProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun analyze(
        imageUri: Uri,
        roiHint: MeterReadingRoiHint? = null,
    ): AppResult<MeterReadingOcrResult> {
        val image = runCatching { InputImage.fromFilePath(context, imageUri) }.getOrElse { throwable ->
            return AppResult.Error(
                AppError(
                    code = "meter_reading_image_error",
                    message = throwable.message ?: "Nao foi possivel abrir a imagem do medidor.",
                ),
            )
        }

        val recognizedText = runCatching { recognizer.process(image).await().text }.getOrElse { throwable ->
            return AppResult.Error(
                AppError(
                    code = "meter_reading_scan_error",
                    message = throwable.message ?: "Nao foi possivel analisar a imagem do medidor.",
                ),
            )
        }

        return AppResult.Success(parseRecognizedText(recognizedText, roiHint))
    }

    companion object {
        @VisibleForTesting
        internal fun parseRecognizedText(
            rawText: String,
            roiHint: MeterReadingRoiHint? = null,
        ): MeterReadingOcrResult {
            val scoredCandidates = buildCandidateInputs(rawText)
                .mapNotNull { input ->
                    val value = input.normalizedValue.toLongOrNull() ?: return@mapNotNull null
                    MeterReadingOcrCandidate(
                        value = value,
                        confidenceScore = scoreCandidate(
                            candidate = input.normalizedValue,
                            rawText = rawText,
                            isolatedLine = input.isolatedLine,
                            roiHint = roiHint,
                        ),
                    )
                }
                .sortedWith(
                    compareByDescending<MeterReadingOcrCandidate> { it.confidenceScore }
                        .thenByDescending { it.value.toString().length }
                        .thenByDescending { it.value },
                )
                .filter { it.value > 0 }
                .distinctBy { it.value }

            val bestCandidate = scoredCandidates.firstOrNull()
            val warningMessage = when {
                bestCandidate == null ->
                    "Nao conseguimos identificar a leitura automaticamente. Voce ainda pode digitar o valor manualmente."
                bestCandidate.confidenceScore < 0.55 ->
                    "A leitura automatica ficou pouco confiavel. Confira os numeros com calma antes de salvar."
                scoredCandidates.size > 1 ->
                    "Encontramos mais de uma leitura possivel. Vale revisar as sugestoes antes de confirmar."
                else -> null
            }

            return MeterReadingOcrResult(
                detectedValue = bestCandidate?.value,
                confidenceScore = bestCandidate?.confidenceScore ?: 0.0,
                rawText = rawText,
                candidates = scoredCandidates.take(5),
                warningMessage = warningMessage,
            )
        }

        private fun buildCandidateInputs(rawText: String): List<CandidateInput> {
            return rawText
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .flatMap { line ->
                    val directMatches = Regex("""\d{4,10}""")
                        .findAll(line)
                        .map { match ->
                            CandidateInput(
                                normalizedValue = normalizeCandidate(match.value),
                                isolatedLine = line.replace(match.value, "").trim().isBlank(),
                            )
                        }
                        .toList()

                    val compactDigits = line.filter(Char::isDigit)
                    val compactCandidate = compactDigits
                        .takeIf { it.length in 4..10 }
                        ?.let {
                            CandidateInput(
                                normalizedValue = normalizeCandidate(it),
                                isolatedLine = line.filterNot(Char::isDigit).trim().isBlank(),
                            )
                        }

                    directMatches + listOfNotNull(compactCandidate)
                }
                .filter { it.normalizedValue.length in 1..10 }
                .distinctBy { it.normalizedValue }
        }

        private fun normalizeCandidate(rawValue: String): String {
            val trimmed = rawValue.trimStart('0')
            return if (trimmed.isBlank()) "0" else trimmed
        }

        private fun scoreCandidate(
            candidate: String,
            rawText: String,
            isolatedLine: Boolean,
            roiHint: MeterReadingRoiHint?,
        ): Double {
            val lengthScore = when (candidate.length) {
                6, 7 -> 0.96
                5, 8 -> 0.88
                4, 9 -> 0.74
                else -> 0.56
            }
            val occurrences = Regex("""(?<!\d)${Regex.escape(candidate)}(?!\d)""")
                .findAll(rawText)
                .count()
            val occurrenceBonus = when {
                occurrences >= 2 -> 0.08
                occurrences == 1 -> 0.04
                else -> 0.0
            }
            val isolatedLineBonus = if (isolatedLine) 0.06 else 0.0
            val allZeroPenalty = if (candidate.all { it == '0' }) 0.45 else 0.0
            val leadingZerosPenalty = when {
                candidate.startsWith("000") -> 0.12
                candidate.startsWith("00") -> 0.06
                else -> 0.0
            }
            val repeatedDigitsPenalty = when {
                candidate.toSet().size <= 1 -> 0.26
                candidate.toSet().size == 2 && candidate.length >= 6 -> 0.10
                else -> 0.0
            }
            val improbableValuePenalty = when {
                candidate.length <= 4 -> 0.12
                candidate.toLongOrNull()?.let { it < 1000 } == true -> 0.16
                else -> 0.0
            }
            val roiPreparedBonus = if (roiHint != null) 0.02 else 0.0

            return (
                lengthScore +
                    occurrenceBonus +
                    isolatedLineBonus +
                    roiPreparedBonus -
                    allZeroPenalty -
                    leadingZerosPenalty -
                    repeatedDigitsPenalty -
                    improbableValuePenalty
                )
                .coerceIn(0.0, 1.0)
        }

        private data class CandidateInput(
            val normalizedValue: String,
            val isolatedLine: Boolean,
        )
    }
}
