package com.example.llegadadelbebe.presentation.medications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.llegadadelbebe.core.notification.MedicationAlarmScheduler
import com.example.llegadadelbebe.core.notification.MedicationCloudSyncManager
import com.example.llegadadelbebe.core.security.SecureConfig
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.core.util.MedicationConflict
import com.example.llegadadelbebe.core.util.MedicationConflictHelper
import com.example.llegadadelbebe.data.remote.GeminiClient
import com.example.llegadadelbebe.data.remote.ParsedMedicationInfo
import com.example.llegadadelbebe.domain.model.MedicationAdherenceSummary
import com.example.llegadadelbebe.domain.model.PrenatalMedication
import kotlinx.coroutines.Dispatchers
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

    private val _medications = MutableStateFlow<List<PrenatalMedication>>(emptyList())
    val medications: StateFlow<List<PrenatalMedication>> = _medications.asStateFlow()

    private val _isAnalyzingPhoto = MutableStateFlow(false)
    val isAnalyzingPhoto: StateFlow<Boolean> = _isAnalyzingPhoto.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _cloudSyncOk = MutableStateFlow(true)
    val cloudSyncOk: StateFlow<Boolean> = _cloudSyncOk.asStateFlow()

    private val _activeUser = MutableStateFlow(localPreferences.getActiveUser())
    val activeUser: StateFlow<String> = _activeUser.asStateFlow()

    fun setActiveUser(user: String) {
        _activeUser.value = user
    }

    fun refreshMedications() {
        _activeUser.value = localPreferences.getActiveUser()
        loadMedications()
        syncWithCloud()
    }

    val scheduleConflicts: StateFlow<List<MedicationConflict>> = _medications.map { list ->
        MedicationConflictHelper.evaluateAllConflicts(list)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

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
        cached.forEach { med ->
            med.scheduleTimes.forEach { time ->
                MedicationAlarmScheduler.scheduleMedicationAlarm(
                    context = getApplication(),
                    medId = med.id,
                    medName = med.name,
                    dose = med.dose,
                    instructions = med.foodInstructions,
                    timeStr = time,
                    forceTomorrow = med.isTakenToday
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
                    photoBase64 = "",
                    updatedAt = System.currentTimeMillis()
                )
            } else med
        }

        _medications.value = updatedList
        localPreferences.saveMedicationsCache(updatedList)
        localPreferences.addPendingUploadMedicationId(id)
        localPreferences.resetMedicationAttemptCount(id, todayStr)
        syncWithCloud(forceUpload = true)
    }

    fun saveMedication(med: PrenatalMedication) {
        // Excluir miniatura Base64 para mantener sincronización instantánea (< 2 KB) y respetar el límite de Google Sheets
        val stampedMed = med.copy(
            photoBase64 = "",
            updatedAt = System.currentTimeMillis()
        )
        val current = _medications.value.toMutableList()
        val index = current.indexOfFirst { it.id == stampedMed.id }
        if (index >= 0) {
            val previousTimes = current[index].scheduleTimes
            (previousTimes - stampedMed.scheduleTimes.toSet()).forEach { removedTime ->
                MedicationAlarmScheduler.cancelMedicationAlarm(getApplication(), stampedMed.id, removedTime)
            }
            current[index] = stampedMed
        } else {
            current.add(0, stampedMed)
        }

        _medications.value = current
        localPreferences.saveMedicationsCache(current)
        localPreferences.addPendingUploadMedicationId(stampedMed.id)
        syncWithCloud(forceUpload = true)

        // Programar alarmas para los horarios configurados
        stampedMed.scheduleTimes.forEach { time ->
            MedicationAlarmScheduler.scheduleMedicationAlarm(
                context = getApplication(),
                medId = stampedMed.id,
                medName = stampedMed.name,
                dose = stampedMed.dose,
                instructions = stampedMed.foodInstructions,
                timeStr = time,
                forceTomorrow = stampedMed.isTakenToday
            )
        }
    }

    fun deleteMedication(id: String) {
        localPreferences.recordDeletedMedicationId(id)
        localPreferences.removePendingUploadMedicationId(id)
        val med = _medications.value.firstOrNull { it.id == id }
        med?.scheduleTimes?.forEach { time ->
            MedicationAlarmScheduler.cancelMedicationAlarm(getApplication(), id, time)
        }
        val updated = _medications.value.filterNot { it.id == id }
        _medications.value = updated
        localPreferences.saveMedicationsCache(updated)
        syncWithCloud(forceUpload = true)
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

    fun syncWithCloud(
        sheetUrl: String = SecureConfig.getGoogleSheetUrl(),
        forceUpload: Boolean = false
    ) {
        if (sheetUrl.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            try {
                _activeUser.value = localPreferences.getActiveUser()
                val outcome = MedicationCloudSyncManager.syncMedications(
                    context = getApplication(),
                    sheetUrl = sheetUrl,
                    forceUpload = forceUpload
                )
                _medications.value = outcome.medications
                _cloudSyncOk.value = outcome.syncedOk
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
