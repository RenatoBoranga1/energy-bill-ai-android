package br.com.energybillai.core.ui

import br.com.energybillai.domain.model.BillExtractionStatus
import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private val decimalFormatter = NumberFormat.getNumberInstance(Locale("pt", "BR")).apply {
    maximumFractionDigits = 1
}

fun Double?.toKwhLabel(): String = if (this == null) "--" else "${decimalFormatter.format(this)} kWh"

fun Double?.toCurrencyLabel(): String = if (this == null) "--" else currencyFormatter.format(this)

fun Double?.toPercentLabel(): String = if (this == null) "--" else "${decimalFormatter.format(this)}%"

fun String?.toMonthLabel(): String {
    if (this.isNullOrBlank()) return "--"
    return runCatching {
        YearMonth.parse(this).format(DateTimeFormatter.ofPattern("MMM/yyyy", Locale("pt", "BR")))
    }.getOrElse { this }
}

fun String?.toDateLabel(): String {
    if (this.isNullOrBlank()) return "--"
    return runCatching {
        when {
            this.length == 10 -> LocalDate.parse(this).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            else -> OffsetDateTime.parse(this).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
    }.getOrElse { this }
}

fun BillExtractionStatus.toUiLabel(): String {
    return when (this) {
        BillExtractionStatus.PENDING_REVIEW -> "Aguardando revisão"
        BillExtractionStatus.CONFIRMED -> "Confirmada"
        BillExtractionStatus.REJECTED -> "Rejeitada"
        BillExtractionStatus.FAILED -> "Falha na extração"
    }
}

fun String?.toTrendLabel(): String {
    val normalized = this?.trim()?.lowercase()?.replace("-", "_") ?: return "--"
    return when (normalized) {
        "up", "alta", "increasing", "rising", "growth" -> "Tendência de alta"
        "down", "queda", "decreasing", "falling", "drop" -> "Tendência de queda"
        "stable", "stability", "estavel", "steady" -> "Estável"
        else -> normalized
            .replace("_", " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }
    }
}

fun String?.toForecastModelLabel(): String {
    val normalized = this?.trim()?.lowercase() ?: return "Modelo não identificado"
    return when (normalized) {
        "prophet" -> "Modelo estatístico Prophet"
        "moving_average_linear_trend" -> "Média móvel com tendência linear"
        "neuralprophet" -> "Modelo NeuralProphet"
        else -> normalized
            .replace("_", " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }
    }
}
