package com.example.llegadadelbebe.domain.model

/**
 * Nivel de cobertura del consumible según estándar pediátrico.
 */
enum class StockStatus(val label: String, val badgeColorHex: Long) {
    OPTIMAL("Cantidad Ideal", 0xFF2E7D32),       // Verde
    DEFICIT("Falta Comprar", 0xFFC62828),        // Rojo
    EXCESS_WARNING("Riesgo de Pérdida", 0xFFEF6C00), // Naranja/Ámbar
    COVERED("Bien Cubierto", 0xFF1565C0)         // Azul
}

/**
 * Resumen de auditoría para un tipo de producto consumible.
 */
data class ConsumableCategoryAudit(
    val categoryName: String,
    val detailLabel: String,
    val currentUnits: Int,
    val recommendedUnits: Int,
    val status: StockStatus,
    val recommendationMessage: String
)

/**
 * Auditoría integral de consumibles esenciales para el bebé niño.
 */
data class ConsumablesAuditSummary(
    val diapersRn: ConsumableCategoryAudit,
    val diapersSize1: ConsumableCategoryAudit,
    val wipes: ConsumableCategoryAudit,
    val babySoaps: ConsumableCategoryAudit,
    val clothingRn: ConsumableCategoryAudit,
    val clothing0to3M: ConsumableCategoryAudit,
    val geminiSmartAdvice: String = ""
)
