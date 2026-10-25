package com.example.llegadadelbebe.domain.usecase

import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ConsumableCategoryAudit
import com.example.llegadadelbebe.domain.model.ConsumablesAuditSummary
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductStatus
import com.example.llegadadelbebe.domain.model.StockStatus

/**
 * Caso de uso para auditar consumibles neonatales basados en guías pediátricas y tallas de Venezuela:
 * - Tallas de pañales: RN (hasta 4.5kg), P (4 a 6kg), M (6 a 9.5kg), G (9 a 12.5kg).
 * - Conteo estricto por paquetes y unidades totales (ej: 1 paquete de 24 pañales = 24 unidades).
 * - Talla RN: se usa solo 2-3 semanas (máx 160-180 unidades recomendadas).
 * - Talla P: requerirá entre 200 y 240 unidades para el primer mes y medio.
 * - Talla M: 2do y 3er mes (~240 unidades).
 * - Talla G: Para cuando supere los 9 kg (excelente si se recibe en baby shower o regalos).
 * - Toallitas húmedas: Conteo de paquetes y toallitas totales (meta ~3 paquetes / 240 toallitas).
 * - Higiene y Salud: Jabón neutro, champú sin lágrimas, termómetro digital, kit cortauñas.
 */
class EvaluateConsumablesUseCase {

    fun execute(products: List<BabyProduct>, geminiCustomAdvice: String = ""): ConsumablesAuditSummary {
        // Filtro de pañales
        val diaperProducts = products.filter { it.category == ProductCategory.DIAPERS }

        // 1. Pañales Talla RN
        val diapersRnItems = diaperProducts.filter { it.stageSize.contains("RN", ignoreCase = true) }
        val diapersRnCount = diapersRnItems.sumOf { it.effectiveTotalUnits }
        val diapersRnPacks = diapersRnItems.sumOf { it.packageCount.coerceAtLeast(1) }

        val diapersRnAudit = when {
            diapersRnCount > 180 -> ConsumableCategoryAudit(
                categoryName = "Pañales Talla RN (Recién Nacido)",
                detailLabel = "$diapersRnCount pañales en $diapersRnPacks paquete(s)",
                currentUnits = diapersRnCount,
                recommendedUnits = 140,
                status = StockStatus.EXCESS_WARNING,
                recommendationMessage = "¡Tienen $diapersRnCount pañales RN! Se sugiere no comprar más; el bebé crece rápido y podrían perderse."
            )
            diapersRnCount in 100..180 -> ConsumableCategoryAudit(
                categoryName = "Pañales Talla RN (Recién Nacido)",
                detailLabel = "$diapersRnCount pañales en $diapersRnPacks paquete(s) (~2-3 semanas)",
                currentUnits = diapersRnCount,
                recommendedUnits = 140,
                status = StockStatus.OPTIMAL,
                recommendationMessage = "Excelente reserva de talla RN para las primeras semanas del bebé y la clínica."
            )
            else -> ConsumableCategoryAudit(
                categoryName = "Pañales Talla RN (Recién Nacido)",
                detailLabel = "$diapersRnCount pañales registrados ($diapersRnPacks paquete(s))",
                currentUnits = diapersRnCount,
                recommendedUnits = 120,
                status = StockStatus.DEFICIT,
                recommendationMessage = "Se sugiere adquirir al menos 1 o 2 paquetes más de Talla RN (meta: ~120-140 uds)."
            )
        }

        // 2. Pañales Talla P (Pequeño / Etapa 1)
        val diapersPItems = diaperProducts.filter {
            it.stageSize.contains("P", ignoreCase = true) || it.stageSize.contains("1") || it.stageSize.contains("pequeñ", ignoreCase = true)
        }.filterNot { it.stageSize.contains("RN", ignoreCase = true) }
        val diapersPCount = diapersPItems.sumOf { it.effectiveTotalUnits }
        val diapersPPacks = diapersPItems.sumOf { it.packageCount.coerceAtLeast(1) }

        val diapersPAudit = if (diapersPCount >= 180) {
            ConsumableCategoryAudit(
                categoryName = "Pañales Talla P (Pequeño / 4-6 kg)",
                detailLabel = "$diapersPCount pañales en $diapersPPacks paquete(s)",
                currentUnits = diapersPCount,
                recommendedUnits = 200,
                status = StockStatus.COVERED,
                recommendationMessage = "Buena cobertura para cuando el niño supere los 4 kg (mes 1 al 2)."
            )
        } else {
            ConsumableCategoryAudit(
                categoryName = "Pañales Talla P (Pequeño / 4-6 kg)",
                detailLabel = "$diapersPCount pañales en $diapersPPacks paquete(s)",
                currentUnits = diapersPCount,
                recommendedUnits = 200,
                status = StockStatus.DEFICIT,
                recommendationMessage = "Es momento de aprovechar ofertas en Talla P (meta: ~180-200 pañales)."
            )
        }

        // 3. Pañales Talla M (Mediano / 6 a 9.5 kg)
        val diapersMItems = diaperProducts.filter {
            it.stageSize.contains("M", ignoreCase = true) || it.stageSize.contains("2") || it.stageSize.contains("median", ignoreCase = true)
        }.filterNot { it.stageSize.contains("0-3", ignoreCase = true) || it.stageSize.contains("3-6", ignoreCase = true) }
        val diapersMCount = diapersMItems.sumOf { it.effectiveTotalUnits }
        val diapersMPacks = diapersMItems.sumOf { it.packageCount.coerceAtLeast(1) }

        val diapersMAudit = if (diapersMCount >= 160) {
            ConsumableCategoryAudit(
                categoryName = "Pañales Talla M (Mediano / 6-9.5 kg)",
                detailLabel = "$diapersMCount pañales en $diapersMPacks paquete(s)",
                currentUnits = diapersMCount,
                recommendedUnits = 180,
                status = StockStatus.COVERED,
                recommendationMessage = "Excelente avance para el 2do y 3er mes del bebé."
            )
        } else {
            ConsumableCategoryAudit(
                categoryName = "Pañales Talla M (Mediano / 6-9.5 kg)",
                detailLabel = "$diapersMCount pañales en $diapersMPacks paquete(s)",
                currentUnits = diapersMCount,
                recommendedUnits = 180,
                status = if (diapersMCount > 0) StockStatus.COVERED else StockStatus.DEFICIT,
                recommendationMessage = if (diapersMCount > 0) "Tienen $diapersMCount pañales de reserva para cuando crezca." else "Pueden ir adquiriendo paquetes de Talla M si ven promociones."
            )
        }

        // 4. Pañales Talla G (Grande / 9 a 12.5 kg)
        val diapersGItems = diaperProducts.filter {
            it.stageSize.contains("G", ignoreCase = true) || it.stageSize.contains("3") || it.stageSize.contains("grand", ignoreCase = true)
        }.filterNot { it.stageSize.contains("XG", ignoreCase = true) }
        val diapersGCount = diapersGItems.sumOf { it.effectiveTotalUnits }
        val diapersGPacks = diapersGItems.sumOf { it.packageCount.coerceAtLeast(1) }

        val diapersGAudit = ConsumableCategoryAudit(
            categoryName = "Pañales Talla G (Grande / 9-12.5 kg)",
            detailLabel = "$diapersGCount pañales en $diapersGPacks paquete(s)",
            currentUnits = diapersGCount,
            recommendedUnits = 120,
            status = if (diapersGCount > 0) StockStatus.OPTIMAL else StockStatus.COVERED,
            recommendationMessage = if (diapersGCount > 0) "¡Genial! Ya tienen reserva de Talla G (comprados o regalados) para cuando supere los 9 kg." else "Talla G es para más adelante (a partir del 5to-6to mes). ¡Ideal para pedir en baby shower!"
        )

        // 5. Toallitas húmedas
        val wipesItems = products.filter {
            it.category == ProductCategory.WIPES_HYGIENE &&
            (it.name.contains("toall", ignoreCase = true) || it.name.contains("wipes", ignoreCase = true))
        }
        val wipesPacks = wipesItems.sumOf { it.packageCount.coerceAtLeast(1) }
        val wipesTotalUnits = wipesItems.sumOf { it.effectiveTotalUnits }

        val wipesAudit = if (wipesPacks >= 3 || wipesTotalUnits >= 200) {
            ConsumableCategoryAudit(
                categoryName = "Toallitas Húmedas",
                detailLabel = "$wipesPacks paquetes ($wipesTotalUnits toallitas en total)",
                currentUnits = wipesPacks,
                recommendedUnits = 3,
                status = StockStatus.OPTIMAL,
                recommendationMessage = "Cubierto para el primer mes. Asegurarse de que sean hipoalergénicas y sin perfume."
            )
        } else {
            ConsumableCategoryAudit(
                categoryName = "Toallitas Húmedas",
                detailLabel = "$wipesPacks paquete(s) ($wipesTotalUnits toallitas)",
                currentUnits = wipesPacks,
                recommendedUnits = 3,
                status = StockStatus.DEFICIT,
                recommendationMessage = "Faltan ${3 - wipesPacks} paquete(s) para asegurar el primer mes (~200 toallitas)."
            )
        }

        // 6. Champús / Jabones / Baño
        val soapsItems = products.filter {
            it.category == ProductCategory.WIPES_HYGIENE &&
            (it.name.contains("jab", ignoreCase = true) || it.name.contains("shamp", ignoreCase = true) || it.name.contains("chamb", ignoreCase = true) || it.name.contains("baño", ignoreCase = true))
        }
        val soapsCount = soapsItems.sumOf { it.effectiveTotalUnits }

        val soapsAudit = if (soapsCount >= 1) {
            ConsumableCategoryAudit(
                categoryName = "Champú / Jabón Líquido Neutro",
                detailLabel = "$soapsCount frasco(s)",
                currentUnits = soapsCount,
                recommendedUnits = 1,
                status = StockStatus.OPTIMAL,
                recommendationMessage = "1 frasco de 400ml dura entre 3 y 4 meses. ¡Están cubiertos!"
            )
        } else {
            ConsumableCategoryAudit(
                categoryName = "Champú / Jabón Líquido Neutro",
                detailLabel = "$soapsCount frasco(s)",
                currentUnits = soapsCount,
                recommendedUnits = 1,
                status = StockStatus.DEFICIT,
                recommendationMessage = "Falta adquirir 1 frasco de champú/jabón líquido hipoalergénico sin lágrimas."
            )
        }

        // 7. Ropita RN
        val clothesRn = products
            .filter { it.category == ProductCategory.CLOTHING && it.stageSize.contains("RN", ignoreCase = true) }
            .sumOf { it.effectiveTotalUnits }

        val clothingRnAudit = ConsumableCategoryAudit(
            categoryName = "Ropita Talla RN (0-1 Mes)",
            detailLabel = "$clothesRn prendas registradas",
            currentUnits = clothesRn,
            recommendedUnits = 8,
            status = if (clothesRn >= 6) StockStatus.OPTIMAL else StockStatus.DEFICIT,
            recommendationMessage = if (clothesRn >= 6) "Suficiente para la clínica y las primeras 2 semanas." else "Recomendado tener 6 a 8 mudas en RN."
        )

        // 8. Ropita 0-3M
        val clothes0to3 = products
            .filter { it.category == ProductCategory.CLOTHING && (it.stageSize.contains("0-3", ignoreCase = true) || it.stageSize.contains("3M", ignoreCase = true)) }
            .sumOf { it.effectiveTotalUnits }

        val clothing0to3Audit = ConsumableCategoryAudit(
            categoryName = "Ropita Talla 0-3 Meses",
            detailLabel = "$clothes0to3 prendas registradas",
            currentUnits = clothes0to3,
            recommendedUnits = 12,
            status = if (clothes0to3 >= 10) StockStatus.OPTIMAL else StockStatus.DEFICIT,
            recommendationMessage = if (clothes0to3 >= 10) "Excelente guardarropa para el primer trimestre del niño." else "Recomendado acumular entre 10 y 12 bodies/pijamas."
        )

        // 9. Artículos Regalados / Baby Shower
        val giftedProducts = products.filter { it.status == ProductStatus.GIFTED }
        val giftedCount = giftedProducts.sumOf { it.effectiveTotalUnits }
        val giftedAudit = if (giftedProducts.isNotEmpty()) {
            ConsumableCategoryAudit(
                categoryName = "Regalos Recibidos / Baby Shower 🎁",
                detailLabel = "$giftedCount artículos obsequiados con amor",
                currentUnits = giftedCount,
                recommendedUnits = 1,
                status = StockStatus.OPTIMAL,
                recommendationMessage = "Tienen ${giftedProducts.size} tipos de artículos regalados por familiares y amigos."
            )
        } else null

        return ConsumablesAuditSummary(
            diapersRn = diapersRnAudit,
            diapersSizeP = diapersPAudit,
            diapersSizeM = diapersMAudit,
            diapersSizeG = diapersGAudit,
            wipes = wipesAudit,
            babySoaps = soapsAudit,
            clothingRn = clothingRnAudit,
            clothing0to3M = clothing0to3Audit,
            giftedAudit = giftedAudit,
            geminiSmartAdvice = geminiCustomAdvice
        )
    }
}
