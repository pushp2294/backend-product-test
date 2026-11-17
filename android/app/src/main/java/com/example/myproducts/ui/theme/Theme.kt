package com.example.myproducts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

// Builds the app theme from Tokens.kt. Screens read colours and type through
// MaterialTheme, and spacing/sizes/radii through AppTheme (below).

// Every one of Material 3's colour roles is assigned explicitly, in both schemes. Any
// role left out would fall back to Material's default purple and bypass Tokens.kt
// (that is how the first on-device run showed lavender cards). ThemeTokensTest enforces it.

internal val LightColors: ColorScheme = ColorScheme(
    primary = Palette.Brand,
    onPrimary = Palette.Paper,
    primaryContainer = Palette.BrandSoft,
    onPrimaryContainer = Palette.BrandDeep,
    inversePrimary = Palette.BrandDark,
    secondary = Palette.Accent,
    onSecondary = Palette.Ink,
    secondaryContainer = Palette.AccentSoft,
    onSecondaryContainer = Palette.AccentDeep,
    tertiary = Palette.InkMuted,
    onTertiary = Palette.Paper,
    tertiaryContainer = Palette.PaperDim,
    onTertiaryContainer = Palette.Ink,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.PaperDim,
    onSurfaceVariant = Palette.InkMuted,
    surfaceTint = Palette.Brand,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Paper,
    error = Palette.Danger,
    onError = Palette.Paper,
    errorContainer = Palette.DangerSoft,
    onErrorContainer = Palette.DangerDeep,
    outline = Palette.Line,
    outlineVariant = Palette.LineSoft,
    scrim = Palette.Ink,
    surfaceBright = Palette.Paper,
    surfaceDim = Palette.PaperDeep,
    // Cards use surfaceContainerHighest: a soft neutral, not Material's lavender.
    surfaceContainer = Palette.PaperDim,
    surfaceContainerHigh = Palette.PaperDim,
    surfaceContainerHighest = Palette.PaperDim,
    surfaceContainerLow = Palette.Paper,
    surfaceContainerLowest = Palette.Paper,
    primaryFixed = Palette.BrandSoft,
    primaryFixedDim = Palette.BrandDark,
    onPrimaryFixed = Palette.BrandDeep,
    onPrimaryFixedVariant = Palette.Brand,
    secondaryFixed = Palette.AccentSoft,
    secondaryFixedDim = Palette.Accent,
    onSecondaryFixed = Palette.AccentDeep,
    onSecondaryFixedVariant = Palette.AccentDeep,
    tertiaryFixed = Palette.PaperDim,
    tertiaryFixedDim = Palette.PaperDeep,
    onTertiaryFixed = Palette.Ink,
    onTertiaryFixedVariant = Palette.InkMuted,
)

internal val DarkColors: ColorScheme = ColorScheme(
    primary = Palette.BrandDark,
    onPrimary = Palette.BrandDeep,
    primaryContainer = Palette.Brand,
    onPrimaryContainer = Palette.BrandSoft,
    inversePrimary = Palette.Brand,
    secondary = Palette.Accent,
    onSecondary = Palette.Ink,
    secondaryContainer = Palette.AccentDeep,
    onSecondaryContainer = Palette.AccentSoft,
    tertiary = Palette.NightInkMuted,
    onTertiary = Palette.NightPaper,
    tertiaryContainer = Palette.NightSurface,
    onTertiaryContainer = Palette.NightInk,
    background = Palette.NightPaper,
    onBackground = Palette.NightInk,
    surface = Palette.NightPaper,
    onSurface = Palette.NightInk,
    surfaceVariant = Palette.NightSurface,
    onSurfaceVariant = Palette.NightInkMuted,
    surfaceTint = Palette.BrandDark,
    inverseSurface = Palette.NightInk,
    inverseOnSurface = Palette.NightPaper,
    error = Palette.DangerDark,
    onError = Palette.DangerDeep,
    errorContainer = Palette.Danger,
    onErrorContainer = Palette.DangerSoft,
    outline = Palette.NightInkMuted,
    outlineVariant = Palette.NightSurfaceHigh,
    scrim = Palette.Ink,
    surfaceBright = Palette.NightSurfaceHigh,
    surfaceDim = Palette.NightPaper,
    surfaceContainer = Palette.NightSurface,
    surfaceContainerHigh = Palette.NightSurface,
    surfaceContainerHighest = Palette.NightSurfaceHigh,
    surfaceContainerLow = Palette.NightPaper,
    surfaceContainerLowest = Palette.NightPaper,
    primaryFixed = Palette.BrandSoft,
    primaryFixedDim = Palette.BrandDark,
    onPrimaryFixed = Palette.BrandDeep,
    onPrimaryFixedVariant = Palette.Brand,
    secondaryFixed = Palette.AccentSoft,
    secondaryFixedDim = Palette.Accent,
    onSecondaryFixed = Palette.AccentDeep,
    onSecondaryFixedVariant = Palette.AccentDeep,
    tertiaryFixed = Palette.PaperDim,
    tertiaryFixedDim = Palette.PaperDeep,
    onTertiaryFixed = Palette.Ink,
    onTertiaryFixedVariant = Palette.InkMuted,
)

private val AppTypography = Typography(
    headlineMedium = TypeTokens.Display,
    titleLarge = TypeTokens.Title,
    titleMedium = TypeTokens.Subtitle,
    bodyLarge = TypeTokens.Body,
    bodyMedium = TypeTokens.Body,
    labelLarge = TypeTokens.Label,
    labelMedium = TypeTokens.Label,
)

// CompositionLocals let any Composable read the tokens without passing them down by hand.
private val LocalSpacing = staticCompositionLocalOf { Spacing() }
private val LocalSizes = staticCompositionLocalOf { Sizes() }
private val LocalRadii = staticCompositionLocalOf { Radii() }

/** Token access for screens: `AppTheme.spacing.l`, `AppTheme.sizes.tileWidth`, ... */
object AppTheme {
    val spacing: Spacing
        @Composable get() = LocalSpacing.current
    val sizes: Sizes
        @Composable get() = LocalSizes.current
    val radii: Radii
        @Composable get() = LocalRadii.current

    /** Price text style (not part of Material's type scale). */
    val priceStyle
        @Composable get() = TypeTokens.Price.copy(color = MaterialTheme.colorScheme.primary)
}

/**
 * The app theme. Light/dark follows the system setting.
 *
 * Material You dynamic colour is deliberately NOT used: it recolours the app from the
 * user's wallpaper, which would bypass Tokens.kt and make the look-and-feel seam false
 * in practice. See DECISIONS.md.
 */
@Composable
fun MyProductsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val radii = Radii()
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalSizes provides Sizes(),
        LocalRadii provides radii,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = Shapes(
                small = RoundedCornerShape(radii.small),
                medium = RoundedCornerShape(radii.medium),
                large = RoundedCornerShape(radii.large),
            ),
            content = content,
        )
    }
}
