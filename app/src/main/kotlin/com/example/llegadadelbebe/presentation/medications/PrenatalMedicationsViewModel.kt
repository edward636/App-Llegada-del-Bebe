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

    private val _activeUser = MutableStateFlow(localPreferences.getActiveUser())
    val activeUser: StateFlow<String> = _activeUser.asStateFlow()

    fun setActiveUser(user: String) {
        _activeUser.value = user
    }

    fun refreshMedications() {
        loadMedications()
    }

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
        syncWithCloud()
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
        syncWithCloud()
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
        syncWithCloud()

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
        syncWithCloud()
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

    fun syncWithCloud(sheetUrl: String = com.example.llegadadelbebe.core.security.SecureConfig.getGoogleSheetUrl()) {
        if (sheetUrl.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val remoteResult = googleSheetsService.fetchSystemRecord(sheetUrl, GoogleSheetsService.SYS_MEDICATIONS)
            if (remoteResult.isSuccess) {
                val remotePayload = remoteResult.getOrNull()
                if (!remotePayload.isNullOrBlank()) {
                    val remoteList = localPreferences.parseMedicationsJson(remotePayload)
                    if (remoteList.isNotEmpty()) {
                        val localMap = _medications.value.associateBy { it.id }.toMutableMap()
                        val merged = mutableListOf<PrenatalMedication>()
                        for (remote in remoteList) {
                            val local = localMap.remove(remote.id)
                            if (local == null) {
                                merged.add(remote)
                            } else {
                                if (local.updatedAt > remote.updatedAt) {
                                    merged.add(local)
                                } else {
                                    merged.add(remote)
                                }
                            }
                        }
                        for ((_, localOnly) in localMap) {
                            merged.add(localOnly)
                        }
                        _medications.value = merged
                        localPreferences.saveMedicationsCache(merged)
                    }
                }
            }

            // Subir el estado unificado a la nube
            val jsonPayload = localPreferences.medicationsToJson(_medications.value)
            googleSheetsService.saveSystemRecord(
                webAppUrl = sheetUrl,
                systemId = GoogleSheetsService.SYS_MEDICATIONS,
                name = "Medicamentos Prenatales",
                jsonPayload = jsonPayload
            )
        }
    }
}
