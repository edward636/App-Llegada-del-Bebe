package com.example.llegadadelbebe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

object ThemeModes {
    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val AMOLED_DARK = "AMOLED_DARK"
}

@Composable
fun LlegadaDelBebeTheme(
    themeMode: String = ThemeModes.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        ThemeModes.LIGHT -> false
        ThemeModes.AMOLED_DARK -> true
        else -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BabyBoyAmoledDarkColorScheme else BabyBoyLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
