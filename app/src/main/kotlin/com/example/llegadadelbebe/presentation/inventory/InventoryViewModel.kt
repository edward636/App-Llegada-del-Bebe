package com.example.llegadadelbebe.presentation.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llegadadelbebe.core.util.BarcodeHelper
import com.example.llegadadelbebe.data.remote.CatalogImageFetcher
import com.example.llegadadelbebe.data.remote.GeminiClient
import com.example.llegadadelbebe.data.repository.BabyInventoryRepository
import com.example.llegadadelbebe.data.repository.CloudSyncStatus
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InventoryViewModel(
    private val repository: BabyInventoryRepository,
    private val geminiClient: GeminiClient = GeminiClient()
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<ProductCategory?>(null)
    val selectedCategory: StateFlow<ProductCategory?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeUser = MutableStateFlow("Papá (Edward)")
    val activeUser: StateFlow<String> = _activeUser.asStateFlow()

    private val _isAnalyzingPhoto = MutableStateFlow(false)
    val isAnalyzingPhoto: StateFlow<Boolean> = _isAnalyzingPhoto.asStateFlow()

    val filteredProducts: StateFlow<List<BabyProduct>> = combine(
        repository.products,
        _selectedCategory,
        _searchQuery
    ) { products, category, query ->
        products.filter { product ->
            val matchesCategory = category == null || product.category == category
            val matchesQuery = query.isBlank() ||
                    product.name.contains(query, ignoreCase = true) ||
                    product.brand.contains(query, ignoreCase = true) ||
                    product.model.contains(query, ignoreCase = true) ||
                    product.ingredients.contains(query, ignoreCase = true) ||
                    product.risksAndWarnings.contains(query, ignoreCase = true) ||
                    product.manufacturedIn.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val syncStatus: StateFlow<CloudSyncStatus> = repository.syncStatus

    fun selectCategory(category: ProductCategory?) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setActiveUser(user: String) {
        _activeUser.value = user
    }

    fun toggleActiveUser() {
        _activeUser.value = if (_activeUser.value.contains("Papá")) "Mamá (Britney)" else "Papá (Edward)"
    }

    fun addProduct(product: BabyProduct, googleSheetUrl: String?) {
        repository.saveProduct(product.copy(registeredBy = _activeUser.value), googleSheetUrl)
    }

    fun updateProduct(product: BabyProduct, googleSheetUrl: String?) {
        repository.saveProduct(product.copy(updatedAt = System.currentTimeMillis()), googleSheetUrl)
    }

    fun toggleProductStatus(product: BabyProduct, googleSheetUrl: String?) {
        val nextStatus = when (product.status) {
            ProductStatus.PENDING -> ProductStatus.PURCHASED
            ProductStatus.PURCHASED -> ProductStatus.HOSPITAL_PACKED
            ProductStatus.HOSPITAL_PACKED -> ProductStatus.PURCHASED
            ProductStatus.GIFTED -> ProductStatus.HOSPITAL_PACKED
        }
        repository.saveProduct(product.copy(status = nextStatus, updatedAt = System.currentTimeMillis()), googleSheetUrl)
    }

    fun deleteProduct(productId: String, googleSheetUrl: String? = null) {
        repository.deleteProduct(productId, googleSheetUrl)
    }

    fun analyzePhoto(
        base64Image: String,
        thumbnailBase64: String = "",
        detectedBarcode: String? = null,
        apiKey: String,
        onComplete: (BabyProduct?) -> Unit
    ) {
        viewModelScope.launch {
            _isAnalyzingPhoto.value = true
            val result = geminiClient.extractProductFromImage(
                base64Image = base64Image,
                thumbnailBase64 = thumbnailBase64,
                apiKey = apiKey
            )
            if (result.isSuccess) {
                var extracted = result.getOrNull()?.copy(registeredBy = _activeUser.value)
                if (extracted != null) {
                    // Integrar código de barra (de ML Kit o de Gemini) y resolver variante para Venezuela
                    val finalBarcode = if (!detectedBarcode.isNullOrBlank()) detectedBarcode else extracted.barcode
                    val resolvedVariant = BarcodeHelper.resolveCountryVariant(finalBarcode, extracted.manufacturedIn)
                    extracted = extracted.copy(
                        barcode = finalBarcode,
                        countryVariant = resolvedVariant
                    )

                    // Buscar imagen de catálogo limpia (fondo blanco e-commerce) en internet
                    val catalogThumb = CatalogImageFetcher.fetchCleanCatalogThumbnail(
                        name = extracted.name,
                        brand = extracted.brand,
                        model = extracted.model,
                        category = extracted.category.displayName
                    )
                    if (catalogThumb != null) {
                        extracted = extracted.copy(
                            photoBase64 = catalogThumb,
                            rawPhotoBase64 = thumbnailBase64
                        )
                    } else {
                        extracted = extracted.copy(
                            photoBase64 = thumbnailBase64,
                            rawPhotoBase64 = thumbnailBase64
                        )
                    }
                }
                _isAnalyzingPhoto.value = false
                onComplete(extracted)
            } else {
                _isAnalyzingPhoto.value = false
                onComplete(null)
            }
        }
    }

    /**
     * Toma una segunda foto (reverso / tabla de ingredientes) y la fusiona en el producto existente.
     */
    fun complementProductDetailsWithPhoto(
        base64Image: String,
        existingProduct: BabyProduct,
        detectedBarcode: String? = null,
        apiKey: String,
        onComplete: (BabyProduct) -> Unit
    ) {
        viewModelScope.launch {
            _isAnalyzingPhoto.value = true
            val result = geminiClient.extractAdditionalDetailsFromImage(
                base64Image = base64Image,
                existingProduct = existingProduct,
                apiKey = apiKey
            )
            _isAnalyzingPhoto.value = false
            if (result.isSuccess) {
                var updated = result.getOrNull() ?: existingProduct
                val finalBarcode = if (!detectedBarcode.isNullOrBlank()) detectedBarcode else updated.barcode
                val resolvedVariant = BarcodeHelper.resolveCountryVariant(finalBarcode, updated.manufacturedIn)
                updated = updated.copy(
                    barcode = finalBarcode,
                    countryVariant = resolvedVariant
                )
                onComplete(updated)
            } else {
                onComplete(existingProduct)
            }
        }
    }

    /**
     * Busca información de un producto directamente a partir de su código de barras escaneado.
     */
    fun lookupBarcodeWithGemini(
        barcode: String,
        apiKey: String,
        onResult: (BabyProduct?) -> Unit
    ) {
        viewModelScope.launch {
            _isAnalyzingPhoto.value = true
            val result = geminiClient.lookupProductByBarcode(barcode, apiKey)
            if (result.isSuccess) {
                var product = result.getOrNull()?.copy(registeredBy = _activeUser.value)
                if (product != null) {
                    val catalogThumb = CatalogImageFetcher.fetchCleanCatalogThumbnail(
                        name = product.name,
                        brand = product.brand,
                        model = product.model,
                        category = product.category.displayName
                    )
                    if (catalogThumb != null) {
                        product = product.copy(photoBase64 = catalogThumb)
                    }
                }
                _isAnalyzingPhoto.value = false
                onResult(product)
            } else {
                _isAnalyzingPhoto.value = false
                onResult(null)
            }
        }
    }

    fun searchCatalogImage(
        name: String,
        brand: String,
        model: String,
        category: String = "",
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val thumb = CatalogImageFetcher.fetchCleanCatalogThumbnail(name, brand, model, category)
            onResult(thumb)
        }
    }

    fun searchMultipleCatalogImages(
        query: String,
        limit: Int = 4,
        onResult: (List<String>) -> Unit
    ) {
        viewModelScope.launch {
            val list = CatalogImageFetcher.searchMultipleThumbnails(query, limit)
            onResult(list)
        }
    }
}
