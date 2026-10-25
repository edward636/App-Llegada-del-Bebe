package com.example.llegadadelbebe.presentation.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                    product.ingredients.contains(query, ignoreCase = true) ||
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

    fun toggleProductStatus(product: BabyProduct, googleSheetUrl: String?) {
        val nextStatus = when (product.status) {
            ProductStatus.PENDING -> ProductStatus.PURCHASED
            ProductStatus.PURCHASED -> ProductStatus.HOSPITAL_PACKED
            ProductStatus.HOSPITAL_PACKED -> ProductStatus.PURCHASED
            ProductStatus.GIFTED -> ProductStatus.HOSPITAL_PACKED
        }
        repository.saveProduct(product.copy(status = nextStatus), googleSheetUrl)
    }

    fun deleteProduct(productId: String) {
        repository.deleteProduct(productId)
    }

    fun analyzePhoto(base64Image: String, apiKey: String, onComplete: (BabyProduct?) -> Unit) {
        viewModelScope.launch {
            _isAnalyzingPhoto.value = true
            val result = geminiClient.extractProductFromImage(
                base64Image = base64Image,
                apiKey = apiKey
            )
            _isAnalyzingPhoto.value = false
            if (result.isSuccess) {
                val extracted = result.getOrNull()?.copy(registeredBy = _activeUser.value)
                onComplete(extracted)
            } else {
                onComplete(null)
            }
        }
    }
}
