package com.example.llegadadelbebe.data.repository

import com.example.llegadadelbebe.data.remote.GoogleSheetsService
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class CloudSyncStatus(val label: String, val icon: String) {
    ONLINE("En línea", "🟢"),
    SYNCING("Sincronizando...", "🔄"),
    OFFLINE("Sin conexión", "🔴")
}

/**
 * Repositorio central del inventario del bebé.
 * Administra el estado reactivo en memoria y sincroniza automáticamente con Google Sheets.
 */
class BabyInventoryRepository(
    private val sheetsService: GoogleSheetsService = GoogleSheetsService(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val _products = MutableStateFlow<List<BabyProduct>>(getInitialStarterProducts())
    val products: StateFlow<List<BabyProduct>> = _products.asStateFlow()

    private val _syncStatus = MutableStateFlow(CloudSyncStatus.ONLINE)
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    private var activeGoogleSheetUrl: String = ""

    fun initializeSheetUrl(url: String) {
        if (url.isNotBlank() && activeGoogleSheetUrl != url) {
            activeGoogleSheetUrl = url
            // Sincronización inicial automática en segundo plano
            scope.launch {
                syncFromGoogleSheets(url)
            }
        }
    }

    /**
     * Guarda o actualiza un producto en memoria y dispara sincronización automática a Google Sheets.
     */
    fun saveProduct(product: BabyProduct, googleSheetUrl: String? = null) {
        val targetUrl = googleSheetUrl?.ifBlank { activeGoogleSheetUrl } ?: activeGoogleSheetUrl
        val currentList = _products.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == product.id }
        if (index >= 0) {
            currentList[index] = product
        } else {
            currentList.add(0, product)
        }
        _products.value = currentList

        // Sincronización automática inmediata en segundo plano
        if (targetUrl.isNotBlank()) {
            scope.launch {
                _syncStatus.value = CloudSyncStatus.SYNCING
                val result = sheetsService.saveProduct(targetUrl, product)
                _syncStatus.value = if (result.isSuccess) CloudSyncStatus.ONLINE else CloudSyncStatus.OFFLINE
            }
        }
    }

    /**
     * Elimina un producto por ID localmente.
     */
    fun deleteProduct(productId: String) {
        _products.value = _products.value.filterNot { it.id == productId }
    }

    /**
     * Sincroniza y descarga el inventario completo desde la hoja de Google Sheets.
     */
    suspend fun syncFromGoogleSheets(googleSheetUrl: String): Result<Unit> {
        val targetUrl = googleSheetUrl.ifBlank { activeGoogleSheetUrl }
        if (targetUrl.isBlank()) return Result.failure(IllegalArgumentException("URL vacía"))

        _syncStatus.value = CloudSyncStatus.SYNCING
        val result = sheetsService.fetchInventory(targetUrl)

        return if (result.isSuccess) {
            _syncStatus.value = CloudSyncStatus.ONLINE
            val remoteItems = result.getOrNull() ?: emptyList()
            if (remoteItems.isNotEmpty()) {
                _products.value = remoteItems
            }
            Result.success(Unit)
        } else {
            _syncStatus.value = CloudSyncStatus.OFFLINE
            Result.failure(result.exceptionOrNull() ?: Exception("Error de conexión"))
        }
    }

    /**
     * Artículos esenciales precargados para iniciar el inventario de Edward y Britney.
     */
    private fun getInitialStarterProducts(): List<BabyProduct> = listOf(
        BabyProduct(
            id = UUID.randomUUID().toString(),
            name = "Set Bodies Algodón Pima Manga Corta (Pack 5)",
            brand = "Carter's",
            model = "Baby Boy Safari",
            category = ProductCategory.CLOTHING,
            stageSize = "RN",
            quantityCurrent = 5,
            quantityTarget = 8,
            status = ProductStatus.PURCHASED,
            ingredients = "100% Algodón Pima Peruano hipoalergénico, broches sin níquel",
            manufacturedIn = "Perú",
            expirationDate = "N/A",
            notes = "Lavado con jabón neutro de bebé",
            geminiOpinion = "Excelente para la piel delicada del recién nacido. Ideal para cambios continuos.",
            registeredBy = "Mamá (Britney)"
        ),
        BabyProduct(
            id = UUID.randomUUID().toString(),
            name = "Pañales Recién Nacido (Pack 36)",
            brand = "Pampers",
            model = "Premium Care RN",
            category = ProductCategory.DIAPERS,
            stageSize = "RN",
            quantityCurrent = 72,
            quantityTarget = 140,
            status = ProductStatus.PURCHASED,
            ingredients = "Loción hipoalergénica con aloe vera, canales de aire absorbentes",
            manufacturedIn = "Brasil",
            expirationDate = "12/2027",
            notes = "Corte especial para cordón umbilical",
            geminiOpinion = "Buena cantidad inicial. Evitar acumular más de 140-160 unidades RN ya que crecen rápido.",
            registeredBy = "Papá (Edward)"
        ),
        BabyProduct(
            id = UUID.randomUUID().toString(),
            name = "Toallitas Húmedas 99% Agua Pura (Pack 4x56)",
            brand = "WaterWipes",
            model = "Bio Sensitive",
            category = ProductCategory.WIPES_HYGIENE,
            stageSize = "0M+",
            quantityCurrent = 224,
            quantityTarget = 300,
            status = ProductStatus.PURCHASED,
            ingredients = "99.9% agua purificada, 0.1% extracto de semilla de toronja",
            manufacturedIn = "Irlanda",
            expirationDate = "06/2026",
            notes = "0% alcohol, perfume ni parabenos",
            geminiOpinion = "Las más recomendadas por pediatras para los primeros 3 meses de vida del bebé.",
            registeredBy = "Mamá (Britney)"
        )
    )
}
