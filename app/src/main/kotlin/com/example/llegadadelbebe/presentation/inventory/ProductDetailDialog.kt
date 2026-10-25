package com.example.llegadadelbebe.presentation.inventory

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
    var quantityCurrent by remember(product.id) { mutableIntStateOf(product.quantityCurrent) }
    var quantityTarget by remember(product.id) { mutableIntStateOf(product.quantityTarget) }
    var status by remember(product.id) { mutableStateOf(product.status) }
    var confirmDelete by remember { mutableStateOf(false) }

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
                                Text(
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

                // 6. Control Rápido de Cantidad (Tenemos vs Meta)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cantidad Actual
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Cantidad Actual", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { if (quantityCurrent > 0) quantityCurrent-- },
                                    modifier = Modifier.size(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("-") }
                                Text(
                                    text = quantityCurrent.toString(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Button(
                                    onClick = { quantityCurrent++ },
                                    modifier = Modifier.size(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("+") }
                            }
                        }
                    }

                    // Cantidad Meta
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Meta Ideal", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { if (quantityTarget > 1) quantityTarget-- },
                                    modifier = Modifier.size(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("-") }
                                Text(
                                    text = quantityTarget.toString(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Button(
                                    onClick = { quantityTarget++ },
                                    modifier = Modifier.size(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("+") }
                            }
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

                // 8. Botones de Acción (Eliminar y Guardar)
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
                            onUpdateProduct(
                                product.copy(
                                    quantityCurrent = quantityCurrent,
                                    quantityTarget = quantityTarget,
                                    status = status,
                                    photoBase64 = currentPhotoBase64
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
    if (base64Str.isBlank()) return null
    return try {
        val bytes = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}
