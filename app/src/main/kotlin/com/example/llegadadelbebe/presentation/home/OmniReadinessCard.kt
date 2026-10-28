package com.example.llegadadelbebe.presentation.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.llegadadelbebe.domain.usecase.OmniDashboardSnapshot
import com.example.llegadadelbebe.core.ui.FormattedMarkdownText

/**
 * Tablero 360° del Índice de Preparación Familiar y Diagnóstico Integral con Gemini.
 */
@Composable
fun OmniReadinessCard(
    snapshot: OmniDashboardSnapshot,
    isGeneratingReport: Boolean,
    reportText: String?,
    onGenerateReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showReportDialog by remember { mutableStateOf(false) }

    val animatedScore by animateFloatAsState(
        targetValue = snapshot.readinessScorePercent / 100f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "readiness_score"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🎯 Control Perfecto 360°",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Índice de preparación para el parto y el bebé",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Anillo de progreso con porcentaje
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                    CircularProgressIndicator(
                        progress = { animatedScore },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        strokeWidth = 5.dp
                    )
                    Text(
                        text = "${snapshot.readinessScorePercent}%",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Desglose de los 3 pilares
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PillarProgressRow(
                    label = "📦 Inventario Clínico",
                    percent = snapshot.inventoryScorePercent,
                    color = MaterialTheme.colorScheme.primary
                )
                PillarProgressRow(
                    label = "🎒 Maleta Clínica (${snapshot.hospitalBagCheckedCount}/${snapshot.hospitalBagTotalCount})",
                    percent = snapshot.hospitalBagScorePercent,
                    color = MaterialTheme.colorScheme.secondary
                )
                PillarProgressRow(
                    label = "💊 Adherencia a Vitaminas de Britney",
                    percent = snapshot.medicationScorePercent,
                    color = Color(0xFFF59E0B)
                )
            }

            // Alertas de déficits si existen
            if (snapshot.criticalDeficits.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "⚠️ Pendientes críticos recomendados:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        snapshot.criticalDeficits.take(3).forEach { deficit ->
                            Text(
                                text = "• $deficit",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Botón de Diagnóstico Integral IA
            Button(
                onClick = {
                    onGenerateReport()
                    showReportDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = !isGeneratingReport
            ) {
                if (isGeneratingReport) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generando Diagnóstico con Gemini...")
                } else {
                    Text(
                        text = "✨ Generar Diagnóstico Integral 360° con IA",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    // Diálogo del Reporte Ejecutivo IA
    if (showReportDialog && (reportText != null || isGeneratingReport)) {
        Dialog(
            onDismissRequest = { showReportDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .clip(RoundedCornerShape(22.dp)),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🩺 Diagnóstico Integral 360°",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { showReportDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isGeneratingReport) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Consultando a Gemini 3.8 Flash...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (reportText != null) {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .height(380.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            FormattedMarkdownText(
                                text = reportText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillarProgressRow(
    label: String,
    percent: Int,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$percent%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}
