package com.example.llegadadelbebe.presentation.inventory

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
import com.example.llegadadelbebe.data.remote.CatalogImageFetcher
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductSafetyLevel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmGeminiProductDialog(
    initialProduct: BabyProduct,
    onDismiss: () -> Unit,
    onConfirm: (BabyProduct) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialProduct.name) }
    var brand by remember { mutableStateOf(initialProduct.brand) }
    var model by remember { mutableStateOf(initialProduct.model) }
    var category by remember { mutableStateOf(initialProduct.category) }
    var stageSize by remember { mutableStateOf(initialProduct.stageSize) }
    var quantity by remember { mutableIntStateOf(initialProduct.quantityCurrent) }
    var ingredients by remember { mutableStateOf(initialProduct.ingredients) }
    var risksAndWarnings by remember { mutableStateOf(initialProduct.risksAndWarnings) }
    var safetyLevel by remember { mutableStateOf(initialProduct.safetyLevel) }
    var manufacturedIn by remember { mutableStateOf(initialProduct.manufacturedIn) }
    var expirationDate by remember { mutableStateOf(initialProduct.expirationDate) }
    var geminiOpinion by remember { mutableStateOf(initialProduct.geminiOpinion) }
    var registeredBy by remember { mutableStateOf(initialProduct.registeredBy.ifBlank { "Papá (Edward)" }) }

    // Manejo de imagen limpia de catálogo vs foto de cámara
    var currentPhotoBase64 by remember { mutableStateOf(initialProduct.photoBase64) }
    val rawCameraPhotoBase64 = remember {
        initialProduct.rawPhotoBase64.ifBlank { initialProduct.photoBase64 }
    }
    var catalogPhotoBase64 by remember {
        mutableStateOf(
            if (initialProduct.photoBase64 != initialProduct.rawPhotoBase64 && initialProduct.photoBase64.isNotBlank()) {
                initialProduct.photoBase64
            } else ""
        )
    }

    var showSearchCandidates by remember { mutableStateOf(false) }
    var customSearchQuery by remember {
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
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
                    text = "Verifica los datos del producto y la foto de catálogo limpia antes de guardar en el mosaico:",
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
                            text = if (isUsingCatalog) "✨ Imagen de Catálogo Limpia" else "📷 Foto Tomada con Cámara",
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
                        Text(if (showSearchCandidates) "Cerrar" else "🔍 Buscar otra", fontSize = 11.sp)
                    }
                }

                // Sub-sección: Búsqueda manual de imagen de catálogo si desea cambiarla
                if (showSearchCandidates) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                    placeholder = { Text("Ej: Pampers Premium Care RN") },
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
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Buscar", fontSize = 12.sp)
                                    }
                                }
                            }

                            // Candidatos de imágenes devueltos
                            if (searchCandidates.isNotEmpty()) {
                                Text(
                                    text = "Toca una imagen para aplicarla al producto:",
                                    style = MaterialTheme.typography.bodySmall,
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
                                                    .size(72.dp)
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
                                                        catalogPhotoBase64 = candidateBase64
                                                        showSearchCandidates = false
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(68.dp)
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
                }

                // Nombre
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Producto") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Marca y Modelo
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Marca") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Modelo / Línea Única") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
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

                // Talla y Cantidad
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stageSize,
                        onValueChange = { stageSize = it },
                        label = { Text("Talla / Etapa (ej: RN)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = quantity.toString(),
                        onValueChange = { quantity = it.toIntOrNull() ?: 1 },
                        label = { Text("Cantidad") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Ingredientes / Materiales
                OutlinedTextField(
                    value = ingredients,
                    onValueChange = { ingredients = it },
                    label = { Text("Ingredientes / Composición Textil") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                // Riesgos y Advertencias
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
                        onValueChange = { manufacturedIn = it },
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
                            Text(text = geminiOpinion, style = MaterialTheme.typography.bodySmall)
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
                                        quantityCurrent = quantity.coerceAtLeast(1),
                                        ingredients = ingredients.trim(),
                                        risksAndWarnings = risksAndWarnings.trim(),
                                        safetyLevel = safetyLevel,
                                        manufacturedIn = manufacturedIn.trim(),
                                        expirationDate = expirationDate.trim(),
                                        photoBase64 = currentPhotoBase64,
                                        rawPhotoBase64 = rawCameraPhotoBase64,
                                        geminiOpinion = geminiOpinion.trim(),
                                        registeredBy = registeredBy,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar en Mosaico")
                    }
                }
            }
        }
    }
}
