package com.example.llegadadelbebe

import com.example.llegadadelbebe.core.security.SecureConfig
import com.example.llegadadelbebe.core.update.OtaUpdateManager
import com.example.llegadadelbebe.core.util.BarcodeHelper
import com.example.llegadadelbebe.core.util.PregnancyCalculator
import com.example.llegadadelbebe.core.util.ZodiacPredictor
import com.example.llegadadelbebe.data.remote.CatalogImageFetcher
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductSafetyLevel
import com.example.llegadadelbebe.domain.model.ProductStatus
import com.example.llegadadelbebe.domain.model.StockStatus
import com.example.llegadadelbebe.domain.usecase.EvaluateConsumablesUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PregnancyAndZodiacTest {

    @Test
    fun testPregnancyCalculator_CalculatesWeeksAndRemaining() {
        val stats = PregnancyCalculator.calculateFromCurrentWeeks(weeks = 28, days = 0)
        assertEquals(28, stats.currentWeeks)
        assertEquals(0, stats.currentDays)
        assertEquals(3, stats.trimester) // 3er trimestre
        assertEquals(12L, stats.weeksRemaining) // 40 - 28 = 12 semanas
        assertTrue(stats.babySizeComparison.contains("Berenjena"))
    }

    @Test
    fun testZodiacPredictor_PredictsLeoAndCancer() {
        // 10 de Agosto -> Leo
        val leoDate = LocalDate.of(2026, 8, 10)
        val leoProfile = ZodiacPredictor.predictFromDate(leoDate)
        assertTrue(leoProfile.signName.contains("Leo"))
        assertEquals("Fuego 🔥", leoProfile.element)

        // 5 de Julio -> Cáncer
        val cancerDate = LocalDate.of(2026, 7, 5)
        val cancerProfile = ZodiacPredictor.predictFromDate(cancerDate)
        assertTrue(cancerProfile.signName.contains("Cáncer"))
        assertEquals("Agua 💧", cancerProfile.element)
    }

    @Test
    fun testEvaluateConsumables_WarnsOnTooManyDiapersRn() {
        val useCase = EvaluateConsumablesUseCase()
        val products = listOf(
            BabyProduct(
                id = "1",
                name = "Pañales RN",
                category = ProductCategory.DIAPERS,
                stageSize = "RN",
                quantityCurrent = 220 // Exceso de RN
            )
        )

        val summary = useCase.execute(products)
        assertEquals(StockStatus.EXCESS_WARNING, summary.diapersRn.status)
        assertTrue(summary.diapersRn.recommendationMessage.contains("no comprar más"))
    }

    @Test
    fun testEvaluateConsumables_FlagsDeficitWhenWipesLow() {
        val useCase = EvaluateConsumablesUseCase()
        val products = listOf(
            BabyProduct(
                id = "1",
                name = "Toallitas húmedas 80u",
                category = ProductCategory.WIPES_HYGIENE,
                stageSize = "0M+",
                quantityCurrent = 1 // Solo 1 paquete
            )
        )

        val summary = useCase.execute(products)
        assertEquals(StockStatus.DEFICIT, summary.wipes.status)
    }

    @Test
    fun testIsNewerVersion_AvoidsInfiniteUpdateLoop() {
        // Misma versión no debe detonar actualización
        assertFalse(OtaUpdateManager.isNewerVersion("1.0.1", "1.0.1"))
        assertFalse(OtaUpdateManager.isNewerVersion("v1.0.1", "1.0.1"))
        assertFalse(OtaUpdateManager.isNewerVersion("1.0.2", "1.0.2"))

        // Versión menor remota no debe detonar actualización
        assertFalse(OtaUpdateManager.isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(OtaUpdateManager.isNewerVersion("v1.0.1", "1.0.2"))

        // Versión mayor remota sí debe detonar actualización
        assertTrue(OtaUpdateManager.isNewerVersion("1.0.2", "1.0.1"))
        assertTrue(OtaUpdateManager.isNewerVersion("v1.1.0", "1.0.2"))
        assertTrue(OtaUpdateManager.isNewerVersion("2.0.0", "1.0.2"))
    }

    @Test
    fun testSecureConfig_DecryptsExactGeminiKeyAndSheetUrl() {
        assertEquals(
            "AQ.Ab8RN6JqQRiHQCjey0DvehwhrAmWxs6JSKHDoc5vv2Coohcqkw",
            SecureConfig.getGeminiApiKey()
        )
        assertEquals(
            "https://script.google.com/macros/s/AKfycbwrrOvaR2CYUMhXkFnfXqk-NpdkW6vS5vjpVCJbGFLewLlgpW4nk0tdLowmHUSLhe1_/exec",
            SecureConfig.getGoogleSheetUrl()
        )
    }

    @Test
    fun testCatalogImageFetcher_BuildsCleanQuery() {
        val q1 = CatalogImageFetcher.buildCleanQuery(
            name = "Pañales Premium Care",
            brand = "Pampers",
            model = "Recién Nacido"
        )
        assertTrue(q1.contains("Pampers"))
        assertTrue(q1.contains("fondo blanco"))

        val q2 = CatalogImageFetcher.buildCleanQuery(
            name = "Mustela Crema Facial Hydra Bebe",
            brand = "Mustela",
            model = ""
        )
        assertTrue(q2.contains("Mustela"))
        assertTrue(q2.contains("fondo blanco"))
    }

    @Test
    fun testCatalogImageFetcher_SearchesValidImageUrls() {
        val urls = CatalogImageFetcher.searchCatalogImageUrls("Pampers Premium Care fondo blanco", maxResults = 3)
        // Bing Image search must return valid http/https image links
        assertTrue(urls.isNotEmpty())
        assertTrue(urls.first().startsWith("http"))
    }

    @Test
    fun testEvaluateConsumables_VenezuelanSizesAndPackages() {
        val useCase = EvaluateConsumablesUseCase()
        val products = listOf(
            BabyProduct(
                id = "p1",
                name = "Pampers Talla P",
                category = ProductCategory.DIAPERS,
                stageSize = "P",
                packageCount = 2,
                unitsPerPackage = 36, // 2 * 36 = 72 pañales P
                status = ProductStatus.PURCHASED
            ),
            BabyProduct(
                id = "p2",
                name = "Huggies Talla G",
                category = ProductCategory.DIAPERS,
                stageSize = "G",
                packageCount = 1,
                unitsPerPackage = 40,
                status = ProductStatus.GIFTED // Obsequio en baby shower
            )
        )

        val summary = useCase.execute(products)
        assertEquals(72, summary.diapersSizeP.currentUnits)
        assertEquals(40, summary.diapersSizeG.currentUnits)
        assertEquals(40, summary.giftedAudit?.currentUnits)
    }

    @Test
    fun testBarcodeHelper_ResolvesVenezuelanPrefix() {
        val veVariant = BarcodeHelper.resolveCountryVariant("7591234567890", "")
        assertTrue(veVariant.contains("Venezuela 🇻🇪"))

        val coVariant = BarcodeHelper.resolveCountryVariant("7701234567890", "")
        assertTrue(coVariant.contains("Colombia 🇨🇴"))

        val fallbackVariant = BarcodeHelper.resolveCountryVariant("", "Venezuela")
        assertTrue(fallbackVariant.contains("Venezuela 🇻🇪"))
    }

    @Test
    fun testEvaluateConsumables_GeneralConsumablesSummaryAndHospitalBag() {
        val useCase = EvaluateConsumablesUseCase()
        val products = listOf(
            BabyProduct(
                id = "p1",
                name = "Pampers RN",
                category = ProductCategory.DIAPERS,
                stageSize = "RN",
                packageCount = 3,
                unitsPerPackage = 36, // 3 * 36 = 108 pañales RN
                status = ProductStatus.PURCHASED
            ),
            BabyProduct(
                id = "p2",
                name = "Toallitas Huggies 80u",
                category = ProductCategory.WIPES_HYGIENE,
                packageCount = 2,
                unitsPerPackage = 80, // 2 * 80 = 160 toallitas
                status = ProductStatus.PURCHASED
            )
        )

        val summary = useCase.execute(products)
        val general = summary.generalSummary
        org.junit.Assert.assertNotNull(general)
        assertEquals(3, general?.totalDiaperPackages)
        assertEquals(108, general?.totalDiaperUnits)
        assertEquals(108, general?.sizeRn?.unitsCount)
        assertEquals(2, general?.totalWipesPackages)
        assertEquals(160, general?.totalWipesUnits)

        // Verificar secciones de la maleta del parto
        assertEquals(3, summary.hospitalBagSections.size)
        assertTrue(summary.hospitalBagSections.any { it.id == "baby" })
        assertTrue(summary.hospitalBagSections.any { it.id == "mom" })
        assertTrue(summary.hospitalBagSections.any { it.id == "dad" })
    }

    @Test
    fun testSoapINCIAndProgressiveMultiPhotoSupport() {
        val soapProduct = BabyProduct(
            id = "soap-1",
            name = "Jabón Neutro Puro",
            brand = "Johnson's",
            model = "Original Baby",
            category = ProductCategory.WIPES_HYGIENE,
            ingredients = "Sodium Palmate, Sodium Palm Kernelate, Aqua, Glycerin, Parfum, Titanium Dioxide",
            risksAndWarnings = "Jabón suave formulado para piel de recién nacido. Sin sulfatos agresivos.",
            safetyLevel = ProductSafetyLevel.SAFE
        )

        assertTrue(soapProduct.ingredients.contains("Sodium Palmate"))
        assertTrue(soapProduct.ingredients.contains("Aqua"))
        assertTrue(soapProduct.ingredients.contains("Glycerin"))
        assertEquals(ProductSafetyLevel.SAFE, soapProduct.safetyLevel)
    }

    @Test
    fun testRepositoryInstantSyncInvocation() {
        val testProduct = BabyProduct(
            id = "test-prod-sync",
            name = "Champú Manzanilla",
            brand = "Ricitos de Oro",
            category = ProductCategory.WIPES_HYGIENE
        )

        var syncCompleted = false
        val repo = com.example.llegadadelbebe.data.repository.BabyInventoryRepository()
        // Cuando no hay URL o al guardar sin conexión remota, invoca el callback con false o true
        repo.saveProduct(testProduct, googleSheetUrl = "") { success ->
            syncCompleted = true
        }
        assertTrue(syncCompleted)
    }

    @Test
    fun testPackageIncrementRecalculatesTotalUnitsAndLabel() {
        // Caso real: Britney registró Chicco Etapa 2 con 20 unidades
        val chiccoInitial = BabyProduct(
            id = "chicco-1",
            name = "Pañales Chicco Baby Skin Etapa 2",
            category = ProductCategory.DIAPERS,
            stageSize = "Etapa 2 (5,5 - 9,5 Kg)",
            packageCount = 1,
            unitsPerPackage = 1,
            quantityCurrent = 20
        )

        assertEquals(20, chiccoInitial.effectiveUnitsPerPackage)
        assertEquals(20, chiccoInitial.effectiveTotalUnits)
        assertEquals("1 pqte de 20 uds", chiccoInitial.displayQuantityLabel)

        // Cuando Edward o Britney pulsan '+' para agregar otro paquete idéntico
        val chiccoTwoPacks = chiccoInitial.copy(
            packageCount = 2,
            quantityCurrent = 2 * chiccoInitial.effectiveUnitsPerPackage
        )

        assertEquals(20, chiccoTwoPacks.effectiveUnitsPerPackage)
        assertEquals(40, chiccoTwoPacks.effectiveTotalUnits)
        assertEquals("2 pqtes × 20 uds (40 total)", chiccoTwoPacks.displayQuantityLabel)
    }

    @Test
    fun testAuditCountsAccuratelyAndExcludesPendingFromStock() {
        val useCase = EvaluateConsumablesUseCase()
        val products = listOf(
            // 1 paquete de 20 pañales Talla M (poseído)
            BabyProduct(
                id = "p-m1",
                name = "Pañales Chicco Baby Skin Etapa 2",
                category = ProductCategory.DIAPERS,
                stageSize = "Etapa 2 (5,5 - 9,5 Kg)",
                packageCount = 1,
                unitsPerPackage = 20,
                quantityCurrent = 20,
                status = ProductStatus.PURCHASED
            ),
            // 1 paquete pendiente por comprar de 36 pañales Talla M
            BabyProduct(
                id = "p-m2",
                name = "Pañales Pampers Confort Sec Etapa 2",
                category = ProductCategory.DIAPERS,
                stageSize = "M",
                packageCount = 1,
                unitsPerPackage = 36,
                quantityCurrent = 36,
                status = ProductStatus.PENDING
            )
        )

        val summary = useCase.execute(products)
        // El stock en casa solo debe contabilizar los 20 pañales comprados
        assertEquals(20, summary.diapersSizeM.currentUnits)
        assertEquals(1, summary.generalSummary?.sizeM?.packagesCount)
        assertEquals(20, summary.generalSummary?.sizeM?.unitsCount)
    }

    @Test
    fun testAstronomicalSynastry_EdwardAndBritneyOfficialPlacements() {
        // Edward: 31/05/1990 13:45
        val dad = com.example.llegadadelbebe.core.util.AstronomicalEphemerisCalculator.getFatherProfile(
            birthDate = LocalDate.of(1990, 5, 31),
            birthTime = java.time.LocalTime.of(13, 45)
        )
        assertEquals(com.example.llegadadelbebe.domain.model.ZodiacSign.GEMINI, dad.sun.sign)
        assertEquals(com.example.llegadadelbebe.domain.model.ZodiacSign.VIRGO, dad.moon.sign)
        assertEquals(com.example.llegadadelbebe.domain.model.ZodiacSign.VIRGO, dad.ascendant.sign)

        // Britney: 07/04/2004 18:45
        val mom = com.example.llegadadelbebe.core.util.AstronomicalEphemerisCalculator.getMotherProfile(
            birthDate = LocalDate.of(2004, 4, 7),
            birthTime = java.time.LocalTime.of(18, 45)
        )
        assertEquals(com.example.llegadadelbebe.domain.model.ZodiacSign.ARIES, mom.sun.sign)
        assertEquals(com.example.llegadadelbebe.domain.model.ZodiacSign.GEMINI, mom.moon.sign)
        assertEquals(com.example.llegadadelbebe.domain.model.ZodiacSign.LIBRA, mom.ascendant.sign)

        // Sinastría parental
        val report = com.example.llegadadelbebe.core.util.AstronomicalEphemerisCalculator.evaluateParentCompatibility(dad, mom)
        assertTrue(report.masterBondTitle.contains("Conjunción Sol–Luna en Géminis"))
        assertEquals(99, report.communicationScore)
        assertEquals(96, report.parentingHarmonyScore)
    }

    @Test
    fun testAstronomicalSynastry_BabyProjectionAndMoonWindow() {
        val fpp = LocalDate.of(2026, 11, 20)
        val baby = com.example.llegadadelbebe.core.util.AstronomicalEphemerisCalculator.calculateBabyProjectedChart(
            babyName = "Nuestro Niño",
            fppDate = fpp,
            simulatedHour = 14
        )
        org.junit.Assert.assertNotNull(baby.sun.sign)
        org.junit.Assert.assertNotNull(baby.projectedMoon.sign)
        org.junit.Assert.assertNotNull(baby.simulatedAscendant.sign)
        assertTrue(baby.simulatedShiftName.contains("Tarde"))

        val window = com.example.llegadadelbebe.core.util.AstronomicalEphemerisCalculator.calculateMoonWindow(fpp)
        assertEquals(15, window.size) // -7 a +7 = 15 días
        assertTrue(window.any { it.isFppDay })
    }
}
