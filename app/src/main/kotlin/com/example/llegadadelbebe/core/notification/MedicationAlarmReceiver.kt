package com.example.llegadadelbebe.core.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.llegadadelbebe.MainActivity
import com.example.llegadadelbebe.core.storage.LocalPreferences
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

        val localPreferences = LocalPreferences(context)
        val activeUser = localPreferences.getActiveUser()
        val isMom = activeUser.contains("Mamá", ignoreCase = true)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationHelper.createNotificationChannels(context)

        when (action) {
            ACTION_TAKE_DOSE -> {
                // El usuario tocó "Ya me la tomé" desde la notificación
                notificationManager.cancel(medId.hashCode())

                val todayStr = LocalDate.now().toString()
                val timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

                val currentMeds = localPreferences.getMedicationsCache().toMutableList()
                val idx = currentMeds.indexOfFirst { it.id == medId }
                var pillsRemaining = 30

                if (idx != -1) {
                    val old = currentMeds[idx]
                    val updated = old.copy(
                        lastTakenDate = todayStr,
                        lastTakenTime = timeNow,
                        lastConfirmedBy = activeUser,
                        pillsRemaining = (old.pillsRemaining - 1).coerceAtLeast(0),
                        updatedAt = System.currentTimeMillis()
                    )
                    pillsRemaining = updated.pillsRemaining
                    currentMeds[idx] = updated
                    localPreferences.saveMedicationsCache(currentMeds)
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
                    .setTimeoutAfter(7000) // Se descarta automáticamente a los 7 segundos
                    .build()

                notificationManager.notify((medId + "_confirmed").hashCode(), confirmationNotification)
            }

            ACTION_SNOOZE_DOSE -> {
                // El usuario tocó "Posponer 15 min" desde la notificación
                notificationManager.cancel(medId.hashCode())

                MedicationAlarmScheduler.scheduleSnoozeAlarm(
                    context = context,
                    medId = medId,
                    medName = medName,
                    dose = dose,
                    instructions = instructions,
                    scheduledTime = scheduledTime,
                    snoozeMinutes = DEFAULT_SNOOZE_MINUTES
                )
            }

            else -> {
                // Alarma disparada por AlarmManager (hora exacta o pospuesta)
                val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)

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

                // Acción: "Posponer 15 min"
                val snoozeIntent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                    this.action = ACTION_SNOOZE_DOSE
                    putExtra(EXTRA_MED_ID, medId)
                    putExtra(EXTRA_MED_NAME, medName)
                    putExtra(EXTRA_DOSE, dose)
                    putExtra(EXTRA_INSTRUCTIONS, instructions)
                    putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
                }
                val snoozePendingIntent = PendingIntent.getBroadcast(
                    context,
                    (medId + "_snooze").hashCode(),
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val titlePrefix = if (isSnooze) "⏰ [Pospuesto] " else ""
                val title = if (isMom) {
                    "$titlePrefix🌸 ¡Hora de tu vitamina, Mamita Britney!"
                } else {
                    "$titlePrefix💝 ¡Recordatorio para Papá Edward!"
                }

                val content = if (isMom) {
                    "Te toca tomar: $medName ($dose). $instructions"
                } else {
                    "Es hora de que Britney tome: $medName ($dose). ¡Recuérdale con un vasito de agua! 💧"
                }

                val takeActionLabel = if (isMom) "✅ ¡Ya me la tomé!" else "✅ Confirmar toma"
                val snoozeActionLabel = "⏰ Posponer 15 min"

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
                    .addAction(android.R.drawable.ic_menu_recent_history, snoozeActionLabel, snoozePendingIntent)
                    .build()

                notificationManager.notify(medId.hashCode(), notification)

                // Si era la alarma diaria estándar, re-programar para el día siguiente a la misma hora
                if (!isSnooze) {
                    MedicationAlarmScheduler.scheduleNextDailyAlarm(context, medId, medName, dose, instructions, scheduledTime)
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "prenatal_medications_channel"
        const val EXTRA_MED_ID = "extra_med_id"
        const val EXTRA_MED_NAME = "extra_med_name"
        const val EXTRA_DOSE = "extra_dose"
        const val EXTRA_INSTRUCTIONS = "extra_instructions"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"

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
        timeStr: String // "HH:mm"
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
            if (before(Calendar.getInstance())) {
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
            // Manejo seguro si falta permiso exact alarm
        }
    }

    fun scheduleSnoozeAlarm(
        context: Context,
        medId: String,
        medName: String,
        dose: String,
        instructions: String,
        scheduledTime: String,
        snoozeMinutes: Int = MedicationAlarmReceiver.DEFAULT_SNOOZE_MINUTES
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
            // Manejo seguro
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
        scheduleMedicationAlarm(context, medId, medName, dose, instructions, timeStr)
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
    }
}
