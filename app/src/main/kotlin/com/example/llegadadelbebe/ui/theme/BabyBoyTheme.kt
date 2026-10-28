package com.example.llegadadelbebe.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Paleta de colores "Baby Boy" adaptada con precisión para:
 * 1. Modo Claro Luminoso (Material 3 tierno y acogedor)
 * 2. Modo Oscuro AMOLED Puro (Fondo #000000 real con apagado físico de píxeles OLED en HyperOS/ColorOS)
 */

// --- Colores Modo Claro ---
val BabyLightBackground = Color(0xFFF8FAFC)
val BabyLightSurface = Color(0xFFFFFFFF)
val BabyLightCard = Color(0xFFF1F5F9)
val BabyLightBorder = Color(0xFFE2E8F0)
val BabyLightPrimary = Color(0xFF0284C7)       // Azul Cielo Intenso
val BabyLightPrimaryContainer = Color(0xFFE0F2FE)
val BabyLightOnPrimaryContainer = Color(0xFF0369A1)
val BabyLightSecondary = Color(0xFF0D9488)     // Menta Fresca
val BabyLightSecondaryContainer = Color(0xFFCCFBF1)
val BabyLightTextPrimary = Color(0xFF0F172A)
val BabyLightTextSecondary = Color(0xFF64748B)

// --- Colores Modo Oscuro AMOLED (Pitch Black #000000) ---
val AmoledPureBlack = Color(0xFF000000)        // Apagado de píxeles en pantallas OLED
val AmoledSurface = Color(0xFF060B14)          // Elevación sutil nivel 1
val AmoledCard = Color(0xFF0C1425)             // Elevación de tarjetas nivel 2
val AmoledCardBorder = Color(0xFF1E293B)       // Borde nítido para separar tarjetas
val AmoledPrimary = Color(0xFF38BDF8)          // Azul Cielo Eléctrico (Alto contraste en negro)
val AmoledPrimaryContainer = Color(0xFF0C2440)
val AmoledOnPrimaryContainer = Color(0xFFBAE6FD)
val AmoledSecondary = Color(0xFF2DD4BF)        // Menta Neón Pastel
val AmoledSecondaryContainer = Color(0xFF0B332E)
val AmoledOnSecondaryContainer = Color(0xFF99F6E4)
val AmoledTextPrimary = Color(0xFFF8FAFC)      // Blanco puro nítido
val AmoledTextSecondary = Color(0xFF94A3B8)    // Gris pizarra legible

val BabyBoyLightColorScheme = lightColorScheme(
    primary = BabyLightPrimary,
    onPrimary = Color.White,
    primaryContainer = BabyLightPrimaryContainer,
    onPrimaryContainer = BabyLightOnPrimaryContainer,
    secondary = BabyLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = BabyLightSecondaryContainer,
    onSecondaryContainer = Color(0xFF115E59),
    tertiary = Color(0xFF3B82F6),
    onTertiary = Color.White,
    background = BabyLightBackground,
    onBackground = BabyLightTextPrimary,
    surface = BabyLightSurface,
    onSurface = BabyLightTextPrimary,
    surfaceVariant = BabyLightCard,
    onSurfaceVariant = BabyLightTextSecondary,
    outline = BabyLightBorder,
    outlineVariant = Color(0xFFCBD5E1)
)

val BabyBoyAmoledDarkColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = Color(0xFF031626),
    primaryContainer = AmoledPrimaryContainer,
    onPrimaryContainer = AmoledOnPrimaryContainer,
    secondary = AmoledSecondary,
    onSecondary = Color(0xFF022A26),
    secondaryContainer = AmoledSecondaryContainer,
    onSecondaryContainer = AmoledOnSecondaryContainer,
    tertiary = Color(0xFF818CF8),
    onTertiary = Color(0xFF0F172A),
    background = AmoledPureBlack,
    onBackground = AmoledTextPrimary,
    surface = AmoledSurface,
    onSurface = AmoledTextPrimary,
    surfaceVariant = AmoledCard,
    onSurfaceVariant = AmoledTextSecondary,
    outline = AmoledCardBorder,
    outlineVariant = Color(0xFF293548)
)
