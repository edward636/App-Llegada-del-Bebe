package com.example.llegadadelbebe.domain.usecase

import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.core.util.PregnancyCalculator
import com.example.llegadadelbebe.core.util.PregnancyStats
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.BcvExchangeRate
import com.example.llegadadelbebe.domain.model.MedicationAdherenceSummary
import com.example.llegadadelbebe.domain.model.PrenatalMedication
import com.example.llegadadelbebe.domain.model.ProductPriceQuote
import java.time.LocalDate

data class OmniDashboardSnapshot(
    val pregnancyStats: PregnancyStats,
    val readinessScorePercent: Int,       // 0 a 100
    val inventoryScorePercent: Int,       // 0 a 100
    val hospitalBagScorePercent: Int,     // 0 a 100
    val medicationScorePercent: Int,      // 0 a 100
    val clinicalVisitsScorePercent: Int,  // 0 a 100
    val criticalDeficits: List<String>,
    val bcvRate: Double,
    val totalPendingBudgetUsd: Double,
    val totalPendingBudgetVes: Double,
    val medicationsAdherence: MedicationAdherenceSummary,
    val hospitalBagCheckedCount: Int,
    val hospitalBagTotalCount: Int
)

class OmniPregnancyContextUseCase(
    private val evaluateConsumablesUseCase: EvaluateConsumablesUseCase = EvaluateConsumablesUseCase()
) {

    fun computeSnapshot(
        lmpDate: LocalDate,
        products: List<BabyProduct>,
        medications: List<PrenatalMedication>,
        quotes: List<ProductPriceQuote>,
        checkedHospitalBagIds: Set<String>,
        bcvRate: Double
    ): OmniDashboardSnapshot {
        val pregnancyStats = PregnancyCalculator.calculateFromLmp(lmpDate)
        val consumablesAudit = evaluateConsumablesUseCase.execute(products)

        val allCategories = listOfNotNull(
            consumablesAudit.diapersRn,
            consumablesAudit.diapersSizeP,
            consumablesAudit.diapersSizeM,
            consumablesAudit.diapersSizeG,
            consumablesAudit.wipes,
            consumablesAudit.babySoaps,
            consumablesAudit.clothingRn,
            consumablesAudit.clothing0to3M,
            consumablesAudit.giftedAudit
        )

        // 1. Puntaje de Inventario (35% peso)
        val totalRecommendedUnits = allCategories.sumOf { it.recommendedUnits }
        val totalCurrentUnits = allCategories.sumOf { it.currentUnits.coerceAtMost(it.recommendedUnits) }
        val inventoryScore = if (totalRecommendedUnits > 0) {
            ((totalCurrentUnits.toDouble() / totalRecommendedUnits) * 100).toInt().coerceIn(0, 100)
        } else 50

        // 2. Puntaje de Maleta Clínica (25% peso) - 25 ítems estándar
        val totalBagItems = 25
        val checkedBagItems = checkedHospitalBagIds.size.coerceAtMost(totalBagItems)
        val hospitalBagScore = ((checkedBagItems.toDouble() / totalBagItems) * 100).toInt().coerceIn(0, 100)

        // 3. Puntaje de Vitaminas Prenatales (25% peso)
        val medicationScore = if (medications.isNotEmpty()) {
            val takenCount = medications.count { it.isTakenToday }
            val inStockCount = medications.count { !it.isOutOfStock }
            val dailyRate = (takenCount.toDouble() / medications.size) * 60
            val stockRate = (inStockCount.toDouble() / medications.size) * 40
            (dailyRate + stockRate).toInt().coerceIn(0, 100)
        } else 70

        // 4. Puntaje de Semanas Gestacionales / Control Clínico (15% peso)
        val clinicalScore = (pregnancyStats.progressPercent * 100).toInt().coerceIn(10, 100)

        // Cálculo Ponderado Total (0 - 100)
        val overallScore = (
            (inventoryScore * 0.35) +
            (hospitalBagScore * 0.25) +
            (medicationScore * 0.25) +
            (clinicalScore * 0.15)
        ).toInt().coerceIn(0, 100)

        // Déficits clínicos críticos
        val criticalDeficits = mutableListOf<String>()
        allCategories.filter { it.status == com.example.llegadadelbebe.domain.model.StockStatus.DEFICIT }.forEach {
            criticalDeficits.add("${it.categoryName}: ${it.currentUnits}/${it.recommendedUnits} unidades")
        }
        if (checkedBagItems < 10 && pregnancyStats.currentWeeks >= 32) {
            criticalDeficits.add("Maleta clínica incompleta ($checkedBagItems/$totalBagItems empacados)")
        }
        val outOfStockMeds = medications.filter { it.isOutOfStock }
        if (outOfStockMeds.isNotEmpty()) {
            criticalDeficits.add("Vitaminas agotadas: ${outOfStockMeds.joinToString { it.name }}")
        }

        // Presupuesto pendiente
        val pendingProducts = products.filter { it.status == com.example.llegadadelbebe.domain.model.ProductStatus.PENDING }
        val pendingUsd = pendingProducts.sumOf { prod ->
            val ref = prod.referencePriceUsd
            if (ref != null && ref > 0.0) ref * prod.packageCount else 0.0
        }
        val pendingVes = pendingUsd * (if (bcvRate > 0) bcvRate else 1.0)

        val totalMeds = medications.size
        val takenToday = medications.count { it.isTakenToday }
        val medSummary = MedicationAdherenceSummary(
            totalMedications = totalMeds,
            takenTodayCount = takenToday,
            pendingTodayCount = (totalMeds - takenToday).coerceAtLeast(0),
            lowStockCount = medications.count { it.isLowStock || it.isOutOfStock }
        )

        return OmniDashboardSnapshot(
            pregnancyStats = pregnancyStats,
            readinessScorePercent = overallScore,
            inventoryScorePercent = inventoryScore,
            hospitalBagScorePercent = hospitalBagScore,
            medicationScorePercent = medicationScore,
            clinicalVisitsScorePercent = clinicalScore,
            criticalDeficits = criticalDeficits,
            bcvRate = bcvRate,
            totalPendingBudgetUsd = pendingUsd,
            totalPendingBudgetVes = pendingVes,
            medicationsAdherence = medSummary,
            hospitalBagCheckedCount = checkedBagItems,
            hospitalBagTotalCount = totalBagItems
        )
    }

    fun buildAiPrompt(snapshot: OmniDashboardSnapshot, babyName: String): String {
        return """
            Actúa como un Obstetra y Consultor de Cuidado Perinatal de cabecera para los padres Edward y Britney.
            Bebé en camino: $babyName.
            
            ESTADO INTEGRAL 360° DE LA FAMILIA:
            - Semana Gestacional: ${snapshot.pregnancyStats.currentWeeks} semanas + ${snapshot.pregnancyStats.currentDays} días.
            - Faltan: ${snapshot.pregnancyStats.daysRemaining} días para la Fecha Probable de Parto (${snapshot.pregnancyStats.estimatedDueDate}).
            - Fruta/Animal de referencia: ${snapshot.pregnancyStats.growthMilestone.fruitName} (${snapshot.pregnancyStats.growthMilestone.lengthCm} cm, ${snapshot.pregnancyStats.growthMilestone.weightGrams} g) / ${snapshot.pregnancyStats.growthMilestone.animalName}.
            - Índice de Preparación General: ${snapshot.readinessScorePercent}% sobre 100%.
              * Preparación de Inventario: ${snapshot.inventoryScorePercent}%
              * Maleta Clínica: ${snapshot.hospitalBagCheckedCount}/${snapshot.hospitalBagTotalCount} ítems listos (${snapshot.hospitalBagScorePercent}%)
              * Cumplimiento de Vitaminas de Mamá: ${snapshot.medicationsAdherence.takenTodayCount}/${snapshot.medicationsAdherence.totalMedications} tomadas hoy.
            - Déficits de Stock / Compras detectados:
              ${if (snapshot.criticalDeficits.isEmpty()) "• ¡Todo el inventario crítico y maleta están cubiertos!" else snapshot.criticalDeficits.joinToString("\n              ") { "• $it" }}
            - Presupuesto Pendiente Estimado: $${String.format(java.util.Locale.US, "%.2f", snapshot.totalPendingBudgetUsd)} USD (Tasa BCV: ${snapshot.bcvRate} Bs/USD).

            INSTRUCCIONES DE RESPUESTA:
            Genera un informe ejecutivo cálido, motivador y sumamente estructurado con:
            1. 🩺 **Diagnóstico Clínico del Momento:** Qué está viviendo el bebé esta semana y cómo cuidar a Britney hoy.
            2. 🎯 **Las 3 Prioridades Inmediatas:** Qué debe comprar o preparar Edward primero sin gastar de más.
            3. 🎒 **Consejo de Maleta y Clínica:** Alerta si la maleta debe estar ya en el carro según las semanas actuales.
            4. 💌 **Mensaje de Aliento Familiar:** Para Edward y Britney.
            (Sé conciso, empático, directo y utiliza viñetas legibles).
        """.trimIndent()
    }
}
