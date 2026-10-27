package com.example.llegadadelbebe.data.remote

import android.content.Context
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.domain.model.BcvExchangeRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP ligero para obtener la Tasa Oficial del Banco Central de Venezuela (BCV).
 * Consulta la API pública oficial de Venezuela (dolarapi.com) con failover y caché persistente.
 */
class BcvExchangeRateClient(private val context: Context) {

    private val localPreferences = LocalPreferences(context)

    suspend fun fetchOfficialBcvRate(): BcvExchangeRate = withContext(Dispatchers.IO) {
        val primaryUrl = "https://ve.dolarapi.com/v1/dolares/oficial"
        val secondaryUrl = "https://ve.dolarapi.com/v1/cotizaciones/usd"

        // 1. Intentar endpoint primario
        val primaryResult = fetchFromEndpoint(primaryUrl)
        if (primaryResult != null) {
            localPreferences.saveLastBcvRate(primaryResult.rate, primaryResult.lastUpdated)
            return@withContext primaryResult
        }

        // 2. Intentar endpoint secundario de respaldo
        val secondaryResult = fetchFromEndpoint(secondaryUrl)
        if (secondaryResult != null) {
            localPreferences.saveLastBcvRate(secondaryResult.rate, secondaryResult.lastUpdated)
            return@withContext secondaryResult
        }

        // 3. Fallback: Usar último valor guardado en LocalPreferences
        val cachedRate = localPreferences.getLastBcvRate()
        val cachedDate = localPreferences.getLastBcvDate()
        BcvExchangeRate(
            rate = cachedRate,
            lastUpdated = cachedDate.ifBlank { "Modo sin conexión" },
            isFallback = true
        )
    }

    private fun fetchFromEndpoint(endpoint: String): BcvExchangeRate? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LlegadaDelBebe-App/1.3.1")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val responseText = reader.use { it.readText() }
                val json = JSONObject(responseText)
                val rate = json.optDouble("promedio", 0.0).takeIf { it > 0.0 }
                    ?: json.optDouble("precio", 0.0).takeIf { it > 0.0 }
                    ?: json.optDouble("rate", 0.0)

                val dateStr = json.optString("fechaActualizacion", "").take(10)

                if (rate > 0.0) {
                    BcvExchangeRate(rate = rate, lastUpdated = dateStr, isFallback = false)
                } else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
