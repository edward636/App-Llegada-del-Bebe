package com.example.llegadadelbebe.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.llegadadelbebe.core.ui.bouncyPress
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.llegadadelbebe.core.util.BabyGrowthMilestone
import com.example.llegadadelbebe.core.util.BabyGrowthMilestones

/**
 * Tarjeta interactiva de referencia visual del crecimiento del bebé.
 * Muestra tanto la Fruta/Vegetal como el Animalito tierno equivalente para la semana gestacional actual,
 * con animaciones suaves, detalles de peso/talla y visor semanal completo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyGrowthVisualCard(
    currentWeeks: Int,
    modifier: Modifier = Modifier
) {
    var viewingWeeks by remember(currentWeeks) { mutableIntStateOf(currentWeeks) }
    val milestone = remember(viewingWeeks) {
        BabyGrowthMilestones.getMilestoneForWeek(viewingWeeks)
    }

    var selectedMode by remember { mutableIntStateOf(0) } // 0 = Fruta, 1 = Animalito
    var showAllWeeksSheet by remember { mutableStateOf(false) }

    val activeColor = if (selectedMode == 0) milestone.fruitColor else milestone.animalColor
    val activeEmoji = if (selectedMode == 0) milestone.fruitEmoji else milestone.animalEmoji
    val activeName = if (selectedMode == 0) milestone.fruitName else milestone.animalName
    val categoryLabel = if (selectedMode == 0) "Fruta criolla venezolana" else "Fauna venezolana"

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
            // Cabecera: Selector Fruta vs Animalito
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🌱 Semana $viewingWeeks de Gestación",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (viewingWeeks == currentWeeks) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(
                                    text = "Hoy",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "$categoryLabel 🇻🇪",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Selector de modo pill con física táctil
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        PillOption(
                            text = "🍎 Fruta",
                            isSelected = selectedMode == 0,
                            onClick = { selectedMode = 0 }
                        )
                        PillOption(
                            text = "🐾 Fauna",
                            isSelected = selectedMode == 1,
                            onClick = { selectedMode = 1 }
                        )
                    }
                }
            }

            // Línea de Tiempo Gestacional Continua (Continuous Timeline Flow)
            val timelineListState = rememberLazyListState()
            LaunchedEffect(viewingWeeks) {
                val index = (viewingWeeks - 4).coerceIn(0, 36)
                timelineListState.animateScrollToItem((index - 2).coerceAtLeast(0))
            }

            LazyRow(
                state = timelineListState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(37) { idx ->
                    val week = idx + 4
                    val isSelected = week == viewingWeeks
                    val isCurrentWeek = week == currentWeeks
                    val weekMilestone = BabyGrowthMilestones.getMilestoneForWeek(week)
                    val emoji = if (selectedMode == 0) weekMilestone.fruitEmoji else weekMilestone.animalEmoji

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isCurrentWeek -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        border = BorderStroke(
                            width = if (isCurrentWeek && !isSelected) 1.5.dp else 1.dp,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isCurrentWeek -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            }
                        ),
                        modifier = Modifier.bouncyPress { viewingWeeks = week }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = emoji, fontSize = 13.sp)
                            Text(
                                text = "Sem $week",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected || isCurrentWeek) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isSelected -> Color.White
                                    isCurrentWeek -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            // Si el usuario navegó a otra semana, ofrecer retorno instantáneo a la semana actual
            if (viewingWeeks != currentWeeks) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Viendo hito de la semana $viewingWeeks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.bouncyPress { viewingWeeks = currentWeeks }
                    ) {
                        Text(
                            text = "↩ Volver a hoy (Sem $currentWeeks)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Cuerpo Principal: Insignia visual grande + métricas de longitud y peso
            AnimatedContent(
                targetState = selectedMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "growth_visual_switch"
            ) { mode ->
                val currentModeColor = if (mode == 0) milestone.fruitColor else milestone.animalColor
                val currentModeEmoji = if (mode == 0) milestone.fruitEmoji else milestone.animalEmoji
                val currentModeName = if (mode == 0) milestone.fruitName else milestone.animalName

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Círculo grande con gradiente y emoji del hito
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        currentModeColor.copy(alpha = 0.35f),
                                        currentModeColor.copy(alpha = 0.08f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentModeEmoji,
                            fontSize = 38.sp
                        )
                    }

                    // Título del hito y pastillas métricas (cm y gramos)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = currentModeName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 21.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MetricChip(
                                label = "📏 ${milestone.lengthCm} cm",
                                bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                textColor = MaterialTheme.colorScheme.primary
                            )
                            val weightStr = if (milestone.weightGrams >= 1000) {
                                String.format(java.util.Locale.US, "%.1f kg", milestone.weightGrams / 1000.0)
                            } else {
                                "${milestone.weightGrams.toInt()} g"
                            }
                            MetricChip(
                                label = "⚖️ $weightStr",
                                bgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                textColor = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Hito de desarrollo perinatal de la semana
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "💡 ${milestone.headline}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = milestone.developmentHighlight,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            // Botón para explorar la galería de semanas 4 a 40
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showAllWeeksSheet = true },
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Text(
                        text = "📖 Explorar Álbum (Semana 4 a 40) →",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet con la galería completa de semanas
    if (showAllWeeksSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val allMilestones = remember { BabyGrowthMilestones.getAllMilestones() }
        val listState = rememberLazyListState()

        LaunchedEffect(currentWeeks) {
            val targetIdx = (currentWeeks - 4).coerceIn(0, allMilestones.size - 1)
            listState.scrollToItem(targetIdx)
        }

        ModalBottomSheet(
            onDismissRequest = { showAllWeeksSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "👶 Álbum de Crecimiento del Bebé",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Guía semana a semana con Frutas Criollas y Fauna de Venezuela 🇻🇪",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { showAllWeeksSheet = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allMilestones, key = { it.week }) { m ->
                        val isCurrent = m.week == currentWeeks
                        val border = if (isCurrent) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                        val containerBg = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            border = border,
                            colors = CardDefaults.cardColors(containerColor = containerBg)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(m.fruitColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${m.fruitEmoji} ${m.animalEmoji}", fontSize = 18.sp)
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Semana ${m.week}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isCurrent) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    text = "¡HOY!",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = "${m.fruitName} • ${m.animalName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Text(
                                        text = "${m.lengthCm} cm • ${if (m.weightGrams >= 1000) String.format(java.util.Locale.US, "%.1f kg", m.weightGrams / 1000.0) else "${m.weightGrams.toInt()} g"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Text(
                                        text = m.headline,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textCol = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        modifier = Modifier.bouncyPress(onClick = onClick)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textCol
        )
    }
}

@Composable
private fun MetricChip(
    label: String,
    bgColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
