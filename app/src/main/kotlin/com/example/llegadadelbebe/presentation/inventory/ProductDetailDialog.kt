package com.example.llegadadelbebe.presentation.inventory

import com.example.llegadadelbebe.core.ui.FormattedMarkdownText
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.data.remote.FarmatodoApiClient
import com.example.llegadadelbebe.domain.model.FarmatodoProductBenchmark
import java.util.Locale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.llegadadelbebe.data.remote.CatalogImageFetcher
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductSafetyLevel
import com.example.llegadadelbebe.domain.model.ProductStatus
import kotlinx.coroutines.launch

/**
 * Vista de detalle completa de un producto al tocar su tarjeta en el mosaico.
 * Muestra su fotografía, marca, modelo, semáforo de seguridad, riesgos/alérgenos,
 * ingredientes, origen, vencimiento y consejos pediátricos de Gemini 3.8.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductDetailDialog(
    product: BabyProduct,
    onDismiss: () -> Unit,
    onUpdateProduct: (BabyProduct) -> Unit,
    onDeleteProduct: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val bcvRate = remember { LocalPreferences(context).getLastBcvRate().coerceAtLeast(1.0) }

    val effectiveUnits = remember(product.id) { product.effectiveUnitsPerPackage }
    var packageCount by remember(product.id) { mutableIntStateOf(product.packageCount.coerceAtLeast(1)) }
    var status by remember(product.id) { mutableStateOf(product.status) }
    var confirmDelete by remember { mutableStateOf(false) }

    var referencePriceUsd by remember(product.id, product.referencePriceUsd) {
        mutableStateOf(product.referencePriceUsd)
    }
    var referencePriceSource by remember(product.id, product.referencePriceSource) {
        mutableStateOf(product.referencePriceSource)
    }
    var priceInputUsd by remember(product.id, product.referencePriceUsd) {
        mutableStateOf(product.referencePriceUsd?.let { String.format(Locale.US, "%.2f", it) } ?: "")
    }
    var priceInputVes by remember(product.id, product.referencePriceUsd) {
        mutableStateOf(product.referencePriceUsd?.let { String.format(Locale.US, "%.2f", it * bcvRate) } ?: "")
    }
    var isSearchingFarmatodo by remember { mutableStateOf(false) }
    var farmatodoMatch by remember { mutableStateOf<FarmatodoProductBenchmark?>(null) }
    var farmatodoSearchMessage by remember { mutableStateOf("") }

    var currentPhotoBase64 by remember(product.id, product.photoBase64) { mutableStateOf(product.photoBase64) }
    var isShowingRawPhoto by remember { mutableStateOf(false) }
    val rawPhotoBase64 = product.rawPhotoBase64

    var isSearchingCatalogImage by remember { mutableStateOf(false) }
    var searchCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCandidatesRow by remember { mutableStateOf(false) }

    val activePhotoToDisplay = if (isShowingRawPhoto && rawPhotoBase64.isNotBlank()) rawPhotoBase64 else currentPhotoBase64
    val decodedBitmap = remember(activePhotoToDisplay) {
        decodeBase64ToBitmap(activePhotoToDisplay)
    }

    val isCatalogActive = currentPhotoBase64.isNotBlank() && currentPhotoBase64 != rawPhotoBase64

    val safetyColor = when (product.safetyLevel) {
        ProductSafetyLevel.SAFE -> Color(0xFF2E7D32)
        ProductSafetyLevel.CAUTION -> Color(0xFFEF6C00)
        ProductSafetyLevel.WARNING -> Color(0xFFC62828)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Encabezado Visual / Fotografía del Producto (Estilo Catálogo E-Commerce)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            RoundedCornerShape(18.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (decodedBitmap != null) {
                        Image(
                            bitmap = decodedBitmap.asImageBitmap(),
                            contentDescription = product.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = product.category.icon, fontSize = 54.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = product.category.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Insignia Superior Izquierda: Catálogo vs Cámara
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCatalogActive && !isShowingRawPhoto) MaterialTheme.colorScheme.primary else Color(0xFF546E7A)
                    ) {
                        Text(
                            text = if (isCatalogActive && !isShowingRawPhoto) "✨ Foto Catálogo Limpio" else "📷 Foto Tomada",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    // Insignia flotante de Seguridad Pediátrica
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = safetyColor
                    ) {
                        Text(
                            text = "${product.safetyLevel.badgeIcon} ${product.safetyLevel.label}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Selector de Imagen y Botón de Búsqueda de Catálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rawPhotoBase64.isNotBlank() && currentPhotoBase64.isNotBlank() && currentPhotoBase64 != rawPhotoBase64) {
                        FilterChip(
                            selected = !isShowingRawPhoto,
                            onClick = { isShowingRawPhoto = false },
                            label = { Text("✨ Catálogo", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = isShowingRawPhoto,
                            onClick = { isShowingRawPhoto = true },
                            label = { Text("📷 Cámara", fontSize = 11.sp) }
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (!isSearchingCatalogImage) {
                                isSearchingCatalogImage = true
                                coroutineScope.launch {
                                    val query = listOf(product.brand, product.name, product.model)
                                        .filter { it.isNotBlank() }.joinToString(" ")
                                    val list = CatalogImageFetcher.searchMultipleThumbnails(query, limit = 4)
                                    searchCandidates = list
                                    showCandidatesRow = list.isNotEmpty()
                                    isSearchingCatalogImage = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        if (isSearchingCatalogImage) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("🔍 Buscar catálogo", fontSize = 11.sp)
                    }
                }

                // Galería de candidatos encontrados si se buscó
                if (showCandidatesRow && searchCandidates.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Toca para aplicar foto limpia de catálogo:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                searchCandidates.forEach { candidateBase64 ->
                                    val bmp = remember(candidateBase64) { decodeBase64ToBitmap(candidateBase64) }
                                    if (bmp != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .border(
                                                    BorderStroke(
                                                        if (currentPhotoBase64 == candidateBase64) 2.dp else 1.dp,
                                                        if (currentPhotoBase64 == candidateBase64) MaterialTheme.colorScheme.primary else Color.LightGray
                                                    ),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    currentPhotoBase64 = candidateBase64
                                                    isShowingRawPhoto = false
                                                    showCandidatesRow = false
                                                    onUpdateProduct(product.copy(photoBase64 = candidateBase64))
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .padding(4.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Nombre, Marca, Modelo y Talla que lo hacen único
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val uniqueIdentity = listOf(
                        product.brand.takeIf { it.isNotBlank() }?.let { "Marca: $it" },
                        product.model.takeIf { it.isNotBlank() }?.let { "Modelo: $it" },
                        product.stageSize.takeIf { it.isNotBlank() }?.let { "Talla/Etapa: $it" }
                    ).filterNotNull().joinToString(" • ")

                    if (uniqueIdentity.isNotBlank()) {
                        Text(
                            text = uniqueIdentity,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val originAndExp = listOf(
                        product.manufacturedIn.takeIf { it.isNotBlank() }?.let { "🏭 Origen: $it" },
                        product.expirationDate.takeIf { it.isNotBlank() && it != "N/A" }?.let { "📅 Vence: $it" },
                        product.registeredBy.takeIf { it.isNotBlank() }?.let { "👤 Por: $it" }
                    ).filterNotNull().joinToString(" • ")

                    if (originAndExp.isNotBlank()) {
                        Text(
                            text = originAndExp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Código de Barra y Variante Regional
                    if (product.barcode.isNotBlank() || product.countryVariant.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "🏷️ " + listOf(product.barcode.takeIf { it.isNotBlank() }, product.countryVariant.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" • "),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Ropita: Edad Útil Estimada
                    if (product.usefulUntilAge.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "👕 Edad Útil: ${product.usefulUntilAge}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    // Cantidad en Paquetes y Unidades
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ) {
                        Text(
                            text = "📦 Cantidad: ${product.displayQuantityLabel}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 3. Riesgos, Alérgenos y Advertencias (Gemini 3.8)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = safetyColor.copy(alpha = 0.12f)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "⚠️ Riesgos, Alérgenos y Precauciones (Gemini 3.8)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = safetyColor
                        )
                        Text(
                            text = product.risksAndWarnings.ifBlank {
                                "Sin riesgos dermatológicos ni alertas especiales reportadas para recién nacidos."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 4. Ingredientes y Composición
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "🌿 Ingredientes / Composición Material",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = product.ingredients.ifBlank {
                                "No especificados en la etiqueta registrada."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 5. Opinión y Recomendación de Uso (Gemini 3.8)
                if (product.geminiOpinion.isNotBlank() || product.notes.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "💡 Recomendación Pediátrica e Información Relevante",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (product.geminiOpinion.isNotBlank()) {
                                FormattedMarkdownText(
                                    text = product.geminiOpinion,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (product.notes.isNotBlank()) {
                                Text(
                                    text = "📝 Nota familiar: ${product.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 6. Control de Paquetes en Inventario (Suma Rápida y Conteo Exacto)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📦 Cantidad de Paquetes en Inventario",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (effectiveUnits > 1) {
                                "Cada paquete contiene $effectiveUnits unidades"
                            } else {
                                "Artículo individual / por unidad"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Button(
                                onClick = { if (packageCount > 1) packageCount-- },
                                enabled = packageCount > 1,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "$packageCount paquete${if (packageCount > 1) "s" else ""}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Button(
                                onClick = { packageCount++ },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (effectiveUnits > 1) {
                            val calculatedUnits = packageCount * effectiveUnits
                            Text(
                                text = "Total en inventario: $packageCount paquete(s) × $effectiveUnits uds = $calculatedUnits unidades",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 7. Estado del Producto
                Text(
                    text = "Estado en el Inventario:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProductStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label, fontSize = 12.sp) }
                        )
                    }
                }

                // 8. Precio de Referencia (Opcional - Nunca Inventado)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏷️ Precio de Referencia",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (referencePriceUsd != null && referencePriceUsd!! > 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFE8F5E9)
                                ) {
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", referencePriceUsd)} USD",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = "Sin precio (en blanco)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (referencePriceSource.isNotBlank()) {
                            Text(
                                text = "Fuente: $referencePriceSource",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            com.example.llegadadelbebe.core.ui.BabyTextField(
                                value = priceInputUsd,
                                onValueChange = { input ->
                                    priceInputUsd = input
                                    val parsed = input.toDoubleOrNull()
                                    if (parsed != null && parsed > 0.0) {
                                        referencePriceUsd = parsed
                                        if (referencePriceSource.isBlank() || referencePriceSource == "Sin precio") {
                                            referencePriceSource = "Cargado por usuario"
                                        }
                                        priceInputVes = String.format(Locale.US, "%.2f", parsed * bcvRate)
                                    } else if (input.isBlank()) {
                                        referencePriceUsd = null
                                        referencePriceSource = ""
                                        priceInputVes = ""
                                    }
                                },
                                label = "Precio USD ($)",
                                placeholder = "Ej: 24.50",
                                prefixText = "$",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )

                            com.example.llegadadelbebe.core.ui.BabyTextField(
                                value = priceInputVes,
                                onValueChange = { input ->
                                    priceInputVes = input
                                    val parsed = input.toDoubleOrNull()
                                    if (parsed != null && parsed > 0.0) {
                                        val usd = parsed / bcvRate
                                        referencePriceUsd = usd
                                        if (referencePriceSource.isBlank() || referencePriceSource == "Sin precio") {
                                            referencePriceSource = "Cargado por usuario"
                                        }
                                        priceInputUsd = String.format(Locale.US, "%.2f", usd)
                                    } else if (input.isBlank()) {
                                        referencePriceUsd = null
                                        referencePriceSource = ""
                                        priceInputUsd = ""
                                    }
                                },
                                label = "Precio Bs (BCV)",
                                placeholder = "Ej: 2150",
                                prefixText = "Bs",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isSearchingFarmatodo = true
                                    farmatodoSearchMessage = "Buscando en Farmatodo Venezuela..."
                                    coroutineScope.launch {
                                        val benchmark = FarmatodoApiClient().lookupBenchmark(
                                            barcode = product.barcode,
                                            productName = product.name,
                                            brand = product.brand,
                                            bcvRate = bcvRate
                                        )
                                        isSearchingFarmatodo = false
                                        if (benchmark != null) {
                                            farmatodoMatch = benchmark
                                            farmatodoSearchMessage = ""
                                        } else {
                                            farmatodoSearchMessage = "No se halló en Farmatodo con ese nombre/código."
                                        }
                                    }
                                },
                                enabled = !isSearchingFarmatodo,
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isSearchingFarmatodo) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("🔍 Buscar en Farmatodo", fontSize = 11.sp)
                            }

                            if (referencePriceUsd != null) {
                                OutlinedButton(
                                    onClick = {
                                        referencePriceUsd = null
                                        referencePriceSource = ""
                                        priceInputUsd = ""
                                        priceInputVes = ""
                                        farmatodoMatch = null
                                        farmatodoSearchMessage = ""
                                    },
                                    modifier = Modifier.weight(0.8f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Quitar", fontSize = 11.sp)
                                }
                            }
                        }

                        if (farmatodoSearchMessage.isNotBlank()) {
                            Text(
                                text = farmatodoSearchMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        farmatodoMatch?.let { match ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "💊 Farmatodo: ${match.name}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Precio oficial: $${String.format(Locale.US, "%.2f", match.effectivePriceUsd)} USD (Bs. ${String.format(Locale.US, "%.2f", match.effectivePriceVes)})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Button(
                                        onClick = {
                                            val price = match.effectivePriceUsd
                                            referencePriceUsd = price
                                            referencePriceSource = "Farmatodo Oficial"
                                            priceInputUsd = String.format(Locale.US, "%.2f", price)
                                            priceInputVes = String.format(Locale.US, "%.2f", price * bcvRate)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Text("Usar este precio de Farmatodo", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // 9. Botones de Acción (Eliminar y Guardar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (confirmDelete) {
                                onDeleteProduct(product.id)
                            } else {
                                confirmDelete = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFC62828)
                        )
                    ) {
                        Text(if (confirmDelete) "⚠️ Confirmar Borrar" else "🗑️ Eliminar")
                    }

                    Button(
                        onClick = {
                            val totalUnits = packageCount * effectiveUnits
                            onUpdateProduct(
                                product.copy(
                                    packageCount = packageCount,
                                    unitsPerPackage = effectiveUnits,
                                    quantityCurrent = totalUnits,
                                    quantityTarget = totalUnits,
                                    status = status,
                                    photoBase64 = currentPhotoBase64,
                                    referencePriceUsd = referencePriceUsd,
                                    referencePriceSource = referencePriceSource,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar y Cerrar")
                    }
                }
            }
        }
    }
}

fun decodeBase64ToBitmap(base64Str: String): android.graphics.Bitmap? {
    return com.example.llegadadelbebe.core.ui.ImageBitmapCache.decodeBase64ToBitmap(base64Str)
}
