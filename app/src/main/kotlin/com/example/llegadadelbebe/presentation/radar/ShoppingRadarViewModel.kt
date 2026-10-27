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

    val activeUser: StateFlow<String> = MutableStateFlow(localPreferences.getActiveUser())

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
        val totalUnits = (packageCount.coerceAtLeast(1)) * (unitsPerPackage.coerceAtLeast(1))

        val priceUsd = if (priceCurrency == CurrencyType.VES) {
            if (currentRate > 0.0) priceAmount / currentRate else priceAmount
        } else {
            priceAmount
        }

        val unitPriceUsd = if (totalUnits > 0) priceUsd / totalUnits else priceUsd
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        val newQuote = ProductPriceQuote(
            productName = productName.trim(),
            brand = brand.trim(),
            category = category,
            stageSize = stageSize.trim(),
            packageCount = packageCount.coerceAtLeast(1),
            unitsPerPackage = unitsPerPackage.coerceAtLeast(1),
            storeName = storeName.trim().ifBlank { "Comercio" },
            storeAddress = storeAddress.trim(),
            latitude = latitude,
            longitude = longitude,
            priceAmount = priceAmount,
            priceCurrency = priceCurrency,
            bcvRateApplied = currentRate,
            priceUsdEquivalent = priceUsd,
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
    }

    fun deletePriceQuote(id: String) {
        val updated = _quotes.value.filterNot { it.id == id }
        _quotes.value = updated
        localPreferences.saveRadarQuotesCache(updated)
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

        for (p in products) {
            val isPurchased = p.status != ProductStatus.PENDING
            val qty = if (isPurchased) p.quantityCurrent.coerceAtLeast(1) else (p.quantityTarget - p.quantityCurrent).coerceAtLeast(1)

            // Buscar si hay cotización registrada en tienda física para este producto
            val matchingQuote = quotes.firstOrNull { q ->
                (q.barcode.isNotBlank() && q.barcode == p.barcode) ||
                (p.name.contains(q.productName, ignoreCase = true) || q.productName.contains(p.name, ignoreCase = true)) ||
                (p.brand.isNotBlank() && q.brand.isNotBlank() && p.brand.equals(q.brand, ignoreCase = true) && p.category == q.category)
            }

            val (unitPriceUsd, sourceLabel) = when {
                matchingQuote != null -> {
                    val unit = if (matchingQuote.unitPriceUsd > 0.0) matchingQuote.unitPriceUsd else matchingQuote.priceUsdEquivalent
                    Pair(unit, "${matchingQuote.storeName} ($${String.format("%.2f", matchingQuote.priceUsdEquivalent)})")
                }
                matchingQuote?.farmatodoBenchmark != null -> {
                    val ft = matchingQuote.farmatodoBenchmark!!
                    Pair(ft.effectivePriceUsd, "Farmatodo Oficial ($${String.format("%.2f", ft.effectivePriceUsd)})")
                }
                else -> {
                    // Referencia estándar de mercado estimada según categoría
                    val defaultRef = when (p.category) {
                        ProductCategory.DIAPERS -> 0.35
                        ProductCategory.WIPES_HYGIENE -> 2.50
                        ProductCategory.FEEDING -> 8.00
                        ProductCategory.CLOTHING -> 4.50
                        ProductCategory.HEALTH -> 3.00
                        ProductCategory.CRIB_SLEEP -> 25.00
                        ProductCategory.TRANSPORT -> 45.00
                        ProductCategory.HOSPITAL_BAG -> 3.50
                    }
                    Pair(defaultRef, "Estimado de Mercado")
                }
            }

            val totalPrice = unitPriceUsd * qty
            if (isPurchased) {
                totalSpent += totalPrice
                purchasedCount++
            } else {
                totalPending += totalPrice
                pendingCount++
            }

            lines.add(
                ProductBudgetLine(
                    productId = p.id,
                    productName = p.name,
                    brand = p.brand,
                    category = p.category,
                    isPurchased = isPurchased,
                    quantity = qty,
                    unitPriceUsd = unitPriceUsd,
                    totalPriceUsd = totalPrice,
                    sourcePriceLabel = sourceLabel
                )
            )
        }

        return FamilyBudgetReport(
            totalSpentUsd = totalSpent,
            totalPendingUsd = totalPending,
            bcvRate = bcvRate,
            purchasedCount = purchasedCount,
            pendingCount = pendingCount,
            lines = lines.sortedWith(compareBy<ProductBudgetLine> { it.isPurchased }.thenByDescending { it.totalPriceUsd })
        )
    }
}
