package com.example.llegadadelbebe.data.remote

import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductSafetyLevel
import com.example.llegadadelbebe.domain.model.ProductStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class SystemRecordData(
    val found: Boolean,
    val payload: String?,
    val deletedIds: Set<String> = emptySet(),
    val updatedAt: Long = 0L
)

/**
 * Cliente de sincronización para Google Sheets mediante Google Apps Script Web App.
 * Maneja la redirección 302 (POST -> GET en script.googleusercontent.com) propia de Apps Script
 * y empaqueta metadatos extendidos (foto miniatura, riesgos, ingredientes, vencimiento) de forma
 * retrocompatible con las columnas desplegadas.
 */
class GoogleSheetsService {

    companion object {
        private const val META_TAG = "||META_JSON:"
        const val SYS_MEDICATIONS = "SYS_PRENATAL_MEDICATIONS"
        const val SYS_RADAR_QUOTES = "SYS_RADAR_QUOTES"
        const val SYS_KNOWN_STORES = "SYS_KNOWN_STORES"
        const val SYS_HOSPITAL_BAG = "SYS_HOSPITAL_BAG"

        // Caché compartida en memoria de registros SYS_* extraídos durante fetchInventory o getRecord
        private val cachedSystemRecords = java.util.concurrent.ConcurrentHashMap<String, SystemRecordData>()
    }

    /**
     * Obtiene la lista completa de artículos desde Google Sheets y aprovecha la misma respuesta
     * para hidratar en memoria los registros de sistema (SYS_PRENATAL_MEDICATIONS, SYS_RADAR_QUOTES, etc.).
     */
    suspend fun fetchInventory(webAppUrl: String): Result<List<BabyProduct>> = withContext(Dispatchers.IO) {
        try {
            val response = executeGetFollowingRedirects(webAppUrl)
            val jsonObject = JSONObject(response)
            if (jsonObject.optString("status") != "success") {
                val errMsg = jsonObject.optString("message", "Respuesta inválida de Google Sheets")
                return@withContext Result.failure(RuntimeException(errMsg))
            }
            val jsonArray = jsonObject.optJSONArray("data") ?: JSONArray()
            val products = mutableListOf<BabyProduct>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val statusStr = item.optString("status", "PURCHASED").uppercase().trim()
                val itemId = item.optString("id")

                if (itemId.startsWith("SYS_") && statusStr != "DELETED") {
                    val rawSysNotes = item.optString("notes", "")
                    val (_, sysMeta) = unpackNotesAndMeta(rawSysNotes)
                    val sysPayload = sysMeta?.optString("payload")?.takeIf { it.isNotBlank() }
                    val sysDeletedIds = extractDeletedIds(sysMeta)
                    val sysUpdatedAt = item.optLong("updatedAt", 0L)
                    cachedSystemRecords[itemId] = SystemRecordData(
                        found = true,
                        payload = sysPayload,
                        deletedIds = sysDeletedIds,
                        updatedAt = sysUpdatedAt
                    )
                    continue
                }

                // Ignorar filas eliminadas lógicamente o filas de prueba técnica
                if (statusStr == "DELETED" || itemId == "test-check-1" || itemId.startsWith("SYS_")) continue

                val categoryStr = item.optString("category", "CLOTHING")
                val category = runCatching {
                    ProductCategory.valueOf(categoryStr.uppercase().trim())
                }.getOrDefault(ProductCategory.CLOTHING)

                val status = runCatching {
                    ProductStatus.valueOf(statusStr)
                }.getOrDefault(ProductStatus.PURCHASED)

                val rawNotes = item.optString("notes", "")
                val (cleanNotes, metaJson) = unpackNotesAndMeta(rawNotes)

                val ingredients = item.optString("ingredients").ifBlank {
                    metaJson?.optString("ingredients", "") ?: ""
                }
                val risksAndWarnings = item.optString("risksAndWarnings").ifBlank {
                    metaJson?.optString("risksAndWarnings", "") ?: ""
                }
                val safetyStr = item.optString("safetyLevel").ifBlank {
                    metaJson?.optString("safetyLevel", "SAFE") ?: "SAFE"
                }
                val safetyLevel = runCatching {
                    ProductSafetyLevel.valueOf(safetyStr.uppercase().trim())
                }.getOrDefault(ProductSafetyLevel.SAFE)

                val manufacturedIn = item.optString("manufacturedIn").ifBlank {
                    metaJson?.optString("manufacturedIn", "") ?: ""
                }
                val expirationDate = item.optString("expirationDate").ifBlank {
                    metaJson?.optString("expirationDate", "") ?: ""
                }
                val photoBase64 = item.optString("photoBase64").ifBlank {
                    metaJson?.optString("photoBase64", "") ?: ""
                }
                val barcode = metaJson?.optString("barcode", "") ?: ""
                val countryVariant = metaJson?.optString("countryVariant", "") ?: ""
                val usefulUntilAge = metaJson?.optString("usefulUntilAge", "") ?: ""
                val packageCount = metaJson?.optInt("packageCount", 1) ?: 1
                val unitsPerPackage = metaJson?.optInt("unitsPerPackage", 1) ?: 1
                val referencePriceUsd = if (metaJson != null && metaJson.has("referencePriceUsd") && !metaJson.isNull("referencePriceUsd")) {
                    metaJson.optDouble("referencePriceUsd", 0.0).takeIf { it > 0.0 }
                } else null
                val referencePriceSource = metaJson?.optString("referencePriceSource", "") ?: ""

                products.add(
                    BabyProduct(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        brand = item.optString("brand"),
                        model = item.optString("model"),
                        category = category,
                        stageSize = item.optString("stageSize"),
                        packageCount = packageCount,
                        unitsPerPackage = unitsPerPackage,
                        quantityCurrent = item.optInt("quantityCurrent", packageCount * unitsPerPackage),
                        quantityTarget = item.optInt("quantityTarget", 1),
                        status = status,
                        ingredients = ingredients,
                        risksAndWarnings = risksAndWarnings,
                        safetyLevel = safetyLevel,
                        manufacturedIn = manufacturedIn,
                        expirationDate = expirationDate,
                        photoBase64 = photoBase64,
                        barcode = barcode,
                        countryVariant = countryVariant,
                        usefulUntilAge = usefulUntilAge,
                        notes = cleanNotes,
                        geminiOpinion = item.optString("geminiOpinion"),
                        registeredBy = item.optString("registeredBy"),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                        referencePriceUsd = referencePriceUsd,
                        referencePriceSource = referencePriceSource
                    )
                )
            }

            Result.success(products)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sincroniza (guarda o actualiza) un producto en Google Sheets manejando la redirección 302 de Apps Script.
     */
    suspend fun saveProduct(webAppUrl: String, product: BabyProduct): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val packedNotes = packNotesWithMeta(product)

            val payload = JSONObject().apply {
                put("action", "saveItem")
                put("item", JSONObject().apply {
                    put("id", product.id)
                    put("name", product.name)
                    put("brand", product.brand)
                    put("model", product.model)
                    put("category", product.category.name)
                    put("stageSize", product.stageSize)
                    put("quantityCurrent", product.quantityCurrent)
                    put("quantityTarget", product.quantityTarget)
                    put("status", product.status.name)
                    put("ingredients", product.ingredients)
                    put("risksAndWarnings", product.risksAndWarnings)
                    put("safetyLevel", product.safetyLevel.name)
                    put("manufacturedIn", product.manufacturedIn)
                    put("expirationDate", product.expirationDate)
                    put("notes", packedNotes)
                    put("geminiOpinion", product.geminiOpinion)
                    put("registeredBy", product.registeredBy)
                    put("updatedAt", product.updatedAt)
                })
            }

            val response = executePostToAppsScript(webAppUrl, payload.toString())
            val jsonObject = JSONObject(response)
            val isSuccess = jsonObject.optString("status") == "success"

            Result.success(isSuccess)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Marca un producto como eliminado en Google Sheets para que desaparezca en ambos teléfonos.
     */
    suspend fun deleteProductRemote(webAppUrl: String, product: BabyProduct): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("action", "saveItem")
                put("item", JSONObject().apply {
                    put("id", product.id)
                    put("name", product.name)
                    put("status", "DELETED")
                    put("updatedAt", System.currentTimeMillis())
                })
            }
            val response = executePostToAppsScript(webAppUrl, payload.toString())
            val jsonObject = JSONObject(response)
            Result.success(jsonObject.optString("status") == "success")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Recupera un registro de sistema junto con sus metadatos de sincronización (tombstones de borrado y timestamp)
     * desde Google Sheets para sincronizar entre Edward y Britney sin condiciones de carrera.
     * Utiliza la consulta rápida específica O(1) de Apps Script v2 (?action=getRecord&id=X), evitando
     * descargas innecesarias de toda la hoja y distinguiendo errores de bloqueo frente a registros vacíos.
     */
    suspend fun fetchSystemRecordWithMeta(webAppUrl: String, systemId: String): Result<SystemRecordData> = withContext(Dispatchers.IO) {
        try {
            // 1. Intento rápido mediante endpoint específico getRecord O(1)
            var fastEndpointHandledError: Exception? = null
            try {
                val separator = if (webAppUrl.contains("?")) "&" else "?"
                val fastUrl = "$webAppUrl${separator}action=getRecord&id=$systemId"
                val fastResponse = executeGetFollowingRedirects(fastUrl)
                val fastJson = JSONObject(fastResponse)
                val fastStatus = fastJson.optString("status")
                if (fastStatus == "error") {
                    val errMsg = fastJson.optString("message", "Error temporal en Google Sheets")
                    fastEndpointHandledError = RuntimeException(errMsg)
                } else if (fastStatus == "success" && fastJson.has("found")) {
                    if (!fastJson.optBoolean("found", false)) {
                        val notFoundData = SystemRecordData(found = false, payload = null)
                        return@withContext Result.success(cachedSystemRecords[systemId] ?: notFoundData)
                    }
                    val item = fastJson.optJSONObject("item")
                    val rawNotes = item?.optString("notes", "") ?: ""
                    val (_, metaJson) = unpackNotesAndMeta(rawNotes)
                    val payload = metaJson?.optString("payload")?.takeIf { it.isNotBlank() }
                    val deletedIds = extractDeletedIds(metaJson)
                    val updatedAt = item?.optLong("updatedAt", 0L) ?: 0L
                    val recordData = SystemRecordData(
                        found = true,
                        payload = payload,
                        deletedIds = deletedIds,
                        updatedAt = updatedAt
                    )
                    cachedSystemRecords[systemId] = recordData
                    return@withContext Result.success(recordData)
                }
            } catch (_: Exception) {
                // Si falla la conexión puntual o el script es v1 sin getRecord, usar caché reciente o fallback
            }

            // Si ya tenemos el registro fresco extraído por fetchInventory, devolverlo de inmediato sin repetir descarga pesada
            cachedSystemRecords[systemId]?.let { cached ->
                return@withContext Result.success(cached)
            }

            if (fastEndpointHandledError != null) {
                return@withContext Result.failure(fastEndpointHandledError)
            }

            // 2. Fallback de barrido general en la hoja (solo si no estaba en caché ni respondió getRecord)
            val response = executeGetFollowingRedirects(webAppUrl)
            val jsonObject = JSONObject(response)
            if (jsonObject.optString("status") != "success") {
                val errMsg = jsonObject.optString("message", "Respuesta inválida de Google Sheets")
                return@withContext Result.failure(RuntimeException(errMsg))
            }
            val jsonArray = jsonObject.optJSONArray("data") ?: JSONArray()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val rowId = item.optString("id")
                if (rowId.startsWith("SYS_")) {
                    val rawNotes = item.optString("notes", "")
                    val (_, metaJson) = unpackNotesAndMeta(rawNotes)
                    val payload = metaJson?.optString("payload")?.takeIf { it.isNotBlank() }
                    val deletedIds = extractDeletedIds(metaJson)
                    val updatedAt = item.optLong("updatedAt", 0L)
                    cachedSystemRecords[rowId] = SystemRecordData(
                        found = true,
                        payload = payload,
                        deletedIds = deletedIds,
                        updatedAt = updatedAt
                    )
                }
            }
            Result.success(cachedSystemRecords[systemId] ?: SystemRecordData(found = false, payload = null))
        } catch (e: Exception) {
            cachedSystemRecords[systemId]?.let { return@withContext Result.success(it) }
            Result.failure(e)
        }
    }

    /**
     * Recupera el payload JSON de un registro de sistema (compatibilidad directa con llamadas simples).
     */
    suspend fun fetchSystemRecord(webAppUrl: String, systemId: String): Result<String?> =
        fetchSystemRecordWithMeta(webAppUrl, systemId).map { it.payload }

    /**
     * Guarda o actualiza un registro de sistema en Google Sheets protegiendo el límite de 50,000 caracteres por celda.
     */
    suspend fun saveSystemRecord(
        webAppUrl: String,
        systemId: String,
        name: String,
        jsonPayload: String,
        deletedIds: Set<String> = emptySet()
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val buildPackedNotes: (String) -> String = { candidatePayload ->
                val meta = JSONObject().apply {
                    put("payload", candidatePayload)
                    if (deletedIds.isNotEmpty()) {
                        val delArr = JSONArray()
                        deletedIds.take(300).forEach { delArr.put(it) }
                        put("deletedIds", delArr)
                    }
                }
                "System Record$META_TAG$meta"
            }

            var packedNotes = buildPackedNotes(jsonPayload)
            // Google Sheets impone un máximo estricto de 50,000 caracteres por celda.
            // Si el payload escapado se acerca al límite, limpiamos miniaturas Base64 embebidas para garantizar la escritura.
            if (packedNotes.length > 45000) {
                val compactPayload = stripEmbeddedPhotosFromJsonArray(jsonPayload)
                packedNotes = buildPackedNotes(compactPayload)
            }

            val nowMillis = System.currentTimeMillis()
            val payload = JSONObject().apply {
                put("action", "saveItem")
                put("item", JSONObject().apply {
                    put("id", systemId)
                    put("name", name)
                    put("category", "SYSTEM")
                    put("status", "ACTIVE")
                    put("notes", packedNotes)
                    put("updatedAt", nowMillis)
                })
            }
            val response = executePostToAppsScript(webAppUrl, payload.toString())
            val jsonObject = JSONObject(response)
            val ok = jsonObject.optString("status") == "success"
            if (ok) {
                val (_, savedMeta) = unpackNotesAndMeta(packedNotes)
                cachedSystemRecords[systemId] = SystemRecordData(
                    found = true,
                    payload = savedMeta?.optString("payload"),
                    deletedIds = deletedIds,
                    updatedAt = nowMillis
                )
            }
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractDeletedIds(metaJson: JSONObject?): Set<String> {
        val arr = metaJson?.optJSONArray("deletedIds") ?: return emptySet()
        val result = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val id = arr.optString(i)
            if (id.isNotBlank()) result.add(id)
        }
        return result
    }

    private fun stripEmbeddedPhotosFromJsonArray(rawJsonArray: String): String {
        return try {
            val arr = JSONArray(rawJsonArray)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.has("photoBase64")) obj.put("photoBase64", "")
                if (obj.has("rawPhotoBase64")) obj.put("rawPhotoBase64", "")
            }
            arr.toString()
        } catch (_: Exception) {
            rawJsonArray
        }
    }

    private fun packNotesWithMeta(product: BabyProduct): String {
        val meta = JSONObject().apply {
            put("packageCount", product.packageCount)
            put("unitsPerPackage", product.unitsPerPackage)
            put("barcode", product.barcode)
            put("countryVariant", product.countryVariant)
            put("usefulUntilAge", product.usefulUntilAge)
            product.referencePriceUsd?.let { put("referencePriceUsd", it) }
            if (product.referencePriceSource.isNotBlank()) {
                put("referencePriceSource", product.referencePriceSource)
            }
            // Guardar miniatura (< 25,000 chars) para sincronización entre ambos teléfonos
            if (product.photoBase64.isNotBlank() && product.photoBase64.length < 25000) {
                put("photoBase64", product.photoBase64)
            }
        }
        return "${product.notes}$META_TAG$meta"
    }

    private fun unpackNotesAndMeta(rawNotes: String): Pair<String, JSONObject?> {
        val idx = rawNotes.indexOf(META_TAG)
        if (idx < 0) return rawNotes to null
        val cleanNotes = rawNotes.substring(0, idx)
        val jsonStr = rawNotes.substring(idx + META_TAG.length)
        val metaObj = runCatching { JSONObject(jsonStr) }.getOrNull()
        return cleanNotes to metaObj
    }

    /**
     * Envía POST a Google Apps Script con instanceFollowRedirects = false.
     * Cuando Google responde 302 Found con Location -> script.googleusercontent.com,
     * realiza un GET a esa URL para obtener el JSON de respuesta sin error HTTP 411/302.
     */
    private fun executePostToAppsScript(webAppUrl: String, jsonPayload: String): String {
        val url = URL(webAppUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 20000
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
            writer.write(jsonPayload)
            writer.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode in 301..308) {
            val redirectLocation = connection.getHeaderField("Location")
            connection.disconnect()
            if (!redirectLocation.isNullOrBlank()) {
                return executeGetFollowingRedirects(redirectLocation)
            }
            return """{"status":"success"}"""
        }

        val response = readStream(connection)
        connection.disconnect()
        return response
    }

    private fun executeGetFollowingRedirects(targetUrl: String): String {
        var currentUrl = targetUrl
        var redirects = 0
        while (redirects < 6) {
            val u = URL(currentUrl)
            val conn = (u.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20000
                readTimeout = 25000
                instanceFollowRedirects = false
            }
            val code = conn.responseCode
            if (code in 301..308) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (!loc.isNullOrBlank()) {
                    currentUrl = URL(u, loc).toString()
                    redirects++
                    continue
                }
            }
            val body = readStream(conn)
            conn.disconnect()
            return body
        }
        throw RuntimeException("Demasiadas redirecciones en Google Apps Script")
    }

    private fun readStream(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: throw RuntimeException("HTTP ${connection.responseCode}")
        }
        return BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }
    }
}
