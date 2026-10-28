package com.example.llegadadelbebe

import com.example.llegadadelbebe.domain.model.MedicationAdherenceSummary
import com.example.llegadadelbebe.domain.model.PrenatalMedication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrenatalMedicationsTest {

    @Test
    fun testMedicationModel_DaysOfSupplyAndStockWarning() {
        val iron = PrenatalMedication(
            name = "Sulfato Ferroso",
            activeIngredient = "Hierro elemental 60mg",
            dose = "1 tableta",
            frequencyHours = 24,
            scheduleTimes = listOf("08:00"),
            foodInstructions = "En ayunas con jugo de naranja",
            pillsRemaining = 4,
            clinicalWarnings = "No consumir con leche"
        )

        assertEquals(4, iron.daysOfSupplyRemaining)
        assertTrue("Debe alertar stock bajo cuando quedan <= 5 pastillas", iron.isLowStock)
        assertFalse(iron.isOutOfStock)

        val outOfStockFolic = iron.copy(pillsRemaining = 0)
        assertTrue(outOfStockFolic.isOutOfStock)
        assertEquals(0, outOfStockFolic.daysOfSupplyRemaining)
    }

    @Test
    fun testMedicationModel_DoseTakenToday() {
        val today = LocalDate.now().toString()
        val calcium = PrenatalMedication(
            name = "Calcio + Vitamina D3",
            activeIngredient = "Calcio 600mg + Vitamina D3 400UI",
            dose = "1 tableta masticable",
            frequencyHours = 12,
            scheduleTimes = listOf("14:00", "22:00"),
            lastTakenDate = today,
            lastConfirmedBy = "Papá (Edward)"
        )

        assertTrue(calcium.isTakenToday)
        assertEquals("Papá (Edward)", calcium.lastConfirmedBy)
    }

    @Test
    fun testMedicationAdherenceSummary_Calculation() {
        val today = LocalDate.now().toString()
        val med1 = PrenatalMedication(
            name = "Ácido Fólico",
            frequencyHours = 24,
            scheduleTimes = listOf("08:00"),
            lastTakenDate = today,
            pillsRemaining = 25
        )
        val med2 = PrenatalMedication(
            name = "Sulfato Ferroso",
            frequencyHours = 24,
            scheduleTimes = listOf("10:00"),
            lastTakenDate = "2026-10-25", // No tomada hoy
            pillsRemaining = 3 // Bajo stock
        )

        val meds = listOf(med1, med2)
        val takenCount = meds.count { it.isTakenToday }
        val pendingCount = meds.count { !it.isTakenToday }
        val lowStock = meds.count { it.isLowStock }

        val summary = MedicationAdherenceSummary(
            totalMedications = meds.size,
            takenTodayCount = takenCount,
            pendingTodayCount = pendingCount,
            lowStockCount = lowStock
        )

        assertEquals(2, summary.totalMedications)
        assertEquals(1, summary.takenTodayCount)
        assertEquals(1, summary.pendingTodayCount)
        assertEquals(0.5f, summary.adherencePercent, 0.001f)
        assertEquals(1, summary.lowStockCount)
        assertFalse(summary.isFullyCompletedToday)
    }

    @Test
    fun testClinicalInteraction_IronAndCalciumConflictDetection() {
        val medIron = PrenatalMedication(
            name = "Sulfato Ferroso",
            scheduleTimes = listOf("08:00")
        )
        val medCalciumSameTime = PrenatalMedication(
            name = "Calcio + Vitamina D3",
            scheduleTimes = listOf("08:00")
        )

        val sharesTime = medIron.scheduleTimes.any { it in medCalciumSameTime.scheduleTimes }
        assertTrue("Debe detectar conflicto de horario entre Hierro y Calcio", sharesTime)
    }

    @Test
    fun testMedicationNotificationActions_SnoozeDurationAndActionNames() {
        assertEquals(
            "com.example.llegadadelbebe.ACTION_TAKE_DOSE",
            com.example.llegadadelbebe.core.notification.MedicationAlarmReceiver.ACTION_TAKE_DOSE
        )
        assertEquals(
            "com.example.llegadadelbebe.ACTION_SNOOZE_DOSE",
            com.example.llegadadelbebe.core.notification.MedicationAlarmReceiver.ACTION_SNOOZE_DOSE
        )
        assertEquals(
            15,
            com.example.llegadadelbebe.core.notification.MedicationAlarmReceiver.DEFAULT_SNOOZE_MINUTES
        )
    }

    @Test
    fun testMedicationModel_DecrementPillsOnTake() {
        val initialMed = PrenatalMedication(
            name = "Sulfato Ferroso",
            pillsRemaining = 20
        )

        val updatedMed = initialMed.copy(
            pillsRemaining = (initialMed.pillsRemaining - 1).coerceAtLeast(0),
            lastTakenDate = "2026-10-28",
            lastTakenTime = "08:15",
            lastConfirmedBy = "Mamá (Britney)"
        )

        assertEquals(19, updatedMed.pillsRemaining)
        assertTrue(updatedMed.isTakenToday)
        assertEquals("Mamá (Britney)", updatedMed.lastConfirmedBy)
    }
}
