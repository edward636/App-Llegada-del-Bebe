package com.example.llegadadelbebe

import com.example.llegadadelbebe.core.util.PregnancyCalculator
import com.example.llegadadelbebe.core.util.ZodiacPredictor
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductStatus
import com.example.llegadadelbebe.domain.model.StockStatus
import com.example.llegadadelbebe.domain.usecase.EvaluateConsumablesUseCase
import org.junit.Assert.assertEquals
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
}
