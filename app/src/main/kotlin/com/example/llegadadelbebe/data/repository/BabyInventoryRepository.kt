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
     * Fuerza una sincronización manual o reactiva al cambiar de pantalla o reanudar la app.
     */
    fun triggerSync(googleSheetUrl: String? = null) {
        val targetUrl = googleSheetUrl?.ifBlank { activeGoogleSheetUrl } ?: activeGoogleSheetUrl
        if (targetUrl.isNotBlank()) {
            scope.launch {
                syncFromGoogleSheets(targetUrl)
            }
        }
    }

    /**
     * Guarda o actualiza un producto en memoria, en caché local y dispara sincronización automática a Google Sheets.
     */
    fun saveProduct(product: BabyProduct, googleSheetUrl: String? = null, onSyncComplete: ((Boolean) -> Unit)? = null) {
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
        localPreferences?.addPendingUploadProductId(product.id)

        if (targetUrl.isNotBlank()) {
            scope.launch {
                _syncStatus.value = CloudSyncStatus.SYNCING
                val result = sheetsService.saveProduct(targetUrl, product)
                val isSuccess = result.isSuccess && (result.getOrNull() == true)
                if (isSuccess) {
                    localPreferences?.removePendingUploadProductId(product.id)
                }
                _syncStatus.value = if (isSuccess) CloudSyncStatus.ONLINE else CloudSyncStatus.OFFLINE
                onSyncComplete?.invoke(isSuccess)
            }
        } else {
            onSyncComplete?.invoke(false)
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
        localPreferences?.removePendingUploadProductId(productId)
        localPreferences?.addDeletedProductId(productId)

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
     * Implementa un Smart Two-Way Merge por [BabyProduct.updatedAt] para evitar pisar
     * cambios o creaciones realizadas localmente mientras no había conexión a internet,
     * y distingue entre productos creados offline y eliminaciones remotas para evitar resurrección.
     */
    suspend fun syncFromGoogleSheets(googleSheetUrl: String): Result<Unit> {
        val targetUrl = googleSheetUrl.ifBlank { activeGoogleSheetUrl }
        if (targetUrl.isBlank()) return Result.failure(IllegalArgumentException("URL vacía"))

        _syncStatus.value = CloudSyncStatus.SYNCING
        val result = sheetsService.fetchInventory(targetUrl)

        return if (result.isSuccess) {
            _syncStatus.value = CloudSyncStatus.ONLINE
            val remoteItems = result.getOrNull() ?: emptyList()
            val deletedIds = localPreferences?.getDeletedProductIds() ?: emptySet()
            val pendingUploadIds = localPreferences?.getPendingUploadProductIds() ?: emptySet()
            val currentLocalMap = _products.value.associateBy { it.id }.toMutableMap()
            val mergedProducts = mutableListOf<BabyProduct>()
            val itemsToUpload = mutableListOf<BabyProduct>()

            // 1. Procesar elementos que vinieron de la nube
            for (remote in remoteItems) {
                if (remote.id in deletedIds) {
                    continue
                }
                val local = currentLocalMap.remove(remote.id)
                if (local == null) {
                    mergedProducts.add(remote)
                } else {
                    if (local.updatedAt > remote.updatedAt) {
                        val finalLocal = if (local.photoBase64.isBlank() && remote.photoBase64.isNotBlank()) {
                            local.copy(photoBase64 = remote.photoBase64)
                        } else local
                        mergedProducts.add(finalLocal)
                        itemsToUpload.add(finalLocal)
                    } else {
                        val finalRemote = if (remote.photoBase64.isBlank() && local.photoBase64.isNotBlank()) {
                            remote.copy(
                                photoBase64 = local.photoBase64,
                                rawPhotoBase64 = local.rawPhotoBase64
                            )
                        } else remote
                        mergedProducts.add(finalRemote)
                        localPreferences?.removePendingUploadProductId(remote.id)
                    }
                }
            }

            // 2. Procesar productos creados localmente sin internet que no existen aún en la nube
            // Solo se conservan y suben los que fueron creados/editados explícitamente en este teléfono (pendingUploadIds).
            // Si no están en pendingUploadIds, significa que fueron eliminados en la nube por el otro dispositivo.
            for ((localId, localItem) in currentLocalMap) {
                if (localId in pendingUploadIds && localId !in deletedIds) {
                    mergedProducts.add(localItem)
                    itemsToUpload.add(localItem)
                }
            }

            _products.value = mergedProducts
            localPreferences?.saveInventoryCache(mergedProducts)

            // 3. Subir en segundo plano productos locales más recientes o creados offline
            if (itemsToUpload.isNotEmpty()) {
                scope.launch {
                    itemsToUpload.forEach { item ->
                        val res = sheetsService.saveProduct(targetUrl, item)
                        if (res.isSuccess && res.getOrNull() == true) {
                            localPreferences?.removePendingUploadProductId(item.id)
                        }
                    }
                }
            }

            // 4. Limpiar eliminaciones pendientes en la nube
            if (deletedIds.isNotEmpty()) {
                scope.launch {
                    deletedIds.forEach { delId ->
                        sheetsService.deleteProductRemote(
                            targetUrl,
                            BabyProduct(
                                id = delId,
                                name = "Deleted Item",
                                category = ProductCategory.CLOTHING,
                                registeredBy = "App"
                            )
                        )
                    }
                }
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
            id = "seed-carters-bodies-rn",
            name = "Set Bodies Algodón Pima Manga Corta (Pack 5)",
            brand = "Carter's",
            model = "Baby Boy Safari",
            category = ProductCategory.CLOTHING,
            stageSize = "RN",
            packageCount = 1,
            unitsPerPackage = 5,
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
            id = "seed-pampers-rn",
            name = "Pañales Recién Nacido (Pack 36)",
            brand = "Pampers",
            model = "Premium Care RN",
            category = ProductCategory.DIAPERS,
            stageSize = "RN",
            packageCount = 2,
            unitsPerPackage = 36,
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
            id = "seed-waterwipes-bio",
            name = "Toallitas Húmedas 99.9% Agua Pura (Pack 4)",
            brand = "WaterWipes",
            model = "Bio Sensitive RN",
            category = ProductCategory.WIPES_HYGIENE,
            stageSize = "0M+",
            packageCount = 4,
            unitsPerPackage = 56,
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
