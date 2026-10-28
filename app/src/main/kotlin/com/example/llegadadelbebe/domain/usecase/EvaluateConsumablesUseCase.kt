package com.example.llegadadelbebe.domain.usecase

import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ConsumableCategoryAudit
import com.example.llegadadelbebe.domain.model.ConsumablesAuditSummary
import com.example.llegadadelbebe.domain.model.DiaperSizeBreakdown
import com.example.llegadadelbebe.domain.model.GeneralConsumablesSummary
import com.example.llegadadelbebe.domain.model.HospitalBagCheckItem
import com.example.llegadadelbebe.domain.model.HospitalBagSection
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductStatus
import com.example.llegadadelbebe.domain.model.StockStatus

/**
 * Caso de uso para auditar consumibles neonatales basados en guías pediátricas y tallas de Venezuela:
 * - Tallas de pañales: RN (hasta 4.5kg), P (4 a 6kg), M (6 a 9.5kg), G (9 a 12.5kg).
 * - Conteo estricto por paquetes y unidades totales (ej: 1 paquete de 24 pañales = 24 unidades).
 * - Talla RN: se usa solo 2-3 semanas (máx 160-180 unidades recomendadas para evitar compras en exceso).
 * - Talla P: requerirá entre 200 y 240 unidades para el primer mes y medio.
 * - Talla M: 2do y 3er mes (~200 a 300 unidades).
 * - Talla G: Para cuando supere los 9 kg (excelente si se recibe en baby shower o regalos).
 * - Toallitas húmedas: Conteo de paquetes y toallitas totales (meta ~3-4 paquetes / 240 toallitas).
 * - Organizado en 3 secciones:
 *   1) Llegada del Bebé (Casa / Semanas Iniciales)
 *   2) El Bolsito para el Día del Parto (Maleta Clínica: Bebé, Mamá Britney, Papá Edward)
 *   3) Para Después del Nacimiento (Etapas Posteriores & Reserva)
 */
class EvaluateConsumablesUseCase {

    fun execute(products: List<BabyProduct>, geminiCustomAdvice: String = ""): ConsumablesAuditSummary {
        // Filtro de pañales que ya poseemos (excluyendo pendientes por comprar)
        val diaperProducts = products.filter { it.category == ProductCategory.DIAPERS && it.status != ProductStatus.PENDING }

        // 1. Pañales Talla RN (Recién Nacido / hasta 4.5 kg)
        val diapersRnItems = diaperProducts.filter {
            it.stageSize.contains("RN", ignoreCase = true) ||
            it.name.contains("RN", ignoreCase = true) ||
            it.name.contains("Recién Nacido", ignoreCase = true)
        }
        val diapersRnCount = diapersRnItems.sumOf { it.effectiveTotalUnits }
        val diapersRnPacks = diapersRnItems.sumOf { it.packageCount.coerceAtLeast(1) }

        val diapersRnAudit = when {
            diapersRnCount > 180 -> ConsumableCategoryAudit(
                categoryName = "Pañales Talla RN (Recién Nacido)",
                detailLabel = "$diapersRnCount pañales en $diapersRnPacks paquete(s)",
                currentUnits = diapersRnCount,
                recommendedUnits = 140,
                status = StockStatus.EXCESS_WARNING,
                recommendationMessage = "¡Tienen $diapersRnCount pañales RN! Se sugiere no comprar más; el bebé crece rápido (~25g/día) y podrían perderse."
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
                recommendationMessage = "Se sugiere adquirir al menos 1 o 2 paquetes más de Talla RN (meta sugerida: ~120-140 uds)."
            )
        }

        // 2. Pañales Talla P (Pequeño / 4 a 6 kg / Etapa 1)
        val diapersPItems = diaperProducts.filter {
            (it.stageSize.contains("P", ignoreCase = true) ||
             it.stageSize.contains("1") ||
             it.stageSize.contains("pequeñ", ignoreCase = true) ||
             it.name.contains("Talla P", ignoreCase = true) ||
             it.name.contains("Etapa 1", ignoreCase = true)) &&
            !it.stageSize.contains("RN", ignoreCase = true) &&
            !it.name.contains("RN", ignoreCase = true) &&
            !it.name.contains("Recién Nacido", ignoreCase = true)
        }
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

        // 3. Pañales Talla M (Mediano / 6 a 9.5 kg / Etapa 2)
        val diapersMItems = diaperProducts.filter {
            (it.stageSize.contains("M", ignoreCase = true) ||
             it.stageSize.contains("2") ||
             it.stageSize.contains("median", ignoreCase = true) ||
             it.name.contains("Talla M", ignoreCase = true) ||
             it.name.contains("Etapa 2", ignoreCase = true)) &&
            !it.stageSize.contains("0-3", ignoreCase = true) &&
            !it.stageSize.contains("3-6", ignoreCase = true) &&
            !it.stageSize.contains("RN", ignoreCase = true) &&
            !it.name.contains("RN", ignoreCase = true)
        }
        val diapersMCount = diapersMItems.sumOf { it.effectiveTotalUnits }
        val diapersMPacks = diapersMItems.sumOf { it.packageCount.coerceAtLeast(1) }

        val diapersMAudit = if (diapersMCount >= 160) {
            ConsumableCategoryAudit(
                categoryName = "Pañales Talla M (Mediano / 6-9.5 kg)",
                detailLabel = "$diapersMCount pañales en $diapersMPacks paquete(s)",
                currentUnits = diapersMCount,
                recommendedUnits = 180,
                status = StockStatus.COVERED,
                recommendationMessage = "Excelente reserva de Talla M. Es la que más tiempo durará en el primer semestre."
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

        // 4. Pañales Talla G (Grande / 9 a 12.5 kg / Etapa 3)
        val diapersGItems = diaperProducts.filter {
            (it.stageSize.contains("G", ignoreCase = true) ||
             it.stageSize.contains("3") ||
             it.stageSize.contains("grand", ignoreCase = true) ||
             it.name.contains("Talla G", ignoreCase = true) ||
             it.name.contains("Etapa 3", ignoreCase = true)) &&
            !it.stageSize.contains("XG", ignoreCase = true)
        }
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
            it.status != ProductStatus.PENDING &&
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
            it.status != ProductStatus.PENDING &&
            (it.name.contains("jab", ignoreCase = true) || it.name.contains("shamp", ignoreCase = true) || it.name.contains("chamb", ignoreCase = true) || it.name.contains("baño", ignoreCase = true) || it.name.contains("soap", ignoreCase = true))
        }
        val soapsCount = soapsItems.sumOf { it.packageCount.coerceAtLeast(1) }

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
            .filter { it.category == ProductCategory.CLOTHING && it.status != ProductStatus.PENDING && it.stageSize.contains("RN", ignoreCase = true) }
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
            .filter { it.category == ProductCategory.CLOTHING && it.status != ProductStatus.PENDING && (it.stageSize.contains("0-3", ignoreCase = true) || it.stageSize.contains("3M", ignoreCase = true)) }
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

        // Desglose consolidado para "Generalizar todo en Qué Tenemos"
        val totalDiapersPacks = diapersRnPacks + diapersPPacks + diapersMPacks + diapersGPacks
        val totalDiapersUnits = diapersRnCount + diapersPCount + diapersMCount + diapersGCount

        val generalSummary = GeneralConsumablesSummary(
            totalDiaperPackages = totalDiapersPacks,
            totalDiaperUnits = totalDiapersUnits,
            sizeRn = DiaperSizeBreakdown(
                sizeLabel = "Talla RN (Recién Nacido)",
                weightRange = "Hasta 4.5 kg (~2-4 semanas)",
                packagesCount = diapersRnPacks,
                unitsCount = diapersRnCount,
                recommendedMaxUnits = 160,
                status = diapersRnAudit.status,
                pediatricNote = "Uso: 8-10 pañales/día. Consejo: No superar 160 uds; el bebé sube ~25g al día y pasarán rápido a talla P."
            ),
            sizeP = DiaperSizeBreakdown(
                sizeLabel = "Talla P (Etapa 1)",
                weightRange = "4 a 6 kg (~1.5 a 2 meses)",
                packagesCount = diapersPPacks,
                unitsCount = diapersPCount,
                recommendedMaxUnits = 240,
                status = diapersPAudit.status,
                pediatricNote = "Uso: 7-9 pañales/día. Requerimiento promedio para 6 semanas: 200 a 240 unidades."
            ),
            sizeM = DiaperSizeBreakdown(
                sizeLabel = "Talla M (Etapa 2)",
                weightRange = "6 a 9.5 kg (~3 a 6 meses)",
                packagesCount = diapersMPacks,
                unitsCount = diapersMCount,
                recommendedMaxUnits = 300,
                status = diapersMAudit.status,
                pediatricNote = "Es la talla más duradera del primer año (~6 pañales/día). Pueden acumular paquetes en ofertas."
            ),
            sizeG = DiaperSizeBreakdown(
                sizeLabel = "Talla G (Etapa 3)",
                weightRange = "9 a 12.5 kg (a partir del 6to mes)",
                packagesCount = diapersGPacks,
                unitsCount = diapersGCount,
                recommendedMaxUnits = 150,
                status = diapersGAudit.status,
                pediatricNote = "Reserva a largo plazo. Ideal para pedir en Baby Shower sin abrir empaques."
            ),
            totalWipesPackages = wipesPacks,
            totalWipesUnits = wipesTotalUnits
        )

        // Secciones completas para el Bolsito del Día del Parto (Maleta Clínica)
        val hospitalBagSections = listOf(
            HospitalBagSection(
                id = "baby",
                title = "Bolso del Bebé",
                icon = "👶",
                description = "Mudas, higiene inmediata y confort para el recién nacido.",
                items = listOf(
                    HospitalBagCheckItem("b_clothes", "4 Mudas Completas de Ropita", "Bodies cruzados, batitas de algodón, pantalón con pie, gorrito y mitones", "4 mudas completas", "BABY"),
                    HospitalBagCheckItem("b_leaving", "1 Muda Especial para la Salida", "Conjunto de algodón suave para el primer regreso a casa", "1 muda", "BABY"),
                    HospitalBagCheckItem("b_diapers", "Pañales Talla RN para la Clínica", "Paquete cerrado de Talla RN (o 12-14 pañales en bolsita hermética)", "1 pqte / 14 uds", "BABY"),
                    HospitalBagCheckItem("b_wipes", "Toallitas Húmedas para Recién Nacido", "Sin alcohol, sin perfume, con tapa dura para evitar que se sequen", "1 paquete", "BABY"),
                    HospitalBagCheckItem("b_blankets", "2 Mantas o Arrullos de Algodón", "Para envolver al bebé y mantener su temperatura tras nacer", "2 mantitas", "BABY"),
                    HospitalBagCheckItem("b_burp", "4 Pañitos de Tela / Babitas", "Algodón suave para limpiar gases o regurgitación", "4 babitas", "BABY"),
                    HospitalBagCheckItem("b_towel", "1 Toalla de Baño con Capucha", "100% algodón suave para su primer aseo", "1 toalla", "BABY"),
                    HospitalBagCheckItem("b_cream", "Pomada Protectora de Pañal", "Con óxido de zinc para prevenir irritaciones por meconio", "1 tubo", "BABY"),
                    HospitalBagCheckItem("b_cord", "Kit de Curación de Cordón Umbilical", "Alcohol al 70% y gasas estériles (o antiséptico pediátrico)", "1 kit", "BABY")
                )
            ),
            HospitalBagSection(
                id = "mom",
                title = "Bolso de Mamá (Britney)",
                icon = "🌸",
                description = "Comodidad, recuperación postparto y lactancia materna.",
                items = listOf(
                    HospitalBagCheckItem("m_gowns", "2 a 3 Batas o Camisones de Lactancia", "Abiertos al frente con botones para amamantar con comodidad", "2-3 camisones", "MOM"),
                    HospitalBagCheckItem("m_leaving", "1 Muda Cómoda y Holgada para el Alta", "Ropa de maternidad suave para salir del hospital", "1 muda", "MOM"),
                    HospitalBagCheckItem("m_bras", "2 Sostenes de Lactancia y Discos Absorbentes", "Sin aros metálicos que compriman el pecho + discos protectores", "2 sostenes + discos", "MOM"),
                    HospitalBagCheckItem("m_pads", "1 Paquete de Toallas Sanitarias Postparto", "De maternidad (máxima absorción para los loquios)", "1 paquete", "MOM"),
                    HospitalBagCheckItem("m_underwear", "4 Bragas de Algodón o Desechables Postparto", "Tiro alto que no presione la zona pélvica", "4-5 unidades", "MOM"),
                    HospitalBagCheckItem("m_slippers", "Pantuflas y Sandalias Antideslizantes", "Para caminar en la habitación y para entrar a la ducha", "2 pares", "MOM"),
                    HospitalBagCheckItem("m_toiletry", "Neceser de Aseo Personal Completo", "Cepillo, pasta dental, jabón neutro, champú, cepillo y bálsamo labial", "1 neceser", "MOM"),
                    HospitalBagCheckItem("m_pillow", "Almohada o Cojín de Lactancia", "Facilita la postura correcta para amamantar sin forzar la espalda", "1 unidad", "MOM")
                )
            ),
            HospitalBagSection(
                id = "dad",
                title = "Bolso de Papá (Edward) y Documentos",
                icon = "💙",
                description = "Documentación legal, logística, energía y apoyo.",
                items = listOf(
                    HospitalBagCheckItem("d_docs", "Carpeta con Documentos y Cédulas", "Cédulas originales de Edward y Britney, seguro, analíticas y ecografías", "Carpeta completa", "DAD"),
                    HospitalBagCheckItem("d_chargers", "Cargadores de Celular de Cable Largo y Powerbank", "Para mantener los teléfonos con batería para fotos y llamadas familiares", "2 cables + powerbank", "DAD"),
                    HospitalBagCheckItem("d_clothes", "Muda Cómoda y Chaqueta/Suéter Abrigado", "En la clínica el aire acondicionado suele ser muy frío durante la noche", "1 muda + suéter", "DAD"),
                    HospitalBagCheckItem("d_snacks", "Botellas de Agua y Snacks Ligeros", "Frutos secos, barras de cereal o galletas para reponer energías", "Agua + snacks", "DAD"),
                    HospitalBagCheckItem("d_contacts", "Lista de Contactos Prioritarios", "Familiares cercanos para avisar inmediatamente la llegada del bebé", "Lista lista", "DAD")
                )
            )
        )

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
            geminiSmartAdvice = geminiCustomAdvice,
            generalSummary = generalSummary,
            hospitalBagSections = hospitalBagSections
        )
    }
}
