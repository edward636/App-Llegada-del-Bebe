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
    val babyWeightEstimate: String,
    val growthMilestone: BabyGrowthMilestone = BabyGrowthMilestones.getMilestoneForWeek(currentWeeks)
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

        val milestone = BabyGrowthMilestones.getMilestoneForWeek(weeks)
        val sizeDesc = "${milestone.fruitEmoji} ${milestone.fruitName} (~${milestone.lengthCm} cm)"
        val weightDesc = "~${if (milestone.weightGrams >= 1000) String.format(java.util.Locale.US, "%.1f kg", milestone.weightGrams / 1000.0) else "${milestone.weightGrams.toInt()} g"}"

        return PregnancyStats(
            currentWeeks = weeks,
            currentDays = days,
            estimatedDueDate = dueDate,
            daysRemaining = daysRemaining,
            weeksRemaining = weeksRemaining,
            trimester = trimester,
            progressPercent = progress,
            babySizeComparison = sizeDesc,
            babyWeightEstimate = weightDesc,
            growthMilestone = milestone
        )
    }
}
