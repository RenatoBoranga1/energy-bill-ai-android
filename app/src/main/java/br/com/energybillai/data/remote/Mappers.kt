package br.com.energybillai.data.remote

import br.com.energybillai.domain.model.AnalyticsSeriesPoint
import br.com.energybillai.domain.model.AuthSession
import br.com.energybillai.domain.model.AuthTokens
import br.com.energybillai.domain.model.BillAnalytics
import br.com.energybillai.domain.model.BillConfidenceScore
import br.com.energybillai.domain.model.BillCore
import br.com.energybillai.domain.model.BillExtractionStatus
import br.com.energybillai.domain.model.BillForecast
import br.com.energybillai.domain.model.BillReview
import br.com.energybillai.domain.model.BillSummary
import br.com.energybillai.domain.model.ConsumptionAnomaly
import br.com.energybillai.domain.model.ConsumptionExtreme
import br.com.energybillai.domain.model.EnergyUser
import br.com.energybillai.domain.model.ExtractedBillData
import br.com.energybillai.domain.model.ExtractionLog
import br.com.energybillai.domain.model.ExtractionLogLevel
import br.com.energybillai.domain.model.ExtractionLogStage
import br.com.energybillai.domain.model.ForecastPoint
import br.com.energybillai.domain.model.HistoricalConsumption
import br.com.energybillai.domain.model.Insight
import br.com.energybillai.domain.model.InsightType
import br.com.energybillai.domain.model.MonthVariation
import br.com.energybillai.domain.model.ReviewedBillData
import br.com.energybillai.domain.model.UploadedDocument
import java.time.Instant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

fun TokenResponseDto.toDomainSession(issuedAtEpochSeconds: Long = Instant.now().epochSecond): AuthSession {
    return AuthSession(
        user = user.toDomain(),
        tokens = AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = tokenType,
            expiresInSeconds = expiresInSeconds,
            refreshExpiresInSeconds = refreshExpiresInSeconds,
            issuedAtEpochSeconds = issuedAtEpochSeconds,
        ),
    )
}

fun UserDto.toDomain(): EnergyUser {
    return EnergyUser(
        id = id,
        name = name,
        email = email,
        createdAt = createdAt,
    )
}

fun UploadedDocumentDto.toDomain(): UploadedDocument {
    return UploadedDocument(
        id = id,
        userId = userId,
        filename = filename,
        mimeType = mimeType,
        fileSizeBytes = fileSizeBytes,
        fileType = fileType,
        filePath = filePath,
        extractedText = extractedText,
        createdAt = createdAt,
    )
}

private fun HistoricalConsumptionDto.toDomain(): HistoricalConsumption {
    return HistoricalConsumption(
        referenceMonth = mesReferencia,
        consumptionKwh = consumoKwh,
        billedDays = diasFaturados,
    )
}

private fun List<HistoricalConsumptionDto>.toDomainList(): List<HistoricalConsumption> = map { it.toDomain() }

private fun ExtractedBillDataDto.toReviewed(): ReviewedBillData {
    return ReviewedBillData(
        concessionaria = concessionaria,
        mesReferencia = mesReferencia,
        consumoKwh = consumoKwh,
        diasFaturados = diasFaturados,
        valorTotal = valorTotal,
        bandeiraTarifaria = bandeiraTarifaria,
        unidadeConsumidora = unidadeConsumidora,
        vencimento = vencimento,
        historicoConsumo = historicoConsumo.toDomainList(),
    )
}

fun ExtractedBillDataDto.toDomain(): ExtractedBillData {
    return ExtractedBillData(
        reviewed = toReviewed(),
        confidence = confidence,
        warnings = warnings,
    )
}

private fun ExtractionConfidenceDto.toDomain(): BillConfidenceScore {
    return BillConfidenceScore(
        fieldName = fieldName,
        confidenceScore = confidenceScore,
    )
}

private fun UtilityBillDetailDto.toDomain(): BillCore {
    return BillCore(
        id = id,
        userId = userId,
        documentId = documentId,
        concessionaria = concessionaria,
        mesReferencia = mesReferencia,
        consumoKwh = consumoKwh,
        diasFaturados = diasFaturados,
        valorTotal = valorTotal,
        bandeiraTarifaria = bandeiraTarifaria,
        unidadeConsumidora = unidadeConsumidora,
        vencimento = vencimento,
        extractionStatus = runCatching { BillExtractionStatus.valueOf(extractionStatus) }.getOrDefault(BillExtractionStatus.PENDING_REVIEW),
        reviewRequired = reviewRequired,
        createdAt = createdAt,
        consumptionHistory = consumptionHistory.toDomainList(),
        confidenceScores = confidenceScores.map { it.toDomain() },
    )
}

private fun ExtractionLogDto.toDomain(): ExtractionLog {
    return ExtractionLog(
        id = id,
        documentId = documentId,
        billId = billId,
        stage = runCatching { ExtractionLogStage.valueOf(stage) }.getOrDefault(ExtractionLogStage.validation),
        level = runCatching { ExtractionLogLevel.valueOf(level) }.getOrDefault(ExtractionLogLevel.INFO),
        message = message,
        sourceComponent = sourceComponent,
        createdAt = createdAt,
    )
}

fun BillReviewDto.toDomain(): BillReview {
    return BillReview(
        billId = billId,
        document = document.toDomain(),
        extractionStatus = runCatching { BillExtractionStatus.valueOf(extractionStatus) }.getOrDefault(BillExtractionStatus.PENDING_REVIEW),
        reviewRequired = reviewRequired,
        structuredData = structuredData.toDomain(),
        fieldsForReview = fieldsForReview,
        bill = bill.toDomain(),
        logs = logs.map { it.toDomain() },
    )
}

fun UserBillHistoryResponseDto.toDomain(): List<BillSummary> {
    return bills.map { entry ->
        BillSummary(
            billId = entry.billId,
            documentId = entry.documentId,
            referenceMonth = entry.mesReferencia,
            provider = entry.concessionaria,
            consumptionKwh = entry.consumoKwh,
            totalValue = entry.valorTotal,
            extractionStatus = runCatching { BillExtractionStatus.valueOf(entry.extractionStatus) }.getOrDefault(BillExtractionStatus.PENDING_REVIEW),
            reviewRequired = entry.reviewRequired,
        )
    }
}

private fun InsightDto.toDomain(): Insight {
    return Insight(
        id = id,
        billId = billId,
        type = runCatching { InsightType.valueOf(insightType) }.getOrDefault(InsightType.general),
        message = message,
        createdAt = createdAt,
    )
}

fun BillAnalyticsDto.toDomain(): BillAnalytics {
    return BillAnalytics(
        billId = billId,
        referenceMonth = referenceMonth,
        historyPointsUsed = historyPointsUsed,
        averageDailyKwh = averageDailyKwh,
        averageMonthlyKwh = averageMonthlyKwh,
        latestMonthOverMonthVariationPct = latestMonthOverMonthVariationPct,
        monthOverMonthVariations = monthOverMonthVariations.map {
            MonthVariation(
                currentMonth = it.currentMonth,
                previousMonth = it.previousMonth,
                variationPct = it.variationPct,
            )
        },
        highestConsumption = highestConsumption?.let {
            ConsumptionExtreme(
                referenceMonth = it.mesReferencia,
                consumptionKwh = it.consumoKwh,
            )
        },
        lowestConsumption = lowestConsumption?.let {
            ConsumptionExtreme(
                referenceMonth = it.mesReferencia,
                consumptionKwh = it.consumoKwh,
            )
        },
        trendDirection = trendDirection,
        trendSummary = trendSummary,
        seasonalityDetected = seasonalityDetected,
        seasonalitySummary = seasonalitySummary,
        anomalies = anomalies.map {
            ConsumptionAnomaly(
                referenceMonth = it.mesReferencia,
                consumptionKwh = it.consumoKwh,
                deviationPct = it.deviationPct,
                reason = it.reason,
            )
        },
        insights = insights.map { it.toDomain() },
        series = series.map {
            AnalyticsSeriesPoint(
                referenceMonth = it.mesReferencia,
                consumptionKwh = it.consumoKwh,
                billedDays = it.diasFaturados,
                avgDailyKwh = it.avgDailyKwh,
                source = it.source,
                confirmedSource = it.confirmedSource,
            )
        },
    )
}

fun BillForecastDto.toDomain(): BillForecast {
    return BillForecast(
        billId = billId,
        referenceMonth = referenceMonth,
        modelUsed = modelUsed,
        horizonMonths = horizonMonths,
        historyPointsUsed = historyPointsUsed,
        explanation = explanation,
        generatedForecasts = generatedForecasts.map {
            ForecastPoint(
                id = it.id,
                billId = it.billId,
                mesReferencia = it.mesReferencia,
                predictedKwh = it.predictedKwh,
                lowerBoundKwh = it.lowerBoundKwh,
                upperBoundKwh = it.upperBoundKwh,
                modelUsed = it.modelUsed,
                createdAt = it.createdAt,
            )
        },
        insights = insights.map { it.toDomain() },
    )
}

fun ReviewedBillData.toDto(): ReviewedBillDataDto {
    return ReviewedBillDataDto(
        concessionaria = concessionaria,
        mesReferencia = mesReferencia,
        consumoKwh = consumoKwh,
        diasFaturados = diasFaturados,
        valorTotal = valorTotal,
        bandeiraTarifaria = bandeiraTarifaria,
        unidadeConsumidora = unidadeConsumidora,
        vencimento = vencimento,
        historicoConsumo = historicoConsumo.map {
            HistoricalConsumptionDto(
                mesReferencia = it.referenceMonth,
                consumoKwh = it.consumptionKwh,
                diasFaturados = it.billedDays,
            )
        },
    )
}

fun createUploadPart(fileName: String, mimeType: String, bytes: ByteArray): MultipartBody.Part {
    val requestBody = bytes.toRequestBody(mimeType.toMediaType())
    return MultipartBody.Part.createFormData("file", fileName, requestBody)
}
