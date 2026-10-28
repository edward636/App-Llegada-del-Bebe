package com.example.llegadadelbebe.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
data class PrenatalMedication(
    val id: String = "MED_${UUID.randomUUID()}",
    val name: String,
    val activeIngredient: String = "",
    val dose: String = "1 tableta",
    val frequencyHours: Int = 24,
    val scheduleTimes: List<String> = listOf("08:00"),
    val foodInstructions: String = "Con el desayuno",
    val clinicalWarnings: String = "",
    val pillsRemaining: Int = 30,
    val totalPillsInPackage: Int = 30,
    val isMandatory: Boolean = true,
    val photoBase64: String = "",
    val notes: String = "",
    val lastTakenDate: String = "",
    val lastTakenTime: String = "",
    val lastConfirmedBy: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isTakenToday: Boolean
        get() {
            val todayStr = LocalDate.now().toString()
            return lastTakenDate == todayStr
        }

    val nextScheduledTime: String
        get() = scheduleTimes.firstOrNull() ?: "08:00"

    val isLowStock: Boolean
        get() = pillsRemaining in 1..5

    val isOutOfStock: Boolean
        get() = pillsRemaining <= 0

    val daysOfSupplyRemaining: Int
        get() {
            val dosesPerDay = (24 / frequencyHours.coerceAtLeast(1)).coerceAtLeast(1)
            return if (dosesPerDay > 0) pillsRemaining / dosesPerDay else pillsRemaining
        }
}

data class MedicationAdherenceSummary(
    val totalMedications: Int,
    val takenTodayCount: Int,
    val pendingTodayCount: Int,
    val lowStockCount: Int
) {
    val adherencePercent: Float
        get() = if (totalMedications > 0) (takenTodayCount.toFloat() / totalMedications).coerceIn(0f, 1f) else 1f

    val isFullyCompletedToday: Boolean
        get() = totalMedications > 0 && takenTodayCount >= totalMedications
}
