package com.example.llegadadelbebe.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.llegadadelbebe.core.security.SecureConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receptor en segundo plano que sincroniza periódicamente las vitaminas prenatales con Google Sheets,
 * programa automáticamente las alarmas en el teléfono de Mamá cuando Papá carga vitaminas nuevas,
 * y notifica al Papá en tiempo real cuando Mamá se toma la pastilla.
 */
class MedicationSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val sheetUrl = SecureConfig.getGoogleSheetUrl()
        if (sheetUrl.isBlank()) {
            MedicationSyncScheduler.scheduleNextSync(context)
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                MedicationCloudSyncManager.syncMedications(
                    context = context,
                    sheetUrl = sheetUrl,
                    forceUpload = false
                )
            } catch (_: Exception) {
            } finally {
                // Programar el siguiente chequeo en 15 minutos
                MedicationSyncScheduler.scheduleNextSync(context)
                pendingResult.finish()
            }
        }
    }
}

object MedicationSyncScheduler {

    private const val SYNC_REQUEST_CODE = 9092
    private const val SYNC_INTERVAL_MILLIS = 15 * 60 * 1000L // Cada 15 minutos en background

    fun scheduleNextSync(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerMillis = System.currentTimeMillis() + SYNC_INTERVAL_MILLIS

        val intent = Intent(context, MedicationSyncReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SYNC_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {}
    }
}
