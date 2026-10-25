package com.example.llegadadelbebe

import com.example.llegadadelbebe.core.security.SecureConfig
import com.example.llegadadelbebe.core.update.OtaUpdateManager
import com.example.llegadadelbebe.core.util.BarcodeHelper
import com.example.llegadadelbebe.core.util.PregnancyCalculator
import com.example.llegadadelbebe.core.util.ZodiacPredictor
import com.example.llegadadelbebe.data.remote.CatalogImageFetcher
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
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
}
