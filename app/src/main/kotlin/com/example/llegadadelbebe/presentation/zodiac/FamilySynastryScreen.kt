package com.example.llegadadelbebe.presentation.zodiac

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.llegadadelbebe.domain.model.BabyProjectedChart
import com.example.llegadadelbebe.domain.model.DayMoonProjection
import com.example.llegadadelbebe.domain.model.FamilyElementalBalance
import com.example.llegadadelbebe.domain.model.FatherBabyFusionReport
import com.example.llegadadelbebe.domain.model.NatalChartProfile
import com.example.llegadadelbebe.domain.model.ParentCompatibilityReport
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySynastryScreen(
    viewModel: FamilySynastryViewModel,
    apiKey: String,
    onBack: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val father by viewModel.fatherProfile.collectAsStateWithLifecycle()
    val mother by viewModel.motherProfile.collectAsStateWithLifecycle()
    val baby by viewModel.babyChart.collectAsStateWithLifecycle()
    val moonWindow by viewModel.moonWindow.collectAsStateWithLifecycle()
    val parentReport by viewModel.parentReport.collectAsStateWithLifecycle()
    val fatherFusion by viewModel.fatherFusionReport.collectAsStateWithLifecycle()
    val elementalBalance by viewModel.elementalBalance.collectAsStateWithLifecycle()
    val simulatedHour by viewModel.simulatedBirthHour.collectAsStateWithLifecycle()
    val geminiReading by viewModel.geminiReading.collectAsStateWithLifecycle()
    val isLoadingReading by viewModel.isLoadingReading.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Barra Superior
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("←", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Sinastría y Fusión Astral ✨",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Papá Edward • Mamá Britney • Bebé",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = { showEditDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("⚙️ Ajustar", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }
        }

        // Resumen Rápido de la Tríada Familiar
        FamilySummaryBar(father = father, mother = mother, baby = baby)

        // Pestañas Principales
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { viewModel.setSelectedTab(0) },
                text = { Text("💑 Pareja", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { viewModel.setSelectedTab(1) },
                text = { Text("💙👶 Papá–Bebé", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { viewModel.setSelectedTab(2) },
                text = { Text("🌸👨‍👩‍👦 Tríada & IA", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
        }

        // Contenido según Pestaña
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                0 -> ParentCompatibilityTabContent(parentReport = parentReport, dad = father, mom = mother)
                1 -> FatherBabyFusionTabContent(
                    father = father,
                    baby = baby,
                    fatherFusion = fatherFusion,
                    moonWindow = moonWindow,
                    simulatedHour = simulatedHour,
                    onSelectHour = { viewModel.setSimulatedHour(it) }
                )
                2 -> FamilyTriadAndGeminiTabContent(
                    mother = mother,
                    baby = baby,
                    elementalBalance = elementalBalance,
                    geminiReading = geminiReading,
                    isLoadingReading = isLoadingReading,
                    onRequestReading = { viewModel.requestGeminiReading(apiKey) }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showEditDialog) {
        EditNatalDataDialog(
            father = father,
            mother = mother,
            onDismiss = { showEditDialog = false },
            onSaveDad = { d, t -> viewModel.updateFatherBirth(d, t) },
            onSaveMom = { d, t -> viewModel.updateMotherBirth(d, t) }
        )
    }
}

@Composable
private fun FamilySummaryBar(
    father: NatalChartProfile,
    mother: NatalChartProfile,
    baby: BabyProjectedChart
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MiniAstralBadge("💙 Papá Edward", "${father.sun.sign.symbol} ☀️", "${father.moon.sign.symbol} 🌙", "${father.ascendant.sign.symbol} ⬆️")
            MiniAstralBadge("🌸 Mamá Britney", "${mother.sun.sign.symbol} ☀️", "${mother.moon.sign.symbol} 🌙", "${mother.ascendant.sign.symbol} ⬆️")
            MiniAstralBadge("👶 Bebé ${baby.babyName}", "${baby.sun.sign.symbol} ☀️", "${baby.projectedMoon.sign.symbol} 🌙", "${baby.simulatedAscendant.sign.symbol} ⬆️")
        }
    }
}

@Composable
private fun MiniAstralBadge(role: String, sun: String, moon: String, asc: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = role, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = "$sun $moon $asc", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// -------------------------------------------------------------------------
// PESTAÑA 0: COMPATIBILIDAD PARENTAL (EDWARD ↔ BRITNEY)
// -------------------------------------------------------------------------

@Composable
private fun ParentCompatibilityTabContent(
    parentReport: ParentCompatibilityReport,
    dad: NatalChartProfile,
    mom: NatalChartProfile
) {
    // Tarjeta del Vínculo Maestro
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = parentReport.masterBondTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "99% Armonía",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = parentReport.masterBondDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    // Puntos de Compatibilidad
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "✨ Dinámica de Pareja y Crianza Compartida",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )

            AspectRow("☀️ Sol de Edward + Sol de Britney", parentReport.sunConnectionDescription)
            AspectRow("🌙 Luna de Edward + Luna de Britney", parentReport.moonConnectionDescription)
            AspectRow("⬆️ Ascendente de Edward + Ascendente de Britney", parentReport.ascendantConnectionDescription)
        }
    }

    // Indicadores Porcentuales
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "📊 Puntuaciones de Sinastría Familiar", fontWeight = FontWeight.Bold, fontSize = 15.sp)

            ScoreBar("💬 Comunicación y Complicidad Mental", parentReport.communicationScore, Color(0xFF1976D2))
            ScoreBar("👶 Trabajo en Equipo en la Crianza", parentReport.parentingHarmonyScore, Color(0xFF388E3C))
            ScoreBar("🛡️ Apoyo Práctico y Emocional en Posparto", parentReport.practicalSupportScore, Color(0xFFF57C00))
        }
    }

    // Consejo Práctico
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "💡", fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "Consejo para el Posparto", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = parentReport.postpartumCooperationTip, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AspectRow(title: String, description: String) {
    Column {
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScoreBar(label: String, score: Int, color: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = "$score%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

// -------------------------------------------------------------------------
// PESTAÑA 1: FUSIÓN PAPÁ ↔ BEBÉ
// -------------------------------------------------------------------------

@Composable
private fun FatherBabyFusionTabContent(
    father: NatalChartProfile,
    baby: BabyProjectedChart,
    fatherFusion: FatherBabyFusionReport,
    moonWindow: List<DayMoonProjection>,
    simulatedHour: Int,
    onSelectHour: (Int) -> Unit
) {
    // Fusión Luna de Papá
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = fatherFusion.moonFusionTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = fatherFusion.moonFusionExplanation, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🌿 Conexión Elemental: ${fatherFusion.fatherRoleBondSummary}",
                    modifier = Modifier.padding(10.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Fusión Ascendente de Papá
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = fatherFusion.ascendantFusionTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = fatherFusion.ascendantFusionExplanation, style = MaterialTheme.typography.bodyMedium)
        }
    }

    // Simulador de Hora de Nacimiento
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "⏰ Simulador: ¿A qué hora nacerá el bebé?",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Toca un turno para descubrir su Ascendente proyectado y cómo se fusiona con Papá Edward:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShiftChip("🌅 Mañana", selected = simulatedHour in 6..11) { onSelectHour(9) }
                ShiftChip("☀️ Tarde", selected = simulatedHour in 12..17) { onSelectHour(14) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShiftChip("🌆 Atardecer", selected = simulatedHour in 18..23) { onSelectHour(19) }
                ShiftChip("🌙 Madrugada", selected = simulatedHour in 0..5) { onSelectHour(3) }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Ascendente Proyectado: ${baby.simulatedAscendant.formatted}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = fatherFusion.simulatedAscendantSynergy, fontSize = 13.sp)
                }
            }
        }
    }

    // Carrusel Lunar de la Ventana de Parto
    Column {
        Text(
            text = "📅 Calendario Lunar de la Ventana de Parto (FPP ± 7 días)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "La Luna cambia cada ~2.3 días. Conoce en qué signo estará cada día:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(moonWindow) { day ->
                MoonDayCard(day)
            }
        }
    }
}

@Composable
private fun ShiftChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp) }
    )
}

@Composable
private fun MoonDayCard(day: DayMoonProjection) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(170.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (day.isFppDay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (day.isFppDay) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${day.date.dayOfMonth}/${day.date.monthValue}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (day.isFppDay) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
                        Text(
                            text = "FPP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                } else if (day.harmonyScoreWithDad >= 95 || day.harmonyScoreWithMom >= 95) {
                    Text("⭐", fontSize = 14.sp)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(text = day.moonPhaseEmoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = day.moonSign.fullDisplayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(text = day.moonPhaseName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                text = day.highlightReason,
                fontSize = 10.sp,
                maxLines = 3,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// -------------------------------------------------------------------------
// PESTAÑA 2: TRÍADA FAMILIAR & ORÁCULO IA
// -------------------------------------------------------------------------

@Composable
private fun FamilyTriadAndGeminiTabContent(
    mother: NatalChartProfile,
    baby: BabyProjectedChart,
    elementalBalance: FamilyElementalBalance,
    geminiReading: String,
    isLoadingReading: Boolean,
    onRequestReading: () -> Unit
) {
    // Vínculo Materno de Britney
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🌸 El Vínculo de Mamá Britney con el Bebé",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "La combinación de Mamá Britney (${mother.sun.displayName} ☀️, ${mother.moon.displayName} 🌙, ${mother.ascendant.displayName} ⬆️) envuelve al bebé en ternura, canciones, estímulo mental temprano y mucha protección. Su Sol en Aries le dará la fuerza y vitalidad para proteger a su hijo en cada etapa, mientras su Luna en Géminis creará momentos de complicidad, juegos y risas.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    // Balance Elemental
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "🌈 Balance de los 4 Elementos en la Familia",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = elementalBalance.balanceSummary,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                ElementPill("💨 Aire", elementalBalance.airPoints, Color(0xFF1976D2))
                ElementPill("🌿 Tierra", elementalBalance.earthPoints, Color(0xFF388E3C))
                ElementPill("🔥 Fuego", elementalBalance.firePoints, Color(0xFFE64A19))
                ElementPill("💧 Agua", elementalBalance.waterPoints, Color(0xFF0097A7))
            }
        }
    }

    // Oráculo con Gemini
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "🔮 Oráculo Familiar con IA (Gemini 3.8 Flash)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Genera una lectura personalizada y profunda cruzando los 9 puntos astrales de Papá Edward, Mamá Britney y su Bebé:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onRequestReading,
                enabled = !isLoadingReading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoadingReading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Interpretando el mapa astral familiar...")
                } else {
                    Text("✨ Consultar Lectura Astrológica a Gemini")
                }
            }

            if (geminiReading.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = geminiReading,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ElementPill(name: String, points: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.padding(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = color)
            Text(text = "$points pts", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = color)
        }
    }
}

// -------------------------------------------------------------------------
// DIÁLOGO PARA AJUSTAR FECHAS Y HORAS NATALES
// -------------------------------------------------------------------------

@Composable
private fun EditNatalDataDialog(
    father: NatalChartProfile,
    mother: NatalChartProfile,
    onDismiss: () -> Unit,
    onSaveDad: (LocalDate, LocalTime) -> Unit,
    onSaveMom: (LocalDate, LocalTime) -> Unit
) {
    var dadDateStr by remember { mutableStateOf(father.birthDate.toString()) }
    var dadTimeStr by remember { mutableStateOf(father.birthTime.toString()) }
    var momDateStr by remember { mutableStateOf(mother.birthDate.toString()) }
    var momTimeStr by remember { mutableStateOf(mother.birthTime.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚙️ Datos Natales de los Padres") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "💙 Papá (Edward):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(
                    value = dadDateStr,
                    onValueChange = { dadDateStr = it },
                    label = { Text("Fecha (AAAA-MM-DD)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = dadTimeStr,
                    onValueChange = { dadTimeStr = it },
                    label = { Text("Hora (HH:MM)") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "🌸 Mamá (Britney):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedTextField(
                    value = momDateStr,
                    onValueChange = { momDateStr = it },
                    label = { Text("Fecha (AAAA-MM-DD)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = momTimeStr,
                    onValueChange = { momTimeStr = it },
                    label = { Text("Hora (HH:MM)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDadDate = runCatching { LocalDate.parse(dadDateStr.trim()) }.getOrNull()
                    val parsedDadTime = runCatching { LocalTime.parse(dadTimeStr.trim()) }.getOrNull()
                    val parsedMomDate = runCatching { LocalDate.parse(momDateStr.trim()) }.getOrNull()
                    val parsedMomTime = runCatching { LocalTime.parse(momTimeStr.trim()) }.getOrNull()

                    if (parsedDadDate != null && parsedDadTime != null) {
                        onSaveDad(parsedDadDate, parsedDadTime)
                    }
                    if (parsedMomDate != null && parsedMomTime != null) {
                        onSaveMom(parsedMomDate, parsedMomTime)
                    }
                    onDismiss()
                }
            ) {
                Text("Guardar y Recalcular")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
