package com.example.llegadadelbebe.presentation.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.llegadadelbebe.core.ui.FormattedMarkdownText
import com.example.llegadadelbebe.core.util.PregnancyStats
import com.example.llegadadelbebe.core.util.ZodiacProfile

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    geminiApiKey: String = "",
    activeUser: String = "Papá (Edward)",
    onNavigateToInventory: () -> Unit,
    onNavigateToConsumables: () -> Unit,
    onNavigateToSynastry: () -> Unit = {},
    onNavigateToRadar: () -> Unit = {},
    onNavigateToMedications: () -> Unit = {},
    onNavigateToSettings: () -> Unit
) {
    val stats by viewModel.pregnancyStats.collectAsStateWithLifecycle()
    val zodiac by viewModel.zodiacProfile.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val babyName by viewModel.babyName.collectAsStateWithLifecycle()
    val dailyWisdom by viewModel.dailyWisdom.collectAsStateWithLifecycle()
    val isMom = activeUser.contains("Mamá", ignoreCase = true)

    LaunchedEffect(activeUser) {
        viewModel.refreshDailyWisdom(geminiApiKey, activeUser)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Encabezado principal con Menú de Ajustes (3 puntos notorios) en la parte superior izquierda
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onNavigateToSettings,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 2.dp,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "⋮",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMom) "¡Hola Mamita Britney! 🌸" else "¡Hola Papá Edward! 💙",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isMom) {
                        "Dulce espera de $babyName con Edward 👶"
                    } else {
                        "Preparando la llegada de $babyName con Britney 👶"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = if (isMom) "🌸 Mamá" else "💙 Papá",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 2. ✨ MENSAJERO DIARIO CON IA: Mensaje tierno para hoy cruzado con el desarrollo y la base de datos
        DailyAiWisdomCard(
            wisdom = dailyWisdom,
            onRefresh = { viewModel.refreshDailyWisdom(geminiApiKey, activeUser) }
        )

        // 3. DIAL DE GESTACIÓN Y CUENTA REGRESIVA CON MICRO-ANIMACIÓN
        PregnancyCountdownCard(stats = stats)

        // 4. DOCK FLOTANTE DE ACCESOS RÁPIDOS (Radar, Vitaminas, Inventario, Sinastría)
        QuickActionsDock(
            onRadar = onNavigateToRadar,
            onMedications = onNavigateToMedications,
            onInventory = onNavigateToInventory,
            onSynastry = onNavigateToSynastry
        )

        // 5. ESTADO DEL INVENTARIO
        InventorySummaryCard(
            summary = summary,
            onViewAllClick = onNavigateToInventory,
            onAuditClick = onNavigateToConsumables
        )

        // 6. SIGNO DEL BEBÉ & TRÍADA ASTRAL
        ZodiacCard(
            zodiac = zodiac,
            dueDateStr = stats.estimatedDueDate.toString(),
            onSynastryClick = onNavigateToSynastry
        )

        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * Tarjeta interactiva del Mensajero Diario con IA:
 * Entrega un consejo tierno, personalizado para el día de hoy, mencionando el hito gestacional
 * de hoy y el avance de los preparativos en la base de datos.
 */
@Composable
fun DailyAiWisdomCard(
    wisdom: DailyAiWisdom,
    onRefresh: () -> Unit
) {
    val bgBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFF3E5F5), // Lavanda pastel
            Color(0xFFE1F5FE), // Azul cielo suave
            Color(0xFFFFF8E1)  // Crema sol cálido
        )
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color(0xFFCE93D8).copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.background(bgBrush).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✨", fontSize = 20.sp)
                        Text(
                            text = "Mensajero Diario con IA",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color(0xFF4A148C)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (wisdom.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF4A148C))
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.7f),
                                onClick = onRefresh,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🔄", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FormattedMarkdownText(
                        text = wisdom.message,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 21.sp,
                        color = Color(0xFF263238)
                    )
                }
            }
        }
    }
}

/**
 * Dial de Gestación y Cuenta Regresiva animado
 */
@Composable
fun PregnancyCountdownCard(stats: PregnancyStats) {
    val animatedProgress by animateFloatAsState(
        targetValue = stats.progressPercent,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "pregnancy_progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Semana ${stats.currentWeeks} + ${stats.currentDays} días",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "FPP: ${stats.estimatedDueDate}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Trimestre ${stats.trimester}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏳ Faltan ${stats.daysRemaining} días (~${stats.weeksRemaining} sem)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${(animatedProgress * 100).toInt()}% transcurrido",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "📏 Tamaño hoy: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        text = "${stats.babySizeComparison} • ${stats.babyWeightEstimate}",
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Dock Flotante de Accesos Rápidos interactivos (Radar, Vitaminas, Inventario, Sinastría)
 */
@Composable
fun QuickActionsDock(
    onRadar: () -> Unit,
    onMedications: () -> Unit,
    onInventory: () -> Unit,
    onSynastry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "⚡ Accesos Rápidos",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickDockCard(
                icon = "🛍️",
                title = "Radar Compras",
                subtitle = "Precios & BCV",
                accentColor = Color(0xFF1976D2),
                modifier = Modifier.weight(1f),
                onClick = onRadar
            )
            QuickDockCard(
                icon = "💊",
                title = "Vitaminas",
                subtitle = "Tomas de Britney",
                accentColor = Color(0xFFC2185B),
                modifier = Modifier.weight(1f),
                onClick = onMedications
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickDockCard(
                icon = "📋",
                title = "Inventario",
                subtitle = "Compras & Maleta",
                accentColor = Color(0xFF388E3C),
                modifier = Modifier.weight(1f),
                onClick = onInventory
            )
            QuickDockCard(
                icon = "✨",
                title = "Sinastría Astral",
                subtitle = "Sol, Luna & Asc",
                accentColor = Color(0xFF7B1FA2),
                modifier = Modifier.weight(1f),
                onClick = onSynastry
            )
        }
    }
}

@Composable
fun QuickDockCard(
    icon: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 20.sp)
                }
            }

            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun InventorySummaryCard(
    summary: HomeSummary,
    onViewAllClick: () -> Unit,
    onAuditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Estado del Inventario",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBadge("Total", summary.totalItems.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatBadge("Comprado", summary.purchasedCount.toString(), Color(0xFF2E7D32), Modifier.weight(1f))
                StatBadge("Faltantes", summary.pendingCount.toString(), Color(0xFFC62828), Modifier.weight(1f))
                StatBadge("Clínica", summary.hospitalBagCount.toString(), Color(0xFF1565C0), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onViewAllClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📋 Inventario", softWrap = false, maxLines = 1, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onAuditClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📊 Qué Falta", softWrap = false, maxLines = 1, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun StatBadge(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ZodiacCard(
    zodiac: ZodiacProfile,
    dueDateStr: String,
    onSynastryClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Signo del Bebé: ${zodiac.signName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = zodiac.element,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${zodiac.symbol} • Ventana estimada: ${zodiac.dateRange}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "✨ Rasgos de tu niño: ${zodiac.boyTraits}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onSynastryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("✨ Ver Sinastría Familiar & Tríada Astral", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
