package com.example.llegadadelbebe.core.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.llegadadelbebe.MainActivity
import com.example.llegadadelbebe.core.security.SecureConfig
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.data.remote.GoogleSheetsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

class MedicationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val medId = intent.getStringExtra(EXTRA_MED_ID) ?: return
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: "Vitamina Prenatal"
        val dose = intent.getStringExtra(EXTRA_DOSE) ?: "1 tableta"
        val instructions = intent.getStringExtra(EXTRA_INSTRUCTIONS) ?: ""
        val scheduledTime = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: "08:00"
        val attemptCount = intent.getIntExtra(EXTRA_ATTEMPT_COUNT, 1)

        val localPreferences = LocalPreferences(context)
        val activeUser = localPreferences.getActiveUser()
        val isMom = activeUser.contains("Mamá", ignoreCase = true)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationHelper.createNotificationChannels(context)

        val todayStr = LocalDate.now().toString()

        when (action) {
            ACTION_TAKE_DOSE -> {
                // Cancelar notificaciones activas de esta vitamina
                notificationManager.cancel(medId.hashCode())
                notificationManager.cancel((medId + "_escalated").hashCode())
                MedicationAlarmScheduler.cancelSnoozeAlarm(context, medId)

                val timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                val pillsRemaining = MedicationCloudSyncManager.recordDoseTakenLocally(
                    context = context,
                    medId = medId,
                    confirmedBy = activeUser
                )
                MedicationAlarmScheduler.scheduleNextDailyAlarm(
                    context = context,
                    medId = medId,
                    medName = medName,
                    dose = dose,
                    instructions = instructions,
                    timeStr = scheduledTime
                )

                // Sincronización Atómica con Google Sheets usando goAsync() + Mutex
                // Fusiona el estado remoto antes de subir para nunca pisar vitaminas cargadas en el otro teléfono
                val pendingResult = goAsync()
                val sheetUrl = SecureConfig.getGoogleSheetUrl()
                if (sheetUrl.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            MedicationCloudSyncManager.syncMedications(
                                context = context,
                                sheetUrl = sheetUrl,
                                forceUpload = true
                            )
                        } finally {
                            pendingResult.finish()
                        }
                    }
                } else {
                    pendingResult.finish()
                }

                val confirmTitle = if (isMom) "🌸 ¡Excelente, Mamita Britney!" else "💙 ¡Registrado por Papá Edward!"
                val confirmText = "Dosis de $medName registrada con éxito a las $timeNow. Quedan $pillsRemaining pastillas en la caja."

                val confirmationNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.checkbox_on_background)
                    .setContentTitle(confirmTitle)
                    .setContentText(confirmText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(confirmText))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setTimeoutAfter(7000)
                    .build()

                notificationManager.notify((medId + "_confirmed").hashCode(), confirmationNotification)
            }

            ACTION_SNOOZE_DOSE -> {
                notificationManager.cancel(medId.hashCode())
                notificationManager.cancel((medId + "_escalated").hashCode())

                val nextAttempt = localPreferences.incrementMedicationAttemptCount(medId, todayStr)
                val snoozeMinutes = if (nextAttempt <= 2) DEFAULT_SNOOZE_MINUTES else 10

                MedicationAlarmScheduler.scheduleSnoozeAlarm(
                    context = context,
                    medId = medId,
                    medName = medName,
                    dose = dose,
                    instructions = instructions,
                    scheduledTime = scheduledTime,
                    snoozeMinutes = snoozeMinutes,
                    attemptCount = nextAttempt
                )
            }

            else -> {
                // Verificar primero si la dosis ya fue tomada hoy (por ejemplo confirmada en el otro teléfono)
                val currentMeds = localPreferences.getMedicationsCache()
                val currentMed = currentMeds.firstOrNull { it.id == medId }
                if (currentMed?.isTakenToday == true) {
                    notificationManager.cancel(medId.hashCode())
                    notificationManager.cancel((medId + "_escalated").hashCode())
                    MedicationAlarmScheduler.cancelSnoozeAlarm(context, medId)
                    MedicationAlarmScheduler.scheduleNextDailyAlarm(
                        context = context,
                        medId = medId,
                        medName = medName,
                        dose = dose,
                        instructions = instructions,
                        timeStr = scheduledTime
                    )
                    return
                }

                val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
                val currentLevel = if (isSnooze) attemptCount else localPreferences.getMedicationAttemptCount(medId, todayStr)

                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    medId.hashCode(),
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Acción: "Ya me la tomé!" / "Confirmar toma"
                val takeIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                    this.action = ACTION_TAKE_DOSE
                    putExtra(EXTRA_MED_ID, medId)
                    putExtra(EXTRA_MED_NAME, medName)
                    putExtra(EXTRA_DOSE, dose)
                    putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
                }
                val takePendingIntent = PendingIntent.getBroadcast(
                    context,
                    (medId + "_take").hashCode(),
                    takeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Acción: "Posponer"
                val snoozeIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                    this.action = ACTION_SNOOZE_DOSE
                    putExtra(EXTRA_MED_ID, medId)
                    putExtra(EXTRA_MED_NAME, medName)
                    putExtra(EXTRA_DOSE, dose)
                    putExtra(EXTRA_INSTRUCTIONS, instructions)
                    putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
                    putExtra(EXTRA_ATTEMPT_COUNT, currentLevel)
                }
                val snoozePendingIntent = PendingIntent.getBroadcast(
                    context,
                    (medId + "_snooze").hashCode(),
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val takeActionLabel = if (isMom) "✅ ¡Ya me la tomé!" else "✅ Confirmar toma"
                val snoozeLabel = if (currentLevel >= 3) "⏰ Posponer 10 min" else "⏰ Posponer 15 min"

                when {
                    // NIVEL 3 EN ADELANTE: Pantalla Completa + Alarma Persistente
                    currentLevel >= 3 -> {
                        val alertIntent = Intent(context, MedicationAlertActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(MedicationAlertActivity.EXTRA_MED_ID, medId)
                            putExtra(MedicationAlertActivity.EXTRA_MED_NAME, medName)
                            putExtra(MedicationAlertActivity.EXTRA_DOSE, dose)
                            putExtra(MedicationAlertActivity.EXTRA_INSTRUCTIONS, instructions)
                            putExtra(MedicationAlertActivity.EXTRA_SCHEDULED_TIME, scheduledTime)
                        }

                        val fullScreenPendingIntent = PendingIntent.getActivity(
                            context,
                            (medId + "_fullscreen").hashCode(),
                            alertIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val title = if (isMom) {
                            "🚨 [Aviso Urgente - Pantalla Completa] 🌸 ¡Britney, tu vitamina!"
                        } else {
                            "🚨 [Aviso Urgente - Pantalla Completa] 💙 ¡Edward, recuerda la vitamina a Britney!"
                        }

                        val content = "Toca para confirmar: $medName ($dose). $instructions"

                        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_MEDICATIONS_ESCALATED)
                            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                            .setContentTitle(title)
                            .setContentText(content)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setCategory(NotificationCompat.CATEGORY_ALARM)
                            .setFullScreenIntent(fullScreenPendingIntent, true)
                            .setContentIntent(fullScreenPendingIntent)
                            .setAutoCancel(false)
                            .setOngoing(true)
                            .addAction(android.R.drawable.checkbox_on_background, takeActionLabel, takePendingIntent)
                            .addAction(android.R.drawable.ic_menu_recent_history, snoozeLabel, snoozePendingIntent)
                            .build()

                        notificationManager.notify(medId.hashCode(), notification)

                        // Re-programar insistencia cada 10 minutos hasta que se confirme la toma
                        MedicationAlarmScheduler.scheduleSnoozeAlarm(
                            context = context,
                            medId = medId,
                            medName = medName,
                            dose = dose,
                            instructions = instructions,
                            scheduledTime = scheduledTime,
                            snoozeMinutes = 10,
                            attemptCount = 3
                        )
                    }

                    // NIVEL 2: Notificación Invasiva con Sonido Fuerte / Heads-Up
                    currentLevel == 2 -> {
                        val title = if (isMom) {
                            "⏰ [Segundo Aviso] 🌸 ¡Britney, tu vitamina te espera!"
                        } else {
                            "⏰ [Segundo Aviso] 💙 ¡Edward, Britney aún no se ha tomado su vitamina!"
                        }

                        val content = "Es muy importante para el bebé: $medName ($dose). $instructions"

                        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_MEDICATIONS_ESCALATED)
                            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                            .setContentTitle(title)
                            .setContentText(content)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(NotificationCompat.CATEGORY_ALARM)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .addAction(android.R.drawable.checkbox_on_background, takeActionLabel, takePendingIntent)
                            .addAction(android.R.drawable.ic_menu_recent_history, snoozeLabel, snoozePendingIntent)
                            .build()

                        notificationManager.notify(medId.hashCode(), notification)

                        // Si no contesta en 15 minutos, auto-escalar a Nivel 3 (Pantalla Completa)
                        MedicationAlarmScheduler.scheduleSnoozeAlarm(
                            context = context,
                            medId = medId,
                            medName = medName,
                            dose = dose,
                            instructions = instructions,
                            scheduledTime = scheduledTime,
                            snoozeMinutes = DEFAULT_SNOOZE_MINUTES,
                            attemptCount = 3
                        )
                    }

                    // NIVEL 1: Notificación Normal
                    else -> {
                        val title = if (isMom) {
                            "🌸 ¡Hora de tu vitamina, Mamita Britney!"
                        } else {
                            "💝 ¡Recordatorio para Papá Edward!"
                        }

                        val content = if (isMom) {
                            "Te toca tomar: $medName ($dose). $instructions"
                        } else {
                            "Es hora de que Britney tome: $medName ($dose). ¡Recuérdale con un vasito de agua! 💧"
                        }

                        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                            .setContentTitle(title)
                            .setContentText(content)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(NotificationCompat.CATEGORY_ALARM)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .addAction(android.R.drawable.checkbox_on_background, takeActionLabel, takePendingIntent)
                            .addAction(android.R.drawable.ic_menu_recent_history, snoozeLabel, snoozePendingIntent)
                            .build()

                        notificationManager.notify(medId.hashCode(), notification)

                        // Re-programar para el día siguiente a la misma hora
                        MedicationAlarmScheduler.scheduleNextDailyAlarm(
                            context, medId, medName, dose, instructions, scheduledTime
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = NotificationHelper.CHANNEL_MEDICATIONS
        const val EXTRA_MED_ID = "extra_med_id"
        const val EXTRA_MED_NAME = "extra_med_name"
        const val EXTRA_DOSE = "extra_dose"
        const val EXTRA_INSTRUCTIONS = "extra_instructions"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
        const val EXTRA_ATTEMPT_COUNT = "extra_attempt_count"

        const val ACTION_TAKE_DOSE = "com.example.llegadadelbebe.ACTION_TAKE_DOSE"
        const val ACTION_SNOOZE_DOSE = "com.example.llegadadelbebe.ACTION_SNOOZE_DOSE"

        const val DEFAULT_SNOOZE_MINUTES = 15
    }
}

object MedicationAlarmScheduler {

    fun scheduleMedicationAlarm(
        context: Context,
        medId: String,
        medName: String,
        dose: String,
        instructions: String,
        timeStr: String, // "HH:mm"
        attemptCount: Int = 1,
        forceTomorrow: Boolean = false
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (forceTomorrow || before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(MedicationAlarmReceiver.EXTRA_MED_ID, medId)
            putExtra(MedicationAlarmReceiver.EXTRA_MED_NAME, medName)
            putExtra(MedicationAlarmReceiver.EXTRA_DOSE, dose)
            putExtra(MedicationAlarmReceiver.EXTRA_INSTRUCTIONS, instructions)
            putExtra(MedicationAlarmReceiver.EXTRA_SCHEDULED_TIME, timeStr)
            putExtra(MedicationAlarmReceiver.EXTRA_IS_SNOOZE, false)
            putExtra(MedicationAlarmReceiver.EXTRA_ATTEMPT_COUNT, attemptCount)
        }

        val requestCode = (medId + timeStr).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            }
        }
    }

    fun scheduleSnoozeAlarm(
        context: Context,
        medId: String,
        medName: String,
        dose: String,
        instructions: String,
        scheduledTime: String,
        snoozeMinutes: Int = MedicationAlarmReceiver.DEFAULT_SNOOZE_MINUTES,
        attemptCount: Int = 2
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMillis = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(MedicationAlarmReceiver.EXTRA_MED_ID, medId)
            putExtra(MedicationAlarmReceiver.EXTRA_MED_NAME, medName)
            putExtra(MedicationAlarmReceiver.EXTRA_DOSE, dose)
            putExtra(MedicationAlarmReceiver.EXTRA_INSTRUCTIONS, instructions)
            putExtra(MedicationAlarmReceiver.EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(MedicationAlarmReceiver.EXTRA_IS_SNOOZE, true)
            putExtra(MedicationAlarmReceiver.EXTRA_ATTEMPT_COUNT, attemptCount)
        }

        val requestCode = (medId + "_snooze_alarm").hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            runCatching {
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
            }
        }
    }

    fun scheduleNextDailyAlarm(
        context: Context,
        medId: String,
        medName: String,
        dose: String,
        instructions: String,
        timeStr: String
    ) {
        scheduleMedicationAlarm(
            context = context,
            medId = medId,
            medName = medName,
            dose = dose,
            instructions = instructions,
            timeStr = timeStr,
            attemptCount = 1,
            forceTomorrow = true
        )
    }

    fun cancelSnoozeAlarm(context: Context, medId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeIntent = Intent(context, MedicationAlarmReceiver::class.java)
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (medId + "_snooze_alarm").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(snoozePendingIntent)
    }

    fun cancelMedicationAlarm(context: Context, medId: String, timeStr: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MedicationAlarmReceiver::class.java)
        val requestCode = (medId + timeStr).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        cancelSnoozeAlarm(context, medId)
    }
}
