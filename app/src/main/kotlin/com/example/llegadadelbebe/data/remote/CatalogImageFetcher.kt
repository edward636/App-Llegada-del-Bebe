package com.example.llegadadelbebe.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Servicio inteligente para búsqueda y obtención de imágenes de catálogo limpias (fondo blanco, estilo e-commerce)
 * desde internet para productos de bebé, sustituyendo la foto casera tomada con la cámara.
 */
object CatalogImageFetcher {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    /**
     * Busca en la web una imagen de catálogo limpia para el producto y devuelve su miniatura en Base64.
     * Si falla o no encuentra resultados, retorna null para usar la foto de la cámara como respaldo.
     */
    suspend fun fetchCleanCatalogThumbnail(
        name: String,
        brand: String = "",
        model: String = "",
        category: String = ""
    ): String? = withContext(Dispatchers.IO) {
        val query = buildCleanQuery(name, brand, model)
        if (query.isBlank()) return@withContext null

        val imageUrls = searchCatalogImageUrls(query, maxResults = 8)
        for (imgUrl in imageUrls) {
            val thumb = downloadAndProcessThumbnail(imgUrl, maxDimension = 300)
            if (thumb != null) {
                return@withContext thumb
            }
        }

        // Respaldo secundario: Si no encontró con Bing, intentar con Open Beauty Facts
        if (brand.isNotBlank() || name.isNotBlank()) {
            val obfUrl = searchOpenBeautyFacts(brand.ifBlank { name })
            if (obfUrl != null) {
                val thumb = downloadAndProcessThumbnail(obfUrl, maxDimension = 300)
                if (thumb != null) return@withContext thumb
            }
        }

        null
    }

    /**
     * Devuelve una lista de miniaturas en Base64 (hasta [limit]) para que el usuario pueda elegir
     * manualmente entre varias opciones de catálogo limpias.
     */
    suspend fun searchMultipleThumbnails(
        query: String,
        limit: Int = 4
    ): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        val urls = searchCatalogImageUrls(query, maxResults = 12)
        for (url in urls) {
            val thumb = downloadAndProcessThumbnail(url, maxDimension = 300)
            if (thumb != null) {
                results.add(thumb)
                if (results.size >= limit) break
            }
        }
        results
    }

    /**
     * Construye una consulta de búsqueda refinada orientada a encontrar fotos oficiales de catálogo con fondo blanco.
     */
    fun buildCleanQuery(name: String, brand: String, model: String): String {
        val tokens = mutableListOf<String>()
        val cleanBrand = brand.trim()
        val cleanName = name.trim()
        val cleanModel = model.trim()

        if (cleanBrand.isNotBlank()) {
            tokens.add(cleanBrand)
        }
        if (cleanName.isNotBlank() && !cleanName.contains(cleanBrand, ignoreCase = true)) {
            tokens.add(cleanName)
        } else if (cleanName.isNotBlank()) {
            tokens.add(cleanName)
        }
        if (cleanModel.isNotBlank() && !cleanName.contains(cleanModel, ignoreCase = true)) {
            tokens.add(cleanModel)
        }

        val raw = tokens.joinToString(" ").trim()
        return if (raw.isNotBlank()) "$raw fondo blanco" else ""
    }

    /**
     * Busca URLs directas de imágenes de catálogo en Bing Images.
     */
    fun searchCatalogImageUrls(query: String, maxResults: Int = 8): List<String> {
        val results = mutableListOf<String>()
        if (query.isBlank()) return results

        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.bing.com/images/search?q=$encoded&form=HDRSC2&first=1"
            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            }

            if (conn.responseCode in 200..299) {
                val html = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                conn.disconnect()

                val pattern = Pattern.compile("murl&quot;:&quot;(https?://[^&]+)&quot;")
                val matcher = pattern.matcher(html)
                while (matcher.find() && results.size < maxResults) {
                    val rawUrl = matcher.group(1) ?: continue
                    val cleanUrl = rawUrl.replace("&amp;", "&")
                    // Filtrar dominios problemáticos o extensiones no imagen
                    if (isValidImageUrl(cleanUrl) && !results.contains(cleanUrl)) {
                        results.add(cleanUrl)
                    }
                }
            } else {
                conn.disconnect()
            }
        } catch (_: Exception) {
            // Ignorar fallos de red en búsqueda
        }

        return results
    }

    /**
     * Busca en Open Beauty Facts como fuente alternativa de cosméticos y productos de cuidado para bebés.
     */
    fun searchOpenBeautyFacts(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://world.openbeautyfacts.org/cgi/search.pl?search_terms=$encoded&search_simple=1&action=process&json=1"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "BabyApp/1.0")
            }
            if (conn.responseCode == 200) {
                val jsonStr = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                conn.disconnect()
                val json = JSONObject(jsonStr)
                val products = json.optJSONArray("products")
                if (products != null && products.length() > 0) {
                    val first = products.getJSONObject(0)
                    val imgUrl = first.optString("image_front_small_url", first.optString("image_url", ""))
                    if (imgUrl.isNotBlank()) return imgUrl
                }
            } else {
                conn.disconnect()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Descarga la imagen desde [imageUrl], la redimensiona y devuelve la cadena Base64 JPEG.
     */
    fun downloadAndProcessThumbnail(imageUrl: String, maxDimension: Int = 300): String? {
        return try {
            val conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", USER_AGENT)
                instanceFollowRedirects = true
            }

            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }

            val input = conn.inputStream
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var totalRead = 0
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                bytes.write(buffer, 0, read)
                totalRead += read
                if (totalRead > 4 * 1024 * 1024) { // Límite de seguridad 4MB
                    break
                }
            }
            input.close()
            conn.disconnect()

            val rawBytes = bytes.toByteArray()
            if (rawBytes.isEmpty()) return null

            // Decodificar con BitmapFactory
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null

            // Escalar manteniendo proporción
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                val (newW, newH) = if (ratio > 1f) {
                    maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
                } else {
                    (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
                }
                Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            } else {
                bitmap
            }

            // Comprimir a JPEG 75%
            val outStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outStream)
            val thumbBytes = outStream.toByteArray()

            Base64.encodeToString(thumbBytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.startsWith("http://") || lower.startsWith("https://")) &&
                (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") || lower.contains(".webp") || lower.contains("image"))
    }
}
