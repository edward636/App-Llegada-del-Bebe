package com.example.llegadadelbebe.domain.model

/**
 * Nivel de cobertura del consumible según estándar pediátrico.
 */
enum class StockStatus(val label: String, val badgeColorHex: Long) {
    OPTIMAL("Cantidad Ideal", 0xFF2E7D32),           // Verde
    DEFICIT("Falta Comprar", 0xFFC62828),            // Rojo
    EXCESS_WARNING("Riesgo de Pérdida", 0xFFEF6C00), // Naranja/Ámbar
    COVERED("Bien Cubierto", 0xFF1565C0)             // Azul
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
 * Desglose específico de pañales por talla (conteo de paquetes y unidades individuales).
 */
data class DiaperSizeBreakdown(
    val sizeLabel: String,
    val weightRange: String,
    val packagesCount: Int,
    val unitsCount: Int,
    val recommendedMaxUnits: Int,
    val status: StockStatus,
    val pediatricNote: String
)

/**
 * Resumen generalizado de pañales y toallitas para consolidar en "¿Qué Tenemos?".
 */
data class GeneralConsumablesSummary(
    val totalDiaperPackages: Int,
    val totalDiaperUnits: Int,
    val sizeRn: DiaperSizeBreakdown,
    val sizeP: DiaperSizeBreakdown,
    val sizeM: DiaperSizeBreakdown,
    val sizeG: DiaperSizeBreakdown,
    val totalWipesPackages: Int,
    val totalWipesUnits: Int
)

/**
 * Elemento interactivo del checklist para la maleta de la clínica / hospital.
 */
data class HospitalBagCheckItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val quantityGuideline: String,
    val bagTarget: String // "BABY", "MOM", "DAD"
)

/**
 * Sub-sección del bolsito para el día del parto.
 */
data class HospitalBagSection(
    val id: String,
    val title: String,
    val icon: String,
    val description: String,
    val items: List<HospitalBagCheckItem>
)

/**
 * Auditoría integral de consumibles esenciales para el bebé niño (adaptada a tallas RN, P, M, G de Venezuela).
 * Organizada en 3 secciones:
 * 1) Llegada del Bebé (Casa / Semanas Iniciales)
 * 2) El Bolsito para el Día del Parto (Maleta de la Clínica)
 * 3) Para Después del Nacimiento (Etapas Posteriores & Reserva)
 */
data class ConsumablesAuditSummary(
    val diapersRn: ConsumableCategoryAudit,       // Talla RN (hasta ~4.5 kg)
    val diapersSizeP: ConsumableCategoryAudit,    // Talla P / Pequeño (4 a 6 kg / Etapa 1)
    val diapersSizeM: ConsumableCategoryAudit,    // Talla M / Mediano (6 a 9.5 kg / Etapa 2)
    val diapersSizeG: ConsumableCategoryAudit,    // Talla G / Grande (9 a 12.5 kg / Etapa 3)
    val wipes: ConsumableCategoryAudit,           // Toallitas húmedas (paquetes y toallitas)
    val babySoaps: ConsumableCategoryAudit,       // Champús y jabones neutros
    val clothingRn: ConsumableCategoryAudit,      // Ropita RN
    val clothing0to3M: ConsumableCategoryAudit,   // Ropita 0-3 Meses
    val giftedAudit: ConsumableCategoryAudit? = null, // Pañales regalados (Baby Shower)
    val geminiSmartAdvice: String = "",
    val generalSummary: GeneralConsumablesSummary? = null,
    val hospitalBagSections: List<HospitalBagSection> = emptyList()
) {
    // Compatibilidad para código o pruebas previas
    val diapersSize1: ConsumableCategoryAudit get() = diapersSizeP
}
