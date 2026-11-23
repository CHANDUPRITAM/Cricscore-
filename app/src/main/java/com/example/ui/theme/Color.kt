package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Stadium Turf & Broadcast Palette — Daylight Light Mode
val PitchEmerald = Color(0xFF0B6B44)
val PitchEmeraldDark = Color(0xFF07472D)
val PitchEmeraldLight = Color(0xFF148A5A)
val MintContainer = Color(0xFFD6F5E5)
val OnMintContainer = Color(0xFF052E1D)

val LeatherCrimson = Color(0xFFC81E3A)
val CrimsonContainer = Color(0xFFFFDADE)
val OnCrimsonContainer = Color(0xFF40000D)

val TrophyGold = Color(0xFFD97706)
val GoldContainer = Color(0xFFFEF3C7)
val OnGoldContainer = Color(0xFF451A03)

val BoundaryBlue = Color(0xFF1D4ED8)
val ExtraAmber = Color(0xFFB45309)

val ChalkBackground = Color(0xFFF3F7F4)
val PureCardSurface = Color(0xFFFFFFFF)
val SoftTurfVariant = Color(0xFFE6EFEA)
val DeepSlateText = Color(0xFF102019)
val MutedTurfText = Color(0xFF4B6358)
val HairlineBorder = Color(0xFFD0DDD6)

// Night Match Floodlight Palette — Dark Mode
val FloodlightEmerald = Color(0xFF38D399)
val OnFloodlightEmerald = Color(0xFF033822)
val DarkEmeraldContainer = Color(0xFF0B4F33)
val OnDarkEmeraldContainer = Color(0xFFB7F7D8)

val NightCrimson = Color(0xFFFF6B81)
val OnNightCrimson = Color(0xFF560013)
val NightCrimsonContainer = Color(0xFF7D0E26)
val OnNightCrimsonContainer = Color(0xFFFFDADE)

val NightGold = Color(0xFFFBBF24)
val OnNightGold = Color(0xFF452300)
val NightGoldContainer = Color(0xFF633A00)
val OnNightGoldContainer = Color(0xFFFEF3C7)

val NightStadiumBackground = Color(0xFF09130E)
val NightStadiumSurface = Color(0xFF111E17)
val NightStadiumSurfaceVariant = Color(0xFF192B22)
val NightTextPrimary = Color(0xFFECF4EF)
val NightTextSecondary = Color(0xFF9BB4A7)
val NightBorder = Color(0xFF283F33)

@Immutable
data class CricketSemanticColors(
    val boundaryFour: Color,
    val onBoundaryFour: Color,
    val boundarySix: Color,
    val onBoundarySix: Color,
    val wicket: Color,
    val onWicket: Color,
    val extra: Color,
    val onExtra: Color,
    val dotBall: Color,
    val onDotBall: Color,
    val headerGradientStart: Color,
    val headerGradientEnd: Color,
    val livePulse: Color,
    val teamOneChart: Color,
    val teamTwoChart: Color
)

val LightCricketColors = CricketSemanticColors(
    boundaryFour = Color(0xFF1D4ED8),
    onBoundaryFour = Color.White,
    boundarySix = Color(0xFFD97706),
    onBoundarySix = Color.White,
    wicket = Color(0xFFC81E3A),
    onWicket = Color.White,
    extra = Color(0xFFEA580C),
    onExtra = Color.White,
    dotBall = Color(0xFFE2EAE6),
    onDotBall = Color(0xFF43594F),
    headerGradientStart = Color(0xFF07472D),
    headerGradientEnd = Color(0xFF0F7C50),
    livePulse = Color(0xFFEF4444),
    teamOneChart = Color(0xFF0B6B44),
    teamTwoChart = Color(0xFFD97706)
)

val DarkCricketColors = CricketSemanticColors(
    boundaryFour = Color(0xFF3B82F6),
    onBoundaryFour = Color.White,
    boundarySix = Color(0xFFF59E0B),
    onBoundarySix = Color(0xFF1C1000),
    wicket = Color(0xFFEF4444),
    onWicket = Color.White,
    extra = Color(0xFFF97316),
    onExtra = Color(0xFF1F0C00),
    dotBall = Color(0xFF24382E),
    onDotBall = Color(0xFFB5C9BF),
    headerGradientStart = Color(0xFF062C1C),
    headerGradientEnd = Color(0xFF0C5A3A),
    livePulse = Color(0xFFFF5A6E),
    teamOneChart = Color(0xFF38D399),
    teamTwoChart = Color(0xFFFBBF24)
)

val LocalCricketColors = staticCompositionLocalOf { LightCricketColors }
