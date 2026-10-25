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

/**
 * Cliente de sincronización para Google Sheets mediante Google Apps Script Web App.
 * Maneja la redirección 302 (POST -> GET en script.googleusercontent.com) propia de Apps Script
 * y empaqueta metadatos extendidos (foto miniatura, riesgos, ingredientes, vencimiento) de forma
 * retrocompatible con las columnas desplegadas.
 */
class GoogleSheetsService {

    companion object {
        private const val META_TAG = "||META_JSON:"
    }

    /**
     * Obtiene la lista completa de artículos desde Google Sheets.
     */
    suspend fun fetchInventory(webAppUrl: String): Result<List<BabyProduct>> = withContext(Dispatchers.IO) {
        try {
            val response = executeGetFollowingRedirects(webAppUrl)
            val jsonObject = JSONObject(response)
            val jsonArray = jsonObject.optJSONArray("data") ?: JSONArray()
            val products = mutableListOf<BabyProduct>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val statusStr = item.optString("status", "PURCHASED").uppercase().trim()
                // Ignorar filas eliminadas lógicamente o filas de prueba técnica
                if (statusStr == "DELETED" || item.optString("id") == "test-check-1") continue

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
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
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

    private fun packNotesWithMeta(product: BabyProduct): String {
        val meta = JSONObject().apply {
            put("ingredients", product.ingredients)
            put("risksAndWarnings", product.risksAndWarnings)
            put("safetyLevel", product.safetyLevel.name)
            put("manufacturedIn", product.manufacturedIn)
            put("expirationDate", product.expirationDate)
            put("packageCount", product.packageCount)
            put("unitsPerPackage", product.unitsPerPackage)
            put("barcode", product.barcode)
            put("countryVariant", product.countryVariant)
            put("usefulUntilAge", product.usefulUntilAge)
            // Guardar miniatura ultraligera (< 6,000 chars) para sincronización instantánea por red móvil
            if (product.photoBase64.isNotBlank() && product.photoBase64.length < 6000) {
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
                connectTimeout = 12000
                readTimeout = 15000
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
