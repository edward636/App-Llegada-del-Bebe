package com.example.llegadadelbebe

import com.example.llegadadelbebe.core.util.StoreLocationHelper
import com.example.llegadadelbebe.domain.model.CurrencyType
import com.example.llegadadelbebe.domain.model.FamilyBudgetReport
import com.example.llegadadelbebe.domain.model.FarmatodoProductBenchmark
import com.example.llegadadelbebe.domain.model.KnownStoreLocation
import com.example.llegadadelbebe.domain.model.PriceComparisonVerdict
import com.example.llegadadelbebe.domain.model.ProductBudgetLine
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductPriceQuote
import com.example.llegadadelbebe.domain.model.StoreLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingRadarTest {

    @Test
    fun testStoreLocation_GoogleMapsUrlGeneration() {
        val locWithCoords = StoreLocation(
            name = "Locatel Chacao",
            address = "Av. Francisco de Miranda, Caracas",
            latitude = 10.4925,
            longitude = -66.8572
        )
        assertTrue(locWithCoords.hasValidCoordinates)
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=10.4925,-66.8572",
            locWithCoords.googleMapsUrl
        )

        val locWithoutCoords = StoreLocation(name = "Farmacia Local")
        assertFalse(locWithoutCoords.hasValidCoordinates)
        assertEquals("", locWithoutCoords.googleMapsUrl)
    }

    @Test
    fun testProductPriceQuote_VesConversionToUsdUsingBcv() {
        val bcvRate = 886.0 // 886 Bs. / USD
        val quoteInVes = ProductPriceQuote(
            productName = "Pañales Huggies Active Sec",
            brand = "Huggies",
            category = ProductCategory.DIAPERS,
            stageSize = "G",
            packageCount = 2,
            unitsPerPackage = 32, // total 64 uds
            storeName = "Automercados Plaza's",
            priceAmount = 17720.0, // 17,720 Bs
            priceCurrency = CurrencyType.VES,
            bcvRateApplied = bcvRate,
            priceUsdEquivalent = 17720.0 / bcvRate, // 20.0 USD
            unitPriceUsd = (17720.0 / bcvRate) / 64.0, // 0.3125 USD/ud
            quotedAt = "2026-10-26 15:30"
        )

        assertEquals(64, quoteInVes.totalUnits)
        assertEquals(20.0, quoteInVes.priceUsdEquivalent, 0.001)
        assertEquals(0.3125, quoteInVes.unitPriceUsd, 0.0001)
        assertEquals("Bs. 17720.00", quoteInVes.formattedPriceStore)
        assertEquals("$20.00", quoteInVes.formattedUsdEquivalent)
        assertEquals("$0.313 / ud", quoteInVes.formattedUnitPriceUsd)
    }

    @Test
    fun testFarmatodoBenchmark_OfferVsNormalPricing() {
        val ftWithOffer = FarmatodoProductBenchmark(
            id = "111035193",
            name = "Toallas Húmedas Huggies Limpieza Efectiva x 48 und",
            brand = "Huggies",
            fullPriceVes = 3252.0,
            offerPriceVes = 2601.6,
            priceUsd = 3.67,
            offerPriceUsd = 2.93,
            urlSlug = "111035193-toallas-humedas-huggies"
        )

        assertTrue(ftWithOffer.hasOffer)
        assertEquals(2601.6, ftWithOffer.effectivePriceVes, 0.01)
        assertEquals(2.93, ftWithOffer.effectivePriceUsd, 0.01)
        assertEquals(
            "https://www.farmatodo.com.ve/producto/111035193-toallas-humedas-huggies",
            ftWithOffer.webProductUrl
        )

        val ftRegular = FarmatodoProductBenchmark(
            id = "111098755",
            name = "Acetaminofén 650 mg Analper",
            brand = "Pharmetique",
            fullPriceVes = 1699.0,
            offerPriceVes = 0.0,
            priceUsd = 1.91,
            offerPriceUsd = 0.0,
            urlSlug = "111098755-acetaminofen"
        )

        assertFalse(ftRegular.hasOffer)
        assertEquals(1699.0, ftRegular.effectivePriceVes, 0.01)
        assertEquals(1.91, ftRegular.effectivePriceUsd, 0.01)
    }

    @Test
    fun testPriceComparisonVerdict_FarmatodoVsLocalStore() {
        val ftBenchmark = FarmatodoProductBenchmark(
            id = "114997494",
            name = "Pañal Huggies Active Sec XXG x 25",
            brand = "Huggies",
            fullPriceVes = 10040.8,
            offerPriceVes = 10040.8,
            priceUsd = 11.33,
            offerPriceUsd = 11.33
        )

        // Escenario A: Local más caro que Farmatodo
        val expensiveQuote = ProductPriceQuote(
            productName = "Pañal Huggies XXG",
            storeName = "Minimarket Local",
            priceAmount = 14.50,
            priceCurrency = CurrencyType.USD,
            priceUsdEquivalent = 14.50,
            unitPriceUsd = 14.50 / 25,
            quotedAt = "2026-10-26 12:00",
            farmatodoBenchmark = ftBenchmark
        )

        val diffA = expensiveQuote.priceUsdEquivalent - ftBenchmark.effectivePriceUsd
        assertTrue("Farmatodo debe ser más barato", diffA > 0)
        assertEquals(3.17, diffA, 0.01)

        // Escenario B: Local más barato que Farmatodo (Ofertón)
        val cheaperQuote = ProductPriceQuote(
            productName = "Pañal Huggies XXG",
            storeName = "Makro La Urbina",
            priceAmount = 9.80,
            priceCurrency = CurrencyType.USD,
            priceUsdEquivalent = 9.80,
            unitPriceUsd = 9.80 / 25,
            quotedAt = "2026-10-26 14:00",
            farmatodoBenchmark = ftBenchmark
        )

        val diffB = cheaperQuote.priceUsdEquivalent - ftBenchmark.effectivePriceUsd
        assertTrue("Makro debe ser más barato que Farmatodo", diffB < 0)
        assertEquals(1.53, -diffB, 0.01)
    }

    @Test
    fun testStoreLocationHelper_DistanceAndProximityMatching() {
        // Coordenadas base de Mercafácil
        val mercafacil = KnownStoreLocation(
            id = "store_mercafacil_1",
            name = "Mercafácil",
            address = "Av. Principal",
            latitude = 10.48000,
            longitude = -66.90000,
            visitCount = 3
        )

        val locatel = KnownStoreLocation(
            id = "store_locatel_1",
            name = "Locatel",
            address = "Centro Comercial",
            latitude = 10.48500,
            longitude = -66.90000,
            visitCount = 1
        )

        val knownStores = listOf(mercafacil, locatel)

        // 1. Distancia a 33 metros de Mercafácil (dentro del umbral de 120m)
        val nearbyLat = 10.48030
        val nearbyLon = -66.90000
        val distToMercafacil = StoreLocationHelper.calculateDistanceMeters(
            nearbyLat, nearbyLon, mercafacil.latitude, mercafacil.longitude
        )
        assertTrue("La distancia debe ser menor a 50m", distToMercafacil < 50.0)

        val detectedStore = StoreLocationHelper.findNearbyKnownStore(
            nearbyLat, nearbyLon, knownStores, maxRadiusMeters = 120.0
        )
        assertNotNull(detectedStore)
        assertEquals("Mercafácil", detectedStore?.name)

        // 2. Coordenada lejana (a más de 400m de ambas tiendas)
        val farLat = 10.49000
        val farLon = -66.90000
        val noStore = StoreLocationHelper.findNearbyKnownStore(
            farLat, farLon, knownStores, maxRadiusMeters = 120.0
        )
        assertNull("No debe asociar ninguna tienda si está fuera del radio de 120m", noStore)

        // 3. Coordenadas 0.0, 0.0 deben retornar null con seguridad
        val zeroStore = StoreLocationHelper.findNearbyKnownStore(0.0, 0.0, knownStores)
        assertNull(zeroStore)
    }

    @Test
    fun testFamilyBudgetReport_CalculationsAndBcvEquivalents() {
        val bcvRate = 880.0 // 880 Bs. / USD

        val lines = listOf(
            ProductBudgetLine(
                productId = "prod_1",
                productName = "Cuna Colecho",
                brand = "Infanti",
                category = ProductCategory.CRIB_SLEEP,
                isPurchased = true,
                quantity = 1,
                unitPriceUsd = 120.0,
                totalPriceUsd = 120.0,
                sourcePriceLabel = "Mercafácil ($120.00)"
            ),
            ProductBudgetLine(
                productId = "prod_2",
                productName = "Extractor de Leche",
                brand = "Philips Avent",
                category = ProductCategory.FEEDING,
                isPurchased = true,
                quantity = 1,
                unitPriceUsd = 45.0,
                totalPriceUsd = 45.0,
                sourcePriceLabel = "Locatel ($45.00)"
            ),
            ProductBudgetLine(
                productId = "prod_3",
                productName = "Pañales Huggies Recién Nacido x 34",
                brand = "Huggies",
                category = ProductCategory.DIAPERS,
                isPurchased = false,
                quantity = 2,
                unitPriceUsd = 11.50,
                totalPriceUsd = 23.0,
                sourcePriceLabel = "Farmatodo Oficial ($11.50)"
            ),
            ProductBudgetLine(
                productId = "prod_4",
                productName = "Toallitas Húmedas x 80",
                brand = "Huggies",
                category = ProductCategory.WIPES_HYGIENE,
                isPurchased = false,
                quantity = 4,
                unitPriceUsd = 3.0,
                totalPriceUsd = 12.0,
                sourcePriceLabel = "Estimado de mercado ($3.00)"
            )
        )

        val totalSpent = lines.filter { it.isPurchased }.sumOf { it.totalPriceUsd } // 120 + 45 = 165.0
        val totalPending = lines.filter { !it.isPurchased }.sumOf { it.totalPriceUsd } // 23 + 12 = 35.0

        val budget = FamilyBudgetReport(
            totalSpentUsd = totalSpent,
            totalPendingUsd = totalPending,
            bcvRate = bcvRate,
            purchasedCount = 2,
            pendingCount = 2,
            lines = lines
        )

        // Verificaciones en Dólares
        assertEquals(165.0, budget.totalSpentUsd, 0.001)
        assertEquals(35.0, budget.totalPendingUsd, 0.001)
        assertEquals(200.0, budget.totalProjectedUsd, 0.001)
        assertEquals(0.825f, budget.spentPercentage, 0.001f) // 165 / 200 = 82.5%

        // Verificaciones en Bolívares Tasa BCV (880.0)
        assertEquals(145200.0, budget.totalSpentVes, 0.01) // 165 * 880
        assertEquals(30800.0, budget.totalPendingVes, 0.01) // 35 * 880
        assertEquals(176000.0, budget.totalProjectedVes, 0.01) // 200 * 880

        // Verificaciones de formato
        assertEquals("$165.00", budget.formattedSpentUsd)
        assertEquals("$35.00", budget.formattedPendingUsd)
        assertEquals("$200.00", budget.formattedProjectedUsd)
        assertEquals("Bs. 145200.00", budget.formattedSpentVes)
        assertEquals("Bs. 30800.00", budget.formattedPendingVes)
        assertEquals("Bs. 176000.00", budget.formattedProjectedVes)
    }

    @Test
    fun testStoreNameMatch_GoogleMapsVsUserFeedback() {
        // Coincidencias válidas (insensible a mayúsculas o con sufijos geográficos comunes)
        assertTrue(StoreLocationHelper.checkStoreNameMatch("Mercafácil", "Mercafácil"))
        assertTrue(StoreLocationHelper.checkStoreNameMatch("Mercafácil", "Mercafácil Los Teques"))
        assertTrue(StoreLocationHelper.checkStoreNameMatch("Farmatodo", "Farmatodo La Castellana"))
        assertTrue(StoreLocationHelper.checkStoreNameMatch("Locatel Chacao", "Locatel"))

        // Discrepancias / Sin coincidencia
        assertFalse(StoreLocationHelper.checkStoreNameMatch("Mercafácil", "Automercados Plaza's"))
        assertFalse(StoreLocationHelper.checkStoreNameMatch("Farmatodo", "Locatel"))
        assertFalse(StoreLocationHelper.checkStoreNameMatch("", "Mercafácil"))
        assertFalse(StoreLocationHelper.checkStoreNameMatch("Mercafácil", ""))

        // Verificación en modelo KnownStoreLocation
        val matchedStore = KnownStoreLocation(
            name = "Mercafácil",
            latitude = 10.4800,
            longitude = -66.9000,
            googleMapsName = "Mercafácil Los Teques",
            isUserConfirmed = true
        )
        assertTrue("Debe detectar coincidencia de Google Maps", matchedStore.hasGoogleMapsMatch)

        val unconfirmedStore = KnownStoreLocation(
            name = "Tienda de Bebé",
            latitude = 10.4800,
            longitude = -66.9000,
            googleMapsName = "Centro Comercial Líder",
            isUserConfirmed = true
        )
        assertFalse("No debe coincidir si son nombres conceptualmente distintos", unconfirmedStore.hasGoogleMapsMatch)
    }
}
