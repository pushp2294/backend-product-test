package com.example.myproducts.ui.common

import com.example.myproducts.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

// The Locale is fixed in every test so results don't depend on the machine running them.
// Note: these run on the JVM's formatting data; Android's (ICU-based) output can differ in
// small details such as spacing, which is why the screens never parse these strings.
class MoneyFormatTest {

    @Test
    fun `USD has 2 decimals`() {
        assertEquals("$109.95", formatMoney(Money(10995, "USD"), Locale.US))
    }

    @Test
    fun `GBP is formatted from the currency code, not assumed to be dollars`() {
        assertEquals("£109.95", formatMoney(Money(10995, "GBP"), Locale.UK))
    }

    @Test
    fun `JPY has 0 decimals, so 10995 minor units is 10,995 yen`() {
        assertEquals("¥10,995", formatMoney(Money(10995, "JPY"), Locale.US))
    }

    @Test
    fun `whole amounts still show the currency's decimals`() {
        assertEquals("$114.00", formatMoney(Money(11400, "USD"), Locale.US))
    }

    @Test
    fun `an unrecognised currency code falls back to CODE plus 2 decimals instead of crashing`() {
        assertEquals("ABC 109.95", formatMoney(Money(10995, "ABC"), Locale.US))
    }
}
