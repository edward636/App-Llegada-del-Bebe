package com.example.llegadadelbebe.presentation.medications

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.llegadadelbebe.domain.model.MedicationAdherenceSummary
import com.example.llegadadelbebe.domain.model.PrenatalMedication
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Composable
fun PrenatalMedicationsScreen(
    viewModel: PrenatalMedicationsViewModel,
    geminiApiKey: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val adherence by viewModel.adherenceSummary.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzingPhoto.collectAsStateWithLifecycle()
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()

    var showEditorDialog by remember { mutableStateOf(false) }
    var selectedMedForEdit by remember { mutableStateOf<PrenatalMedication?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var analysisErrorMessage by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingPhotoUri != null) {
            val base64 = runCatching {
                val input = context.contentResolver.openInputStream(pendingPhotoUri!!)
                val bytes = input?.use { it.readBytes() } ?: return@runCatching ""
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }.getOrDefault("")

            if (base64.isNotBlank()) {
                viewModel.analyzeMedicationPhoto(base64, geminiApiKey) { parsed ->
                    if (parsed != null) {
                        val newMed = PrenatalMedication(
                            name = parsed.name,
                            activeIngredient = parsed.activeIngredient,
                            dose = parsed.dose,
                            frequencyHours = parsed.frequencyHours,
                            scheduleTimes = parsed.scheduleTimes,
                            foodInstructions = parsed.foodInstructions,
                            clinicalWarnings = parsed.clinicalWarnings,
                            pillsRemaining = parsed.pillsInBox,
                            totalPillsInPackage = parsed.pillsInBox,
                            notes = parsed.notes,
                            photoBase64 = base64
                        )
                        selectedMedForEdit = newMed
                        showEditorDialog = true
                    } else {
                        analysisErrorMessage = "No se pudieron extraer datos legibles de la caja. Puedes registrarla manualmente."
                    }
                }
            }
        }
    }

    val launchCamera = {
        val file = File(context.cacheDir, "camera_med_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhotoUri = uri
        cameraLauncher.launch(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            // Cabecera superior
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Text("←", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        text = "💊 Vitaminas de Britney",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Alarmas y tomas duales (Britney & Edward)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = if (activeUser.contains("Mamá", ignoreCase = true)) "🌸 Mamá" else "💙 Papá",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tarjeta Hero con Micro-animación de Adherencia
            AdherenceHeroCard(adherence = adherence)

            if (isAnalyzing) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Gemini analiza la caja de pastillas/récipe...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (analysisErrorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "⚠️ $analysisErrorMessage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { analysisErrorMessage = "" }, modifier = Modifier.size(24.dp)) {
                            Text("✕", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lista de Medicamentos
            if (medications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💊", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No hay vitaminas registradas aún.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Toma una foto a la caja con la cámara para auto-registrarla con IA.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(medications, key = { it.id }) { med ->
                        MedicationCard(
                            med = med,
                            activeUser = activeUser,
                            onMarkTaken = { viewModel.markMedicationAsTaken(med.id) },
                            onEdit = {
                                selectedMedForEdit = med
                                showEditorDialog = true
                            },
                            onDelete = { viewModel.deleteMedication(med.id) }
                        )
                    }
                }
            }
        }

        // Botón Flotante para Escanear / Registrar
        FloatingActionButton(
            onClick = {
                selectedMedForEdit = null
                showEditorDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("📷 Escanear Caja", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }

    if (showEditorDialog) {
        MedicationEditorDialog(
            initial = selectedMedForEdit,
            onDismiss = {
                showEditorDialog = false
                selectedMedForEdit = null
            },
            onScanCamera = launchCamera,
            onSave = { savedMed ->
                viewModel.saveMedication(savedMed)
                showEditorDialog = false
                selectedMedForEdit = null
            }
        )
    }
}

@Composable
fun AdherenceHeroCard(adherence: MedicationAdherenceSummary) {
    val animatedProgress by animateFloatAsState(
        targetValue = adherence.adherencePercent,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "adherence_progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🌿", fontSize = 22.sp)
                    Column {
                        Text(
                            text = if (adherence.isFullyCompletedToday) "¡Tomas de hoy completadas! 🎉" else "Progreso de Vitaminas Hoy",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (adherence.isFullyCompletedToday) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${adherence.takenTodayCount} de ${adherence.totalMedications} tomas realizadas",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (adherence.isFullyCompletedToday) Color(0xFF2E7D32).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = if (adherence.isFullyCompletedToday) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (adherence.isFullyCompletedToday) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Alerta si hay medicamentos con bajo stock
            if (adherence.lowStockCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF3E0),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Hay ${adherence.lowStockCount} vitamina(s) por agotarse (quedan ≤ 5 pastillas).",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MedicationCard(
    med: PrenatalMedication,
    activeUser: String,
    onMarkTaken: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isTaken = med.isTakenToday
    val isMom = activeUser.contains("Mamá", ignoreCase = true)

    val scaleAnim by animateFloatAsState(
        targetValue = if (isTaken) 1.0f else 1.02f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "med_card_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scaleAnim)
            .clickable { onEdit() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTaken) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isTaken) Color(0xFF81C784) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTaken) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isTaken) Color(0xFF2E7D32).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (isTaken) "✅" else "💊", fontSize = 18.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = med.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (isTaken) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${med.activeIngredient.ifBlank { med.name }} • ${med.dose}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "⏰ ${med.scheduleTimes.joinToString(", ")}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Text("🗑️", fontSize = 12.sp)
                    }
                }
            }

            // Indicaciones de Comidas & Advertencias Clínicas
            if (med.foodInstructions.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🍽️", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = med.foodInstructions,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (med.clinicalWarnings.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ ${med.clinicalWarnings}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFC62828),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Pie de la tarjeta: Botón de Toma & Stock Restante
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock restante
                Text(
                    text = if (med.isOutOfStock) "❌ Agotada" else "📦 ${med.pillsRemaining} pastillas (~${med.daysOfSupplyRemaining} d)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (med.isLowStock || med.isOutOfStock) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Botón interactivo de toma
                if (isTaken) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "✅ Tomada hoy (${med.lastTakenTime})",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                    }
                } else {
                    Button(
                        onClick = onMarkTaken,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isMom) "✅ Ya me la tomé" else "✅ Confirmar toma",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MedicationEditorDialog(
    initial: PrenatalMedication?,
    onDismiss: () -> Unit,
    onScanCamera: () -> Unit,
    onSave: (PrenatalMedication) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var activeIngredient by remember { mutableStateOf(initial?.activeIngredient ?: "") }
    var dose by remember { mutableStateOf(initial?.dose ?: "1 tableta") }
    var frequencyHours by remember { mutableIntStateOf(initial?.frequencyHours ?: 24) }
    var timeText by remember { mutableStateOf(initial?.scheduleTimes?.firstOrNull() ?: "08:00") }
    var foodInstructions by remember { mutableStateOf(initial?.foodInstructions ?: "Con el desayuno") }
    var clinicalWarnings by remember { mutableStateOf(initial?.clinicalWarnings ?: "") }
    var pillsRemaining by remember { mutableIntStateOf(initial?.pillsRemaining ?: 30) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initial == null) "💊 Nueva Vitamina Prenatal" else "✏️ Editar Vitamina",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedButton(
                        onClick = onScanCamera,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("📷 Escanear con IA", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre Comercial (ej: Ácido Fólico)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = activeIngredient,
                    onValueChange = { activeIngredient = it },
                    label = { Text("Principio Activo y Concentración (ej: 1 mg)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dose,
                        onValueChange = { dose = it },
                        label = { Text("Dosis (ej: 1 tableta)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("Hora (ej: 08:00)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = foodInstructions,
                    onValueChange = { foodInstructions = it },
                    label = { Text("Indicación con Comidas (ej: En ayunas, lejos de lácteos)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = clinicalWarnings,
                    onValueChange = { clinicalWarnings = it },
                    label = { Text("Advertencia / Interacción (ej: Separar del Calcio)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = pillsRemaining.toString(),
                    onValueChange = { pillsRemaining = it.toIntOrNull()?.coerceAtLeast(0) ?: 0 },
                    label = { Text("Pastillas restantes en la caja") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(
                                    (initial ?: PrenatalMedication(name = name)).copy(
                                        name = name.trim(),
                                        activeIngredient = activeIngredient.trim(),
                                        dose = dose.trim(),
                                        frequencyHours = frequencyHours,
                                        scheduleTimes = listOf(timeText.trim().ifBlank { "08:00" }),
                                        foodInstructions = foodInstructions.trim(),
                                        clinicalWarnings = clinicalWarnings.trim(),
                                        pillsRemaining = pillsRemaining,
                                        totalPillsInPackage = pillsRemaining.coerceAtLeast(initial?.totalPillsInPackage ?: 30)
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Guardar Vitamina")
                    }
                }
            }
        }
    }
}
