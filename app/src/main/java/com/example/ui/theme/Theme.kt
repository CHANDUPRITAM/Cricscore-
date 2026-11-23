package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = PitchEmerald,
    onPrimary = Color.White,
    primaryContainer = MintContainer,
    onPrimaryContainer = OnMintContainer,
    secondary = LeatherCrimson,
    onSecondary = Color.White,
    secondaryContainer = CrimsonContainer,
    onSecondaryContainer = OnCrimsonContainer,
    tertiary = TrophyGold,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = OnGoldContainer,
    background = ChalkBackground,
    onBackground = DeepSlateText,
    surface = PureCardSurface,
    onSurface = DeepSlateText,
    surfaceVariant = SoftTurfVariant,
    onSurfaceVariant = MutedTurfText,
    outline = HairlineBorder,
    outlineVariant = Color(0xFFE2EBE6),
    error = LeatherCrimson,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = FloodlightEmerald,
    onPrimary = OnFloodlightEmerald,
    primaryContainer = DarkEmeraldContainer,
    onPrimaryContainer = OnDarkEmeraldContainer,
    secondary = NightCrimson,
    onSecondary = OnNightCrimson,
    secondaryContainer = NightCrimsonContainer,
    onSecondaryContainer = OnNightCrimsonContainer,
    tertiary = NightGold,
    onTertiary = OnNightGold,
    tertiaryContainer = NightGoldContainer,
    onTertiaryContainer = OnNightGoldContainer,
    background = NightStadiumBackground,
    onBackground = NightTextPrimary,
    surface = NightStadiumSurface,
    onSurface = NightTextPrimary,
    surfaceVariant = NightStadiumSurfaceVariant,
    onSurfaceVariant = NightTextSecondary,
    outline = NightBorder,
    outlineVariant = Color(0xFF1E3228),
    error = NightCrimson,
    onError = OnNightCrimson
)

val CricscoreShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Daylight"),
    DARK("Floodlight Dark")
}

@Composable
fun CricscoreTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val cricketColors = if (darkTheme) DarkCricketColors else LightCricketColors

    CompositionLocalProvider(LocalCricketColors provides cricketColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = CricscoreShapes,
            content = content
        )
    }
}
