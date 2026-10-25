package com.example.llegadadelbebe.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Paleta de colores "Baby Boy" moderna, tierna y accesible (Material 3).
 * Tonos: Azul cielo suave, menta pastel, crema cálido y contrastes legibles.
 */
val BabyBlueLight = Color(0xFFE8F1F5)
val BabyBluePrimary = Color(0xFF4A90E2)
val BabyBlueSecondary = Color(0xFF64B5F6)
val BabyMint = Color(0xFFA8E6CF)
val BabyCream = Color(0xFFFAF7F2)
val BabySurface = Color(0xFFFFFFFF)
val BabyDarkNavy = Color(0xFF1A365D)
val BabyMutedText = Color(0xFF5A7184)
val BabyCardBackground = Color(0xFFF4F8FB)

val BabyBoyLightColorScheme = lightColorScheme(
    primary = BabyBluePrimary,
    onPrimary = Color.White,
    primaryContainer = BabyBlueLight,
    onPrimaryContainer = BabyDarkNavy,
    secondary = BabyMint,
    onSecondary = BabyDarkNavy,
    secondaryContainer = Color(0xFFD8F3DC),
    onSecondaryContainer = Color(0xFF1B4332),
    tertiary = BabyBlueSecondary,
    onTertiary = Color.White,
    background = BabyCream,
    onBackground = BabyDarkNavy,
    surface = BabySurface,
    onSurface = BabyDarkNavy,
    surfaceVariant = BabyCardBackground,
    onSurfaceVariant = BabyMutedText
)

val BabyBoyDarkColorScheme = darkColorScheme(
    primary = BabyBlueSecondary,
    onPrimary = Color(0xFF0D253F),
    primaryContainer = Color(0xFF1A365D),
    onPrimaryContainer = BabyBlueLight,
    secondary = BabyMint,
    onSecondary = Color(0xFF0F382A),
    background = Color(0xFF121B24),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1A2634),
    onSurface = Color(0xFFE2E8F0)
)
