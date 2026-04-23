package br.com.energybillai.domain.model

import java.time.Instant

enum class BillExtractionStatus {
    PENDING_REVIEW,
    CONFIRMED,
    REJECTED,
    FAILED,
}

enum class InsightType {
    trend,
    anomaly,
    seasonality,
    forecast,
    general,
}

enum class ExtractionLogLevel {
    INFO,
    WARNING,
    ERROR,
}

enum class ExtractionLogStage {
    upload,
    text_extraction,
    normalization,
    semantic_parsing,
    validation,
    review,
}

data class EnergyUser(
    val id: String,
    val name: String,
    val email: String,
    val createdAt: String,
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresInSeconds: Int,
    val refreshExpiresInSeconds: Int,
    val issuedAtEpochSeconds: Long,
) {
    private val skewSeconds = 30L

    fun isAccessTokenValid(nowEpochSeconds: Long = Instant.now().epochSecond): Boolean {
        return accessToken.isNotBlank() && issuedAtEpochSeconds + expiresInSeconds > nowEpochSeconds + skewSeconds
    }

    fun canRefresh(nowEpochSeconds: Long = Instant.now().epochSecond): Boolean {
        return refreshToken.isNotBlank() && issuedAtEpochSeconds + refreshExpiresInSeconds > nowEpochSeconds + skewSeconds
    }
}

data class AuthSession(
    val user: EnergyUser,
    val tokens: AuthTokens,
)

data class UploadedDocument(
    val id: String,
    val userId: String,
    val filename: String,
    val mimeType: String,
    val fileSizeBytes: Int,
    val fileType: String,
    val filePath: String,
    val extractedText: String?,
    val createdAt: String,
)

data class HistoricalConsumption(
    val referenceMonth: String,
    val consumptionKwh: Double,
    val billedDays: Int?,
)

data class ReviewedBillData(
    val concessionaria: String? = null,
    val mesReferencia: String? = null,
    val consumoKwh: Double? = null,
    val diasFaturados: Int? = null,
    val valorTotal: Double? = null,
    val bandeiraTarifaria: String? = null,
    val unidadeConsumidora: String? = null,
    val vencimento: String? = null,
    val historicoConsumo: List<HistoricalConsumption> = emptyList(),
)

data class ExtractedBillData(
    val reviewed: ReviewedBillData,
    val confidence: Map<String, Double>,
    val warnings: List<String>,
)

data class BillConfidenceScore(
    val fieldName: String,
    val confidenceScore: Double,
)

data class BillCore(
    val id: String,
    val userId: String,
    val documentId: String,
    val concessionaria: String?,
    val mesReferencia: String?,
    val consumoKwh: Double?,
    val diasFaturados: Int?,
    val valorTotal: Double?,
    val bandeiraTarifaria: String?,
    val unidadeConsumidora: String?,
    val vencimento: String?,
    val extractionStatus: BillExtractionStatus,
    val reviewRequired: Boolean,
    val createdAt: String,
    val consumptionHistory: List<HistoricalConsumption> = emptyList(),
    val confidenceScores: List<BillConfidenceScore> = emptyList(),
)

data class ExtractionLog(
    val id: String,
    val documentId: String,
    val billId: String?,
    val stage: ExtractionLogStage,
    val level: ExtractionLogLevel,
    val message: String,
    val sourceComponent: String?,
    val createdAt: String,
)

data class BillReview(
    val billId: String,
    val document: UploadedDocument,
    val extractionStatus: BillExtractionStatus,
    val reviewRequired: Boolean,
    val structuredData: ExtractedBillData,
    val fieldsForReview: List<String>,
    val bill: BillCore,
    val logs: List<ExtractionLog>,
)

data class BillSummary(
    val billId: String,
    val documentId: String,
    val referenceMonth: String?,
    val provider: String?,
    val consumptionKwh: Double?,
    val totalValue: Double?,
    val extractionStatus: BillExtractionStatus,
    val reviewRequired: Boolean,
)

data class Insight(
    val id: String,
    val billId: String,
    val type: InsightType,
    val message: String,
    val createdAt: String,
)

data class AnalyticsSeriesPoint(
    val referenceMonth: String,
    val consumptionKwh: Double,
    val billedDays: Int?,
    val avgDailyKwh: Double?,
    val source: String,
    val confirmedSource: Boolean,
)

data class MonthVariation(
    val currentMonth: String,
    val previousMonth: String,
    val variationPct: Double,
)

data class ConsumptionExtreme(
    val referenceMonth: String,
    val consumptionKwh: Double,
)

data class ConsumptionAnomaly(
    val referenceMonth: String,
    val consumptionKwh: Double,
    val deviationPct: Double,
    val reason: String,
)

data class BillAnalytics(
    val billId: String,
    val referenceMonth: String?,
    val historyPointsUsed: Int,
    val averageDailyKwh: Double?,
    val averageMonthlyKwh: Double?,
    val latestMonthOverMonthVariationPct: Double?,
    val monthOverMonthVariations: List<MonthVariation>,
    val highestConsumption: ConsumptionExtreme?,
    val lowestConsumption: ConsumptionExtreme?,
    val trendDirection: String,
    val trendSummary: String,
    val seasonalityDetected: Boolean,
    val seasonalitySummary: String,
    val anomalies: List<ConsumptionAnomaly>,
    val insights: List<Insight>,
    val series: List<AnalyticsSeriesPoint>,
)

data class ForecastPoint(
    val id: String,
    val billId: String,
    val mesReferencia: String,
    val predictedKwh: Double,
    val lowerBoundKwh: Double,
    val upperBoundKwh: Double,
    val estimatedValueBrl: Double?,
    val lowerBoundValueBrl: Double?,
    val upperBoundValueBrl: Double?,
    val modelUsed: String,
    val createdAt: String,
)

data class BillForecast(
    val billId: String,
    val referenceMonth: String?,
    val modelUsed: String,
    val horizonMonths: Int,
    val historyPointsUsed: Int,
    val explanation: String,
    val referenceTariffBrlPerKwh: Double?,
    val generatedForecasts: List<ForecastPoint>,
    val insights: List<Insight>,
)

data class UploadPayload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)
