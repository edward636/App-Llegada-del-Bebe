package com.example.llegadadelbebe.core.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object OemOptimizationHelper {

    val isXiaomiOrRedmi: Boolean
        get() {
            val manufacturer = Build.MANUFACTURER ?: ""
            val brand = Build.BRAND ?: ""
            return manufacturer.contains("Xiaomi", ignoreCase = true) ||
                    manufacturer.contains("Redmi", ignoreCase = true) ||
                    brand.contains("Xiaomi", ignoreCase = true) ||
                    brand.contains("Redmi", ignoreCase = true)
        }

    val isOppoOrRealme: Boolean
        get() {
            val manufacturer = Build.MANUFACTURER ?: ""
            val brand = Build.BRAND ?: ""
            return manufacturer.contains("Oppo", ignoreCase = true) ||
                    manufacturer.contains("Realme", ignoreCase = true) ||
                    brand.contains("Oppo", ignoreCase = true) ||
                    brand.contains("Realme", ignoreCase = true)
        }

    val detectedOemName: String
        get() = when {
            isXiaomiOrRedmi -> "Xiaomi HyperOS (Redmi Note 13 Pro+)"
            isOppoOrRealme -> "Oppo ColorOS (Oppo Reno 12F)"
            else -> {
                val manufacturer = (Build.MANUFACTURER ?: "Android").ifBlank { "Android" }
                val release = Build.VERSION.RELEASE ?: "14"
                "${manufacturer.replaceFirstChar { it.uppercase() }} (Android $release)"
            }
        }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        return true
    }

    @SuppressLint("BatteryLife")
    fun createIgnoreBatteryOptimizationIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    fun openOemAutostartSettings(context: Context): Boolean {
        val intentsToTry = mutableListOf<Intent>()

        if (isXiaomiOrRedmi) {
            // Xiaomi HyperOS / MIUI Auto-start
            intentsToTry.add(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
            )
        }

        if (isOppoOrRealme) {
            // Oppo ColorOS Auto-start / Startup list
            intentsToTry.add(
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
            )
            intentsToTry.add(
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                )
            )
            intentsToTry.add(
                Intent().setComponent(
                    ComponentName(
                        "com.oplus.battery",
                        "com.oplus.battery.ui.BatteryMainActivity"
                    )
                )
            )
        }

        // Fallback genérico a la pantalla de detalles de la app
        intentsToTry.add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )

        for (intent in intentsToTry) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Siguiente intento
            }
        }
        return false
    }
}
