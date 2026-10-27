package com.example.llegadadelbebe.data.remote

import com.example.llegadadelbebe.domain.model.FarmatodoProductBenchmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente HTTP para consultar productos, presentaciones y precios oficiales en tiempo real
 * en Farmatodo Venezuela (farmatodo.com.ve) a través de su motor de búsqueda público.
 */
class FarmatodoApiClient {

    companion object {
        private const val ALGOLIA_APP_ID = "VCOJEYD2PO"
        private const val ALGOLIA_API_KEY = "869a91e98550dd668b8b1dc04bca9011"
        private const val ALGOLIA_INDEX = "products-venezuela"
        private const val SEARCH_ENDPOINT =
            "https://$ALGOLIA_APP_ID-dsn.algolia.net/1/indexes/$ALGOLIA_INDEX/query"
    }

    suspend fun searchProducts(
        query: String,
        bcvRate: Double,
        limit: Int = 5
    ): List<FarmatodoProductBenchmark> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        var conn: HttpURLConnection? = null
        try {
            val endpointWithAuth = "$SEARCH_ENDPOINT?x-algolia-application-id=$ALGOLIA_APP_ID&x-algolia-api-key=$ALGOLIA_API_KEY"
            val url = URL(endpointWithAuth)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 7000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "LlegadaDelBebe-ShoppingRadar/1.3.1")
            }

            val encodedParams = "query=${URLEncoder.encode(trimmed, "UTF-8")}&hitsPerPage=$limit"
            val requestBody = JSONObject().apply {
                put("params", encodedParams)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val responseText = reader.use { it.readText() }
                val rootJson = JSONObject(responseText)
                val hitsArray = rootJson.optJSONArray("hits") ?: return@withContext emptyList()

                val results = mutableListOf<FarmatodoProductBenchmark>()
                for (i in 0 until hitsArray.length()) {
                    val hit = hitsArray.getJSONObject(i)
                    val id = hit.optString("id", hit.optString("objectID"))
                    val name = hit.optString("mediaDescription").ifBlank {
                        hit.optString("largeDescription", hit.optString("brand"))
                    }.trim()

                    if (name.isBlank()) continue

                    val brand = hit.optString("marca").ifBlank { hit.optString("brand") }.trim()
                    val fullPriceVes = hit.optDouble("fullPrice", 0.0)
                    val offerPriceVes = hit.optDouble("offerPrice", 0.0)

                    val rate = if (bcvRate > 0.0) bcvRate else 1.0
                    val priceUsd = fullPriceVes / rate
                    val offerPriceUsd = if (offerPriceVes > 0.0) offerPriceVes / rate else 0.0

                    val mediaImageUrl = hit.optString("mediaImageUrl").trim()
                    val urlSlug = hit.optString("url").trim()
                    val barcode = hit.optString("barcode").trim()

                    results.add(
                        FarmatodoProductBenchmark(
                            id = id,
                            name = name,
                            brand = brand,
                            fullPriceVes = fullPriceVes,
                            offerPriceVes = offerPriceVes,
                            priceUsd = priceUsd,
                            offerPriceUsd = offerPriceUsd,
                            mediaImageUrl = mediaImageUrl,
                            urlSlug = urlSlug,
                            barcode = barcode
                        )
                    )
                }
                results
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Búsqueda híbrida inteligente:
     * 1. Si se proporciona código de barras, busca primero por código exacto.
     * 2. Si no arroja resultados o no hay código, busca por palabras clave (marca + nombre de producto).
     */
    suspend fun lookupBenchmark(
        barcode: String?,
        productName: String,
        brand: String,
        bcvRate: Double
    ): FarmatodoProductBenchmark? = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode?.trim() ?: ""
        if (cleanBarcode.length >= 6) {
            val barcodeHits = searchProducts(cleanBarcode, bcvRate, limit = 2)
            if (barcodeHits.isNotEmpty()) {
                return@withContext barcodeHits.first()
            }
        }

        val keywords = listOf(brand, productName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ ]"), " ")
            .trim()

        if (keywords.isNotBlank()) {
            val textHits = searchProducts(keywords, bcvRate, limit = 3)
            if (textHits.isNotEmpty()) {
                return@withContext textHits.first()
            }
        }

        null
    }
}
