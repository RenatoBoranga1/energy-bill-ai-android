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
    }

    @Test
    fun parseRecognizedText_returnsFriendlyWarningWhenNoCandidateExists() {
        val result = MeterReadingOcrProcessor.parseRecognizedText("visor sem leitura legível")

        assertThat(result.detectedValue).isNull()
        assertThat(result.warningMessage).contains("digitar")
    }
}
