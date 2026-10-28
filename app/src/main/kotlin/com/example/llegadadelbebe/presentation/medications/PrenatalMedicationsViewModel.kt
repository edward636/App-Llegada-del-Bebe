package com.example.llegadadelbebe.presentation.medications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.llegadadelbebe.core.notification.MedicationAlarmScheduler
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.data.remote.GeminiClient
import com.example.llegadadelbebe.data.remote.GoogleSheetsService
import com.example.llegadadelbebe.data.remote.ParsedMedicationInfo
import com.example.llegadadelbebe.domain.model.MedicationAdherenceSummary
import com.example.llegadadelbebe.domain.model.PrenatalMedication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class PrenatalMedicationsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val localPreferences = LocalPreferences(application)
    private val geminiClient = GeminiClient()
    private val googleSheetsService = GoogleSheetsService()

    private val _medications = MutableStateFlow<List<PrenatalMedication>>(emptyList())
    val medications: StateFlow<List<PrenatalMedication>> = _medications.asStateFlow()

    private val _isAnalyzingPhoto = MutableStateFlow(false)
    val isAnalyzingPhoto: StateFlow<Boolean> = _isAnalyzingPhoto.asStateFlow()

    val activeUser: StateFlow<String> = MutableStateFlow(localPreferences.getActiveUser())

    val adherenceSummary: StateFlow<MedicationAdherenceSummary> = _medications.map { list ->
        val total = list.size
        val taken = list.count { it.isTakenToday }
        val pending = total - taken
        val lowStock = list.count { it.isLowStock || it.isOutOfStock }
        MedicationAdherenceSummary(
            totalMedications = total,
            takenTodayCount = taken,
            pendingTodayCount = pending,
            lowStockCount = lowStock
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MedicationAdherenceSummary(0, 0, 0, 0)
    )

    init {
        loadMedications()
    }

    private fun loadMedications() {
        val cached = localPreferences.getMedicationsCache()
        _medications.value = cached
        // Asegurar alarmas programadas
        cached.forEach { med ->
            med.scheduleTimes.forEach { time ->
                MedicationAlarmScheduler.scheduleMedicationAlarm(
                    context = getApplication(),
                    medId = med.id,
                    medName = med.name,
                    dose = med.dose,
                    instructions = med.foodInstructions,
                    timeStr = time
                )
            }
        }
    }

    fun markMedicationAsTaken(id: String, user: String = activeUser.value) {
        val todayStr = LocalDate.now().toString()
        val timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        val updatedList = _medications.value.map { med ->
            if (med.id == id) {
                med.copy(
                    lastTakenDate = todayStr,
                    lastTakenTime = timeNow,
                    lastConfirmedBy = user,
                    pillsRemaining = (med.pillsRemaining - 1).coerceAtLeast(0),
                    updatedAt = System.currentTimeMillis()
                )
            } else med
        }

        _medications.value = updatedList
        localPreferences.saveMedicationsCache(updatedList)
    }

    fun saveMedication(med: PrenatalMedication) {
        val current = _medications.value.toMutableList()
        val index = current.indexOfFirst { it.id == med.id }
        if (index >= 0) {
            current[index] = med
        } else {
            current.add(0, med)
        }

        _medications.value = current
        localPreferences.saveMedicationsCache(current)

        // Programar alarmas para los horarios configurados
        med.scheduleTimes.forEach { time ->
            MedicationAlarmScheduler.scheduleMedicationAlarm(
                context = getApplication(),
                medId = med.id,
                medName = med.name,
                dose = med.dose,
                instructions = med.foodInstructions,
                timeStr = time
            )
        }
    }

    fun deleteMedication(id: String) {
        val med = _medications.value.firstOrNull { it.id == id }
        med?.scheduleTimes?.forEach { time ->
            MedicationAlarmScheduler.cancelMedicationAlarm(getApplication(), id, time)
        }
        val updated = _medications.value.filterNot { it.id == id }
        _medications.value = updated
        localPreferences.saveMedicationsCache(updated)
    }

    fun analyzeMedicationPhoto(
        base64Image: String,
        apiKey: String,
        onResult: (ParsedMedicationInfo?) -> Unit
    ) {
        viewModelScope.launch {
            _isAnalyzingPhoto.value = true
            try {
                val res = geminiClient.extractMedicationFromImage(
                    base64Image = base64Image,
                    apiKey = apiKey
                )
                onResult(res.getOrNull())
            } catch (_: Exception) {
                onResult(null)
            } finally {
                _isAnalyzingPhoto.value = false
            }
        }
    }
}
