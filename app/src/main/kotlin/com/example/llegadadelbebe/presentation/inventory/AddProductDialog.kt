package com.example.llegadadelbebe.presentation.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.example.llegadadelbebe.data.remote.CatalogImageFetcher
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductStatus
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductDialog(
    defaultRegisteredBy: String = "Papá (Edward)",
    onDismiss: () -> Unit,
    onScanBarcode: (() -> Unit)? = null,
    onSave: (BabyProduct) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ProductCategory.CLOTHING) }
    var stageSize by remember { mutableStateOf("RN") }
    var packageCount by remember { mutableIntStateOf(1) }
    var unitsPerPackage by remember { mutableIntStateOf(1) }
    var barcode by remember { mutableStateOf("") }
    var countryVariant by remember { mutableStateOf("") }
    var usefulUntilAge by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf("") }
    var manufacturedIn by remember { mutableStateOf("") }
    var expirationDate by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var registeredBy by remember { mutableStateOf(defaultRegisteredBy) }

    var photoBase64 by remember { mutableStateOf("") }
    var isSearchingImage by remember { mutableStateOf(false) }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val decodedBitmap = remember(photoBase64) { decodeBase64ToBitmap(photoBase64) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Registrar Producto Manual 👶💙",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Vista previa de imagen de catálogo si fue encontrada
                if (decodedBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = decodedBitmap.asImageBitmap(),
                            contentDescription = name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                            contentScale = ContentScale.Fit
                        )
                        OutlinedButton(
                            onClick = { photoBase64 = "" },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Quitar", fontSize = 10.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del artículo") },
                    placeholder = { Text("Ej: Bodies manga corta, Pañales") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

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
                        label = { Text("Modelo/Línea") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Botón para buscar foto de catálogo limpia en la web
                if (photoBase64.isBlank() && (name.isNotBlank() || brand.isNotBlank())) {
                    OutlinedButton(
                        onClick = {
                            if (!isSearchingImage) {
                                isSearchingImage = true
                                coroutineScope.launch {
                                    val found = CatalogImageFetcher.fetchCleanCatalogThumbnail(
                                        name = name,
                                        brand = brand,
                                        model = model,
                                        category = selectedCategory.displayName
                                    )
                                    if (found != null) {
                                        photoBase64 = found
                                    }
                                    isSearchingImage = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSearchingImage) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("✨ Buscar foto de catálogo limpia en internet")
                    }
                }

                // Categoría Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = "${selectedCategory.icon} ${selectedCategory.displayName}",
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
                                    selectedCategory = cat
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Talla o Etapa
                OutlinedTextField(
                    value = stageSize,
                    onValueChange = { stageSize = it },
                    label = { Text("Talla / Etapa (ej: RN, P, M, G, 0-3M)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Paquetes y Unidades por Paquete
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ) {
                    Text(
                        text = "📦 Total: $packageCount paquete(s) × $unitsPerPackage uds = ${packageCount * unitsPerPackage} unidades",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Código de Barras y Variante Regional
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = {
                            barcode = it
                            countryVariant = com.example.llegadadelbebe.core.util.BarcodeHelper.resolveCountryVariant(it, manufacturedIn)
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
                        label = { Text("Variante") },
                        placeholder = { Text("Venezuela 🇻🇪") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (selectedCategory == ProductCategory.CLOTHING) {
                    OutlinedTextField(
                        value = usefulUntilAge,
                        onValueChange = { usefulUntilAge = it },
                        label = { Text("👕 Edad Útil Estimada") },
                        placeholder = { Text("Ej: Útil hasta los 3 meses (~6 kg)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = ingredients,
                    onValueChange = { ingredients = it },
                    label = { Text("Ingredientes o Material") },
                    placeholder = { Text("Ej: 100% Algodón, Agua purificada") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

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
                        placeholder = { Text("Ej: 12/2026 o N/A") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Detalles / Notas") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Usuario global (definido en Ajustes)
                Text(
                    text = "👤 Registrado automáticamente por: $registeredBy",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val totalUnits = (packageCount * unitsPerPackage).coerceAtLeast(1)
                                onSave(
                                    BabyProduct(
                                        id = UUID.randomUUID().toString(),
                                        name = name.trim(),
                                        brand = brand.trim(),
                                        model = model.trim(),
                                        category = selectedCategory,
                                        stageSize = stageSize.trim(),
                                        packageCount = packageCount,
                                        unitsPerPackage = unitsPerPackage,
                                        quantityCurrent = totalUnits,
                                        ingredients = ingredients.trim(),
                                        manufacturedIn = manufacturedIn.trim(),
                                        expirationDate = expirationDate.trim(),
                                        photoBase64 = photoBase64,
                                        rawPhotoBase64 = photoBase64,
                                        barcode = barcode.trim(),
                                        countryVariant = countryVariant.trim(),
                                        usefulUntilAge = usefulUntilAge.trim(),
                                        notes = notes.trim(),
                                        registeredBy = registeredBy,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}
