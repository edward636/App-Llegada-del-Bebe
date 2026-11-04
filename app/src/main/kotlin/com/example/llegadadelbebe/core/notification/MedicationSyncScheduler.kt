package com.example.llegadadelbebe.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.llegadadelbebe.core.security.SecureConfig
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.data.remote.GoogleSheetsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Receptor en segundo plano que sincroniza periódicamente las vitaminas prenatales con Google Sheets
 * y notifica al Papá en tiempo real cuando Mamá se toma la pastilla, cancelando alarmas redundantes.
 */
class MedicationSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val sheetUrl = SecureConfig.getGoogleSheetUrl()
        if (sheetUrl.isBlank()) {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val localPreferences = LocalPreferences(context)
                val googleSheetsService = GoogleSheetsService()
                val deletedIds = localPreferences.getDeletedMedicationIds()
                val pendingUploadIds = localPreferences.getPendingUploadMedicationIds()
                val activeUser = localPreferences.getActiveUser()
                val isDad = activeUser.contains("Papá", ignoreCase = true)
                val todayStr = LocalDate.now().toString()

                val remoteResult = googleSheetsService.fetchSystemRecord(sheetUrl, GoogleSheetsService.SYS_MEDICATIONS)
                if (remoteResult.isSuccess) {
                    val remotePayload = remoteResult.getOrNull()
                    if (!remotePayload.isNullOrBlank()) {
                        val remoteList = localPreferences.parseMedicationsJson(remotePayload).filterNot { it.id in deletedIds }
                        val localMap = localPreferences.getMedicationsCache().filterNot { it.id in deletedIds }.associateBy { it.id }.toMutableMap()
                        val merged = mutableListOf<com.example.llegadadelbebe.domain.model.PrenatalMedication>()

                        for (remote in remoteList) {
                            val local = localMap.remove(remote.id)
                            if (local == null) {
                                merged.add(remote)
                            } else {
                                if (local.updatedAt > remote.updatedAt) {
                                    merged.add(local)
                                } else {
                                    merged.add(remote)
                                }
                            }

                            // Si es el teléfono del Papá, verificar si Mamá confirmó una toma hoy
                            if (isDad && remote.lastTakenDate == todayStr && remote.lastConfirmedBy.contains("Mamá", ignoreCase = true)) {
                                if (!localPreferences.isDadNotifiedForDose(remote.id, remote.lastTakenDate, remote.lastTakenTime)) {
                                    NotificationHelper.showDadMedicationNotification(
                                        context = context,
                                        medName = remote.name,
                                        dose = remote.dose,
                                        takenTime = remote.lastTakenTime,
                                        pillsRemaining = remote.pillsRemaining
                                    )
                                    localPreferences.markDadNotifiedForDose(remote.id, remote.lastTakenDate, remote.lastTakenTime)

                                    // Cancelar la alarma redundante del papá para esta vitamina
                                    remote.scheduleTimes.forEach { time ->
                                        MedicationAlarmScheduler.cancelMedicationAlarm(context, remote.id, time)
                                    }
                                }
                            }
                        }

                        // Preservar pendientes creados offline
                        for ((_, localOnly) in localMap) {
                            if (localOnly.id in pendingUploadIds && localOnly.id !in deletedIds) {
                                merged.add(localOnly)
                            }
                        }

                        localPreferences.saveMedicationsCache(merged)
                    }
                }
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
