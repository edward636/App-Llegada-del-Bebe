package com.example.llegadadelbebe

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.ui.theme.LlegadaDelBebeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Optimización Display Cutout para Oppo Reno 12F (ColorOS) y Redmi Note 13 Pro+ (HyperOS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val localPreferences = LocalPreferences(this)

        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableStateOf(localPreferences.getThemeMode()) }

            LlegadaDelBebeTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        currentThemeMode = themeMode,
                        onThemeModeChanged = { newMode ->
                            themeMode = newMode
                            localPreferences.saveThemeMode(newMode)
                        }
                    )
                }
            }
        }
    }
}
