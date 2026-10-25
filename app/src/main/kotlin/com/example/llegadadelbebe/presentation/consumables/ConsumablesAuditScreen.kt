package com.example.llegadadelbebe.presentation.consumables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ConsumableCategoryAudit
import com.example.llegadadelbebe.domain.model.ConsumablesAuditSummary
import com.example.llegadadelbebe.domain.model.DiaperSizeBreakdown
import com.example.llegadadelbebe.domain.model.GeneralConsumablesSummary
import com.example.llegadadelbebe.domain.model.HospitalBagCheckItem
import com.example.llegadadelbebe.domain.model.HospitalBagSection
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductStatus
import com.example.llegadadelbebe.domain.model.StockStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumablesAuditScreen(
    viewModel: ConsumablesAuditViewModel,
    apiKey: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val localPreferences = remember { LocalPreferences(context) }
    var checkedHospitalBagItemIds by remember {
        mutableStateOf(localPreferences.getHospitalBagCheckedIds())
    }

    val summary by viewModel.auditSummary.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val advice by viewModel.geminiAdvice.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingAdvice.collectAsStateWithLifecycle()

    // 0 = Llegada del Bebé (Casa), 1 = Bolsito del Parto (Maleta Clínica), 2 = Después del Nacimiento
    var selectedSection by remember { mutableIntStateOf(0) }

    val ownedProducts = remember(products) {
        products.filter { it.status != ProductStatus.PENDING }
    }
    val missingOrIncompleteProducts = remember(products) {
        products.filter { it.status == ProductStatus.PENDING }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("¿Qué Tenemos y Qué Falta? 📊", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Button(onClick = onBack, modifier = Modifier.padding(start = 8.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("←", fontSize = 16.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =========================================================================
            // 1. TARJETA CONSOLIDADA: "GENERALIZAR TODO" (Paquetes y Unidades de Pañales y Toallas)
            // =========================================================================
            summary.generalSummary?.let { general ->
                GeneralConsumablesSummaryCard(general = general)
            }

            // =========================================================================
            // 2. SELECTOR DE LAS 3 SECCIONES PRINCIPALES
            // =========================================================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Selecciona la etapa a revisar:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        label = { Text("🏡 1. Llegada (Casa)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1.1f)
                    )
                    FilterChip(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        label = { Text("🏥 2. Maleta Parto", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedSection == 2,
                        onClick = { selectedSection = 2 },
                        label = { Text("🚀 3. Posterior", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(0.95f)
                    )
                }
            }

            // =========================================================================
            // 3. CONTENIDO SEGÚN LA SECCIÓN SELECCIONADA
            // =========================================================================
            when (selectedSection) {
                0 -> {
                    // SECCIÓN 1: LLEGADA DEL BEBÉ (PREPARACIÓN PARA EL HOGAR / SEMANAS INICIALES)
                    HomeArrivalSection(
                        summary = summary,
                        ownedProducts = ownedProducts,
                        missingProducts = missingOrIncompleteProducts
                    )
                }

                1 -> {
                    // SECCIÓN 2: EL BOLSITO PARA EL DÍA DEL PARTO (MALETA CLÍNICA / CHECKLIST)
                    HospitalBagSectionView(
                        sections = summary.hospitalBagSections,
                        checkedItemIds = checkedHospitalBagItemIds,
                        onToggleItem = { itemId ->
                            val updated = localPreferences.toggleHospitalBagItemChecked(itemId)
                            checkedHospitalBagItemIds = updated
                        }
                    )
                }

                2 -> {
                    // SECCIÓN 3: PARA DESPUÉS DEL NACIMIENTO (ETAPAS POSTERIORES & RESERVA)
                    PostBirthSection(
                        summary = summary,
                        products = products
                    )
                }
            }

            // =========================================================================
            // 4. ASESOR INTELIGENTE GEMINI 3.8
            // =========================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🤖 Diagnóstico Pediátrico con IA (Gemini 3.8)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (advice.isNotBlank()) {
                        Text(text = advice, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        Text(
                            text = "Gemini analiza la fecha de parto, el inventario actual y les aconseja qué priorizar para la maleta de la clínica y las primeras semanas sin gastar de más.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Button(
                        onClick = { viewModel.requestGeminiAdvice(apiKey) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (advice.isBlank()) "Analizar Balance e Inventario con IA" else "Actualizar Diagnóstico con IA")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 1. Tarjeta Consolidada: "Generalizar Todo" (Paquetes y Unidades Totales de Pañales y Toallitas).
 */
@Composable
fun GeneralConsumablesSummaryCard(general: GeneralConsumablesSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📦", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Inventario Consolidado de Pañales y Toallitas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Total Pañales en Casa: ${general.totalDiaperPackages} paquetes (${general.totalDiaperUnits} unidades en total)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            // Fila de tallas venezolanas de pañales
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DiaperSizeMiniRow(general.sizeRn)
                DiaperSizeMiniRow(general.sizeP)
                DiaperSizeMiniRow(general.sizeM)
                DiaperSizeMiniRow(general.sizeG)
            }

            // Toallitas húmedas
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🛁 Toallitas Húmedas:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${general.totalWipesPackages} paquetes (${general.totalWipesUnits} toallitas)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun DiaperSizeMiniRow(size: DiaperSizeBreakdown) {
    val statusColor = Color(size.status.badgeColorHex)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = statusColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${size.sizeLabel} (${size.weightRange})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${size.packagesCount} paquete(s) • ${size.unitsCount} pañales acumulados",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusColor
            ) {
                Text(
                    text = size.status.label,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * SECCIÓN 1: LLEGADA DEL BEBÉ (Hogar & Primeras Semanas)
 */
@Composable
fun HomeArrivalSection(
    summary: ConsumablesAuditSummary,
    ownedProducts: List<BabyProduct>,
    missingProducts: List<BabyProduct>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Tarjeta de Guía Pediátrica de Pañales para no comprar de más
        PediatricDiaperGuidanceCard()

        Text(
            text = "🍼 Metas de Consumibles para las Semanas 1 a 6:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        AuditItemCard(summary.diapersRn)
        AuditItemCard(summary.diapersSizeP)
        AuditItemCard(summary.wipes)
        AuditItemCard(summary.babySoaps)
        AuditItemCard(summary.clothingRn)
        AuditItemCard(summary.clothing0to3M)

        if (missingProducts.isNotEmpty()) {
            Text(
                text = "🛒 Artículos esenciales pendientes por adquirir:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC62828)
            )
            missingProducts.forEach { prod ->
                InventoryBalanceProductRow(product = prod, isMissingView = true)
            }
        }

        if (ownedProducts.isNotEmpty()) {
            Text(
                text = "✅ Artículos que ya tenemos listos en casa:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )
            ownedProducts.take(8).forEach { prod ->
                InventoryBalanceProductRow(product = prod, isMissingView = false)
            }
        }
    }
}

/**
 * Tarjeta explicativa con la investigación médica de consumo de pañales.
 */
@Composable
fun PediatricDiaperGuidanceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        border = BorderStroke(1.dp, Color(0xFF81C784))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💡", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Investigación Pediátrica: ¿Cuánto comprar para no perder pañales?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF2E7D32)
                )
            }
            Text(
                text = "• Talla RN (0 a 4.5 kg): El bebé usa de 8 a 10 pañales/día. Crecen muy rápido (~25g al día) y esta talla dura solo 2 a 4 semanas. Meta recomendada: 140 a 160 pañales (máximo 180). ¡No compres de más!",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1B5E20)
            )
            Text(
                text = "• Talla P (4 a 6 kg / Etapa 1): Se usa del mes 1 al 2 (~7-9 pañales/día). Meta: 200 a 240 unidades. Es una compra segura que se aprovechará completamente.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1B5E20)
            )
        }
    }
}

/**
 * SECCIÓN 2: EL BOLSITO PARA EL DÍA DEL PARTO (Maleta Clínica con Checklist Interactivo)
 */
@Composable
fun HospitalBagSectionView(
    sections: List<HospitalBagSection>,
    checkedItemIds: Set<String>,
    onToggleItem: (String) -> Unit
) {
    val totalItems = sections.sumOf { it.items.size }
    val packedCount = sections.sumOf { sec -> sec.items.count { checkedItemIds.contains(it.id) } }
    val progress = if (totalItems > 0) packedCount.toFloat() / totalItems.toFloat() else 0f

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🏥 Maleta de la Clínica (Semana 34+)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$packedCount de $totalItems empacados (${(progress * 100).toInt()}%)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (progress >= 1f) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (progress >= 1f) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "Toca cada artículo para marcarlo como empacado. Se guarda automáticamente en tu teléfono.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        sections.forEach { section ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(section.icon, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = section.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = section.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        section.items.forEach { item ->
                            val isChecked = checkedItemIds.contains(item.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleItem(item.id) }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleItem(item.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF2E7D32))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChecked) FontWeight.Normal else FontWeight.Bold,
                                        color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${item.subtitle} • Sugerido: ${item.quantityGuideline}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * SECCIÓN 3: PARA DESPUÉS DEL NACIMIENTO (Etapas Posteriores & Reserva)
 */
@Composable
fun PostBirthSection(
    summary: ConsumablesAuditSummary,
    products: List<BabyProduct>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
            border = BorderStroke(1.dp, Color(0xFFFFB74D))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚀", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Estrategia para después del 2do mes del bebé",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFE65100)
                    )
                }
                Text(
                    text = "• Talla M (6 a 9.5 kg): Es la talla más duradera del primer año (desde los 3 hasta los 6-7 meses). Pueden aprovechar ofertas y pedir en Baby Shower.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBF360C)
                )
                Text(
                    text = "• Talla G (9 a 12.5 kg): Se usa a partir del 6to mes. No abran paquetes con anticipación para poder cambiarlos si el bebé tiene contextura diferente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBF360C)
                )
            }
        }

        Text(
            text = "🧸 Pañales y Reservas para Etapas Posteriores:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        AuditItemCard(summary.diapersSizeM)
        AuditItemCard(summary.diapersSizeG)

        summary.giftedAudit?.let { gifted ->
            AuditItemCard(gifted)
        }

        // Ropita 3M+
        val olderClothing = products.filter {
            it.category == ProductCategory.CLOTHING &&
                (it.stageSize.contains("3-6", ignoreCase = true) || it.stageSize.contains("6M", ignoreCase = true) || it.stageSize.contains("9M", ignoreCase = true) || it.stageSize.contains("12M", ignoreCase = true))
        }

        if (olderClothing.isNotEmpty()) {
            Text(
                text = "👕 Ropita registrada para 3 meses en adelante:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            olderClothing.forEach { prod ->
                InventoryBalanceProductRow(product = prod, isMissingView = false)
            }
        }
    }
}

@Composable
fun InventoryBalanceProductRow(
    product: BabyProduct,
    isMissingView: Boolean
) {
    val missingCount = (product.quantityTarget - product.quantityCurrent).coerceAtLeast(0)
    val accentColor = if (isMissingView) Color(0xFFC62828) else Color(0xFF2E7D32)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${product.category.icon} ${product.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val sub = listOf(
                    product.brand.takeIf { it.isNotBlank() },
                    product.model.takeIf { it.isNotBlank() },
                    product.stageSize.takeIf { it.isNotBlank() }?.let { "Talla $it" }
                ).filterNotNull().joinToString(" • ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = if (isMissingView) {
                        "Falta adquirir (${product.displayQuantityLabel})"
                    } else {
                        "Tenemos: ${product.displayQuantityLabel}"
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun AuditItemCard(item: ConsumableCategoryAudit) {
    val missingUnits = (item.recommendedUnits - item.currentUnits).coerceAtLeast(0)
    val progress = if (item.recommendedUnits > 0) {
        (item.currentUnits.toFloat() / item.recommendedUnits.toFloat()).coerceIn(0f, 1f)
    } else 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.categoryName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(item.status.badgeColorHex)
                ) {
                    Text(
                        text = if (missingUnits > 0) "Faltan $missingUnits uds" else item.status.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tenemos: ${item.currentUnits} uds • Meta sugerida: ${item.recommendedUnits} uds",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(item.status.badgeColorHex),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "💡 ${item.recommendationMessage}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
