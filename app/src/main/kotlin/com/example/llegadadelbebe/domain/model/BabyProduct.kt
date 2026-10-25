package com.example.llegadadelbebe.domain.model

import kotlinx.serialization.Serializable

/**
 * Categorías de artículos para la llegada del bebé.
 */
enum class ProductCategory(val displayName: String, val icon: String) {
    CLOTHING("Ropita", "👕"),
    DIAPERS("Pañales", "🧷"),
    WIPES_HYGIENE("Higiene y Baño", "🛁"),
    FEEDING("Alimentación y Lactancia", "🍼"),
    CRIB_SLEEP("Cuna y Habitación", "🛏️"),
    TRANSPORT("Paseo y Auto", "🚗"),
    HEALTH("Salud y Botiquín", "🩺"),
    HOSPITAL_BAG("Maleta Clínica", "🏥")
}

/**
 * Estado de adquisición del producto.
 */
enum class ProductStatus(val label: String) {
    PURCHASED("Comprado"),
    GIFTED("Regalado / Baby Shower"),
    PENDING("Falta Adquirir"),
    HOSPITAL_PACKED("En Maleta Clínica")
}

/**
 * Nivel de seguridad dermatológica / pediátrica evaluado por Gemini 3.8.
 */
enum class ProductSafetyLevel(val label: String, val badgeIcon: String) {
    SAFE("Seguro RN", "🟢"),
    CAUTION("Precaución", "🟡"),
    WARNING("Riesgo / Evitar RN", "🔴")
}

/**
 * Entidad principal de producto para el inventario del bebé.
 */
@Serializable
data class BabyProduct(
    val id: String,
    val name: String,
    val brand: String = "",
    val model: String = "",
    val category: ProductCategory,
    val stageSize: String = "",       // Ej: "RN", "0-3M", "3-6M"
    val quantityCurrent: Int = 1,
    val quantityTarget: Int = 1,
    val status: ProductStatus = ProductStatus.PURCHASED,
    val ingredients: String = "",     // Ingredientes de cosméticos/alimentos o composición textil
    val risksAndWarnings: String = "",// Riesgos, alérgenos, fragancias, advertencias de uso detectadas por Gemini
    val safetyLevel: ProductSafetyLevel = ProductSafetyLevel.SAFE,
    val manufacturedIn: String = "",  // País o lugar de origen/fabricación
    val expirationDate: String = "",  // Fecha de vencimiento/caducidad (si aplica)
    val photoBase64: String = "",     // Miniatura optimizada en Base64 de la foto de catálogo limpia o de cámara
    val rawPhotoBase64: String = "",  // Miniatura de la foto real tomada con la cámara (respaldo)
    val notes: String = "",
    val geminiOpinion: String = "",   // Consejo u opinión extraído por Gemini 3.8
    val registeredBy: String = "",    // "Papá (Edward)" o "Mamá (Britney)"
    val updatedAt: Long = System.currentTimeMillis()
)
