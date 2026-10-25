package com.example.llegadadelbebe.data.remote

import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
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
 * Permite a Papá y Mamá compartir la base de datos en tiempo real de forma segura.
 */
class GoogleSheetsService {

    /**
     * Obtiene la lista completa de artículos desde Google Sheets.
     */
    suspend fun fetchInventory(webAppUrl: String): Result<List<BabyProduct>> = withContext(Dispatchers.IO) {
        try {
            val url = URL(webAppUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 15000
                instanceFollowRedirects = true
            }

            val response = readStream(connection)
            val jsonObject = JSONObject(response)
            val jsonArray = jsonObject.optJSONArray("data") ?: JSONArray()
            val products = mutableListOf<BabyProduct>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val categoryStr = item.optString("category", "CLOTHING")
                val category = try {
                    ProductCategory.valueOf(categoryStr.uppercase())
                } catch (_: Exception) {
                    ProductCategory.CLOTHING
                }

                val statusStr = item.optString("status", "PURCHASED")
                val status = try {
                    ProductStatus.valueOf(statusStr.uppercase())
                } catch (_: Exception) {
                    ProductStatus.PURCHASED
                }

                products.add(
                    BabyProduct(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        brand = item.optString("brand"),
                        model = item.optString("model"),
                        category = category,
                        stageSize = item.optString("stageSize"),
                        quantityCurrent = item.optInt("quantityCurrent", 1),
                        quantityTarget = item.optInt("quantityTarget", 1),
                        status = status,
                        notes = item.optString("notes"),
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
     * Sincroniza (guarda o actualiza) un producto en Google Sheets.
     */
    suspend fun saveProduct(webAppUrl: String, product: BabyProduct): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
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
                    put("notes", product.notes)
                    put("geminiOpinion", product.geminiOpinion)
                    put("registeredBy", product.registeredBy)
                    put("updatedAt", product.updatedAt)
                })
            }

            val url = URL(webAppUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 18000
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val response = readStream(connection)
            val jsonObject = JSONObject(response)
            val isSuccess = jsonObject.optString("status") == "success"

            Result.success(isSuccess)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
