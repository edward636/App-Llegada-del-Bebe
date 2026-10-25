package com.example.llegadadelbebe.presentation.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llegadadelbebe.data.repository.BabyInventoryRepository
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
    private val repository: BabyInventoryRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<ProductCategory?>(null)
    val selectedCategory: StateFlow<ProductCategory?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredProducts: StateFlow<List<BabyProduct>> = combine(
        repository.products,
        _selectedCategory,
        _searchQuery
    ) { products, category, query ->
        products.filter { product ->
            val matchesCategory = category == null || product.category == category
            val matchesQuery = query.isBlank() ||
                    product.name.contains(query, ignoreCase = true) ||
                    product.brand.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val isSyncing: StateFlow<Boolean> = repository.isSyncing

    fun selectCategory(category: ProductCategory?) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addProduct(product: BabyProduct, googleSheetUrl: String?) {
        viewModelScope.launch {
            repository.saveProduct(product, googleSheetUrl)
        }
    }

    fun toggleProductStatus(product: BabyProduct, googleSheetUrl: String?) {
        val nextStatus = when (product.status) {
            ProductStatus.PENDING -> ProductStatus.PURCHASED
            ProductStatus.PURCHASED -> ProductStatus.HOSPITAL_PACKED
            ProductStatus.HOSPITAL_PACKED -> ProductStatus.PURCHASED
            ProductStatus.GIFTED -> ProductStatus.HOSPITAL_PACKED
        }
        viewModelScope.launch {
            repository.saveProduct(product.copy(status = nextStatus), googleSheetUrl)
        }
    }

    fun deleteProduct(productId: String) {
        repository.deleteProduct(productId)
    }

    fun syncWithGoogleSheets(googleSheetUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.syncFromGoogleSheets(googleSheetUrl)
            if (result.isSuccess) {
                onResult(true, "¡Sincronización exitosa con Google Sheets!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Error al sincronizar")
            }
        }
    }
}
