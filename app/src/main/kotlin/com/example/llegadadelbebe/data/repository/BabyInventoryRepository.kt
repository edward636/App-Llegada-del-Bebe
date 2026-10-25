package com.example.llegadadelbebe.data.repository

import com.example.llegadadelbebe.data.remote.GoogleSheetsService
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Repositorio central del inventario del bebé.
 * Administra el estado en memoria y sincroniza bidireccionalmente con Google Sheets.
 */
class BabyInventoryRepository(
    private val sheetsService: GoogleSheetsService = GoogleSheetsService()
) {

    private val _products = MutableStateFlow<List<BabyProduct>>(getInitialStarterProducts())
    val products: StateFlow<List<BabyProduct>> = _products.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /**
     * Agrega o actualiza un producto localmente y opcionalmente en Google Sheets.
     */
    suspend fun saveProduct(product: BabyProduct, googleSheetUrl: String? = null) {
        val currentList = _products.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == product.id }
        if (index >= 0) {
            currentList[index] = product
        } else {
            currentList.add(0, product)
        }
        _products.value = currentList

        if (!googleSheetUrl.isNullOrBlank()) {
            _isSyncing.value = true
            sheetsService.saveProduct(googleSheetUrl, product)
            _isSyncing.value = false
        }
    }

    /**
     * Elimina un producto por ID.
     */
    fun deleteProduct(productId: String) {
        _products.value = _products.value.filterNot { it.id == productId }
    }

    /**
     * Sincroniza y descarga el inventario completo desde la hoja de Google Sheets.
     */
    suspend fun syncFromGoogleSheets(googleSheetUrl: String): Result<Unit> {
        if (googleSheetUrl.isBlank()) return Result.failure(IllegalArgumentException("URL vacía"))
        _isSyncing.value = true
        val result = sheetsService.fetchInventory(googleSheetUrl)
        _isSyncing.value = false

        return if (result.isSuccess) {
            val remoteItems = result.getOrNull() ?: emptyList()
            if (remoteItems.isNotEmpty()) {
                _products.value = remoteItems
            }
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Error desconocido"))
        }
    }

    /**
     * Productos precargados esenciales para que los padres tengan una guía inicial inmediata.
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
            notes = "Algodón hipoalergénico, broches sin níquel",
            geminiOpinion = "Excelente para la piel delicada del recién nacido. Práctico para los cambios rápidos.",
            registeredBy = "Mamá"
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
            notes = "Con indicador de humedad y corte umbilical",
            geminiOpinion = "Buena absorción para las primeras 2 semanas. Recordar no sobrecomprar esta talla.",
            registeredBy = "Papá"
        ),
        BabyProduct(
            id = UUID.randomUUID().toString(),
            name = "Toallitas Húmedas Hipoalergénicas (Pack 80)",
            brand = "Huggies",
            model = "Pure & Natural 99% Agua",
            category = ProductCategory.WIPES_HYGIENE,
            stageSize = "0M+",
            quantityCurrent = 2,
            quantityTarget = 4,
            status = ProductStatus.PURCHASED,
            notes = "Sin alcohol, sin fragancia",
            geminiOpinion = "Muy recomendadas para prevenir dermatitis del pañal.",
            registeredBy = "Mamá"
        ),
        BabyProduct(
            id = UUID.randomUUID().toString(),
            name = "Silla de Auto para Recién Nacido (Huevito)",
            brand = "Chicco",
            model = "KeyFit 30",
            category = ProductCategory.TRANSPORT,
            stageSize = "0M+ (hasta 13kg)",
            quantityCurrent = 0,
            quantityTarget = 1,
            status = ProductStatus.PENDING,
            notes = "Requisito obligatorio para el alta médica de la clínica",
            geminiOpinion = "Elemento crítico de seguridad vial. Instalar a contra marcha.",
            registeredBy = "Papá"
        ),
        BabyProduct(
            id = UUID.randomUUID().toString(),
            name = "Biberón Anticólicos 125ml / 4oz",
            brand = "Philips Avent",
            model = "Natural Response Tetina Flujo 1",
            category = ProductCategory.FEEDING,
            stageSize = "0M",
            quantityCurrent = 2,
            quantityTarget = 2,
            status = ProductStatus.PURCHASED,
            notes = "Libre de BPA con válvula para reducir cólicos",
            geminiOpinion = "Compatible con lactancia materna para minimizar confusión de pezón.",
            registeredBy = "Mamá"
        )
    )
}
