package com.example.llegadadelbebe.core.notification

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.llegadadelbebe.core.security.SecureConfig
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.data.remote.GoogleSheetsService
import com.example.llegadadelbebe.ui.theme.LlegadaDelBebeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Actividad a Pantalla Completa (Full-Screen Intent) para el Nivel 3 de Recordatorio Persistente.
 * Se enciende la pantalla incluso si el teléfono está bloqueado y reproduce un sonido claro y suave
 * con volumen de alarma médica hasta que Mamá o Papá confirmen la toma o la pospongan.
 */
class MedicationAlertActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración para encender pantalla y mostrar sobre la pantalla de bloqueo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val medId = intent.getStringExtra(EXTRA_MED_ID) ?: ""
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: "Vitamina Prenatal"
        val dose = intent.getStringExtra(EXTRA_DOSE) ?: "1 tableta"
        val instructions = intent.getStringExtra(EXTRA_INSTRUCTIONS) ?: ""
        val scheduledTime = intent.getStringExtra(EXTRA_SCHEDULED_TIME) ?: "08:00"

        startGentleAlarmSound()

        setContent {
            LlegadaDelBebeTheme {
                MedicationAlertScreen(
                    medName = medName,
                    dose = dose,
                    instructions = instructions,
                    scheduledTime = scheduledTime,
                    onTakeDose = {
                        stopAlarmSound()
                        handleTakeDose(medId, medName)
                        finish()
                    },
                    onSnoozeDose = {
                        stopAlarmSound()
                        handleSnoozeDose(medId, medName, dose, instructions, scheduledTime)
                        finish()
                    }
                )
            }
        }
    }

    private fun startGentleAlarmSound() {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                    volume = 0.85f // Suave pero firme y audible
                }
                play()
            }
        } catch (_: Exception) {}
    }

    private fun stopAlarmSound() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (_: Exception) {}
    }

    private fun handleTakeDose(medId: String, medName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        notificationManager?.cancel(medId.hashCode())
        notificationManager?.cancel((medId + "_escalated").hashCode())
        MedicationAlarmScheduler.cancelSnoozeAlarm(this, medId)

        val localPreferences = LocalPreferences(this)
        val activeUser = localPreferences.getActiveUser()
        MedicationCloudSyncManager.recordDoseTakenLocally(
            context = this,
            medId = medId,
            confirmedBy = activeUser
        )

        // Fusionar y subir atómicamente a Google Sheets en segundo plano para que el Papá se entere en tiempo real
        val appContext = applicationContext
        val sheetUrl = SecureConfig.getGoogleSheetUrl()
        if (sheetUrl.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                MedicationCloudSyncManager.syncMedications(
                    context = appContext,
                    sheetUrl = sheetUrl,
                    forceUpload = true
                )
            }
        }
    }

    private fun handleSnoozeDose(
        medId: String,
        medName: String,
        dose: String,
        instructions: String,
        scheduledTime: String
    ) {
        val localPreferences = LocalPreferences(this)
        val todayStr = LocalDate.now().toString()
        localPreferences.incrementMedicationAttemptCount(medId, todayStr)

        // Posponer 10 minutos más manteniendo el Nivel 3 de insistencia
        MedicationAlarmScheduler.scheduleSnoozeAlarm(
            context = this,
            medId = medId,
            medName = medName,
            dose = dose,
            instructions = instructions,
            scheduledTime = scheduledTime,
            snoozeMinutes = 10,
            attemptCount = 3
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
    }

    companion object {
        const val EXTRA_MED_ID = "alert_extra_med_id"
        const val EXTRA_MED_NAME = "alert_extra_med_name"
        const val EXTRA_DOSE = "alert_extra_dose"
        const val EXTRA_INSTRUCTIONS = "alert_extra_instructions"
        const val EXTRA_SCHEDULED_TIME = "alert_extra_scheduled_time"
    }
}

@Composable
fun MedicationAlertScreen(
    medName: String,
    dose: String,
    instructions: String,
    scheduledTime: String,
    onTakeDose: () -> Unit,
    onSnoozeDose: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_scale")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_animation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF0F5),
                        Color(0xFFFCE4EC),
                        Color(0xFFFFFFFF)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Ícono pulsante llamativo
            Surface(
                shape = CircleShape,
                color = Color(0xFFE91E63).copy(alpha = 0.15f),
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🌸", fontSize = 46.sp)
                }
            }

            Text(
                text = "⏰ ¡Hora de tu Vitamina!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFC2185B),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Recordatorio prioritario de salud materna",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF880E4F),
                fontWeight = FontWeight.Medium
            )

            // Tarjeta central con los detalles del medicamento
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(2.dp, Color(0xFFF8BBD0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFCE4EC),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "⏰ Horario programado: $scheduledTime",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2185B),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Text(
                        text = medName,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "🥄 Dosis recomendada: $dose",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (instructions.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE3F2FD),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🍽️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = instructions,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0D47A1),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFF8E1),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💙", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Al pulsar 'Ya me la tomé', se le avisará a Papá Edward en tiempo real para su tranquilidad.",
                                fontSize = 11.sp,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botón Gigante de Confirmación
            Button(
                onClick = onTakeDose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text(
                    text = "✅ ¡YA ME LA TOMÉ!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            // Botón Secundario de Posponer
            OutlinedButton(
                onClick = onSnoozeDose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Color(0xFFC2185B))
            ) {
                Text(
                    text = "⏰ Posponer 10 minutos",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC2185B)
                )
            }
        }
    }
}
