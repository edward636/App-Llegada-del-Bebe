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
import java.util.UUID

/**
 * Cliente de integración oficial para la API de Gemini 3.8 Flash (Google AI Studio).
 * Utiliza Structured Outputs (JSON Schema) para extracción multimodal precisa y auditoría.
 */
class GeminiClient(
    private val preferredModel: String = "gemini-3.8-flash"
) {

    // Lista de modelos con fallback automático en caso de saturación temporal (503/429)
    private val modelChain = listOf(
        preferredModel,
        "gemini-flash-lite-latest",
        "gemini-flash-latest"
    )

    private fun getUrlForModel(model: String) =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    /**
     * Extrae información estructurada de un producto de bebé a partir de una foto en Base64.
     * Captura nombre, marca, modelo, ingredientes/materiales, lugar de fabricación, vencimiento y consejo.
     */
    suspend fun extractProductFromImage(
        base64Image: String,
        mimeType: String = "image/jpeg",
        apiKey: String
    ): Result<BabyProduct> = withContext(Dispatchers.IO) {
        try {
            val promptText = """
                Eres un asistente experto en puericultura, pediatría y compras para la llegada de un bebé varón.
                Analiza con atención la foto del producto o empaque (pañales, ropitas, biberones, toallas húmedas, jabón, cremas, etc.).
                Extrae en formato JSON exacto las siguientes propiedades:
                1. "nombre": Nombre claro y descriptivo del producto.
                2. "marca": Marca visible (ej: Pampers, Carter's, Avent, Johnson's, Huggies).
                3. "modelo": Línea o modelo específico si aparece (ej: Premium Care, Natural Response, Soft Dreams).
                4. "categoria": Una de: CLOTHING, DIAPERS, WIPES_HYGIENE, FEEDING, CRIB_SLEEP, TRANSPORT, HEALTH, HOSPITAL_BAG.
                5. "talla_etapa": Talla o etapa para el bebé (ej: RN, 0-3M, 3-6M, Etapa 1, o vacío si no aplica).
                6. "cantidad": Cantidad de unidades o piezas contenidas (ej: 36 pañales -> 36, pack 3 bodies -> 3).
                7. "ingredientes_materiales": Ingredientes activos de cosméticos/higiene (ej: manzanilla, aloe vera, sin alcohol, hipoalergénico) o composición textil (ej: 100% Algodón Pima, silicón grado médico libre de BPA).
                8. "fabricado_en": País o lugar de fabricación si se lee en el empaque (ej: Hecho en Perú, Hecho en Colombia, USA, etc., o "No especificado").
                9. "fecha_vencimiento": Fecha de caducidad o lote de vencimiento si aplica (ej: 08/2027) o "N/A" si es ropa o artículo sin fecha de vencimiento.
                10. "opinion_consejo": Consejo pediátrico breve y afectuoso para Edward y Britney sobre el uso seguro de este producto con su niño.
            """.trimIndent()

            val requestBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", promptText))
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", mimeType)
                                    put("data", base64Image)
                                })
                            })
                        }
                        put("parts", parts)
                    })
                }
                put("contents", contents)

                // Forzar Structured Output en formato JSON
                put("generationConfig", JSONObject().apply {
                    put("response_mime_type", "application/json")
                    put("temperature", 0.2)
                })
            }

            val responseString = executePost(requestBody.toString(), apiKey)
            val jsonResponse = JSONObject(responseString)
            val textContent = jsonResponse
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val productJson = JSONObject(textContent)

            val categoryString = productJson.optString("categoria", productJson.optString("category", "CLOTHING"))
            val resolvedCategory = try {
                ProductCategory.valueOf(categoryString.uppercase())
            } catch (_: Exception) {
                ProductCategory.CLOTHING
            }

            val product = BabyProduct(
                id = UUID.randomUUID().toString(),
                name = productJson.optString("nombre", productJson.optString("name", "Producto de Bebé")),
                brand = productJson.optString("marca", productJson.optString("brand", "")),
                model = productJson.optString("modelo", productJson.optString("model", "")),
                category = resolvedCategory,
                stageSize = productJson.optString("talla_etapa", productJson.optString("stageSize", "")),
                quantityCurrent = productJson.optInt("cantidad", productJson.optInt("quantity", 1)),
                quantityTarget = productJson.optInt("cantidad_meta", 1),
                status = ProductStatus.PURCHASED,
                ingredients = productJson.optString("ingredientes_materiales", productJson.optString("ingredients", "")),
                manufacturedIn = productJson.optString("fabricado_en", productJson.optString("manufacturedIn", "")),
                expirationDate = productJson.optString("fecha_vencimiento", productJson.optString("expirationDate", "")),
                geminiOpinion = productJson.optString("opinion_consejo", productJson.optString("opinion", "")),
                notes = productJson.optString("detalles", "")
            )

            Result.success(product)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Consulta a Gemini 3.8 Flash para obtener consejos inteligentes y personalizados
     * sobre la auditoría de consumibles (pañales, jabón, toallitas, ropa).
     */
    suspend fun getConsumablesAuditAdvice(
        auditSummaryText: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Eres un asesor pediátrico y puericultor empático acompañando a unos padres en la espera de su bebé niño.
                Revisa el estado actual de su inventario de consumibles:
                $auditSummaryText
                
                Proporciona en un tono cariñoso, breve (máximo 3 párrafos concisos) y profesional:
                1. Qué están haciendo excelente.
                2. Qué consumible deben frenar para no acumular de más (ej. pañales RN).
                3. Qué consumible deben priorizar comprar en las próximas semanas.
            """.trimIndent()

            val requestBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        }
                        put("parts", parts)
                    })
                }
                put("contents", contents)
            }

            val responseString = executePost(requestBody.toString(), apiKey)
            val jsonResponse = JSONObject(responseString)
            val adviceText = jsonResponse
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            Result.success(adviceText.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executePost(jsonPayload: String, apiKey: String): String {
        var lastException: Exception? = null

        for (model in modelChain) {
            try {
                val url = URL("${getUrlForModel(model)}?key=$apiKey")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000
                    readTimeout = 18000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }

                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(jsonPayload)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: throw RuntimeException("HTTP Error $responseCode: ${connection.responseMessage}")
                }

                val response = BufferedReader(InputStreamReader(stream, "UTF-8")).use { reader ->
                    reader.readText()
                }

                if (responseCode in 200..299) {
                    return response
                } else if (responseCode == 503 || responseCode == 404) {
                    lastException = RuntimeException("Modelo $model temporalmente no disponible ($responseCode)")
                    continue
                } else {
                    throw RuntimeException("Gemini API Error ($responseCode): $response")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw lastException ?: RuntimeException("No se pudo conectar con ningún modelo de Gemini.")
    }
}
