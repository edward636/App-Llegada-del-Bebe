package com.example.llegadadelbebe.presentation.medications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.llegadadelbebe.core.notification.MedicationAlarmScheduler
import com.example.llegadadelbebe.core.notification.NotificationHelper
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.core.util.MedicationConflict
import com.example.llegadadelbebe.core.util.MedicationConflictHelper
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

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _activeUser = MutableStateFlow(localPreferences.getActiveUser())
    val activeUser: StateFlow<String> = _activeUser.asStateFlow()

    fun setActiveUser(user: String) {
        _activeUser.value = user
    }

    fun refreshMedications() {
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
        localPreferences.addPendingUploadMedicationId(id)
        syncWithCloud(forceUpload = true)
    }

    fun saveMedication(med: PrenatalMedication) {
        val stampedMed = med.copy(updatedAt = System.currentTimeMillis())
        val current = _medications.value.toMutableList()
        val index = current.indexOfFirst { it.id == stampedMed.id }
        if (index >= 0) {
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
                timeStr = time
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
        sheetUrl: String = com.example.llegadadelbebe.core.security.SecureConfig.getGoogleSheetUrl(),
        forceUpload: Boolean = false
    ) {
        if (sheetUrl.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isSyncing.value = true
            try {
                // Recargar caché primero por si se confirmó una dosis desde la notificación de Android
                val freshCached = localPreferences.getMedicationsCache()
                _medications.value = freshCached

                val deletedIds = localPreferences.getDeletedMedicationIds()
                val pendingUploadIds = localPreferences.getPendingUploadMedicationIds()
                val isDad = activeUser.value.contains("Papá", ignoreCase = true)
                val todayStr = LocalDate.now().toString()

                val remoteResult = googleSheetsService.fetchSystemRecord(sheetUrl, GoogleSheetsService.SYS_MEDICATIONS)
                var hasLocalChangesToUpload = pendingUploadIds.isNotEmpty() || forceUpload

                if (remoteResult.isSuccess) {
                    val remotePayload = remoteResult.getOrNull()
                    if (!remotePayload.isNullOrBlank()) {
                        val parsedRemote = localPreferences.parseMedicationsJson(remotePayload)
                        if (parsedRemote.any { it.id in deletedIds }) {
                            hasLocalChangesToUpload = true
                        }
                        val remoteList = parsedRemote.filterNot { it.id in deletedIds }
                        val localMap = freshCached.filterNot { it.id in deletedIds }.associateBy { it.id }.toMutableMap()
                        val merged = mutableListOf<PrenatalMedication>()

                        for (remote in remoteList) {
                            val local = localMap.remove(remote.id)
                            if (local == null) {
                                merged.add(remote)
                            } else {
                                if (local.updatedAt > remote.updatedAt) {
                                    merged.add(local)
                                    hasLocalChangesToUpload = true
                                } else {
                                    merged.add(remote)
                                }
                            }

                            // Si es el teléfono del Papá, avisarle proactivamente cuando Mamá se tomó la vitamina
                            if (isDad && remote.lastTakenDate == todayStr && remote.lastConfirmedBy.contains("Mamá", ignoreCase = true)) {
                                if (!localPreferences.isDadNotifiedForDose(remote.id, remote.lastTakenDate, remote.lastTakenTime)) {
                                    NotificationHelper.showDadMedicationNotification(
                                        context = getApplication(),
                                        medName = remote.name,
                                        dose = remote.dose,
                                        takenTime = remote.lastTakenTime,
                                        pillsRemaining = remote.pillsRemaining
                                    )
                                    localPreferences.markDadNotifiedForDose(remote.id, remote.lastTakenDate, remote.lastTakenTime)

                                    // Cancelar alarmas pendientes del papá para no recordarle innecesariamente
                                    remote.scheduleTimes.forEach { time ->
                                        MedicationAlarmScheduler.cancelMedicationAlarm(getApplication(), remote.id, time)
                                    }
                                }
                            }
                        }

                        // Solo conservar los creados offline en este teléfono que aún no se hayan subido
                        for ((_, localOnly) in localMap) {
                            if (localOnly.id in pendingUploadIds && localOnly.id !in deletedIds) {
                                merged.add(localOnly)
                                hasLocalChangesToUpload = true
                            } else {
                                // Eliminado remotamente por el otro teléfono: cancelar sus alarmas
                                localOnly.scheduleTimes.forEach { time ->
                                    MedicationAlarmScheduler.cancelMedicationAlarm(getApplication(), localOnly.id, time)
                                }
                            }
                        }

                        _medications.value = merged
                        localPreferences.saveMedicationsCache(merged)

                        // Reconciliar alarmas para todas las vitaminas activas
                        merged.forEach { med ->
                            // Si ya fue tomada hoy, no activar alarma
                            if (!med.isTakenToday) {
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
                    } else {
                        hasLocalChangesToUpload = true
                    }

                    // Subir estado consolidado a la nube solo cuando la lectura remota fue exitosa y hay cambios
                    if (hasLocalChangesToUpload) {
                        val activeMedications = _medications.value.filterNot { it.id in deletedIds }
                        val jsonPayload = localPreferences.medicationsToJson(activeMedications)
                        val saveResult = googleSheetsService.saveSystemRecord(
                            webAppUrl = sheetUrl,
                            systemId = GoogleSheetsService.SYS_MEDICATIONS,
                            name = "Medicamentos Prenatales",
                            jsonPayload = jsonPayload
                        )
                        if (saveResult.isSuccess && saveResult.getOrNull() == true) {
                            localPreferences.clearPendingUploadMedicationIds()
                        }
                    }
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
