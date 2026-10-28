package com.example.llegadadelbebe.presentation.radar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.llegadadelbebe.core.util.BarcodeScannerHelper
import com.example.llegadadelbebe.core.util.StoreLocationHelper
import com.example.llegadadelbebe.domain.model.BcvExchangeRate
import com.example.llegadadelbebe.domain.model.CurrencyType
import com.example.llegadadelbebe.domain.model.FamilyBudgetReport
import com.example.llegadadelbebe.domain.model.FarmatodoProductBenchmark
import com.example.llegadadelbebe.domain.model.GoogleMapsPlaceInfo
import com.example.llegadadelbebe.domain.model.KnownStoreLocation
import com.example.llegadadelbebe.domain.model.ProductBudgetLine
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductPriceQuote
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingRadarScreen(
    viewModel: ShoppingRadarViewModel,
    geminiApiKey: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Radar & Cotizaciones, 1: Presupuesto Bebé, 2: Memoria GPS

    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val isLoadingBcv by viewModel.isLoadingBcv.collectAsStateWithLifecycle()
    val quotes by viewModel.filteredQuotes.collectAsStateWithLifecycle()
    val knownStores by viewModel.knownStores.collectAsStateWithLifecycle()
    val budgetReport by viewModel.familyBudget.collectAsStateWithLifecycle()

    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isAnalyzingQuote by viewModel.isAnalyzingQuote.collectAsStateWithLifecycle()
    val isSearchingFarmatodo by viewModel.isSearchingFarmatodo.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showManualBcvDialog by remember { mutableStateOf(false) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingQuoteDraft by remember { mutableStateOf<ProductPriceQuote?>(null) }
    var radarErrorMessage by remember { mutableStateOf("") }

    // Lanzador de Cámara para foto de anaquel / hablador de precio con lectura EXIF GPS
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            coroutineScope.launch {
                val triple = runCatching { readAndCompressPhotoWithExif(context, uri) }.getOrNull()
                if (triple != null) {
                    val (base64, bmp, exifCoords) = triple
                    val barcode = bmp?.let { BarcodeScannerHelper.scanBarcode(it) } ?: ""

                    // 1. Obtener ubicación: Prioridad coordenadas EXIF de la foto, luego GPS en vivo
                    val liveLoc = StoreLocationHelper.captureCurrentStoreLocation(context, knownStores = knownStores)
                    val finalLat = exifCoords?.first ?: liveLoc.latitude
                    val finalLon = exifCoords?.second ?: liveLoc.longitude

                    // 2. Verificar Memoria GPS: ¿Coincide con una tienda conocida en proximidad? (ej: Mercafácil)
                    val matchedNearby = StoreLocationHelper.findNearbyKnownStore(finalLat, finalLon, knownStores)
                    val gmapsPlace = if (finalLat != 0.0 && finalLon != 0.0) {
                        StoreLocationHelper.resolveGoogleMapsPlace(context, finalLat, finalLon)
                    } else null
                    val detectedStoreName = matchedNearby?.name ?: liveLoc.name.ifBlank { gmapsPlace?.placeName ?: "" }
                    val resolvedGmapsName = gmapsPlace?.placeName ?: liveLoc.googleMapsName

                    viewModel.analyzePricePhoto(base64, geminiApiKey) { parsed ->
                        if (parsed != null) {
                            val resolvedStoreName = when {
                                detectedStoreName.isNotBlank() -> detectedStoreName
                                parsed.storeNameHint.isNotBlank() -> parsed.storeNameHint
                                else -> ""
                            }

                            val draft = ProductPriceQuote(
                                productName = parsed.productName,
                                brand = parsed.brand,
                                category = parsed.category,
                                stageSize = parsed.stageSize,
                                packageCount = parsed.packageCount,
                                unitsPerPackage = parsed.unitsPerPackage,
                                storeName = resolvedStoreName,
                                storeAddress = matchedNearby?.address ?: (gmapsPlace?.fullAddress ?: liveLoc.address),
                                latitude = finalLat,
                                longitude = finalLon,
                                priceAmount = parsed.priceAmount,
                                priceCurrency = if (parsed.priceCurrency == "VES") CurrencyType.VES else CurrencyType.USD,
                                bcvRateApplied = bcvRate.rate,
                                priceUsdEquivalent = if (parsed.priceCurrency == "VES" && bcvRate.rate > 0) parsed.priceAmount / bcvRate.rate else parsed.priceAmount,
                                unitPriceUsd = 0.0,
                                quotedAt = "",
                                photoBase64 = base64,
                                notes = parsed.notes,
                                barcode = if (parsed.barcode.isNotBlank()) parsed.barcode else barcode,
                                googleMapsName = resolvedGmapsName
                            )

                            // Consultar Farmatodo en tiempo real
                            viewModel.lookupFarmatodoBenchmark(
                                barcode = draft.barcode,
                                productName = draft.productName,
                                brand = draft.brand
                            ) { ftResult ->
                                pendingQuoteDraft = draft.copy(farmatodoBenchmark = ftResult)
                                showAddDialog = true
                            }
                        } else {
                            radarErrorMessage = "No se pudieron extraer datos del hablador. Intenta con más luz o usa registro manual."
                        }
                    }
                } else {
                    radarErrorMessage = "No se pudo procesar la fotografía tomada."
                }
            }
        }
    }

    val launchCamera = {
        try {
            val photoFile = File(context.cacheDir, "radar_capture.jpg")
            photoFile.parentFile?.mkdirs()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            pendingPhotoUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            radarErrorMessage = "Error al abrir la cámara: ${e.localizedMessage}"
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera() else radarErrorMessage = "Permiso de cámara requerido para escanear precios."
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Radar & Presupuesto 🛍️📍",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Memoria GPS • Farmatodo • Tasa BCV",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("←", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                actions = {
                    Surface(
                        onClick = { viewModel.refreshBcvRate() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 12.dp).size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isLoadingBcv) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("🔄", fontSize = 16.sp)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = {
                            val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasCam) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📷 Escanear Anaquel", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                val loc = StoreLocationHelper.captureCurrentStoreLocation(context, knownStores = knownStores)
                                pendingQuoteDraft = ProductPriceQuote(
                                    productName = "",
                                    storeName = loc.name,
                                    storeAddress = loc.address,
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    googleMapsName = loc.googleMapsName,
                                    priceAmount = 0.0,
                                    priceCurrency = CurrencyType.USD,
                                    bcvRateApplied = bcvRate.rate,
                                    priceUsdEquivalent = 0.0,
                                    unitPriceUsd = 0.0,
                                    quotedAt = ""
                                )
                                showAddDialog = true
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("➕ Manual / Barcode", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Pestañas Principales: 1. Radar & Cotizaciones, 2. Presupuesto Familiar, 3. Memoria GPS
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("🛍️ Radar", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("💰 Presupuesto", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("📍 Memoria GPS", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Cabecera Oficial BCV del Día
                BcvRateHeaderCard(
                    rate = bcvRate,
                    isLoading = isLoadingBcv,
                    onRefresh = { viewModel.refreshBcvRate() },
                    onManualAdjust = { showManualBcvDialog = true }
                )

                if (isAnalyzingQuote) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Gemini lee el anaquel y valida memoria GPS...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (radarErrorMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "⚠️ $radarErrorMessage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { radarErrorMessage = "" }, modifier = Modifier.size(24.dp)) {
                                Text("✕", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (selectedTab) {
                    0 -> RadarQuotesTabContent(
                        quotes = quotes,
                        selectedCategory = selectedCategory,
                        searchQuery = searchQuery,
                        onCategorySelect = { viewModel.setCategoryFilter(it) },
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onDeleteQuote = { viewModel.deletePriceQuote(it) }
                    )
                    1 -> FamilyBudgetTabContent(budgetReport = budgetReport)
                    2 -> GpsMemoryTabContent(
                        knownStores = knownStores,
                        onDeleteStore = { viewModel.deleteKnownStore(it) }
                    )
                }
            }
        }
    }

    // Diálogo de Ajuste Manual de Tasa BCV
    if (showManualBcvDialog) {
        ManualBcvDialog(
            currentRate = bcvRate.rate,
            onDismiss = { showManualBcvDialog = false },
            onSave = { newRate ->
                viewModel.updateManualBcvRate(newRate)
                showManualBcvDialog = false
            }
        )
    }

    // Diálogo de Registro y Confirmación de Cotización con Memoria GPS
    if (showAddDialog && pendingQuoteDraft != null) {
        QuoteEditorDialog(
            draft = pendingQuoteDraft!!,
            bcvRate = bcvRate.rate,
            knownStores = knownStores,
            isSearchingFarmatodo = isSearchingFarmatodo,
            onLookupFarmatodo = { b, n, br ->
                viewModel.lookupFarmatodoBenchmark(b, n, br) { result ->
                    pendingQuoteDraft = pendingQuoteDraft?.copy(farmatodoBenchmark = result)
                }
            },
            onDismiss = {
                showAddDialog = false
                pendingQuoteDraft = null
            },
            onSave = { quoteToSave ->
                viewModel.savePriceQuote(
                    productName = quoteToSave.productName,
                    brand = quoteToSave.brand,
                    category = quoteToSave.category,
                    stageSize = quoteToSave.stageSize,
                    packageCount = quoteToSave.packageCount,
                    unitsPerPackage = quoteToSave.unitsPerPackage,
                    storeName = quoteToSave.storeName,
                    storeAddress = quoteToSave.storeAddress,
                    latitude = quoteToSave.latitude,
                    longitude = quoteToSave.longitude,
                    priceAmount = quoteToSave.priceAmount,
                    priceCurrency = quoteToSave.priceCurrency,
                    photoBase64 = quoteToSave.photoBase64,
                    barcode = quoteToSave.barcode,
                    notes = quoteToSave.notes,
                    farmatodoBenchmark = quoteToSave.farmatodoBenchmark,
                    googleMapsName = quoteToSave.googleMapsName
                )
                showAddDialog = false
                pendingQuoteDraft = null
            }
        )
    }
}

// -------------------------------------------------------------------------
// PESTAÑA 0: RADAR & COTIZACIONES DE COMERCIOS
// -------------------------------------------------------------------------

@Composable
fun RadarQuotesTabContent(
    quotes: List<ProductPriceQuote>,
    selectedCategory: ProductCategory?,
    searchQuery: String,
    onCategorySelect: (ProductCategory?) -> Unit,
    onSearchChange: (String) -> Unit,
    onDeleteQuote: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Buscar por producto, marca, tienda o barcode...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelect(null) },
                label = { Text("Todos", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            ProductCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onCategorySelect(if (selectedCategory == cat) null else cat) },
                    label = { Text("${cat.icon} ${cat.displayName}", fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (quotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛍️📍", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Aún no hay cotizaciones en este filtro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Toca '📷 Escanear Anaquel' en Mercafácil, Locatel u otra tienda para comparar con Farmatodo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(quotes, key = { it.id }) { quote ->
                    PriceQuoteCard(
                        quote = quote,
                        onDelete = { onDeleteQuote(quote.id) }
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// PESTAÑA 1: PRESUPUESTO DINÁMICO FAMILIAR (GASTADO VS. FALTANTE)
// -------------------------------------------------------------------------

@Composable
fun FamilyBudgetTabContent(budgetReport: FamilyBudgetReport) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tarjeta Resumen Consolidada de Presupuesto
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "💰 Presupuesto de Inversión del Bebé",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Cruzado en tiempo real con cotizaciones de tiendas físicas y catálogo oficial de Farmatodo:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Dos columnas: Gastado vs Faltante
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Columna Gastado
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("💵 Ya Invertido", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text(budgetReport.formattedSpentUsd, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                            Text(budgetReport.formattedSpentVes, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${budgetReport.purchasedCount} artículos comprados", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Columna Faltante
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFE65100).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFFE65100).copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("⏳ Por Comprar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                            Text(budgetReport.formattedPendingUsd, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE65100))
                            Text(budgetReport.formattedPendingVes, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${budgetReport.pendingCount} artículos pendientes", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Barra de Progreso
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "Meta de Preparativos: ${(budgetReport.spentPercentage * 100).toInt()}% cubierto",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Total Estimado: ${budgetReport.formattedProjectedUsd}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { budgetReport.spentPercentage },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF2E7D32),
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }
        }

        // Desglose de Artículos del Presupuesto
        Text(
            text = "📋 Desglose Artículo por Artículo (Precios Reales y Oficiales)",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )

        budgetReport.lines.forEach { line ->
            BudgetLineCard(line = line, bcvRate = budgetReport.bcvRate)
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun BudgetLineCard(line: ProductBudgetLine, bcvRate: Double) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (line.isPurchased) Color(0xFF2E7D32) else Color(0xFFE65100)
                    ) {
                        Text(
                            text = if (line.isPurchased) "✅ Comprado" else "⏳ Falta",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = line.productName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Ref: ${line.sourcePriceLabel} • ${line.quantityDisplayLabel.ifBlank { "Cant: ${line.quantity}" }}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = line.formattedTotalUsd,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (line.isPurchased) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = line.formattedTotalVes(bcvRate),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// PESTAÑA 2: MEMORIA GPS DE COMERCIOS (RECONOCIMIENTO POR PROXIMIDAD)
// -------------------------------------------------------------------------

@Composable
fun GpsMemoryTabContent(
    knownStores: List<KnownStoreLocation>,
    onDeleteStore: (String) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "📍 Memoria GPS de Tiendas y Comercios",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "La app recuerda cada coordenada donde registraste un local (Mercafácil, Locatel, etc.). Cuando regreses a un radio de ~120m, autocompletará el nombre automáticamente.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (knownStores.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📍", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Aún no hay tiendas en la memoria GPS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Al tomar una foto de anaquel o guardar una cotización con GPS, el local se guardará aquí.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            knownStores.forEach { store ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🏪 ${store.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (store.hasGoogleMapsMatch) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("✅ Maps", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                            }
                            if (store.googleMapsName.isNotBlank() && !store.hasGoogleMapsMatch) {
                                Text(
                                    text = "🗺️ En Google Maps: \"${store.googleMapsName}\"",
                                    fontSize = 11.sp,
                                    color = Color(0xFF0277BD)
                                )
                            }
                            if (store.address.isNotBlank()) {
                                Text(text = store.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = "Coords: ${String.format(java.util.Locale.US, "%.4f, %.4f", store.latitude, store.longitude)} • ${store.visitCount} visita(s)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                onClick = {
                                    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(store.googleMapsUrl))
                                    context.startActivity(mapIntent)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text("🗺️", modifier = Modifier.padding(6.dp), fontSize = 14.sp)
                            }

                            IconButton(onClick = { onDeleteStore(store.id) }, modifier = Modifier.size(32.dp)) {
                                Text("🗑️", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// -------------------------------------------------------------------------
// COMPONENTES AUXILIARES Y DIÁLOGOS
// -------------------------------------------------------------------------

@Composable
fun BcvRateHeaderCard(
    rate: BcvExchangeRate,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onManualAdjust: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏦", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Tasa Oficial BCV del Día",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (rate.isFallback) "Modo sin conexión • Tasa guardada" else "Actualizado: ${rate.lastUpdated}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    onClick = onManualAdjust,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "⚙️ Ajustar",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = rate.formattedRate,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Referencia: \$1.00 USD",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PriceQuoteCard(
    quote: ProductPriceQuote,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val ft = quote.farmatodoBenchmark

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quote.productName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val details = listOf(
                        quote.brand.takeIf { it.isNotBlank() },
                        quote.stageSize.takeIf { it.isNotBlank() },
                        if (quote.isMultiUnitPackage) {
                            if (quote.packageCount > 1) "${quote.packageCount} pqtes de ${quote.unitsPerPackage} uds (${quote.totalUnits} total)"
                            else "${quote.unitsPerPackage} uds / paquete"
                        } else {
                            if (quote.packageCount > 1) "${quote.packageCount} unidades" else "1 unidad"
                        }
                    ).filterNotNull().joinToString(" • ")

                    Text(text = details, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Text("🗑️", fontSize = 14.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text("📍", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = quote.storeName + if (quote.storeAddress.isNotBlank()) " (${quote.storeAddress})" else "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (quote.latitude != 0.0 && quote.longitude != 0.0) {
                    Surface(
                        onClick = {
                            val mapUri = Uri.parse("geo:${quote.latitude},${quote.longitude}?q=${quote.latitude},${quote.longitude}(${Uri.encode(quote.storeName)})")
                            val intent = Intent(Intent.ACTION_VIEW, mapUri)
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "🗺️ Mapa",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (quote.isMultiUnitPackage) "Precio del paquete:" else "Precio en tienda:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(text = quote.formattedPriceStore, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Ref. Dólar BCV:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (quote.isMultiUnitPackage) "${quote.formattedUsdEquivalent} (${quote.formattedUnitPriceUsd})" else quote.formattedUsdEquivalent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            // Inversión acumulada si se registraron múltiples paquetes
            if (quote.packageCount > 1) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📦 Inversión total (${quote.packageCount} paquetes):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "${quote.formattedTotalUsd} (${quote.formattedTotalVes})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Comparador con Farmatodo
            if (ft != null) {
                val diffUsd = quote.packagePriceUsd - ft.effectivePriceUsd
                val isFarmatodoCheaper = diffUsd > 0.05
                val isStoreCheaper = diffUsd < -0.05

                val bannerBg = when {
                    isFarmatodoCheaper -> Color(0xFFFFF3E0)
                    isStoreCheaper -> Color(0xFFE8F5E9)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }

                val borderStrokeColor = when {
                    isFarmatodoCheaper -> Color(0xFFFFB74D)
                    isStoreCheaper -> Color(0xFF81C784)
                    else -> Color.Transparent
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = bannerBg,
                    border = BorderStroke(1.dp, borderStrokeColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💊 Comparativa Farmatodo Oficial:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1565C0)
                            )

                            Text(
                                text = String.format(Locale.US, "$%.2f (Bs. %.2f)", ft.effectivePriceUsd, ft.effectivePriceVes),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1565C0)
                            )
                        }

                        val verdictText = when {
                            isFarmatodoCheaper -> String.format(Locale.US, "⚠️ En Farmatodo el paquete está $%.2f más barato (-%.0f%%). ¡Te conviene en Farmatodo!", diffUsd, (diffUsd / quote.packagePriceUsd) * 100)
                            isStoreCheaper -> String.format(Locale.US, "🎉 ¡Gran oferta! Te ahorras $%.2f por paquete en este comercio frente a Farmatodo.", -diffUsd)
                            else -> "⚖️ Precio por paquete prácticamente igual al de Farmatodo Venezuela."
                        }

                        Text(
                            text = verdictText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFarmatodoCheaper) Color(0xFFE65100) else if (isStoreCheaper) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Ver en Farmatodo.com.ve ↗",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1976D2),
                                modifier = Modifier
                                    .clickable {
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ft.webProductUrl))
                                        context.startActivity(webIntent)
                                    }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteEditorDialog(
    draft: ProductPriceQuote,
    bcvRate: Double,
    knownStores: List<KnownStoreLocation>,
    isSearchingFarmatodo: Boolean,
    onLookupFarmatodo: (barcode: String?, name: String, brand: String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ProductPriceQuote) -> Unit
) {
    var productName by remember { mutableStateOf(draft.productName) }
    var brand by remember { mutableStateOf(draft.brand) }
    var category by remember { mutableStateOf(draft.category) }
    var stageSize by remember { mutableStateOf(draft.stageSize) }
    var packageCount by remember { mutableIntStateOf(draft.packageCount) }
    var unitsPerPackage by remember { mutableIntStateOf(draft.unitsPerPackage) }
    var storeName by remember { mutableStateOf(draft.storeName) }
    var storeAddress by remember { mutableStateOf(draft.storeAddress) }
    var priceText by remember { mutableStateOf(if (draft.priceAmount > 0) draft.priceAmount.toString() else "") }
    var currency by remember { mutableStateOf(draft.priceCurrency) }
    var barcode by remember { mutableStateOf(draft.barcode) }
    var notes by remember { mutableStateOf(draft.notes) }
    var farmatodoBenchmark by remember { mutableStateOf(draft.farmatodoBenchmark) }

    val parsedPrice = priceText.toDoubleOrNull() ?: 0.0
    val isMultiUnit = unitsPerPackage > 1
    val safeUnitsPerPkg = unitsPerPackage.coerceAtLeast(1)
    val safePkgCount = packageCount.coerceAtLeast(1)

    // parsedPrice es el precio por paquete registrado en tienda
    val packagePriceUsd = if (currency == CurrencyType.VES) {
        if (bcvRate > 0) parsedPrice / bcvRate else parsedPrice
    } else parsedPrice
    val unitCostUsd = if (safeUnitsPerPkg > 1) packagePriceUsd / safeUnitsPerPkg else packagePriceUsd
    val totalCostUsd = packagePriceUsd * safePkgCount

    // Verificar si las coordenadas coinciden con la memoria GPS
    val matchedStore = remember(draft.latitude, draft.longitude) {
        StoreLocationHelper.findNearbyKnownStore(draft.latitude, draft.longitude, knownStores)
    }

    val context = LocalContext.current
    val googlePlaceInfo = remember(draft.latitude, draft.longitude) {
        if (draft.latitude != 0.0 && draft.longitude != 0.0) {
            StoreLocationHelper.resolveGoogleMapsPlace(context, draft.latitude, draft.longitude)
        } else {
            GoogleMapsPlaceInfo()
        }
    }

    val effectiveGoogleName = if (draft.googleMapsName.isNotBlank()) draft.googleMapsName else googlePlaceInfo.placeName
    val isGoogleMatch = remember(storeName, effectiveGoogleName) {
        StoreLocationHelper.checkStoreNameMatch(storeName, effectiveGoogleName)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "📝 Cotización con Memoria GPS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Indicador de Reconocimiento GPS
                if (matchedStore != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE8F5E9),
                        border = BorderStroke(1.dp, Color(0xFF81C784)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📍⭐", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Comercio reconocido por GPS: ${matchedStore.name}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20)
                            )
                        }
                    }
                } else if (draft.latitude != 0.0 && draft.longitude != 0.0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFF3E0),
                        border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📍 Nueva ubicación GPS. Escribe el nombre del comercio (ej: Mercafácil) para guardarlo en la memoria.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text("Nombre del Producto") },
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
                        value = stageSize,
                        onValueChange = { stageSize = it },
                        label = { Text("Talla (ej: G)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = packageCount.toString(),
                        onValueChange = { packageCount = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                        label = { Text("Paquetes") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = unitsPerPackage.toString(),
                        onValueChange = { unitsPerPackage = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                        label = { Text("Uds / Paquete") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Comercio (ej: Mercafácil, Locatel...)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Integración y Retroalimentación con Google Maps
                if (draft.latitude != 0.0 && draft.longitude != 0.0) {
                    if (effectiveGoogleName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isGoogleMatch) Color(0xFFE8F5E9) else Color(0xFFE1F5FE),
                            border = BorderStroke(1.dp, if (isGoogleMatch) Color(0xFF81C784) else Color(0xFF4FC3F7)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (isGoogleMatch) "🗺️✅" else "🗺️💡", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isGoogleMatch) "¡Coincide con Google Maps! ($effectiveGoogleName)" else "Google Maps detecta: \"$effectiveGoogleName\"",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isGoogleMatch) Color(0xFF1B5E20) else Color(0xFF0277BD)
                                    )
                                }
                                if (!isGoogleMatch) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (storeName.isNotBlank()) "Al guardar, confirmas \"$storeName\" en estas coordenadas." else "Puedes adoptar el nombre de Google Maps:",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            onClick = { storeName = effectiveGoogleName },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Usar nombre", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Botón para verificar en Google Maps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        TextButton(
                            onClick = {
                                val mapUrl = "https://www.google.com/maps/search/?api=1&query=${draft.latitude},${draft.longitude}"
                                try {
                                    uriHandler.openUri(mapUrl)
                                } catch (_: Exception) {}
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("🗺️ Ver en Google Maps", fontSize = 11.sp)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text(if (isMultiUnit) "Precio por Paquete" else "Precio en Tienda") },
                        modifier = Modifier.weight(1.3f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = currency == CurrencyType.USD,
                                onClick = { currency = CurrencyType.USD },
                                label = { Text("$", fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = currency == CurrencyType.VES,
                                onClick = { currency = CurrencyType.VES },
                                label = { Text("Bs.", fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val previewText = buildString {
                        if (isMultiUnit) {
                            append(String.format(Locale.US, "Ref. BCV: $%.2f USD / pqte • $%.3f / ud", packagePriceUsd, unitCostUsd))
                            if (safePkgCount > 1) {
                                append(String.format(Locale.US, "\nTotal ($safePkgCount pqtes): $%.2f USD", totalCostUsd))
                            }
                        } else {
                            append(String.format(Locale.US, "Ref. BCV: $%.2f USD", packagePriceUsd))
                            if (safePkgCount > 1) {
                                append(String.format(Locale.US, " • Total ($safePkgCount uds): $%.2f USD", totalCostUsd))
                            }
                        }
                    }
                    Text(
                        text = previewText,
                        modifier = Modifier.padding(10.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedButton(
                    onClick = { onLookupFarmatodo(barcode, productName, brand) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSearchingFarmatodo) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscando en Farmatodo...")
                    } else {
                        Text(if (farmatodoBenchmark == null) "💊 Comparar con Farmatodo Venezuela" else "💊 Actualizar Farmatodo")
                    }
                }

                if (farmatodoBenchmark != null) {
                    val ft = farmatodoBenchmark!!
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE3F2FD),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("✅ Coincidencia en Farmatodo:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                            Text(ft.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(String.format("Precio Farmatodo: $%.2f (Bs. %.2f)", ft.effectivePriceUsd, ft.effectivePriceVes), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            if (productName.isNotBlank() && parsedPrice > 0.0) {
                                onSave(
                                    draft.copy(
                                        productName = productName,
                                        brand = brand,
                                        category = category,
                                        stageSize = stageSize,
                                        packageCount = packageCount,
                                        unitsPerPackage = unitsPerPackage,
                                        storeName = storeName.ifBlank { "Comercio" },
                                        storeAddress = storeAddress.ifBlank { googlePlaceInfo.fullAddress },
                                        priceAmount = parsedPrice,
                                        priceCurrency = currency,
                                        barcode = barcode,
                                        notes = notes,
                                        farmatodoBenchmark = farmatodoBenchmark,
                                        googleMapsName = effectiveGoogleName
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

@Composable
fun ManualBcvDialog(
    currentRate: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var textRate by remember { mutableStateOf(currentRate.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🏦 Ajuste Manual de Tasa BCV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Introduce la tasa oficial del día si te encuentras en un local sin señal de datos:",
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = textRate,
                    onValueChange = { textRate = it },
                    label = { Text("Tasa BCV (Bs. por $1 USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = textRate.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0) onSave(parsed)
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Helper para leer la foto, corregir orientación y extraer coordenadas EXIF GPS nativas de la imagen.
 */
private fun readAndCompressPhotoWithExif(context: Context, uri: Uri): Triple<String, Bitmap?, Pair<Double, Double>?>? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val original = BitmapFactory.decodeStream(stream) ?: return null

            var exifGps: Pair<Double, Double>? = null
            val rotated = try {
                context.contentResolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    exifGps = StoreLocationHelper.extractGpsFromExif(exif)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(original, 90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(original, 180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(original, 270f)
                        else -> original
                    }
                } ?: original
            } catch (_: Exception) {
                original
            }

            val maxDim = 1280
            val width = rotated.width
            val height = rotated.height
            val scaled = if (width > maxDim || height > maxDim) {
                val ratio = width.toFloat() / height.toFloat()
                val (newW, newH) = if (ratio > 1f) maxDim to (maxDim / ratio).toInt() else (maxDim * ratio).toInt() to maxDim
                Bitmap.createScaledBitmap(rotated, newW, newH, true)
            } else rotated

            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
            val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            Triple(base64, scaled, exifGps)
        }
    } catch (_: Exception) {
        null
    }
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
