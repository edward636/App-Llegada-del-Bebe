package com.example.llegadadelbebe.presentation.consumables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ConsumableCategoryAudit
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
    val summary by viewModel.auditSummary.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val advice by viewModel.geminiAdvice.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingAdvice.collectAsStateWithLifecycle()

    // 0 = Qué nos Falta, 1 = Qué ya Tenemos, 2 = Metas Esenciales
    var selectedTab by remember { mutableIntStateOf(0) }

    val ownedProducts = remember(products) {
        products.filter { it.status != ProductStatus.PENDING && it.quantityCurrent > 0 }
    }
    val missingOrIncompleteProducts = remember(products) {
        products.filter { it.status == ProductStatus.PENDING || it.quantityCurrent < it.quantityTarget }
    }

    val allAudits = remember(summary) {
        listOfNotNull(
            summary.diapersRn,
            summary.diapersSizeP,
            summary.diapersSizeM,
            summary.diapersSizeG,
            summary.wipes,
            summary.babySoaps,
            summary.clothingRn,
            summary.clothing0to3M,
            summary.giftedAudit
        )
    }

    val deficitAudits = remember(allAudits) {
        allAudits.filter { it.status == StockStatus.DEFICIT }
    }

    val emptyCategories = remember(products) {
        ProductCategory.entries.filter { cat ->
            products.none { it.category == cat && it.status != ProductStatus.PENDING }
        }
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Tarjeta de Balance General basado en el Inventario
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Balance en Tiempo Real del Inventario",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val totalUnitsOwned = products.filter { it.status != ProductStatus.PENDING }.sumOf { it.quantityCurrent }
                    val totalUnitsTarget = products.sumOf { maxOf(it.quantityCurrent, it.quantityTarget) }.coerceAtLeast(1)
                    val completionRatio = (totalUnitsOwned.toFloat() / totalUnitsTarget.toFloat()).coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "✅ Ya tenemos: ${ownedProducts.size} prod. ($totalUnitsOwned uds)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "🛒 Por completar: ${missingOrIncompleteProducts.size + deficitAudits.size}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                    }

                    LinearProgressIndicator(
                        progress = { completionRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF2E7D32),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // 2. Pestañas de Navegación Rápida
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("🛒 Qué nos Falta") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("✅ Qué Tenemos") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("🍼 Metas") },
                    modifier = Modifier.weight(0.85f)
                )
            }

            // 3. Contenido según la Pestaña Seleccionada
            when (selectedTab) {
                0 -> {
                    // SECCIÓN: QUÉ NOS HACE FALTA EN BASE AL INVENTARIO
                    if (missingOrIncompleteProducts.isNotEmpty()) {
                        Text(
                            text = "📌 Productos de tu lista que faltan o están incompletos:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        missingOrIncompleteProducts.forEach { prod ->
                            InventoryBalanceProductRow(product = prod, isMissingView = true)
                        }
                    }

                    if (deficitAudits.isNotEmpty()) {
                        Text(
                            text = "🍼 Esenciales neonatales que aún faltan por cubrir:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        deficitAudits.forEach { item ->
                            AuditItemCard(item)
                        }
                    }

                    if (emptyCategories.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "🗂️ Categorías sin artículos comprados aún:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                emptyCategories.forEach { cat ->
                                    Text(
                                        text = "• ${cat.icon} ${cat.displayName}: Aún no hay productos registrados como comprados.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // SECCIÓN: QUÉ YA TENEMOS EN EL INVENTARIO
                    Text(
                        text = "✅ Artículos que ya tenemos listos para el bebé:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    if (ownedProducts.isEmpty()) {
                        Text(
                            text = "Aún no hay productos marcados como comprados.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        ownedProducts.forEach { prod ->
                            InventoryBalanceProductRow(product = prod, isMissingView = false)
                        }
                    }
                }

                else -> {
                    // SECCIÓN: TODAS LAS METAS ESENCIALES PEDIÁTRICAS
                    Text(
                        text = "🍼 Evaluación completa de metas esenciales:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    allAudits.forEach { item ->
                        AuditItemCard(item)
                    }
                }
            }

            // 4. Tarjeta del Asesor Gemini 3.8
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🤖 Diagnóstico Inteligente Gemini 3.8",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
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
                            text = "Gemini analiza todo lo que ya registraron en el inventario y les recomienda qué priorizar en sus próximas compras.",
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
                        Text(if (advice.isBlank()) "Analizar Qué Tenemos y Qué Falta con IA" else "Actualizar Análisis con IA")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
                        if (product.status == ProductStatus.PENDING) {
                            "Falta comprar (${product.quantityTarget})"
                        } else {
                            "Tenemos ${product.quantityCurrent} • Faltan $missingCount"
                        }
                    } else {
                        "Tenemos ${product.quantityCurrent} uds"
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
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
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
