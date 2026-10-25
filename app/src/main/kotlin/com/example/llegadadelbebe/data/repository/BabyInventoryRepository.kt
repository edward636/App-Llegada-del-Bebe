package com.example.llegadadelbebe.data.repository

import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.data.remote.GoogleSheetsService
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductSafetyLevel
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
 * Administra el estado reactivo en memoria, guarda caché local (Offline-first)
 * y sincroniza automáticamente con Google Sheets en segundo plano.
 */
class BabyInventoryRepository(
    private val localPreferences: LocalPreferences? = null,
    private val sheetsService: GoogleSheetsService = GoogleSheetsService(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val _products = MutableStateFlow<List<BabyProduct>>(
        localPreferences?.getInventoryCache() ?: getInitialStarterProducts()
    )
    val products: StateFlow<List<BabyProduct>> = _products.asStateFlow()

    private val _syncStatus = MutableStateFlow(CloudSyncStatus.ONLINE)
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    private var activeGoogleSheetUrl: String = ""

    fun initializeSheetUrl(url: String) {
        if (url.isNotBlank() && activeGoogleSheetUrl != url) {
            activeGoogleSheetUrl = url
            scope.launch {
                syncFromGoogleSheets(url)
            }
        }
    }

    /**
     * Guarda o actualiza un producto en memoria, en caché local y dispara sincronización automática a Google Sheets.
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
        localPreferences?.saveInventoryCache(currentList)

        if (targetUrl.isNotBlank()) {
            scope.launch {
                _syncStatus.value = CloudSyncStatus.SYNCING
                val result = sheetsService.saveProduct(targetUrl, product)
                _syncStatus.value = if (result.isSuccess) CloudSyncStatus.ONLINE else CloudSyncStatus.OFFLINE
            }
        }
    }

    /**
     * Elimina un producto localmente y sincroniza su eliminación en Google Sheets.
     */
    fun deleteProduct(productId: String, googleSheetUrl: String? = null) {
        val targetUrl = googleSheetUrl?.ifBlank { activeGoogleSheetUrl } ?: activeGoogleSheetUrl
        val itemToRemove = _products.value.firstOrNull { it.id == productId }
        val updated = _products.value.filterNot { it.id == productId }
        _products.value = updated
        localPreferences?.saveInventoryCache(updated)

        if (targetUrl.isNotBlank() && itemToRemove != null) {
            scope.launch {
                _syncStatus.value = CloudSyncStatus.SYNCING
                val res = sheetsService.deleteProductRemote(targetUrl, itemToRemove)
                _syncStatus.value = if (res.isSuccess) CloudSyncStatus.ONLINE else CloudSyncStatus.OFFLINE
            }
        }
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
                localPreferences?.saveInventoryCache(remoteItems)
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
            risksAndWarnings = "Sin riesgos dermatológicos. Lavar previamente con jabón neutro sin suavizante y retirar etiquetas internas que puedan rozar el cuello.",
            safetyLevel = ProductSafetyLevel.SAFE,
            manufacturedIn = "Perú",
            expirationDate = "N/A",
            notes = "Lavado con jabón neutro de bebé",
            geminiOpinion = "Excelente para la piel delicada del recién nacido. El tejido Pima permite transpiración óptima y evita sudamina.",
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
            ingredients = "Celulosa suave, loción hipoalergénica con extracto de aloe vera, canales de aire absorbentes, 0% parabenos y látex",
            risksAndWarnings = "Seguro para recién nacidos. Vigilar que el ajuste no presione el muñón del cordón umbilical y cambiar cada 2-3 horas.",
            safetyLevel = ProductSafetyLevel.SAFE,
            manufacturedIn = "Brasil",
            expirationDate = "12/2027",
            notes = "Corte especial para cordón umbilical",
            geminiOpinion = "Buena cantidad inicial. Evitar acumular más de 140-160 unidades RN ya que crecen rápido en las primeras 3 semanas.",
            registeredBy = "Papá (Edward)"
        ),
        BabyProduct(
            id = UUID.randomUUID().toString(),
            name = "Toallitas Húmedas 99.9% Agua Pura (Pack 4)",
            brand = "WaterWipes",
            model = "Bio Sensitive RN",
            category = ProductCategory.WIPES_HYGIENE,
            stageSize = "0M+",
            quantityCurrent = 4,
            quantityTarget = 4,
            status = ProductStatus.PURCHASED,
            ingredients = "99.9% agua purificada, 0.1% extracto de semilla de toronja (Citrus Grandis)",
            risksAndWarnings = "0% alcohol, fragancia, sulfatos ni parabenos. Cerrar bien el sello adhesivo tras cada uso para evitar contaminación o secado.",
            safetyLevel = ProductSafetyLevel.SAFE,
            manufacturedIn = "Irlanda",
            expirationDate = "06/2027",
            notes = "4 paquetes de 56 unidades c/u",
            geminiOpinion = "Las más recomendadas por neonatólogos para el primer mes de vida por su pureza extrema equivalente a algodón y agua hervida.",
            registeredBy = "Mamá (Britney)"
        )
    )
}
