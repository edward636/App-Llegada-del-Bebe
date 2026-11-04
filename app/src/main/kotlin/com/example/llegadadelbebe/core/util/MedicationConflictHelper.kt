package com.example.llegadadelbebe.core.util

import com.example.llegadadelbebe.domain.model.PrenatalMedication
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

enum class ConflictSeverity {
    WARNING, // Severo (ej: Calcio bloqueando absorción de Hierro)
    CAUTION, // Moderado (ej: Multivitamínico con minerales cerca del Hierro)
    INFO     // Informativo (ej: Múltiples pastillas al mismo minuto)
}

data class MedicationConflict(
    val med1Name: String,
    val med1Time: String,
    val med2Name: String,
    val med2Time: String,
    val differenceMinutes: Int,
    val title: String,
    val description: String,
    val severity: ConflictSeverity
)

/**
 * Evaluador clínico de interacciones y espaciado horario para vitaminas y medicamentos prenatales.
 * Basado en farmacología obstétrica:
 * 1. El Calcio inhibe de forma competitiva la absorción intestinal del Hierro hasta en un 50-60%.
 *    Deben separarse al menos 2 horas (120 minutos).
 * 2. Los Polivitamínicos (MaternaVit, Gestavit, etc.) que contienen sales minerales deben separarse 1-2 horas del Hierro.
 * 3. Múltiples pastillas tomadas en el mismo minuto pueden inducir náuseas o reflujo gástrico en el embarazo.
 */
object MedicationConflictHelper {

    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

    private val IRON_KEYWORDS = listOf(
        "hierro", "ferganic", "ferro", "sulfato ferroso", "iron",
        "fumarato", "ferrimax", "gluconato ferroso"
    )

    private val CALCIUM_KEYWORDS = listOf(
        "calcio", "calcibon", "caltrate", "calcium", "citracal",
        "carbonato de calcio", "citrato de calcio"
    )

    private val MULTIVITAMIN_KEYWORDS = listOf(
        "maternavit", "gestavit", "prenatal", "elevit", "multivitamin",
        "complejo b", "centrum", "supradyn"
    )

    fun isIron(med: PrenatalMedication): Boolean {
        val text = "${med.name} ${med.activeIngredient} ${med.clinicalWarnings}".lowercase()
        return IRON_KEYWORDS.any { text.contains(it) }
    }

    fun isCalcium(med: PrenatalMedication): Boolean {
        val text = "${med.name} ${med.activeIngredient} ${med.clinicalWarnings}".lowercase()
        return CALCIUM_KEYWORDS.any { text.contains(it) }
    }

    fun isMultivitamin(med: PrenatalMedication): Boolean {
        val text = "${med.name} ${med.activeIngredient} ${med.clinicalWarnings}".lowercase()
        return MULTIVITAMIN_KEYWORDS.any { text.contains(it) }
    }

    /**
     * Calcula la diferencia absoluta en minutos entre dos horas en formato "HH:mm".
     * Maneja el cambio de medianoche (ej: 23:30 a 00:30 = 60 minutos).
     */
    fun calculateMinutesDifference(time1: String, time2: String): Int {
        return try {
            val t1 = LocalTime.parse(time1.trim(), TIME_FORMATTER)
            val t2 = LocalTime.parse(time2.trim(), TIME_FORMATTER)
            val diff = abs(t1.toSecondOfDay() - t2.toSecondOfDay()) / 60
            minOf(diff, 1440 - diff)
        } catch (_: Exception) {
            9999
        }
    }

    /**
     * Evalúa todos los conflictos existentes en la lista completa de medicamentos activos.
     */
    fun evaluateAllConflicts(medications: List<PrenatalMedication>): List<MedicationConflict> {
        val conflicts = mutableListOf<MedicationConflict>()
        for (i in medications.indices) {
            for (j in i + 1 until medications.size) {
                val med1 = medications[i]
                val med2 = medications[j]

                for (t1 in med1.scheduleTimes) {
                    for (t2 in med2.scheduleTimes) {
                        val conflict = checkPairConflict(med1, t1, med2, t2)
                        if (conflict != null) {
                            conflicts.add(conflict)
                        }
                    }
                }
            }
        }
        return conflicts
    }

    /**
     * Evalúa si una hora tentativa para un medicamento específico entra en conflicto con las demás vitaminas existentes.
     */
    fun evaluateCandidateConflict(
        candidate: PrenatalMedication,
        candidateTime: String,
        otherMedications: List<PrenatalMedication>
    ): List<MedicationConflict> {
        val conflicts = mutableListOf<MedicationConflict>()
        val filteredOthers = otherMedications.filterNot { it.id == candidate.id }

        for (other in filteredOthers) {
            for (otherTime in other.scheduleTimes) {
                val conflict = checkPairConflict(candidate, candidateTime, other, otherTime)
                if (conflict != null) {
                    conflicts.add(conflict)
                }
            }
        }
        return conflicts
    }

    private fun checkPairConflict(
        med1: PrenatalMedication,
        time1: String,
        med2: PrenatalMedication,
        time2: String
    ): MedicationConflict? {
        val diffMin = calculateMinutesDifference(time1, time2)
        if (diffMin >= 120) return null

        val isM1Iron = isIron(med1)
        val isM2Iron = isIron(med2)
        val isM1Calcium = isCalcium(med1)
        val isM2Calcium = isCalcium(med2)

        // 1. REGLA ORO: Hierro vs Calcio (Interferencia clínica mayor)
        if ((isM1Iron && isM2Calcium) || (isM2Iron && isM1Calcium)) {
            val ironName = if (isM1Iron) med1.name else med2.name
            val calciumName = if (isM1Calcium) med1.name else med2.name
            val ironTime = if (isM1Iron) time1 else time2
            val calciumTime = if (isM1Calcium) time1 else time2

            return MedicationConflict(
                med1Name = ironName,
                med1Time = ironTime,
                med2Name = calciumName,
                med2Time = calciumTime,
                differenceMinutes = diffMin,
                title = "⚠️ Interferencia de absorción: Calcio y Hierro",
                description = "El Calcio en '$calciumName' ($calciumTime) bloquea la absorción del Hierro en '$ironName' ($ironTime). Tienen solo $diffMin min de diferencia. Se recomienda espaciarlos al menos 2 horas (120 min).",
                severity = ConflictSeverity.WARNING
            )
        }

        // 2. REGLA: Hierro vs Multivitamínico (Polivitamínico con sales minerales)
        val isM1Multi = isMultivitamin(med1)
        val isM2Multi = isMultivitamin(med2)
        if (((isM1Iron && isM2Multi) || (isM2Iron && isM1Multi)) && diffMin < 60) {
            val ironName = if (isM1Iron) med1.name else med2.name
            val multiName = if (isM1Multi) med1.name else med2.name
            val ironTime = if (isM1Iron) time1 else time2
            val multiTime = if (isM1Multi) time1 else time2

            return MedicationConflict(
                med1Name = ironName,
                med1Time = ironTime,
                med2Name = multiName,
                med2Time = multiTime,
                differenceMinutes = diffMin,
                title = "💡 Consejo de espaciado: Hierro y Multivitamínico",
                description = "'$ironName' ($ironTime) y '$multiName' ($multiTime) están muy cercanos ($diffMin min). Separarlos al menos 1 hora permite asimilar mejor el hierro sin sobrecarga gástrica.",
                severity = ConflictSeverity.CAUTION
            )
        }

        // 3. REGLA: Mismo horario exacto para dos vitaminas distintas
        if (diffMin == 0) {
            return MedicationConflict(
                med1Name = med1.name,
                med1Time = time1,
                med2Name = med2.name,
                med2Time = time2,
                differenceMinutes = 0,
                title = "ℹ️ Múltiples vitaminas a la misma hora ($time1)",
                description = "Tomar '${med1.name}' y '${med2.name}' simultáneamente a las $time1 puede causar pesadez o náuseas. Se recomienda distribuirlas entre desayuno, almuerzo y cena.",
                severity = ConflictSeverity.INFO
            )
        }

        return null
    }
}
