package com.example.llegadadelbebe.presentation.inventory

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductStatus
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    googleSheetUrl: String,
    geminiApiKey: String,
    onUserChanged: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val isAnalyzingPhoto by viewModel.isAnalyzingPhoto.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var geminiProductToConfirm by remember { mutableStateOf<BabyProduct?>(null) }
    var cameraErrorMessage by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Lanzador de Cámara en Alta Definición (Optimizado a 1280px para lectura nítida de ingredientes y fechas)
    val highResCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val base64 = uriToBase64(context, uri)
            if (base64 != null) {
                cameraErrorMessage = ""
                viewModel.analyzePhoto(base64, geminiApiKey) { extracted ->
                    if (extracted != null) {
                        geminiProductToConfirm = extracted
                    } else {
                        cameraErrorMessage = "No se pudo extraer la información del producto. Intenta con más luz o usa registro manual."
                    }
                }
            } else {
                cameraErrorMessage = "No se pudo procesar la fotografía tomada."
            }
        }
    }

    val launchCameraWithUri = {
        try {
            val photoDir = File(context.cacheDir, "camera_photos")
            if (!photoDir.exists()) photoDir.mkdirs()
            val photoFile = File(photoDir, "capture_gemini.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            pendingCameraUri = uri
            highResCameraLauncher.launch(uri)
        } catch (e: Exception) {
            cameraErrorMessage = "Error al iniciar la cámara: ${e.localizedMessage}"
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            launchCameraWithUri()
        } else {
            cameraErrorMessage = "Permiso de cámara denegado. Actívalo para escanear etiquetas con Gemini."
        }
    }

    // Lanzador alternativo de Galería
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = uriToBase64(context, uri)
            if (base64 != null) {
                cameraErrorMessage = ""
                viewModel.analyzePhoto(base64, geminiApiKey) { extracted ->
                    if (extracted != null) {
                        geminiProductToConfirm = extracted
                    } else {
                        cameraErrorMessage = "No se pudo analizar la foto de la galería."
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("Inventario del Bebé 📋", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Button(onClick = onBack, modifier = Modifier.padding(start = 8.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("←", fontSize = 16.sp)
                    }
                },
                actions = {
                    // Estado de sincronización en tiempo real
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = syncStatus.icon, fontSize = 12.sp)
                            Text(
                                text = syncStatus.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Botón Cámara HD con Gemini 3.8
                FloatingActionButton(
                    onClick = {
                        val hasCamPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCamPerm) {
                            launchCameraWithUri()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📷 Foto Gemini", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Botón Galería con Gemini 3.8
                FloatingActionButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("🖼️ Galería IA", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp))
                }

                // Botón Registro Manual
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("➕ Manual", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Selector de Perfil Activo (Edward vs Britney)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registrando como: $activeUser",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = {
                        viewModel.toggleActiveUser()
                        val updated = if (activeUser.contains("Papá")) "Mamá (Britney)" else "Papá (Edward)"
                        onUserChanged(updated)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = if (activeUser.contains("Papá")) "Cambiar a Mamá 🌸" else "Cambiar a Papá 💙",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (cameraErrorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ $cameraErrorMessage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Buscador
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Buscar producto, marca, ingrediente...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Chips de filtro por categoría
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text("Todos") }
                )
                ProductCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text("${category.icon} ${category.displayName}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Lista de productos
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(products) { product ->
                    ProductItemCard(
                        product = product,
                        onStatusToggle = { viewModel.toggleProductStatus(product, googleSheetUrl) }
                    )
                }
            }
        }
    }

    // Modal de Análisis de Gemini con Foto en Progreso
    if (isAnalyzingPhoto) {
        Dialog(onDismissRequest = {}) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("🤖 Gemini 3.8 Flash está analizando...", fontWeight = FontWeight.Bold)
                    Text(
                        text = "Extrayendo marca, ingredientes, origen, fecha de vencimiento y recomendación...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Modal de Confirmación de Datos Extraídos por Gemini
    geminiProductToConfirm?.let { product ->
        ConfirmGeminiProductDialog(
            initialProduct = product,
            onDismiss = { geminiProductToConfirm = null },
            onConfirm = { confirmed ->
                viewModel.addProduct(confirmed, googleSheetUrl)
                geminiProductToConfirm = null
            }
        )
    }

    // Diálogo Manual
    if (showAddDialog) {
        AddProductDialog(
            defaultRegisteredBy = activeUser,
            onDismiss = { showAddDialog = false },
            onSave = { newProduct ->
                viewModel.addProduct(newProduct, googleSheetUrl)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ProductItemCard(
    product: BabyProduct,
    onStatusToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (product.brand.isNotBlank() || product.model.isNotBlank()) {
                        Text(
                            text = listOf(product.brand, product.model).filter { it.isNotBlank() }.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                val badgeColor = when (product.status) {
                    ProductStatus.PURCHASED -> Color(0xFF2E7D32)
                    ProductStatus.PENDING -> Color(0xFFC62828)
                    ProductStatus.HOSPITAL_PACKED -> Color(0xFF1565C0)
                    ProductStatus.GIFTED -> Color(0xFF6A1B9A)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.12f),
                    onClick = onStatusToggle
                ) {
                    Text(
                        text = product.status.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Ingredientes / Composición
            if (product.ingredients.isNotBlank()) {
                Text(
                    text = "🌿 Composición: ${product.ingredients}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Origen y Vencimiento
            if (product.manufacturedIn.isNotBlank() || (product.expirationDate.isNotBlank() && product.expirationDate != "N/A")) {
                val metaParts = mutableListOf<String>()
                if (product.manufacturedIn.isNotBlank()) metaParts.add("🏭 ${product.manufacturedIn}")
                if (product.expirationDate.isNotBlank() && product.expirationDate != "N/A") metaParts.add("📅 Vence: ${product.expirationDate}")
                Text(
                    text = metaParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cant: ${product.quantityCurrent} ${if (product.stageSize.isNotBlank()) "• Talla: ${product.stageSize}" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                if (product.registeredBy.isNotBlank()) {
                    Text(
                        text = "Por: ${product.registeredBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (product.geminiOpinion.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 ${product.geminiOpinion}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Redimensiona proporcionalmente la imagen a un máximo de 1280px por lado
 * para lectura óptica nítida de ingredientes/fechas sin saturar la memoria RAM ni el ancho de banda.
 */
private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int = 1280): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxDimension && height <= maxDimension) return bitmap

    val ratio = width.toFloat() / height.toFloat()
    val (newWidth, newHeight) = if (ratio > 1f) {
        maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
    } else {
        (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
    }
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val scaled = scaleBitmapDown(bitmap, 1280)
    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}

private fun uriToBase64(context: Context, uri: Uri): String? {
    return try {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: return null
        bitmapToBase64(bitmap)
    } catch (_: Exception) {
        null
    }
}
