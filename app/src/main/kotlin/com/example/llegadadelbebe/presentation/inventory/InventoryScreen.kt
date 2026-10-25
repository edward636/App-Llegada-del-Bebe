package com.example.llegadadelbebe.presentation.inventory

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductSafetyLevel
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
    var selectedProductForDetail by remember { mutableStateOf<BabyProduct?>(null) }
    var cameraErrorMessage by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Lanzador de Cámara HD (1280px para OCR de Gemini + miniatura 300px para el mosaico)
    val highResCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = pendingCameraUri ?: runCatching { getDeterministicCameraUri(context) }.getOrNull()
        if (success && uri != null) {
            val pair = uriToHighResAndThumbBase64(context, uri)
            if (pair != null) {
                cameraErrorMessage = ""
                val (highRes, thumb) = pair
                viewModel.analyzePhoto(
                    base64Image = highRes,
                    thumbnailBase64 = thumb,
                    apiKey = geminiApiKey
                ) { extracted ->
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
            val uri = getDeterministicCameraUri(context)
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

    // Lanzador de Galería con miniatura para el mosaico
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val pair = uriToHighResAndThumbBase64(context, uri)
            if (pair != null) {
                cameraErrorMessage = ""
                val (highRes, thumb) = pair
                viewModel.analyzePhoto(
                    base64Image = highRes,
                    thumbnailBase64 = thumb,
                    apiKey = geminiApiKey
                ) { extracted ->
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
                title = { Text("Mosaico de Inventario 📋", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Button(onClick = onBack, modifier = Modifier.padding(start = 8.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("←", fontSize = 16.sp)
                    }
                },
                actions = {
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

                FloatingActionButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("🖼️ Galería IA", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp))
                }

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
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Indicador del perfil global configurado en Ajustes (sin botones de alternar)
            Text(
                text = "👤 Perfil activo en este teléfono: $activeUser",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

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

            Spacer(modifier = Modifier.height(6.dp))

            // Buscador
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Buscar por nombre, marca, modelo, ingrediente...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(10.dp))

            // Mosaico de Productos (2 Columnas)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    ProductMosaicTile(
                        product = product,
                        onClick = { selectedProductForDetail = product },
                        onStatusToggle = { viewModel.toggleProductStatus(product, googleSheetUrl) }
                    )
                }
            }
        }
    }

    // Modal de Análisis de Gemini en Progreso
    if (isAnalyzingPhoto) {
        Dialog(onDismissRequest = {}) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("🤖 Gemini 3.8 está analizando la foto...", fontWeight = FontWeight.Bold)
                    Text(
                        text = "Extrayendo nombre, modelo, ingredientes, riesgos dermatológicos, origen y vencimiento...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Ventana de Confirmación tras tomar la foto
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

    // Ventana de Detalle Completo al tocar un producto en el Mosaico
    selectedProductForDetail?.let { product ->
        ProductDetailDialog(
            product = product,
            onDismiss = { selectedProductForDetail = null },
            onUpdateProduct = { updated ->
                viewModel.updateProduct(updated, googleSheetUrl)
                selectedProductForDetail = null
            },
            onDeleteProduct = { productId ->
                viewModel.deleteProduct(productId, googleSheetUrl)
                selectedProductForDetail = null
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

/**
 * Tarjeta en formato Mosaico (2 por fila) que muestra la fotografía del producto,
 * su nombre, marca, modelo, talla/etapa, estado y semáforo de seguridad.
 */
@Composable
fun ProductMosaicTile(
    product: BabyProduct,
    onClick: () -> Unit,
    onStatusToggle: () -> Unit
) {
    val thumbBitmap = remember(product.photoBase64) {
        decodeBase64ToBitmap(product.photoBase64)
    }

    val badgeColor = when (product.status) {
        ProductStatus.PURCHASED -> Color(0xFF2E7D32)
        ProductStatus.PENDING -> Color(0xFFC62828)
        ProductStatus.HOSPITAL_PACKED -> Color(0xFF1565C0)
        ProductStatus.GIFTED -> Color(0xFF6A1B9A)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Miniatura de la Foto del Producto o Ícono de Categoría
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap.asImageBitmap(),
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(text = product.category.icon, fontSize = 40.sp)
                }

                // Insignia de Seguridad en esquina superior derecha
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = product.safetyLevel.badgeIcon,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 12.sp
                    )
                }

                // Insignia de Talla / Etapa en esquina inferior izquierda
                if (product.stageSize.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                    ) {
                        Text(
                            text = product.stageSize,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // 2. Datos únicos del producto
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val uniqueSub = listOf(product.brand, product.model)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                    .ifBlank { product.category.displayName }

                Text(
                    text = uniqueSub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cant: ${product.quantityCurrent}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.14f),
                        onClick = onStatusToggle
                    ) {
                        Text(
                            text = product.status.label,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }

                Text(
                    text = "🔍 Ver riesgos e ingredientes",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getDeterministicCameraUri(context: Context): Uri {
    val photoDir = File(context.cacheDir, "camera_photos")
    if (!photoDir.exists()) photoDir.mkdirs()
    val photoFile = File(photoDir, "capture_gemini.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}

private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
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

/**
 * Genera tanto la imagen de alta nitidez (1280px, JPEG 85%) para el análisis OCR de Gemini 3.8
 * como una miniatura ligera (300px, JPEG 70%, ~12KB) para guardar en el mosaico y sincronizar en Google Sheets.
 * Incluye sub-muestreo (inSampleSize) contra errores de memoria en cámaras de 50MP/200MP
 * y rotación automática según metadatos EXIF para que las fotos nunca salgan volteadas.
 */
private fun uriToHighResAndThumbBase64(context: Context, uri: Uri): Pair<String, String>? {
    return try {
        // 1. Leer dimensiones sin cargar en RAM (protección OOM para sensores de 200MP)
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, boundsOptions)
        }

        var sampleSize = 1
        val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
        while (maxSide / sampleSize > 2048) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        } ?: return null

        // 2. Corregir orientación EXIF (Xiaomi HyperOS / Oppo ColorOS)
        val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val exif = ExifInterface(inputStream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val orientedBitmap = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }

        val highResBitmap = scaleBitmapDown(orientedBitmap, 1280)
        val highResStream = ByteArrayOutputStream()
        highResBitmap.compress(Bitmap.CompressFormat.JPEG, 85, highResStream)
        val highResBase64 = Base64.encodeToString(highResStream.toByteArray(), Base64.NO_WRAP)

        val thumbBitmap = scaleBitmapDown(orientedBitmap, 300)
        val thumbStream = ByteArrayOutputStream()
        thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 70, thumbStream)
        val thumbBase64 = Base64.encodeToString(thumbStream.toByteArray(), Base64.NO_WRAP)

        highResBase64 to thumbBase64
    } catch (_: Exception) {
        null
    }
}
