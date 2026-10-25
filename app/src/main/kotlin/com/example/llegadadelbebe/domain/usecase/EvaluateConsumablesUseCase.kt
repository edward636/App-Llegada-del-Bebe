package com.example.llegadadelbebe.domain.usecase

import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ConsumableCategoryAudit
import com.example.llegadadelbebe.domain.model.ConsumablesAuditSummary
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.StockStatus

/**
 * Caso de uso para auditar consumibles neonatales basados en guías pediátricas:
 * - 8 a 10 pañales/día para recién nacidos (~240/mes).
 * - Talla RN se usa solo 2-3 semanas (máx 160 unidades sugeridas).
 * - Talla 1 requerirá entre 200 y 240 unidades para el primer mes.
 * - Toallitas húmedas: 2 a 3 paquetes de 72-80 unidades por mes.
 * - Jabón líquido neutro de bebé: 1 a 2 frascos de 250-400ml.
 * - Bodies manga corta/larga: 6-8 en RN, 10-12 en 0-3M.
 */
class EvaluateConsumablesUseCase {

    fun execute(products: List<BabyProduct>, geminiCustomAdvice: String = ""): ConsumablesAuditSummary {
        // 1. Pañales RN
        val diapersRnCount = products
            .filter { it.category == ProductCategory.DIAPERS && it.stageSize.contains("RN", ignoreCase = true) }
            .sumOf { it.quantityCurrent }

        val diapersRnAudit = when {
            diapersRnCount > 180 -> ConsumableCategoryAudit(
                categoryName = "Pañales Talla RN",
                detailLabel = "$diapersRnCount pañales registrados",
                currentUnits = diapersRnCount,
                recommendedUnits = 140,
                status = StockStatus.EXCESS_WARNING,
                recommendationMessage = "¡Tienen más de 180 pañales RN! Se sugiere no comprar más; el bebé crece rápido y podrían perderse."
            )
            diapersRnCount in 100..180 -> ConsumableCategoryAudit(
                categoryName = "Pañales Talla RN",
                detailLabel = "$diapersRnCount pañales (~2 a 3 semanas)",
                currentUnits = diapersRnCount,
                recommendedUnits = 140,
                status = StockStatus.OPTIMAL,
                recommendationMessage = "Excelente reserva de talla RN para las primeras semanas del bebé."
            )
            else -> ConsumableCategoryAudit(
                categoryName = "Pañales Talla RN",
                detailLabel = "$diapersRnCount pañales registrados",
                currentUnits = diapersRnCount,
                recommendedUnits = 120,
                status = StockStatus.DEFICIT,
                recommendationMessage = "Se sugiere adquirir al menos 1 o 2 paquetes más de Talla RN (meta: ~120-140 uds)."
            )
        }

        // 2. Pañales Talla 1
        val diapers1Count = products
            .filter { it.category == ProductCategory.DIAPERS && (it.stageSize.contains("1") || it.stageSize.contains("P")) }
            .sumOf { it.quantityCurrent }

        val diapers1Audit = if (diapers1Count >= 180) {
            ConsumableCategoryAudit(
                categoryName = "Pañales Talla 1",
                detailLabel = "$diapers1Count pañales (~1 mes cubierto)",
                currentUnits = diapers1Count,
                recommendedUnits = 200,
                status = StockStatus.COVERED,
                recommendationMessage = "Buena cobertura para cuando el niño supere los 4 kg."
            )
        } else {
            ConsumableCategoryAudit(
                categoryName = "Pañales Talla 1",
                detailLabel = "$diapers1Count pañales",
                currentUnits = diapers1Count,
                recommendedUnits = 200,
                status = StockStatus.DEFICIT,
                recommendationMessage = "Es momento de comenzar a aprovechar ofertas en Talla 1 (meta: ~180-200 uds)."
            )
        }

        // 3. Toallitas húmedas
        val wipesCount = products
            .filter { it.category == ProductCategory.WIPES_HYGIENE && it.name.contains("toall", ignoreCase = true) }
            .sumOf { it.quantityCurrent }

        val wipesAudit = if (wipesCount >= 3) {
            ConsumableCategoryAudit(
                categoryName = "Toallitas Húmedas",
                detailLabel = "$wipesCount paquetes en casa",
                currentUnits = wipesCount,
                recommendedUnits = 3,
                status = StockStatus.OPTIMAL,
                recommendationMessage = "Cubierto para el primer mes. Asegurarse de que sean hipoalergénicas y sin perfume."
            )
        } else {
            ConsumableCategoryAudit(
                categoryName = "Toallitas Húmedas",
                detailLabel = "$wipesCount paquete(s)",
                currentUnits = wipesCount,
                recommendedUnits = 3,
                status = StockStatus.DEFICIT,
                recommendationMessage = "Faltan ${3 - wipesCount} paquete(s) para asegurar el primer mes."
            )
        }

        // 4. Jabones / Baño
        val soapsCount = products
            .filter { it.category == ProductCategory.WIPES_HYGIENE && (it.name.contains("jab", ignoreCase = true) || it.name.contains("shamp", ignoreCase = true)) }
            .sumOf { it.quantityCurrent }

        val soapsAudit = if (soapsCount >= 1) {
            ConsumableCategoryAudit(
                categoryName = "Jabón / Shampoo Neutro",
                detailLabel = "$soapsCount frasco(s)",
                currentUnits = soapsCount,
                recommendedUnits = 1,
                status = StockStatus.OPTIMAL,
                recommendationMessage = "1 frasco de 400ml dura entre 3 y 4 meses. ¡Están cubiertos!"
            )
        } else {
            ConsumableCategoryAudit(
                categoryName = "Jabón / Shampoo Neutro",
                detailLabel = "$soapsCount frasco(s)",
                currentUnits = soapsCount,
                recommendedUnits = 1,
                status = StockStatus.DEFICIT,
                recommendationMessage = "Falta adquirir 1 frasco de jabón líquido hipoalergénico de cabeza a pies."
            )
        }

        // 5. Ropita RN
        val clothesRn = products
            .filter { it.category == ProductCategory.CLOTHING && it.stageSize.contains("RN", ignoreCase = true) }
            .sumOf { it.quantityCurrent }

        val clothingRnAudit = ConsumableCategoryAudit(
            categoryName = "Ropita Talla RN",
            detailLabel = "$clothesRn prendas",
            currentUnits = clothesRn,
            recommendedUnits = 8,
            status = if (clothesRn >= 6) StockStatus.OPTIMAL else StockStatus.DEFICIT,
            recommendationMessage = if (clothesRn >= 6) "Suficiente para la primera semana y la clínica." else "Recomendado tener 6 a 8 mudas en RN."
        )

        // 6. Ropita 0-3M
        val clothes0to3 = products
            .filter { it.category == ProductCategory.CLOTHING && (it.stageSize.contains("0-3", ignoreCase = true) || it.stageSize.contains("3M", ignoreCase = true)) }
            .sumOf { it.quantityCurrent }

        val clothing0to3Audit = ConsumableCategoryAudit(
            categoryName = "Ropita Talla 0-3 Meses",
            detailLabel = "$clothes0to3 prendas",
            currentUnits = clothes0to3,
            recommendedUnits = 12,
            status = if (clothes0to3 >= 10) StockStatus.OPTIMAL else StockStatus.DEFICIT,
            recommendationMessage = if (clothes0to3 >= 10) "Excelente guardarropa para el primer trimestre del niño." else "Recomendado acumular entre 10 y 12 bodies/pijamas."
        )

        return ConsumablesAuditSummary(
            diapersRn = diapersRnAudit,
            diapersSize1 = diapers1Audit,
            wipes = wipesAudit,
            babySoaps = soapsAudit,
            clothingRn = clothingRnAudit,
            clothing0to3M = clothing0to3Audit,
            geminiSmartAdvice = geminiCustomAdvice
        )
    }
}
