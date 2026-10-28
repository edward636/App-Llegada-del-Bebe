package com.example.llegadadelbebe.presentation.inventory

import com.example.llegadadelbebe.core.ui.FormattedMarkdownText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.llegadadelbebe.core.util.BarcodeHelper
import com.example.llegadadelbebe.data.remote.CatalogImageFetcher
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductSafetyLevel
import com.example.llegadadelbebe.domain.model.ProductStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmGeminiProductDialog(
    initialProduct: BabyProduct,
    onDismiss: () -> Unit,
    onScanBackPhoto: ((BabyProduct) -> Unit)? = null,
    onScanBarcode: (() -> Unit)? = null,
    onConfirm: (BabyProduct) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var name by remember(initialProduct.id) { mutableStateOf(initialProduct.name) }
    var brand by remember(initialProduct.id) { mutableStateOf(initialProduct.brand) }
    var model by remember(initialProduct.id) { mutableStateOf(initialProduct.model) }
    var category by remember(initialProduct.id) { mutableStateOf(initialProduct.category) }
    var stageSize by remember(initialProduct.id) { mutableStateOf(initialProduct.stageSize) }
    var packageCount by remember(initialProduct.id) { mutableIntStateOf(initialProduct.packageCount.coerceAtLeast(1)) }
    var unitsPerPackage by remember(initialProduct.id) {
        val initialUnits = when {
            initialProduct.unitsPerPackage > 1 -> initialProduct.unitsPerPackage
            initialProduct.quantityCurrent > 1 -> initialProduct.quantityCurrent
            else -> 1
        }
        mutableIntStateOf(initialUnits)
    }
    var status by remember(initialProduct.id) { mutableStateOf(initialProduct.status) }
    var ingredients by remember(initialProduct.id, initialProduct.ingredients) { mutableStateOf(initialProduct.ingredients) }
    var risksAndWarnings by remember(initialProduct.id, initialProduct.risksAndWarnings) { mutableStateOf(initialProduct.risksAndWarnings) }
    var safetyLevel by remember(initialProduct.id, initialProduct.safetyLevel) { mutableStateOf(initialProduct.safetyLevel) }
    var manufacturedIn by remember(initialProduct.id, initialProduct.manufacturedIn) { mutableStateOf(initialProduct.manufacturedIn) }
    var expirationDate by remember(initialProduct.id, initialProduct.expirationDate) { mutableStateOf(initialProduct.expirationDate) }
    var barcode by remember(initialProduct.id, initialProduct.barcode) { mutableStateOf(initialProduct.barcode) }
    var countryVariant by remember(initialProduct.id, initialProduct.countryVariant) { mutableStateOf(initialProduct.countryVariant) }
    var usefulUntilAge by remember(initialProduct.id, initialProduct.usefulUntilAge) { mutableStateOf(initialProduct.usefulUntilAge) }
    var geminiOpinion by remember(initialProduct.id) { mutableStateOf(initialProduct.geminiOpinion) }
    var registeredBy by remember(initialProduct.id) { mutableStateOf(initialProduct.registeredBy.ifBlank { "Papá (Edward)" }) }
    var referencePriceText by remember(initialProduct.id) {
        mutableStateOf(initialProduct.referencePriceUsd?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "")
    }

    // Manejo de imagen limpia de catálogo vs foto de cámara
    var currentPhotoBase64 by remember(initialProduct.id) { mutableStateOf(initialProduct.photoBase64) }
    val rawCameraPhotoBase64 = remember(initialProduct.id) {
        initialProduct.rawPhotoBase64.ifBlank { initialProduct.photoBase64 }
    }
    var catalogPhotoBase64 by remember(initialProduct.id) {
        mutableStateOf(
            if (initialProduct.photoBase64 != initialProduct.rawPhotoBase64 && initialProduct.photoBase64.isNotBlank()) {
                initialProduct.photoBase64
            } else ""
        )
    }

    var showSearchCandidates by remember { mutableStateOf(false) }
    var customSearchQuery by remember(name, brand, model) {
        mutableStateOf(
            listOf(brand, name, model).filter { it.isNotBlank() }.joinToString(" ")
        )
    }
    var isSearchingMoreImages by remember { mutableStateOf(false) }
    var searchCandidates by remember { mutableStateOf<List<String>>(emptyList()) }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val isUsingCatalog = currentPhotoBase64.isNotBlank() && currentPhotoBase64 != rawCameraPhotoBase64
    val previewBitmap = remember(currentPhotoBase64) {
        decodeBase64ToBitmap(currentPhotoBase64)
    }

    val safetyColor = when (safetyLevel) {
        ProductSafetyLevel.SAFE -> Color(0xFF2E7D32)
        ProductSafetyLevel.CAUTION -> Color(0xFFEF6C00)
        ProductSafetyLevel.WARNING -> Color(0xFFC62828)
    }

    val totalCalculatedUnits = packageCount.coerceAtLeast(1) * unitsPerPackage.coerceAtLeast(1)

    // Tallas rápidas según la categoría
    val quickSizes = when (category) {
        ProductCategory.DIAPERS -> listOf("RN", "P", "M", "G", "XG", "XXG")
        ProductCategory.CLOTHING -> listOf("RN", "0-3M", "3-6M", "6-9M", "9-12M")
        else -> emptyList()
    }

    val buildCurrentSnapshot: () -> BabyProduct = {
        initialProduct.copy(
            name = name.trim(),
            brand = brand.trim(),
            model = model.trim(),
            category = category,
            stageSize = stageSize.trim(),
            packageCount = packageCount.coerceAtLeast(1),
            unitsPerPackage = unitsPerPackage.coerceAtLeast(1),
            quantityCurrent = totalCalculatedUnits,
            status = status,
            ingredients = ingredients.trim(),
            risksAndWarnings = risksAndWarnings.trim(),
            safetyLevel = safetyLevel,
            manufacturedIn = manufacturedIn.trim(),
            expirationDate = expirationDate.trim(),
            photoBase64 = currentPhotoBase64,
            rawPhotoBase64 = rawCameraPhotoBase64,
            barcode = barcode.trim(),
            countryVariant = countryVariant.trim(),
            usefulUntilAge = usefulUntilAge.trim(),
            geminiOpinion = geminiOpinion.trim(),
            registeredBy = registeredBy
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 14.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "✨ Confirmar Producto de Catálogo 🤖💙",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Verifica empaque, paquetes, unidades, ingredientes y variantes para Venezuela antes de guardar:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Tarjeta Visual de Foto de Catálogo / Cámara
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(text = category.icon, fontSize = 48.sp)
                    }

                    // Insignia Superior Izquierda: Origen de la Imagen
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isUsingCatalog) MaterialTheme.colorScheme.primary else Color(0xFF546E7A)
                    ) {
                        Text(
                            text = if (isUsingCatalog) "✨ Catálogo Limpio" else "📷 Foto Tomada",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }

                    // Insignia Superior Derecha: Semáforo de Seguridad
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = safetyColor
                    ) {
                        Text(
                            text = "${safetyLevel.badgeIcon} ${safetyLevel.label}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                // 2. Selector de Imagen: Alternar entre Catálogo Limpio, Foto de Cámara o Buscar Más
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (catalogPhotoBase64.isNotBlank()) {
                        FilterChip(
                            selected = isUsingCatalog,
                            onClick = { currentPhotoBase64 = catalogPhotoBase64 },
                            label = { Text("✨ Catálogo", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                    if (rawCameraPhotoBase64.isNotBlank() && rawCameraPhotoBase64 != catalogPhotoBase64) {
                        FilterChip(
                            selected = !isUsingCatalog,
                            onClick = { currentPhotoBase64 = rawCameraPhotoBase64 },
                            label = { Text("📷 Foto Real", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = { showSearchCandidates = !showSearchCandidates },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(if (showSearchCandidates) "✕ Ocultar" else "🔍 Buscar otra", fontSize = 11.sp)
                    }
                }

                // Sub-sección: Búsqueda manual de imagen de catálogo si desea cambiarla
                if (showSearchCandidates) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Buscar otra imagen de catálogo en internet:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customSearchQuery,
                                    onValueChange = { customSearchQuery = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Ej: Pampers Talla P") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Button(
                                    onClick = {
                                        if (customSearchQuery.isNotBlank() && !isSearchingMoreImages) {
                                            isSearchingMoreImages = true
                                            coroutineScope.launch {
                                                val found = CatalogImageFetcher.searchMultipleThumbnails(
                                                    query = customSearchQuery,
                                                    limit = 4
                                                )
                                                searchCandidates = found
                                                isSearchingMoreImages = false
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = !isSearchingMoreImages && customSearchQuery.isNotBlank()
                                ) {
                                    if (isSearchingMoreImages) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Buscar", fontSize = 12.sp)
                                    }
                                }
                            }

                            if (searchCandidates.isNotEmpty()) {
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
                                                    .size(70.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White)
                                                    .border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        currentPhotoBase64 = candidateBase64
                                                        catalogPhotoBase64 = candidateBase64
                                                        showSearchCandidates = false
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(66.dp).padding(4.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. AVISO INTELIGENTE: Si falta información (ingredientes o vencimiento), solicitar foto de reverso
                if (initialProduct.missingInfoPrompt.isNotBlank() || (ingredients.isBlank() && category in listOf(ProductCategory.WIPES_HYGIENE, ProductCategory.HEALTH, ProductCategory.FEEDING))) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                        border = BorderStroke(1.dp, Color(0xFFFFB300))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📸", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Falta información de ingredientes o vencimiento",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFF57F17)
                                )
                            }
                            Text(
                                text = initialProduct.missingInfoPrompt.ifBlank {
                                    "No se detectaron ingredientes o fecha de caducidad en el frente. Fotografía el reverso del frasco o caja para completar los datos al 100%."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF37474F)
                            )
                            if (onScanBackPhoto != null) {
                                Button(
                                    onClick = { onScanBackPhoto.invoke(buildCurrentSnapshot()) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57F17))
                                ) {
                                    Text("📷 Tomar Foto del Reverso / Ingredientes", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 4. Nombre del Producto
                com.example.llegadadelbebe.core.ui.BabyTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Nombre del Producto",
                    leadingEmoji = "📦",
                    modifier = Modifier.fillMaxWidth()
                )

                // Marca y Modelo
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.llegadadelbebe.core.ui.BabyTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = "Marca",
                        modifier = Modifier.weight(1f)
                    )
                    com.example.llegadadelbebe.core.ui.BabyTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = "Modelo / Presentación",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Categoría
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "${category.icon} ${category.displayName}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        ProductCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${cat.icon} ${cat.displayName}") },
                                onClick = {
                                    category = cat
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // 5. Talla o Etapa (con selectores rápidos para Venezuela RN, P, M, G)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = stageSize,
                        onValueChange = { stageSize = it },
                        label = { Text("Talla / Etapa (ej: RN, P, M, G, 0-3M)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (quickSizes.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickSizes.forEach { qSize ->
                                val isSelected = stageSize.equals(qSize, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { stageSize = qSize },
                                    label = { Text(qSize, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }
                    }
                }

                // 6. Paquetes y Unidades por Paquete (Control Estricto de Inventario)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = packageCount.toString(),
                        onValueChange = { packageCount = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                        label = { Text("Paquetes") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = unitsPerPackage.toString(),
                        onValueChange = { unitsPerPackage = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                        label = { Text("Uds / Paquete") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = "📦 Total: $packageCount paquete(s) × $unitsPerPackage uds = $totalCalculatedUnits unidades registradas",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 7. Código de Barras y Variante para Venezuela
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = {
                            barcode = it
                            countryVariant = BarcodeHelper.resolveCountryVariant(it, manufacturedIn)
                        },
                        label = { Text("Código de Barras (EAN)") },
                        placeholder = { Text("Toca 📷 para escanear") },
                        trailingIcon = {
                            if (onScanBarcode != null) {
                                IconButton(onClick = onScanBarcode) {
                                    Text("📷", fontSize = 16.sp)
                                }
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = countryVariant,
                        onValueChange = { countryVariant = it },
                        label = { Text("Variante País") },
                        placeholder = { Text("Venezuela 🇻🇪") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // 8. Ropita: Edad útil recomendada y meses
                if (category == ProductCategory.CLOTHING || usefulUntilAge.isNotBlank()) {
                    OutlinedTextField(
                        value = usefulUntilAge,
                        onValueChange = { usefulUntilAge = it },
                        label = { Text("👕 Edad Útil Estimada") },
                        placeholder = { Text("Ej: Útil hasta los 3 meses (~6 kg de peso)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // 9. Ingredientes / Materiales
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ingredientes / Composición (INCI)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    if (onScanBackPhoto != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.clickable {
                                onScanBackPhoto.invoke(buildCurrentSnapshot())
                            }
                        ) {
                            Text(
                                text = "📷 Tomar otra foto del envase",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = ingredients,
                    onValueChange = { ingredients = it },
                    label = { Text("Lista de Ingredientes (INCI en inglés o español)") },
                    placeholder = { Text("Ej: Aqua, Glycerin, Sodium Palmate, Coco-Glucoside...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                // 10. Riesgos y Advertencias
                OutlinedTextField(
                    value = risksAndWarnings,
                    onValueChange = { risksAndWarnings = it },
                    label = { Text("Riesgos, Alérgenos y Advertencias") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                // Semáforo de Seguridad (SAFE, CAUTION, WARNING)
                Text(text = "Nivel de Seguridad Pediátrica:", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProductSafetyLevel.entries.forEach { level ->
                        val isSelected = safetyLevel == level
                        val chipColor = when (level) {
                            ProductSafetyLevel.SAFE -> Color(0xFF2E7D32)
                            ProductSafetyLevel.CAUTION -> Color(0xFFEF6C00)
                            ProductSafetyLevel.WARNING -> Color(0xFFC62828)
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { safetyLevel = level },
                            label = { Text("${level.badgeIcon} ${level.label}", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor.copy(alpha = 0.2f),
                                selectedLabelColor = chipColor
                            )
                        )
                    }
                }

                // Fabricado en & Vencimiento
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manufacturedIn,
                        onValueChange = {
                            manufacturedIn = it
                            if (countryVariant.isBlank() || countryVariant == "Internacional") {
                                countryVariant = BarcodeHelper.resolveCountryVariant(barcode, it)
                            }
                        },
                        label = { Text("Fabricado en") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = expirationDate,
                        onValueChange = { expirationDate = it },
                        label = { Text("Vencimiento") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Precio de Referencia (Opcional - Nunca Inventado)
                OutlinedTextField(
                    value = referencePriceText,
                    onValueChange = { referencePriceText = it },
                    label = { Text("Precio de Referencia USD ($) - Opcional") },
                    placeholder = { Text("Ej: 24.50 (dejar en blanco si no se conoce)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Estado (Comprado vs Regalado / Baby Shower)
                Text(text = "Tipo de Adquisición:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProductStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label, fontSize = 11.sp) }
                        )
                    }
                }

                // Consejo de Gemini
                if (geminiOpinion.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "💡 Consejo Pediátrico de Gemini:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FormattedMarkdownText(text = geminiOpinion, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Usuario global (definido en Ajustes)
                Text(
                    text = "👤 Registrado automáticamente por: $registeredBy",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Acciones
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(
                                    initialProduct.copy(
                                        name = name.trim(),
                                        brand = brand.trim(),
                                        model = model.trim(),
                                        category = category,
                                        stageSize = stageSize.trim(),
                                        packageCount = packageCount.coerceAtLeast(1),
                                        unitsPerPackage = unitsPerPackage.coerceAtLeast(1),
                                        quantityCurrent = totalCalculatedUnits,
                                        status = status,
                                        ingredients = ingredients.trim(),
                                        risksAndWarnings = risksAndWarnings.trim(),
                                        safetyLevel = safetyLevel,
                                        manufacturedIn = manufacturedIn.trim(),
                                        expirationDate = expirationDate.trim(),
                                        photoBase64 = currentPhotoBase64,
                                        rawPhotoBase64 = rawCameraPhotoBase64,
                                        barcode = barcode.trim(),
                                        countryVariant = countryVariant.trim(),
                                        usefulUntilAge = usefulUntilAge.trim(),
                                        missingInfoPrompt = "",
                                        geminiOpinion = geminiOpinion.trim(),
                                        registeredBy = registeredBy,
                                        updatedAt = System.currentTimeMillis(),
                                        referencePriceUsd = referencePriceText.toDoubleOrNull()?.takeIf { it > 0.0 },
                                        referencePriceSource = if (referencePriceText.toDoubleOrNull() != null) "Cargado por $registeredBy" else ""
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar en Inventario")
                    }
                }
            }
        }
    }
}
