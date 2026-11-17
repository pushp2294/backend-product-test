package com.example.myproducts.ui.theme

import androidx.compose.material3.ColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

/**
 * Guards the look-and-feel seam: every Material colour role must come from Palette in
 * Tokens.kt. A role left unset falls back to Material's default purple, so restyling
 * the palette would not restyle that part of the app.
 *
 * Reflection is used so that roles added by a future Material version are checked too,
 * without anyone remembering to update this test.
 */
class ThemeTokensTest {
    // Compose's Color is a Kotlin value class: on the JVM, a Color getter returns a raw long
    // and its name carries a "-0d7_KjU" suffix. That is how the colour getters are found.
    private fun colorGetters(type: Class<*>): List<Method> =
        type.declaredMethods.filter { it.name.startsWith("get") && it.name.endsWith("-0d7_KjU") && it.parameterCount == 0 }

    private val paletteColors: Set<Long> =
        colorGetters(Palette::class.java).map { it.invoke(Palette) as Long }.toSet()

    private fun rolesNotFromPalette(scheme: ColorScheme): List<String> =
        colorGetters(ColorScheme::class.java)
            .filter { (it.invoke(scheme) as Long) !in paletteColors }
            .map { it.name.removePrefix("get").substringBefore('-') }

    @Test
    fun `the reflection actually finds the palette and all colour roles (guards against a vacuous pass)`() {
        assertTrue("palette colours found: ${paletteColors.size}", paletteColors.size >= 10)
        assertTrue("roles found: ${colorGetters(ColorScheme::class.java).size}", colorGetters(ColorScheme::class.java).size >= 48)
    }

    @Test
    fun `every light-theme colour role comes from Palette`() {
        assertEquals(emptyList<String>(), rolesNotFromPalette(LightColors))
    }

    @Test
    fun `every dark-theme colour role comes from Palette`() {
        assertEquals(emptyList<String>(), rolesNotFromPalette(DarkColors))
    }
}
