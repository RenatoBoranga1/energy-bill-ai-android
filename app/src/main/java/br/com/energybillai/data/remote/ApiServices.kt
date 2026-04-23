package br.com.energybillai.data.remote

import okhttp3.MultipartBody
import retrofit2.http.DELETE
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

data class ErrorDetailDto(
    val code: String,
    val message: String,
)

data class ErrorResponseDto(
    val error: ErrorDetailDto,
)

data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val createdAt: String,
)

data class TokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresInSeconds: Int,
    val refreshExpiresInSeconds: Int,
    val user: UserDto,
)

data class AuthLoginRequestDto(
    val email: String,
    val password: String,
)

data class AuthRegisterRequestDto(
    val name: String,
    val email: String,
    val password: String,
)

data class AuthRefreshRequestDto(
    val refreshToken: String,
)

data class AuthLogoutRequestDto(
    val refreshToken: String,
)

data class UploadedDocumentDto(
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

data class HistoricalConsumptionDto(
    val mesReferencia: String,
    val consumoKwh: Double,
    val diasFaturados: Int?,
)

data class ReviewedBillDataDto(
    val concessionaria: String? = null,
    val mesReferencia: String? = null,
    val consumoKwh: Double? = null,
    val diasFaturados: Int? = null,
    val valorTotal: Double? = null,
    val bandeiraTarifaria: String? = null,
    val unidadeConsumidora: String? = null,
    val vencimento: String? = null,
    val historicoConsumo: List<HistoricalConsumptionDto> = emptyList(),
)

data class ExtractedBillDataDto(
    val concessionaria: String? = null,
    val mesReferencia: String? = null,
    val consumoKwh: Double? = null,
    val diasFaturados: Int? = null,
    val valorTotal: Double? = null,
    val bandeiraTarifaria: String? = null,
    val unidadeConsumidora: String? = null,
    val vencimento: String? = null,
    val historicoConsumo: List<HistoricalConsumptionDto> = emptyList(),
    val confidence: Map<String, Double> = emptyMap(),
    val warnings: List<String> = emptyList(),
)

data class ExtractionConfidenceDto(
    val id: String,
    val billId: String,
    val fieldName: String,
    val confidenceScore: Double,
)

data class UtilityBillDetailDto(
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
    val extractionStatus: String,
    val reviewRequired: Boolean,
    val createdAt: String,
    val consumptionHistory: List<HistoricalConsumptionDto> = emptyList(),
    val confidenceScores: List<ExtractionConfidenceDto> = emptyList(),
)

data class ExtractionLogDto(
    val id: String,
    val documentId: String,
    val billId: String?,
    val stage: String,
    val level: String,
    val message: String,
    val sourceComponent: String?,
    val createdAt: String,
)

data class BillReviewDto(
    val billId: String,
    val document: UploadedDocumentDto,
    val extractionStatus: String,
    val reviewRequired: Boolean,
    val structuredData: ExtractedBillDataDto,
    val fieldsForReview: List<String>,
    val bill: UtilityBillDetailDto,
    val logs: List<ExtractionLogDto>,
)

data class UserBillHistoryEntryDto(
    val billId: String,
    val documentId: String,
    val mesReferencia: String?,
    val concessionaria: String?,
    val consumoKwh: Double?,
    val valorTotal: Double?,
    val extractionStatus: String,
    val reviewRequired: Boolean,
)

data class UserBillHistoryResponseDto(
    val userId: String,
    val bills: List<UserBillHistoryEntryDto>,
)

data class InsightDto(
    val id: String,
    val billId: String,
    val insightType: String,
    val message: String,
    val createdAt: String,
)

data class MonthVariationDto(
    val currentMonth: String,
    val previousMonth: String,
    val variationPct: Double,
)

data class ConsumptionExtremeDto(
    val mesReferencia: String,
    val consumoKwh: Double,
)

data class ConsumptionAnomalyDto(
    val mesReferencia: String,
    val consumoKwh: Double,
    val deviationPct: Double,
    val reason: String,
)

data class ConsumptionSeriesPointDto(
    val mesReferencia: String,
    val consumoKwh: Double,
    val diasFaturados: Int?,
    val avgDailyKwh: Double?,
    val source: String,
    val confirmedSource: Boolean,
)

data class BillAnalyticsDto(
    val billId: String,
    val referenceMonth: String?,
    val historyPointsUsed: Int,
    val averageDailyKwh: Double?,
    val averageMonthlyKwh: Double?,
    val latestMonthOverMonthVariationPct: Double?,
    val monthOverMonthVariations: List<MonthVariationDto>,
    val highestConsumption: ConsumptionExtremeDto?,
    val lowestConsumption: ConsumptionExtremeDto?,
    val trendDirection: String,
    val trendSummary: String,
    val seasonalityDetected: Boolean,
    val seasonalitySummary: String,
    val anomalies: List<ConsumptionAnomalyDto>,
    val insights: List<InsightDto>,
    val series: List<ConsumptionSeriesPointDto>,
)

data class ForecastPointDto(
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

data class BillForecastDto(
    val billId: String,
    val referenceMonth: String?,
    val modelUsed: String,
    val horizonMonths: Int,
    val historyPointsUsed: Int,
    val explanation: String,
    val referenceTariffBrlPerKwh: Double?,
    val generatedForecasts: List<ForecastPointDto>,
    val insights: List<InsightDto>,
)

data class ExtractBillRequestDto(
    val documentId: String,
)

data class ConfirmBillRequestDto(
    val data: ReviewedBillDataDto,
)

interface AuthService {
    @POST("api/v1/auth/register")
    suspend fun register(@Body payload: AuthRegisterRequestDto): TokenResponseDto

    @POST("api/v1/auth/login")
    suspend fun login(@Body payload: AuthLoginRequestDto): TokenResponseDto

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body payload: AuthRefreshRequestDto): TokenResponseDto

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body payload: AuthLogoutRequestDto)

    @GET("api/v1/auth/me")
    suspend fun me(): UserDto
}

interface DocumentService {
    @Multipart
    @POST("api/v1/documents/upload")
    suspend fun upload(@Part file: MultipartBody.Part): UploadedDocumentDto
}

interface BillService {
    @POST("api/v1/bills/extract")
    suspend fun extract(@Body payload: ExtractBillRequestDto): BillReviewDto

    @GET("api/v1/bills/{billId}")
    suspend fun getBill(@Path("billId") billId: String): BillReviewDto

    @DELETE("api/v1/bills/{billId}")
    suspend fun deleteBill(@Path("billId") billId: String)

    @POST("api/v1/bills/{billId}/confirm")
    suspend fun confirm(@Path("billId") billId: String, @Body payload: ConfirmBillRequestDto): BillReviewDto

    @GET("api/v1/bills/{billId}/analytics")
    suspend fun getAnalytics(@Path("billId") billId: String): BillAnalyticsDto

    @GET("api/v1/bills/{billId}/forecast")
    suspend fun getForecast(@Path("billId") billId: String): BillForecastDto

    @GET("api/v1/users/{userId}/history")
    suspend fun getHistory(@Path("userId") userId: String): UserBillHistoryResponseDto
}
