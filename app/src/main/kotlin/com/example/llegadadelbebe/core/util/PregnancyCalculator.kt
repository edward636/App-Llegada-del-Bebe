package com.example.llegadadelbebe.core.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Modelo de datos con estadísticas gestacionales para los padres.
 */
data class PregnancyStats(
    val currentWeeks: Int,
    val currentDays: Int,
    val estimatedDueDate: LocalDate,
    val daysRemaining: Long,
    val weeksRemaining: Long,
    val trimester: Int,
    val progressPercent: Float,
    val babySizeComparison: String,
    val babyWeightEstimate: String
)

/**
 * Utilidad clínica para cálculo gestacional basado en la Regla de Naegele.
 * FPP = FUM + 7 días - 3 meses + 1 año (40 semanas gestacionales estándar).
 */
object PregnancyCalculator {

    private const val TOTAL_GESTATIONAL_DAYS = 280L // 40 semanas

    /**
     * Calcula estadísticas a partir de la Fecha de Última Menstruación (FUM).
     */
    fun calculateFromLmp(lmp: LocalDate, today: LocalDate = LocalDate.now()): PregnancyStats {
        val estimatedDueDate = lmp.plusDays(TOTAL_GESTATIONAL_DAYS)
        val daysPassed = ChronoUnit.DAYS.between(lmp, today).coerceAtLeast(0)
        return buildStats(daysPassed, estimatedDueDate, today)
    }

    /**
     * Calcula estadísticas a partir de la Fecha Probable de Parto (FPP).
     */
    fun calculateFromDueDate(dueDate: LocalDate, today: LocalDate = LocalDate.now()): PregnancyStats {
        val lmp = dueDate.minusDays(TOTAL_GESTATIONAL_DAYS)
        val daysPassed = ChronoUnit.DAYS.between(lmp, today).coerceAtLeast(0)
        return buildStats(daysPassed, dueDate, today)
    }

    /**
     * Calcula estadísticas a partir de semanas y días actuales ingresados directamente.
     */
    fun calculateFromCurrentWeeks(weeks: Int, days: Int = 0, today: LocalDate = LocalDate.now()): PregnancyStats {
        val totalDaysPassed = (weeks * 7L) + days
        val dueDate = today.plusDays(TOTAL_GESTATIONAL_DAYS - totalDaysPassed)
        return buildStats(totalDaysPassed, dueDate, today)
    }

    private fun buildStats(daysPassed: Long, dueDate: LocalDate, today: LocalDate): PregnancyStats {
        val weeks = (daysPassed / 7).toInt().coerceIn(0, 42)
        val days = (daysPassed % 7).toInt().coerceIn(0, 6)
        val daysRemaining = ChronoUnit.DAYS.between(today, dueDate).coerceAtLeast(0)
        val weeksRemaining = (daysRemaining / 7).coerceAtLeast(0)

        val trimester = when {
            weeks < 13 -> 1
            weeks < 27 -> 2
            else -> 3
        }

        val progress = (daysPassed.toFloat() / TOTAL_GESTATIONAL_DAYS).coerceIn(0f, 1f)

        val (sizeDesc, weightDesc) = getBabyMilestone(weeks)

        return PregnancyStats(
            currentWeeks = weeks,
            currentDays = days,
            estimatedDueDate = dueDate,
            daysRemaining = daysRemaining,
            weeksRemaining = weeksRemaining,
            trimester = trimester,
            progressPercent = progress,
            babySizeComparison = sizeDesc,
            babyWeightEstimate = weightDesc
        )
    }

    private fun getBabyMilestone(weeks: Int): Pair<String, String> {
        return when (weeks) {
            in 0..7 -> "Semilla de manzana (~5 mm)" to "< 1 g"
            in 8..11 -> "Fresita (~3 cm)" to "~4 g"
            in 12..15 -> "Limón dulce (~8 cm)" to "~45 g"
            in 16..19 -> "Mango tierno (~15 cm)" to "~240 g"
            in 20..23 -> "Plátano (~26 cm)" to "~430 g"
            in 24..27 -> "Espiga de maíz (~35 cm)" to "~750 g"
            in 28..31 -> "Berenjena grande (~38 cm)" to "~1.2 kg"
            in 32..35 -> "Piña dulce (~43 cm)" to "~1.9 kg"
            in 36..38 -> "Melón cantaloupe (~48 cm)" to "~2.7 kg"
            else -> "Sandía lista para nacer (~50 cm)" to "~3.3 kg"
        }
    }
}
