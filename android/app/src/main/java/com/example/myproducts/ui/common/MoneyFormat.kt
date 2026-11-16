package com.example.myproducts.ui.common

import com.example.myproducts.domain.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats a price for display from minor units and the currency code the server sent.
 *
 * The number of decimal places comes from the currency itself, so nothing assumes USD:
 * 10995 USD -> "$109.95" (2 decimals), 10995 JPY -> "¥10,995" (0 decimals).
 * An unrecognised currency code falls back to "CODE 109.95" rather than crashing.
 */
fun formatMoney(money: Money, locale: Locale = Locale.getDefault()): String {
    val currency = try {
        Currency.getInstance(money.currency)
    } catch (e: IllegalArgumentException) {
        null
    }

    if (currency == null) {
        // Unknown code: assume 2 decimals and show the code as-is.
        return "${money.currency} ${BigDecimal.valueOf(money.amountMinor, 2).toPlainString()}"
    }

    // defaultFractionDigits is -1 for pseudo-currencies (e.g. "XXX"); treat those as 0.
    val decimals = currency.defaultFractionDigits.coerceAtLeast(0)
    // BigDecimal.valueOf(10995, 2) is exactly 109.95: no floating-point division.
    val amount = BigDecimal.valueOf(money.amountMinor, decimals)

    val format = NumberFormat.getCurrencyInstance(locale)
    format.currency = currency
    format.minimumFractionDigits = decimals
    format.maximumFractionDigits = decimals
    return format.format(amount)
}
