package com.example.llegadadelbebe.presentation.radar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.core.util.StoreLocationHelper
import com.example.llegadadelbebe.data.remote.BcvExchangeRateClient
import com.example.llegadadelbebe.data.remote.FarmatodoApiClient
import com.example.llegadadelbebe.data.remote.GeminiClient
import com.example.llegadadelbebe.data.remote.ParsedPriceQuote
import com.example.llegadadelbebe.data.repository.BabyInventoryRepository
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.BcvExchangeRate
import com.example.llegadadelbebe.domain.model.CurrencyType
import com.example.llegadadelbebe.domain.model.FamilyBudgetReport
import com.example.llegadadelbebe.domain.model.FarmatodoProductBenchmark
import com.example.llegadadelbebe.domain.model.KnownStoreLocation
import com.example.llegadadelbebe.domain.model.PriceComparisonVerdict
import com.example.llegadadelbebe.domain.model.ProductBudgetLine
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductPriceQuote
import com.example.llegadadelbebe.domain.model.ProductStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ShoppingRadarViewModel(
    application: Application,
    private val repository: BabyInventoryRepository? = null
) : AndroidViewModel(application) {

    private val localPreferences = LocalPreferences(application)
    private val bcvClient = BcvExchangeRateClient(application)
    private val farmatodoClient = FarmatodoApiClient()
    private val geminiClient = GeminiClient()
    private val googleSheetsService = com.example.llegadadelbebe.data.remote.GoogleSheetsService()

    private val _bcvRate = MutableStateFlow(
        BcvExchangeRate(
            rate = localPreferences.getLastBcvRate(),
            lastUpdated = localPreferences.getLastBcvDate(),
            isFallback = true
        )
    )
    val bcvRate: StateFlow<BcvExchangeRate> = _bcvRate.asStateFlow()

    private val _isLoadingBcv = MutableStateFlow(false)
    val isLoadingBcv: StateFlow<Boolean> = _isLoadingBcv.asStateFlow()

    private val _quotes = MutableStateFlow<List<ProductPriceQuote>>(emptyList())
    val quotes: StateFlow<List<ProductPriceQuote>> = _quotes.asStateFlow()

    private val _knownStores = MutableStateFlow<List<KnownStoreLocation>>(emptyList())
    val knownStores: StateFlow<List<KnownStoreLocation>> = _knownStores.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ProductCategory?>(null)
    val selectedCategory: StateFlow<ProductCategory?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isAnalyzingQuote = MutableStateFlow(false)
    val isAnalyzingQuote: StateFlow<Boolean> = _isAnalyzingQuote.asStateFlow()

    private val _isSearchingFarmatodo = MutableStateFlow(false)
    val isSearchingFarmatodo: StateFlow<Boolean> = _isSearchingFarmatodo.asStateFlow()

    private val _activeUser = MutableStateFlow(localPreferences.getActiveUser())
    val activeUser: StateFlow<String> = _activeUser.asStateFlow()

    fun setActiveUser(user: String) {
        _activeUser.value = user
    }

    // Productos del inventario (compartido con repositorio o caché local)
    private val inventoryProducts: StateFlow<List<BabyProduct>> = repository?.products
        ?: MutableStateFlow(localPreferences.getInventoryCache() ?: emptyList())

    init {
        // Cargar cotizaciones cacheadas
        _quotes.value = localPreferences.getRadarQuotesCache()
        // Cargar memoria de comercios GPS guardados
        _knownStores.value = localPreferences.getKnownStoreLocations()
        // Actualizar tasa BCV oficial
        refreshBcvRate()
        syncQuotesWithCloud()
    }

    val filteredQuotes: StateFlow<List<ProductPriceQuote>> = combine(
        _quotes,
        _selectedCategory,
        _searchQuery
    ) { allQuotes, category, query ->
        allQuotes.filter { q ->
            val matchesCategory = category == null || q.category == category
            val matchesQuery = query.isBlank() ||
                q.productName.contains(query, ignoreCase = true) ||
                q.brand.contains(query, ignoreCase = true) ||
                q.storeName.contains(query, ignoreCase = true) ||
                q.stageSize.contains(query, ignoreCase = true) ||
                q.barcode.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }.sortedByDescending { it.quotedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Presupuesto Dinámico Familiar:
     * Calcula en tiempo real cuánto se ha gastado (comprado) y cuánto falta por gastar (pendiente)
     * cruzando los productos del inventario con las cotizaciones de tiendas físicas y Farmatodo.
     */
    val familyBudget: StateFlow<FamilyBudgetReport> = combine(
        inventoryProducts,
        _quotes,
        _bcvRate
    ) { products, allQuotes, rate ->
        calculateFamilyBudgetReport(products, allQuotes, rate.rate)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        FamilyBudgetReport(0.0, 0.0, _bcvRate.value.rate, 0, 0, emptyList())
    )

    fun refreshBcvRate() {
        viewModelScope.launch {
            _isLoadingBcv.value = true
            try {
                val rate = bcvClient.fetchOfficialBcvRate()
                _bcvRate.value = rate
            } catch (_: Exception) {
            } finally {
                _isLoadingBcv.value = false
            }
        }
    }

    fun updateManualBcvRate(manualRate: Double) {
        if (manualRate > 0.0) {
            val updated = BcvExchangeRate(
                rate = manualRate,
                lastUpdated = "Manual (Ajustado)",
                isFallback = false
            )
            _bcvRate.value = updated
            localPreferences.saveLastBcvRate(manualRate, updated.lastUpdated)
        }
    }

    fun setCategoryFilter(category: ProductCategory?) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Memoria GPS: Busca si las coordenadas corresponden a un comercio ya conocido en la memoria (ej: Mercafácil)
     * dentro de un radio de proximidad de 120 metros.
     */
    fun findNearbyKnownStore(latitude: Double, longitude: Double): KnownStoreLocation? {
        return StoreLocationHelper.findNearbyKnownStore(latitude, longitude, _knownStores.value)
    }

    /**
     * Guarda un comercio en la Memoria GPS para auto-detectarlo automáticamente en futuras visitas,
     * retroalimentando el nombre confirmado por el usuario frente al nombre detectado en Google Maps.
     */
    fun recordKnownStore(
        name: String,
        address: String,
        latitude: Double,
        longitude: Double,
        googleMapsName: String = "",
        isUserConfirmed: Boolean = true
    ) {
        if (name.isBlank() || (latitude == 0.0 && longitude == 0.0)) return

        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val store = KnownStoreLocation(
            name = name.trim(),
            address = address.trim(),
            latitude = latitude,
            longitude = longitude,
            lastVisitedAt = now,
            googleMapsName = googleMapsName.trim(),
            isUserConfirmed = isUserConfirmed
        )

        localPreferences.saveKnownStoreLocation(store)
        _knownStores.value = localPreferences.getKnownStoreLocations()
    }

    fun deleteKnownStore(id: String) {
        val updated = _knownStores.value.filterNot { it.id == id }
        _knownStores.value = updated
        localPreferences.saveAllKnownStores(updated)
    }

    fun savePriceQuote(
        productName: String,
        brand: String,
        category: ProductCategory,
        stageSize: String,
        packageCount: Int,
        unitsPerPackage: Int,
        storeName: String,
        storeAddress: String,
        latitude: Double,
        longitude: Double,
        priceAmount: Double,
        priceCurrency: CurrencyType,
        photoBase64: String,
        barcode: String,
        notes: String,
        farmatodoBenchmark: FarmatodoProductBenchmark?,
        googleMapsName: String = ""
    ) {
        val currentRate = _bcvRate.value.rate
        val safeUnitsPerPackage = unitsPerPackage.coerceAtLeast(1)

        // priceAmount es el PRECIO POR PAQUETE registrado en el anaquel o etiqueta
        val packagePriceUsd = if (priceCurrency == CurrencyType.VES) {
            if (currentRate > 0.0) priceAmount / currentRate else priceAmount
        } else {
            priceAmount
        }

        // Costo por unidad individual dentro del paquete (ej: por pañal o toallita)
        val unitPriceUsd = if (safeUnitsPerPackage > 1) packagePriceUsd / safeUnitsPerPackage else packagePriceUsd
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        val newQuote = ProductPriceQuote(
            productName = productName.trim(),
            brand = brand.trim(),
            category = category,
            stageSize = stageSize.trim(),
            packageCount = packageCount.coerceAtLeast(1),
            unitsPerPackage = safeUnitsPerPackage,
            storeName = storeName.trim().ifBlank { "Comercio" },
            storeAddress = storeAddress.trim(),
            latitude = latitude,
            longitude = longitude,
            priceAmount = priceAmount,
            priceCurrency = priceCurrency,
            bcvRateApplied = currentRate,
            priceUsdEquivalent = packagePriceUsd,
            unitPriceUsd = unitPriceUsd,
            quotedAt = timestamp,
            registeredBy = activeUser.value,
            photoBase64 = photoBase64,
            notes = notes.trim(),
            barcode = barcode.trim(),
            googleMapsName = googleMapsName.trim(),
            farmatodoBenchmark = farmatodoBenchmark
        )

        // Registrar automáticamente en la memoria de tiendas GPS con retroalimentación Google Maps
        if (latitude != 0.0 && longitude != 0.0 && storeName.isNotBlank() && !storeName.equals("Comercio", ignoreCase = true)) {
            recordKnownStore(
                name = storeName,
                address = storeAddress,
                latitude = latitude,
                longitude = longitude,
                googleMapsName = googleMapsName,
                isUserConfirmed = true
            )
        }

        val updatedList = listOf(newQuote) + _quotes.value
        _quotes.value = updatedList
        localPreferences.saveRadarQuotesCache(updatedList)
        syncQuotesWithCloud()
    }

    fun deletePriceQuote(id: String) {
        localPreferences.recordDeletedQuoteId(id)
        val updated = _quotes.value.filterNot { it.id == id }
        _quotes.value = updated
        localPreferences.saveRadarQuotesCache(updated)
        syncQuotesWithCloud()
    }

    fun analyzePricePhoto(
        base64Image: String,
        apiKey: String,
        onResult: (ParsedPriceQuote?) -> Unit
    ) {
        viewModelScope.launch {
            _isAnalyzingQuote.value = true
            try {
                val res = geminiClient.extractPriceQuoteFromImage(
                    base64Image = base64Image,
                    apiKey = apiKey
                )
                onResult(res.getOrNull())
            } catch (_: Exception) {
                onResult(null)
            } finally {
                _isAnalyzingQuote.value = false
            }
        }
    }

    fun lookupFarmatodoBenchmark(
        barcode: String?,
        productName: String,
        brand: String,
        onResult: (FarmatodoProductBenchmark?) -> Unit
    ) {
        viewModelScope.launch {
            _isSearchingFarmatodo.value = true
            try {
                val currentRate = _bcvRate.value.rate
                val result = farmatodoClient.lookupBenchmark(
                    barcode = barcode,
                    productName = productName,
                    brand = brand,
                    bcvRate = currentRate
                )
                onResult(result)
            } catch (_: Exception) {
                onResult(null)
            } finally {
                _isSearchingFarmatodo.value = false
            }
        }
    }

    /**
     * Motor de Cálculo de Presupuesto:
     * Cruza cada artículo del inventario con:
     * 1. Cotizaciones en tiendas físicas (Mercafácil, etc.)
     * 2. Catálogo oficial de Farmatodo
     * 3. Estimaciones por categoría para lo no cotizado
     */
    private fun calculateFamilyBudgetReport(
        products: List<BabyProduct>,
        quotes: List<ProductPriceQuote>,
        bcvRate: Double
    ): FamilyBudgetReport {
        if (products.isEmpty()) {
            return FamilyBudgetReport(0.0, 0.0, bcvRate, 0, 0, emptyList())
        }

        val lines = mutableListOf<ProductBudgetLine>()
        var totalSpent = 0.0
        var totalPending = 0.0
        var purchasedCount = 0
        var pendingCount = 0
        var pricedCount = 0
        var unpricedCount = 0

        for (p in products) {
            val isPurchased = p.status != ProductStatus.PENDING
            val isPackageProduct = p.category == ProductCategory.DIAPERS ||
                p.category == ProductCategory.WIPES_HYGIENE ||
                p.unitsPerPackage > 1
            val unitsPerPkg = p.effectiveUnitsPerPackage.coerceAtLeast(1)

            // Buscar si hay cotización registrada en tienda física para este producto
            val matchingQuote = quotes.firstOrNull { q ->
                (q.barcode.isNotBlank() && q.barcode == p.barcode) ||
                (p.name.contains(q.productName, ignoreCase = true) || q.productName.contains(p.name, ignoreCase = true)) ||
                (p.brand.isNotBlank() && q.brand.isNotBlank() && p.brand.equals(q.brand, ignoreCase = true) && p.category == q.category)
            }

            // Buscar coincidencia en Farmatodo de las cotizaciones
            val ftBenchmark = matchingQuote?.farmatodoBenchmark
                ?: quotes.mapNotNull { it.farmatodoBenchmark }.firstOrNull { ft ->
                    (p.barcode.isNotBlank() && ft.barcode == p.barcode) ||
                    (p.name.contains(ft.name, ignoreCase = true) || ft.name.contains(p.name, ignoreCase = true))
                }

            // Determinar costo por paquete y costo por unidad individual
            // NUNCA INVENTAR PRECIOS: Orden de prioridad estricto:
            // 1. Precio de referencia fijado en el producto (por usuario o Farmatodo en inventario)
            // 2. Cotización física en tienda capturada en el Radar
            // 3. Coincidencia en Farmatodo
            // 4. Si no tiene precio real: Dejar en BLANCO (null)
            val (packagePriceUsd: Double?, unitPriceUsd: Double?, sourceLabel: String) = when {
                p.hasReferencePrice -> {
                    val pkg = p.referencePriceUsd!!
                    val unit = if (isPackageProduct && unitsPerPkg > 1) pkg / unitsPerPkg else pkg
                    val sourceName = p.referencePriceSource.ifBlank { "Precio fijado en Inventario" }
                    val label = if (isPackageProduct && unitsPerPkg > 1) {
                        "$sourceName ($${String.format(java.util.Locale.US, "%.2f", pkg)}/pqte • $${String.format(java.util.Locale.US, "%.3f", unit)}/ud)"
                    } else {
                        "$sourceName ($${String.format(java.util.Locale.US, "%.2f", pkg)})"
                    }
                    Triple(pkg, unit, label)
                }
                matchingQuote != null -> {
                    val pkg = matchingQuote.packagePriceUsd
                    val unit = if (isPackageProduct && unitsPerPkg > 1) {
                        pkg / unitsPerPkg
                    } else {
                        matchingQuote.effectiveUnitPriceUsd
                    }
                    val label = if (isPackageProduct && unitsPerPkg > 1) {
                        "${matchingQuote.storeName} ($${String.format(java.util.Locale.US, "%.2f", pkg)}/pqte • $${String.format(java.util.Locale.US, "%.3f", unit)}/ud)"
                    } else {
                        "${matchingQuote.storeName} ($${String.format(java.util.Locale.US, "%.2f", pkg)})"
                    }
                    Triple(pkg, unit, label)
                }
                ftBenchmark != null -> {
                    val pkg = ftBenchmark.effectivePriceUsd
                    val unit = if (isPackageProduct && unitsPerPkg > 1) pkg / unitsPerPkg else pkg
                    val label = if (isPackageProduct && unitsPerPkg > 1) {
                        "Farmatodo Oficial ($${String.format(java.util.Locale.US, "%.2f", pkg)}/pqte • $${String.format(java.util.Locale.US, "%.3f", unit)}/ud)"
                    } else {
                        "Farmatodo Oficial ($${String.format(java.util.Locale.US, "%.2f", pkg)})"
                    }
                    Triple(pkg, unit, label)
                }
                else -> {
                    // ¡CERO PRECIOS INVENTADOS! Se deja en blanco para ingresar o cotizar
                    Triple(null, null, "Sin precio de referencia")
                }
            }

            // Cálculo exacto del costo total y etiqueta descriptiva
            val totalPrice: Double?
            val quantityDisplayLabel: String
            val effectiveQty: Int

            if (isPackageProduct && unitsPerPkg > 1) {
                if (isPurchased) {
                    val packs = p.packageCount.coerceAtLeast(1)
                    val totalUnits = p.effectiveTotalUnits
                    totalPrice = packagePriceUsd?.let { it * packs }
                    effectiveQty = totalUnits
                    quantityDisplayLabel = "$packs pqte(s) × $unitsPerPkg uds ($totalUnits total)"
                } else {
                    val remainingUnits = (p.quantityTarget - p.quantityCurrent).coerceAtLeast(1)
                    val remainingPacks = kotlin.math.ceil(remainingUnits.toDouble() / unitsPerPkg).toInt().coerceAtLeast(1)
                    totalPrice = packagePriceUsd?.let { it * remainingPacks }
                    effectiveQty = remainingUnits
                    quantityDisplayLabel = "$remainingPacks pqte(s) faltantes (~$remainingUnits uds)"
                }
            } else {
                // Producto unitario (cuna, coche, ropita, tetero individual)
                effectiveQty = if (isPurchased) p.quantityCurrent.coerceAtLeast(1) else (p.quantityTarget - p.quantityCurrent).coerceAtLeast(1)
                totalPrice = unitPriceUsd?.let { it * effectiveQty }
                quantityDisplayLabel = "$effectiveQty unidad(es)"
            }

            if (isPurchased) {
                purchasedCount++
                if (totalPrice != null) totalSpent += totalPrice
            } else {
                pendingCount++
                if (totalPrice != null) totalPending += totalPrice
            }

            if (totalPrice != null) pricedCount++ else unpricedCount++

            lines.add(
                ProductBudgetLine(
                    productId = p.id,
                    productName = p.name,
                    brand = p.brand,
                    category = p.category,
                    isPurchased = isPurchased,
                    quantity = effectiveQty,
                    unitPriceUsd = unitPriceUsd,
                    totalPriceUsd = totalPrice,
                    sourcePriceLabel = sourceLabel,
                    packageCount = if (isPurchased) p.packageCount.coerceAtLeast(1) else kotlin.math.ceil((p.quantityTarget - p.quantityCurrent).coerceAtLeast(1).toDouble() / unitsPerPkg).toInt().coerceAtLeast(1),
                    unitsPerPackage = unitsPerPkg,
                    isPackageProduct = isPackageProduct,
                    quantityDisplayLabel = quantityDisplayLabel
                )
            )
        }

        return FamilyBudgetReport(
            totalSpentUsd = totalSpent,
            totalPendingUsd = totalPending,
            bcvRate = bcvRate,
            purchasedCount = purchasedCount,
            pendingCount = pendingCount,
            lines = lines.sortedWith(
                compareBy<ProductBudgetLine> { it.isPurchased }
                    .thenByDescending { it.hasPrice }
                    .thenByDescending { it.totalPriceUsd ?: 0.0 }
            ),
            pricedCount = pricedCount,
            unpricedCount = unpricedCount
        )
    }

    /**
     * Permite fijar o actualizar el precio de referencia de un producto en el inventario
     * (cargado por el usuario o fijado desde Farmatodo).
     */
    fun setProductReferencePrice(
        productId: String,
        priceUsd: Double?,
        source: String,
        googleSheetUrl: String? = null
    ) {
        val currentList = inventoryProducts.value
        val prod = currentList.firstOrNull { it.id == productId } ?: return
        val updated = prod.copy(
            referencePriceUsd = priceUsd,
            referencePriceSource = source,
            updatedAt = System.currentTimeMillis()
        )
        if (repository != null) {
            repository.saveProduct(updated, googleSheetUrl)
        } else {
            val newList = currentList.map { if (it.id == productId) updated else it }
            localPreferences.saveInventoryCache(newList)
        }
    }

    /**
     * Búsqueda en vivo de un producto en Farmatodo Venezuela para consultar su precio oficial.
     */
    suspend fun searchFarmatodoPrice(query: String, barcode: String = ""): FarmatodoProductBenchmark? {
        val currentRate = _bcvRate.value.rate
        return farmatodoClient.lookupBenchmark(barcode, query, "", currentRate)
    }

    fun syncQuotesWithCloud(sheetUrl: String = com.example.llegadadelbebe.core.security.SecureConfig.getGoogleSheetUrl()) {
        if (sheetUrl.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val deletedQuoteIds = localPreferences.getDeletedQuoteIds()
            val remoteResult = googleSheetsService.fetchSystemRecord(sheetUrl, com.example.llegadadelbebe.data.remote.GoogleSheetsService.SYS_RADAR_QUOTES)
            if (remoteResult.isSuccess) {
                val remotePayload = remoteResult.getOrNull()
                if (!remotePayload.isNullOrBlank()) {
                    val remoteQuotes = localPreferences.parseQuotesJson(remotePayload).filterNot { it.id in deletedQuoteIds }
                    if (remoteQuotes.isNotEmpty()) {
                        val currentMap = _quotes.value.filterNot { it.id in deletedQuoteIds }.associateBy { it.id }.toMutableMap()
                        for (rq in remoteQuotes) {
                            if (!currentMap.containsKey(rq.id)) {
                                currentMap[rq.id] = rq
                            }
                        }
                        val merged = currentMap.values.sortedByDescending { it.quotedAt }
                        _quotes.value = merged
                        localPreferences.saveRadarQuotesCache(merged)
                    }
                }
            }

            // Subir cotizaciones unificadas a la nube (excluyendo eliminadas)
            val activeQuotes = _quotes.value.filterNot { it.id in deletedQuoteIds }
            val jsonPayload = localPreferences.quotesToJson(activeQuotes)
            googleSheetsService.saveSystemRecord(
                webAppUrl = sheetUrl,
                systemId = com.example.llegadadelbebe.data.remote.GoogleSheetsService.SYS_RADAR_QUOTES,
                name = "Cotizaciones de Precios",
                jsonPayload = jsonPayload
            )
        }
    }
}
