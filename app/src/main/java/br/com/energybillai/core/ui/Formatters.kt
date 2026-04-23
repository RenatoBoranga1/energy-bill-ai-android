package br.com.energybillai.core.ui

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
