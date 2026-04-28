package br.com.energybillai.core.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MeterReadingOcrProcessorTest {

    @Test
    fun parseRecognizedText_prefersMostPlausibleCandidate() {
        val rawText = """
            LEITURA
            0012
            182345
            kWh
        """.trimIndent()

        val result = MeterReadingOcrProcessor.parseRecognizedText(rawText)

        assertThat(result.detectedValue).isEqualTo(182345L)
        assertThat(result.confidenceScore).isGreaterThan(0.7)
        assertThat(result.candidates.first().value).isEqualTo(182345L)
    }

    @Test
    fun parseRecognizedText_ordersCandidatesByConfidenceAndFiltersNoise() {
        val rawText = """
            visor
            000000
            leitura 182345
            apoio 54321
            182345
        """.trimIndent()

        val result = MeterReadingOcrProcessor.parseRecognizedText(rawText)

        assertThat(result.candidates).isNotEmpty()
        assertThat(result.candidates.first().value).isEqualTo(182345L)
        assertThat(result.candidates.map { it.value }).doesNotContain(0L)
        assertThat(result.candidates.first().confidenceScore)
            .isAtLeast(result.candidates.last().confidenceScore)
    }

    @Test
    fun parseRecognizedText_returnsFriendlyWarningWhenNoCandidateExists() {
        val result = MeterReadingOcrProcessor.parseRecognizedText("visor sem leitura legivel")

        assertThat(result.detectedValue).isNull()
        assertThat(result.warningMessage).contains("digitar")
    }
}
