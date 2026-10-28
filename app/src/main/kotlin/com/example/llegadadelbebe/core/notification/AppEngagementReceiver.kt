package com.example.llegadadelbebe.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.core.util.PregnancyCalculator
import java.time.LocalDate
import java.util.Calendar

class AppEngagementReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val localPreferences = LocalPreferences(context)

        // Si el teléfono se reinicia o se actualiza la app, reactiva el planificador
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AppEngagementScheduler.scheduleNextCheck(context)
            rescheduleMedicationsIfAny(context, localPreferences)
            return
        }

        // Chequeo de inactividad (> 24 horas sin abrir la app)
        val lastOpenMillis = localPreferences.getLastAppOpenMillis()
        val hoursInactive = (System.currentTimeMillis() - lastOpenMillis) / (1000 * 60 * 60)

        if (hoursInactive >= 24) {
            val todayEpoch = LocalDate.now().toEpochDay()
            if (localPreferences.getLastEngagementNotifEpochDay() != todayEpoch) {
                val stats = PregnancyCalculator.calculateFromLmp(localPreferences.getLmpDate())
                val isMom = localPreferences.getActiveUser().contains("Mamá", ignoreCase = true)
                val babyName = localPreferences.getBabyName()

                val title = if (isMom) {
                    "🌸 ¡Hola Mamita Britney! ¿Cómo va la pancita?"
                } else {
                    "💙 ¡Hola Papá Edward! Un abrazo para la familia"
                }

                val content = if (isMom) {
                    "Tu bebé ya tiene el tamaño de un(a) ${stats.babySizeComparison} (Semana ${stats.currentWeeks} + ${stats.currentDays}d). Entra a ver el mensaje de hoy y registrar tus vitaminas 💕"
                } else {
                    "El bebé $babyName tiene el tamaño de un(a) ${stats.babySizeComparison}. Recuerda consentir a Britney y ver el avance de los preparativos 🍼"
                }

                NotificationHelper.showEngagementNotification(context, title, content)
                localPreferences.saveLastEngagementNotifEpochDay(todayEpoch)
            }
        }

        // Re-agendar el siguiente chequeo diario a las 11:30 AM
        AppEngagementScheduler.scheduleNextCheck(context)
    }

    private fun rescheduleMedicationsIfAny(context: Context, prefs: LocalPreferences) {
        val meds = prefs.getMedicationsCache()
        for (med in meds) {
            for (time in med.scheduleTimes) {
                MedicationAlarmScheduler.scheduleMedicationAlarm(
                    context = context,
                    medId = med.id,
                    medName = med.name,
                    dose = med.dose,
                    instructions = med.foodInstructions,
                    timeStr = time
                )
            }
        }
    }
}

object AppEngagementScheduler {

    private const val ENGAGEMENT_REQUEST_CODE = 9091

    fun scheduleNextCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Agendar para las 11:30 AM del día (o del siguiente día si ya pasaron las 11:30 AM)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 11)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, AppEngagementReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ENGAGEMENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
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
        } catch (_: SecurityException) {
            // Manejo de seguridad en caso de restricciones de batería o permisos
        }
    }
}
