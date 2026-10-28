package com.example.llegadadelbebe.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.llegadadelbebe.MainActivity
import com.example.llegadadelbebe.core.update.UpdateInfo

object NotificationHelper {

    const val CHANNEL_MEDICATIONS = "prenatal_medications_channel"
    const val CHANNEL_OTA_UPDATES = "ota_updates_channel"
    const val CHANNEL_ENGAGEMENT = "baby_engagement_channel"

    const val NOTIF_ID_OTA = 1001
    const val NOTIF_ID_ENGAGEMENT = 2001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Canal 1: Medicamentos y Vitaminas (Optimizado HyperOS / ColorOS)
            val medChannel = NotificationChannel(
                CHANNEL_MEDICATIONS,
                "Medicamentos y Vitaminas Prenatales",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recordatorios de tomas médicas y vitaminas duales para Britney y Edward"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                enableLights(true)
                lightColor = android.graphics.Color.MAGENTA
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setBypassDnd(true)
            }

            // Canal 2: Actualizaciones OTA (Optimizado HyperOS / ColorOS)
            val otaChannel = NotificationChannel(
                CHANNEL_OTA_UPDATES,
                "Actualizaciones de la App",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos de nuevas versiones disponibles para mantener la app actualizada"
                enableVibration(true)
                enableLights(true)
                lightColor = android.graphics.Color.CYAN
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }

            // Canal 3: Acompañamiento del Bebé e Inactividad
            val engagementChannel = NotificationChannel(
                CHANNEL_ENGAGEMENT,
                "Acompañamiento y Recordatorios del Bebé",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones cariñosas sobre el crecimiento del bebé e hitos de preparación"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannels(listOf(medChannel, otaChannel, engagementChannel))
        }
    }

    fun showOtaUpdateNotification(context: Context, updateInfo: UpdateInfo) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_OPEN_UPDATE_DIALOG", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID_OTA,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "🚀 ¡Nueva actualización disponible (${updateInfo.latestVersionName})!"
        val content = "Hay mejoras y optimizaciones listas para la app del bebé. Toca aquí para actualizar."

        val notification = NotificationCompat.Builder(context, CHANNEL_OTA_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIF_ID_OTA, notification)
        } catch (_: SecurityException) {
            // Manejo de seguridad si permisos fueron revocados
        }
    }

    fun showEngagementNotification(
        context: Context,
        title: String,
        content: String,
        notificationId: Int = NOTIF_ID_ENGAGEMENT
    ) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ENGAGEMENT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Manejo de seguridad
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
