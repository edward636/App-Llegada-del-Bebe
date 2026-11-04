package com.example.llegadadelbebe

import com.example.llegadadelbebe.core.util.ConflictSeverity
import com.example.llegadadelbebe.core.util.MedicationConflictHelper
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
            name = "Ferganic forte",
            activeIngredient = "ácido fólico + hierro",
            scheduleTimes = listOf("10:00")
        )
        // Calcio a las 11:00 (diferencia de 60 minutos < 120 minutos) -> Conflicto WARNING
        val medCalciumNear = PrenatalMedication(
            name = "Calcibon Natal",
            scheduleTimes = listOf("11:00")
        )

        val conflictsNear = MedicationConflictHelper.evaluateAllConflicts(listOf(medIron, medCalciumNear))
        assertEquals(1, conflictsNear.size)
        assertEquals(ConflictSeverity.WARNING, conflictsNear[0].severity)
        assertTrue(conflictsNear[0].title.contains("Calcio y Hierro"))
        assertEquals(60, conflictsNear[0].differenceMinutes)

        // Si se separan por 2 horas (ej: Calcio a las 13:00 y Hierro a las 10:00 -> 180 min) -> Sin conflicto
        val medCalciumSafe = PrenatalMedication(
            name = "Calcibon Natal",
            scheduleTimes = listOf("13:00")
        )
        val conflictsSafe = MedicationConflictHelper.evaluateAllConflicts(listOf(medIron, medCalciumSafe))
        assertTrue("No debe haber conflicto si están separados por 3 horas", conflictsSafe.isEmpty())
    }

    @Test
    fun testMedicationConflictHelper_CandidateConflictDetection() {
        val existingCalcium = PrenatalMedication(
            id = "med_calcibon",
            name = "Calcibon Natal",
            scheduleTimes = listOf("13:00")
        )

        val candidateIron = PrenatalMedication(
            id = "med_ferganic",
            name = "Ferganic forte",
            activeIngredient = "Hierro elemental"
        )

        // Intentar programar el Hierro a las 12:30 (diferencia de 30 min con el Calcio de las 13:00)
        val candidateConflicts = MedicationConflictHelper.evaluateCandidateConflict(
            candidate = candidateIron,
            candidateTime = "12:30",
            otherMedications = listOf(existingCalcium)
        )

        assertEquals(1, candidateConflicts.size)
        assertEquals(ConflictSeverity.WARNING, candidateConflicts[0].severity)
        assertEquals(30, candidateConflicts[0].differenceMinutes)
    }

    @Test
    fun testMedicationConflictHelper_SameTimeMultiplePills() {
        val med1 = PrenatalMedication(
            name = "Vitamina C",
            scheduleTimes = listOf("08:00")
        )
        val med2 = PrenatalMedication(
            name = "Omega 3 DHA",
            scheduleTimes = listOf("08:00")
        )

        val conflicts = MedicationConflictHelper.evaluateAllConflicts(listOf(med1, med2))
        assertEquals(1, conflicts.size)
        assertEquals(ConflictSeverity.INFO, conflicts[0].severity)
        assertEquals(0, conflicts[0].differenceMinutes)
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
            lastTakenDate = java.time.LocalDate.now().toString(),
            lastTakenTime = "08:15",
            lastConfirmedBy = "Mamá (Britney)"
        )

        assertEquals(19, updatedMed.pillsRemaining)
        assertTrue(updatedMed.isTakenToday)
        assertEquals("Mamá (Britney)", updatedMed.lastConfirmedBy)
    }

    @Test
    fun testMedicationConflictHelper_FerganicDoesNotConflictWithItselfEvenWithCalciumWarning() {
        // Reproduce el caso exacto reportado por el usuario:
        // Ferganic Folic con advertencia médica que menciona "calcio"
        val ferganic = PrenatalMedication(
            id = "med_ferganic_folic",
            name = "Ferganic Folic",
            activeIngredient = "Hierro polimaltosado + Ácido Fólico",
            scheduleTimes = listOf("10:00"),
            clinicalWarnings = "El calcio bloquea la absorción del hierro. Separar 2 horas de lácteos o calcio."
        )

        // 1. Debe clasificarse como Hierro pero NUNCA como Calcio a pesar de la advertencia
        assertTrue("Ferganic debe ser clasificado como Hierro", MedicationConflictHelper.isIron(ferganic))
        assertFalse("Ferganic NO debe ser clasificado como Calcio a pesar del texto de la advertencia", MedicationConflictHelper.isCalcium(ferganic))

        // 2. No debe entrar en conflicto sobre sí mismo
        val selfConflicts = MedicationConflictHelper.evaluateAllConflicts(listOf(ferganic))
        assertTrue("Ferganic Folic nunca debe entrar en conflicto consigo mismo", selfConflicts.isEmpty())

        // 3. Al evaluar como candidato (en el diálogo de edición), no debe colisionar consigo mismo
        val candidateConflicts = MedicationConflictHelper.evaluateCandidateConflict(
            candidate = ferganic.copy(id = "candidate_med"),
            candidateTime = "10:00",
            otherMedications = listOf(ferganic)
        )
        assertTrue("Candidato con el mismo nombre no debe colisionar con la medicina ya existente", candidateConflicts.isEmpty())

        // 4. Si se compara con Calcibon Natal a las 11:00, los nombres deben asignarse correctamente
        val calcibon = PrenatalMedication(
            id = "med_calcibon",
            name = "Calcibon Natal",
            activeIngredient = "Calcio citrato",
            scheduleTimes = listOf("11:00")
        )
        val pairConflicts = MedicationConflictHelper.evaluateAllConflicts(listOf(ferganic, calcibon))
        assertEquals(1, pairConflicts.size)
        assertEquals("Calcibon Natal", pairConflicts[0].med2Name)
        assertEquals("Ferganic Folic", pairConflicts[0].med1Name)
        assertTrue(pairConflicts[0].description.contains("El Calcio en 'Calcibon Natal' (11:00) bloquea la absorción del Hierro en 'Ferganic Folic' (10:00)"))
    }
}
