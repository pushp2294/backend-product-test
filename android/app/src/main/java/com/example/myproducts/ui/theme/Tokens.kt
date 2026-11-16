package com.example.myproducts.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// DESIGN TOKENS: the look-and-feel seam.
//
// Every colour, spacing, size, corner radius and text style in the app comes from this
// file. Screen code never writes Color(0xFF...) or a raw 16.dp. When the creative work
// lands, restyling the app means editing this file (and Theme.kt), nothing else.
//
// These values are deliberately neutral placeholders until that work arrives.

/**
 * Brand palette. The only place in the app with literal colour values. Theme.kt assigns
 * EVERY Material colour role from these, and ThemeTokensTest fails if any role isn't one
 * of them (otherwise Material silently fills unset roles with its default purple).
 */
internal object Palette {
    // Neutrals (light)
    val Ink = Color(0xFF1B1F24)
    val InkMuted = Color(0xFF5B6470)
    val Paper = Color(0xFFFFFFFF)
    val PaperDim = Color(0xFFF4F5F7)
    val PaperDeep = Color(0xFFE6E8EC)
    val Line = Color(0xFF8A939D)
    val LineSoft = Color(0xFFD9DDE2)

    // Neutrals (dark)
    val NightPaper = Color(0xFF111418)
    val NightSurface = Color(0xFF1C2026)
    val NightSurfaceHigh = Color(0xFF2A2F37)
    val NightInk = Color(0xFFE6E8EB)
    val NightInkMuted = Color(0xFFA4ACB6)

    // Brand
    val Brand = Color(0xFF1F5FAD)
    val BrandDark = Color(0xFF9CC2F5)
    val BrandSoft = Color(0xFFD6E4F7)
    val BrandDeep = Color(0xFF0B2A52)

    // Accent
    val Accent = Color(0xFFE0A100)
    val AccentSoft = Color(0xFFFBEFCC)
    val AccentDeep = Color(0xFF4A3500)

    // Errors
    val Danger = Color(0xFFB3261E)
    val DangerDark = Color(0xFFF2B8B5)
    val DangerSoft = Color(0xFFF9DEDC)
    val DangerDeep = Color(0xFF410E0B)
}

/** Space between things. Use these instead of raw dp in layouts. */
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

/** Fixed sizes of components. */
@Immutable
data class Sizes(
    val tileWidth: Dp = 168.dp,
    val tileImageHeight: Dp = 140.dp,
    val detailImageHeight: Dp = 300.dp,
    val ratingIcon: Dp = 16.dp,
    val buttonProgress: Dp = 20.dp,
    val minTouchTarget: Dp = 48.dp,
    val contentMaxWidth: Dp = 600.dp,
)

/** Corner radii. */
@Immutable
data class Radii(
    val small: Dp = 6.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 20.dp,
)

/** Text styles, mapped onto Material's type scale in Theme.kt. */
internal object TypeTokens {
    private val family = FontFamily.Default

    val Display = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp)
    val Title = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp)
    val Subtitle = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)
    val Body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val Label = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val Price = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
}
