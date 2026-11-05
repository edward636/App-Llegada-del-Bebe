package com.example.llegadadelbebe.core.notification

import android.app.NotificationManager
import android.content.Context
import com.example.llegadadelbebe.core.security.SecureConfig
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.data.remote.GoogleSheetsService
import com.example.llegadadelbebe.data.remote.SystemRecordData
import com.example.llegadadelbebe.domain.model.PrenatalMedication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class MedicationMergePlan(
    val merged: List<PrenatalMedication>,
    val allDeletedIds: Set<String>,
    val shouldUpload: Boolean,
    val removedLocalItems: List<PrenatalMedication>
)

data class MedicationSyncOutcome(
    val medications: List<PrenatalMedication>,
    val syncedOk: Boolean
)

/**
 * Coordinador centralizado y atómico (Mutex global de proceso) para sincronizar las vitaminas prenatales
 * entre el teléfono del Papá (Edward) y el de la Mamá (Britney) sobre Google Sheets.
 *
 * Previene:
 * 1. Condiciones de carrera entre la UI, ON_RESUME, el receptor cada 15 min y las acciones de notificación.
 * 2. Desbordamiento del límite de 50,000 caracteres por celda en Google Sheets al excluir fotos Base64.
 * 3. Sobrescritura accidental con "[]" ante errores de red o bloqueos temporales de Apps Script.
 * 4. Pérdida de vitaminas creadas previamente en el teléfono del Papá o resurrección de borrados (vía tombstones).
 */
object MedicationCloudSyncManager {

    private val syncMutex = Mutex()

    /**
     * Algoritmo puro y determinista de fusión bidireccional entre el caché local y el registro en la nube.
     */
    fun mergeMedicationsTwoPhone(
        localCached: List<PrenatalMedication>,
        remoteData: SystemRecordData,
        parsedRemote: List<PrenatalMedication>,
        localDeletedIds: Set<String>,
        pendingUploadIds: Set<String>,
        forceUpload: Boolean
    ): MedicationMergePlan {
        val allDeletedIds = localDeletedIds + remoteData.deletedIds
        var shouldUpload = forceUpload ||
            pendingUploadIds.isNotEmpty() ||
            (localDeletedIds - remoteData.deletedIds).isNotEmpty()

        if (parsedRemote.any { it.id in allDeletedIds }) {
            shouldUpload = true
        }

        val removedLocalItems = mutableListOf<PrenatalMedication>()
        for (item in localCached) {
            if (item.id in allDeletedIds) {
                removedLocalItems.add(item)
            }
        }

        val remoteActive = parsedRemote.filterNot { it.id in allDeletedIds }
        val localMap = localCached
            .filterNot { it.id in allDeletedIds }
            .associateBy { it.id }
            .toMutableMap()

        val merged = mutableListOf<PrenatalMedication>()

        for (remote in remoteActive) {
            val local = localMap.remove(remote.id)
            if (local == null) {
                merged.add(remote.copy(photoBase64 = ""))
            } else {
                if (local.updatedAt > remote.updatedAt) {
                    merged.add(local.copy(photoBase64 = ""))
                    shouldUpload = true
                } else {
                    merged.add(remote.copy(photoBase64 = ""))
                }
            }
        }

        // Evaluar elementos presentes solo en este teléfono y que NO han sido eliminados en ninguno de los dos
        for ((_, localOnly) in localMap) {
            val isUntouchedFactoryDefault = localOnly.id.startsWith("MED_DEFAULT_") &&
                localOnly.updatedAt <= 1000L &&
                localOnly.id !in pendingUploadIds

            if (isUntouchedFactoryDefault && remoteData.found) {
                // Descartar plantillas por defecto intactas cuando el registro remoto ya fue inicializado
                removedLocalItems.add(localOnly)
            } else {
                // Conservar vitaminas reales cargadas por Papá o Mamá y marcarlas para subida inmediata
                merged.add(localOnly.copy(photoBase64 = ""))
                shouldUpload = true
            }
        }

        if (!remoteData.found && merged.isNotEmpty()) {
            shouldUpload = true
        }

        return MedicationMergePlan(
            merged = merged,
            allDeletedIds = allDeletedIds,
            shouldUpload = shouldUpload,
            removedLocalItems = removedLocalItems
        )
    }

    /**
     * Registra localmente la toma de una dosis y retorna las pastillas restantes actualizadas.
     */
    fun recordDoseTakenLocally(
        context: Context,
        medId: String,
        confirmedBy: String? = null
    ): Int {
        val localPreferences = LocalPreferences(context)
        val todayStr = LocalDate.now().toString()
        val timeNow = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val user = confirmedBy ?: localPreferences.getActiveUser()

        val currentMeds = localPreferences.getMedicationsCache().toMutableList()
        val idx = currentMeds.indexOfFirst { it.id == medId }
        var pillsRemaining = 30

        if (idx != -1) {
            val old = currentMeds[idx]
            val updated = old.copy(
                lastTakenDate = todayStr,
                lastTakenTime = timeNow,
                lastConfirmedBy = user,
                pillsRemaining = (old.pillsRemaining - 1).coerceAtLeast(0),
                photoBase64 = "",
                updatedAt = System.currentTimeMillis()
            )
            pillsRemaining = updated.pillsRemaining
            currentMeds[idx] = updated
            localPreferences.saveMedicationsCache(currentMeds)
            localPreferences.addPendingUploadMedicationId(medId)
            localPreferences.resetMedicationAttemptCount(medId, todayStr)
        }
        return pillsRemaining
    }

    /**
     * Ejecuta una sincronización completa protegida por Mutex:
     * 1. Descarga estado remoto + tombstones.
     * 2. Fusiona cambios locales y remotos sin perder medicamentos reales.
     * 3. Notifica al Papá si Mamá confirmó una toma hoy y reconcilia todas las alarmas locales.
     * 4. Sube el JSON ligero (sin Base64) + tombstones cuando existen cambios pendientes.
     */
    suspend fun syncMedications(
        context: Context,
        sheetUrl: String = SecureConfig.getGoogleSheetUrl(),
        forceUpload: Boolean = false
    ): MedicationSyncOutcome = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val localPreferences = LocalPreferences(appContext)
        if (sheetUrl.isBlank()) {
            return@withContext MedicationSyncOutcome(
                medications = localPreferences.getMedicationsCache(),
                syncedOk = false
            )
        }

        syncMutex.withLock {
            val googleSheetsService = GoogleSheetsService()
            val freshCached = localPreferences.getMedicationsCache()
            val localDeletedIds = localPreferences.getDeletedMedicationIds()
            val pendingUploadIds = localPreferences.getPendingUploadMedicationIds()
            val activeUser = localPreferences.getActiveUser()
            val isDad = activeUser.contains("Papá", ignoreCase = true)
            val todayStr = LocalDate.now().toString()

            val remoteResult = googleSheetsService.fetchSystemRecordWithMeta(
                webAppUrl = sheetUrl,
                systemId = GoogleSheetsService.SYS_MEDICATIONS
            )

            if (remoteResult.isFailure) {
                // Ante fallo de red o bloqueo temporal en Apps Script, jamás sobrescribir la nube
                return@withLock MedicationSyncOutcome(
                    medications = freshCached,
                    syncedOk = false
                )
            }

            val remoteData = remoteResult.getOrThrow()
            if (remoteData.deletedIds.isNotEmpty()) {
                localPreferences.recordDeletedMedicationIds(remoteData.deletedIds)
            }

            val parsedRemote = if (!remoteData.payload.isNullOrBlank()) {
                localPreferences.parseMedicationsJson(remoteData.payload)
            } else {
                emptyList()
            }

            val previousSchedulesById = freshCached.associate { it.id to it.scheduleTimes }

            val plan = mergeMedicationsTwoPhone(
                localCached = freshCached,
                remoteData = remoteData,
                parsedRemote = parsedRemote,
                localDeletedIds = localDeletedIds,
                pendingUploadIds = pendingUploadIds,
                forceUpload = forceUpload
            )

            // Cancelar alarmas de vitaminas eliminadas o plantillas descartadas
            plan.removedLocalItems.forEach { removed ->
                removed.scheduleTimes.forEach { time ->
                    MedicationAlarmScheduler.cancelMedicationAlarm(appContext, removed.id, time)
                }
            }

            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            NotificationHelper.createNotificationChannels(appContext)

            // Reconciliar alarmas y avisos al Papá para cada vitamina activa
            plan.merged.forEach { med ->
                val oldTimes = previousSchedulesById[med.id].orEmpty()
                (oldTimes - med.scheduleTimes.toSet()).forEach { obsoleteTime ->
                    MedicationAlarmScheduler.cancelMedicationAlarm(appContext, med.id, obsoleteTime)
                }

                if (isDad &&
                    med.lastTakenDate == todayStr &&
                    med.lastConfirmedBy.contains("Mamá", ignoreCase = true)
                ) {
                    if (!localPreferences.isDadNotifiedForDose(med.id, med.lastTakenDate, med.lastTakenTime)) {
                        NotificationHelper.showDadMedicationNotification(
                            context = appContext,
                            medName = med.name,
                            dose = med.dose,
                            takenTime = med.lastTakenTime,
                            pillsRemaining = med.pillsRemaining
                        )
                        localPreferences.markDadNotifiedForDose(med.id, med.lastTakenDate, med.lastTakenTime)
                    }
                }

                if (med.isTakenToday) {
                    // Limpiar cualquier notificación o posposición pendiente de hoy y dejar lista la alarma de mañana
                    notificationManager?.cancel(med.id.hashCode())
                    notificationManager?.cancel((med.id + "_escalated").hashCode())
                    localPreferences.resetMedicationAttemptCount(med.id, todayStr)
                    MedicationAlarmScheduler.cancelSnoozeAlarm(appContext, med.id)
                    med.scheduleTimes.forEach { time ->
                        MedicationAlarmScheduler.scheduleNextDailyAlarm(
                            context = appContext,
                            medId = med.id,
                            medName = med.name,
                            dose = med.dose,
                            instructions = med.foodInstructions,
                            timeStr = time
                        )
                    }
                } else {
                    med.scheduleTimes.forEach { time ->
                        MedicationAlarmScheduler.scheduleMedicationAlarm(
                            context = appContext,
                            medId = med.id,
                            medName = med.name,
                            dose = med.dose,
                            instructions = med.foodInstructions,
                            timeStr = time
                        )
                    }
                }
            }

            localPreferences.saveMedicationsCache(plan.merged)

            var uploadSuccess = true
            if (plan.shouldUpload) {
                val activeMedications = plan.merged.filterNot { it.id in plan.allDeletedIds }
                val compactJsonPayload = localPreferences.medicationsToJson(
                    medications = activeMedications,
                    includePhotos = false
                )
                val saveResult = googleSheetsService.saveSystemRecord(
                    webAppUrl = sheetUrl,
                    systemId = GoogleSheetsService.SYS_MEDICATIONS,
                    name = "Medicamentos Prenatales",
                    jsonPayload = compactJsonPayload,
                    deletedIds = plan.allDeletedIds
                )
                uploadSuccess = saveResult.isSuccess && saveResult.getOrNull() == true
                if (uploadSuccess) {
                    localPreferences.clearPendingUploadMedicationIds()
                }
            }

            MedicationSyncOutcome(
                medications = plan.merged,
                syncedOk = uploadSuccess
            )
        }
    }
}
