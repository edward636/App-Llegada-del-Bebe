package com.example.llegadadelbebe.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.llegadadelbebe.APP_VERSION_CODE
import com.example.llegadadelbebe.APP_VERSION_NAME
import com.example.llegadadelbebe.core.security.SecureConfig
import com.example.llegadadelbebe.core.update.OtaInstallOutcome
import com.example.llegadadelbebe.core.update.OtaUpdateManager
import com.example.llegadadelbebe.core.util.OemOptimizationHelper
import com.example.llegadadelbebe.core.util.PregnancyCalculator
import com.example.llegadadelbebe.core.util.ZodiacPredictor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    babyName: String,
    currentLmp: LocalDate,
    currentUser: String,
    onSaveBabySettings: (name: String, lmp: LocalDate, user: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf(babyName.ifBlank { "Nuestro Bebé" }) }
    var lmpDate by remember { mutableStateOf(currentLmp) }
    var selectedUser by remember { mutableStateOf(currentUser.ifBlank { "Papá (Edward)" }) }

    var otaCheckStatus by remember { mutableStateOf("") }
    var isCheckingOta by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    var awaitingInstallPermission by remember { mutableStateOf(false) }

    // Si el usuario fue a Ajustes de Android a activar "Instalar apps desconocidas" y regresa,
    // se lanza el instalador automáticamente sin tener que volver a descargar el APK.
    DisposableEffect(lifecycleOwner, awaitingInstallPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingInstallPermission) {
                if (OtaUpdateManager.canInstallPackages(context) && OtaUpdateManager.hasCachedApk(context)) {
                    awaitingInstallPermission = false
                    coroutineScope.launch {
                        otaCheckStatus = "🚀 Permiso concedido. Abriendo instalador de Android..."
                        OtaUpdateManager.launchCachedApkInstaller(context)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Cálculo dinámico en tiempo real derivado de la FUR seleccionada
    val calculatedStats = remember(lmpDate) {
        PregnancyCalculator.calculateFromLmp(lmpDate)
    }
    val calculatedZodiac = remember(calculatedStats.estimatedDueDate) {
        ZodiacPredictor.predictFromDate(calculatedStats.estimatedDueDate)
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ajustes del Bebé ⚙️💙",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "←",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Identificación de Usuario (Papá vs Mamá)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "👤 Usuario en este teléfono",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Selecciona quién está usando la app (se guarda permanentemente en este teléfono y etiqueta cada registro en Google Sheets):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                selectedUser = "Papá (Edward)"
                                onSaveBabySettings(name, lmpDate, selectedUser)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedUser.contains("Papá")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                "💙 Papá (Edward)",
                                color = if (selectedUser.contains("Papá")) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                selectedUser = "Mamá (Britney)"
                                onSaveBabySettings(name, lmpDate, selectedUser)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedUser.contains("Mamá")) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                "🌸 Mamá (Britney)",
                                color = if (selectedUser.contains("Mamá")) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Cálculo Preciso por Fecha de Última Regla (FUR / FUM)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "📅 Cálculo por Fecha de Última Regla (FUR)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre o Apodo del Bebé") },
                        placeholder = { Text("Ej: Liam, Mateo, Thiago") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = "Fecha de inicio de la última menstruación (FUM / FUR):",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    // Control Ergonómico de la FUR (Día, Mes, Año)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Día
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Día", style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { if (lmpDate.dayOfMonth > 1) lmpDate = lmpDate.minusDays(1) },
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) { Text("-", fontSize = 16.sp) }

                                Text(
                                    text = String.format("%02d", lmpDate.dayOfMonth),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )

                                Button(
                                    onClick = { if (lmpDate.dayOfMonth < lmpDate.lengthOfMonth()) lmpDate = lmpDate.plusDays(1) },
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) { Text("+", fontSize = 16.sp) }
                            }
                        }

                        // Mes
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Mes", style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { lmpDate = lmpDate.minusMonths(1) },
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) { Text("-", fontSize = 16.sp) }

                                Text(
                                    text = String.format("%02d", lmpDate.monthValue),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )

                                Button(
                                    onClick = { lmpDate = lmpDate.plusMonths(1) },
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) { Text("+", fontSize = 16.sp) }
                            }
                        }

                        // Año
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Año", style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { lmpDate = lmpDate.minusYears(1) },
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) { Text("-", fontSize = 16.sp) }

                                Text(
                                    text = lmpDate.year.toString(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                Button(
                                    onClick = { lmpDate = lmpDate.plusYears(1) },
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) { Text("+", fontSize = 16.sp) }
                            }
                        }
                    }

                    // Tarjeta de Resultado Clínico de Naegele
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "✨ Resultado Clínico Obstétrico:",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "• Edad gestacional: Semana ${calculatedStats.currentWeeks} + ${calculatedStats.currentDays} días",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "• Fecha Probable de Parto (FPP): ${calculatedStats.estimatedDueDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "• Signo del Bebé: ${calculatedZodiac.signName} (${calculatedZodiac.element})",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Faltan: ${calculatedStats.daysRemaining} días (~${calculatedStats.weeksRemaining} semanas)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Estado de Conexión Automática (Google Sheets & Gemini)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "🔒 Sincronización Automática y Caché Local",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Cada cambio se guarda al instante en la memoria interna del teléfono (Offline-First) y se sincroniza automáticamente con Google Sheets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(label = "Google Sheets", status = "🟢 Automático")
                        StatusChip(label = "Gemini 3.8", status = "🟢 Cámara Activa")
                    }
                }
            }

            // 4. Actualizaciones Inalámbricas OTA
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚀 Actualizaciones OTA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "v$APP_VERSION_NAME",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Distribución inalámbrica desde GitHub: Edward636/App-Llegada-del-Bebe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (otaCheckStatus.isNotBlank()) {
                        Text(
                            text = otaCheckStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (downloadProgress >= 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (downloadProgress >= 100) {
                                "✅ Descarga completada (100%)"
                            } else {
                                "Descargando actualización: $downloadProgress%"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            progress = { (downloadProgress.coerceIn(0, 100)) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }

                    val isActivelyDownloading = downloadProgress in 0..99

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                // Si ya se descargó al 100%, lanzar directamente el instalador desde caché
                                if (downloadProgress >= 100 && OtaUpdateManager.hasCachedApk(context)) {
                                    val launchResult = OtaUpdateManager.launchCachedApkInstaller(context)
                                    val outcome = launchResult.getOrNull()
                                    if (outcome == OtaInstallOutcome.PERMISSION_REQUIRED) {
                                        awaitingInstallPermission = true
                                        otaCheckStatus = "⚠️ Activa 'Confiar en esta fuente' en la pantalla que se abrió y regresa aquí."
                                    } else if (launchResult.isSuccess) {
                                        otaCheckStatus = "🚀 Instalador de Android abierto. Confirma 'Actualizar' en pantalla."
                                    } else {
                                        otaCheckStatus = "❌ Error al abrir instalador: ${launchResult.exceptionOrNull()?.localizedMessage}"
                                    }
                                    return@launch
                                }

                                isCheckingOta = true
                                otaCheckStatus = "🔍 Comprobando actualizaciones en GitHub..."
                                val result = OtaUpdateManager.checkForUpdate(
                                    currentVersionName = APP_VERSION_NAME,
                                    currentVersionCode = APP_VERSION_CODE,
                                    checkUrl = SecureConfig.getGoogleSheetUrl()
                                )
                                if (result.isSuccess) {
                                    val info = result.getOrNull()
                                    if (info != null && info.hasUpdate && info.apkUrl.isNotBlank()) {
                                        otaCheckStatus = "📥 ¡Nueva versión ${info.latestVersionName} lista! Descargando..."
                                        downloadProgress = 0
                                        val downloadResult = OtaUpdateManager.downloadAndInstallApk(
                                            context = context,
                                            apkUrl = info.apkUrl,
                                            onProgress = { progress -> downloadProgress = progress }
                                        )
                                        isCheckingOta = false
                                        if (downloadResult.isSuccess) {
                                            val outcome = downloadResult.getOrNull()
                                            if (outcome == OtaInstallOutcome.PERMISSION_REQUIRED) {
                                                awaitingInstallPermission = true
                                                otaCheckStatus = "⚠️ Activa 'Permitir desde esta fuente' en tu teléfono y regresa; se instalará al instante."
                                            } else {
                                                otaCheckStatus = "🚀 Instalador abierto. Confirma 'Actualizar' en pantalla."
                                            }
                                        } else {
                                            val err = downloadResult.exceptionOrNull()?.localizedMessage ?: "Error desconocido"
                                            otaCheckStatus = "❌ Error al descargar/instalar: $err"
                                            downloadProgress = -1
                                        }
                                    } else {
                                        isCheckingOta = false
                                        otaCheckStatus = "✅ ¡Tu app ya está en la versión más reciente (v$APP_VERSION_NAME)!"
                                    }
                                } else {
                                    isCheckingOta = false
                                    otaCheckStatus = "⚠️ No se pudo comprobar actualización. Revisa la conexión a internet."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isCheckingOta && !isActivelyDownloading
                    ) {
                        if (isCheckingOta || isActivelyDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        } else {
                            Text(
                                if (downloadProgress >= 100) {
                                    "📲 Instalar Actualización Descargada"
                                } else {
                                    "Buscar y Actualizar Ahora"
                                }
                            )
                        }
                    }
                }
            }

            // 5. Optimización de Batería y Sistema (HyperOS / ColorOS)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "⚡ Optimización de Sistema (HyperOS / ColorOS)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Dispositivo: ${OemOptimizationHelper.detectedOemName}.\n" +
                               "Para que las alarmas de vitaminas suenen con total precisión y nunca sean cerradas en segundo plano por el sistema, activa el inicio automático y batería sin restricciones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(OemOptimizationHelper.createIgnoreBatteryOptimizationIntent(context))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Batería Sin Restricción", fontSize = 11.sp, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                OemOptimizationHelper.openOemAutostartSettings(context)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Inicio Automático", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }

            // 6. Guardar Ajustes
            Button(
                onClick = {
                    onSaveBabySettings(name, lmpDate, selectedUser)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Guardar Cambios", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun StatusChip(label: String, status: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(text = status, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
