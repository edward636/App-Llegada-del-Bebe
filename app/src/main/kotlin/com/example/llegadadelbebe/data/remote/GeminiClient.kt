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
import java.util.UUID

/**
 * Cliente de integración oficial para la familia Gemini 3.8 / 3.6 Flash (Google AI Studio).
 * Utiliza Structured Outputs (JSON) y cadena de alta disponibilidad automática (Failover)
 * para extracción multimodal de productos, ingredientes, riesgos y balance de inventario.
 */
class GeminiClient(
    private val preferredModel: String = "gemini-3.8-flash"
) {

    // Cadena de alta disponibilidad verificada en vivo ante picos de demanda (503/429)
    private val modelChain = listOf(
        preferredModel,
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-flash-lite-latest",
        "gemini-flash-latest"
    )

    private fun getUrlForModel(model: String) =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    /**
     * Extrae información estructurada completa de un producto de bebé a partir de una foto en Base64.
     * Captura nombre, marca, modelo, ingredientes, riesgos/alérgenos, nivel de seguridad,
     * lugar de fabricación, vencimiento y recomendación pediátrica.
     */
    suspend fun extractProductFromImage(
        base64Image: String,
        thumbnailBase64: String = "",
        mimeType: String = "image/jpeg",
        apiKey: String
    ): Result<BabyProduct> = withContext(Dispatchers.IO) {
        try {
            val promptText = """
                Eres un pediatra neonatólogo, dermatólogo infantil y experto en puericultura acompañando a Edward (Papá) y Britney (Mamá) en la llegada de su bebé varón.
                Analiza con máxima precisión la fotografía del producto o su etiqueta (pañales, cremas, jabones, toallitas, ropita, biberones, medicamentos, accesorios, etc.).
                Devuelve únicamente un objeto JSON válido con las siguientes propiedades exactas:
                1. "nombre": Nombre claro y descriptivo del producto.
                2. "marca": Marca visible (ej: Pampers, Carter's, Avent, Mustela, WaterWipes, Huggies).
                3. "modelo": Línea, modelo o presentación específica que lo hace único (ej: Premium Care Recién Nacido, Natural Response Anticólicos, Hydra Bebé).
                4. "categoria": Una de: CLOTHING, DIAPERS, WIPES_HYGIENE, FEEDING, CRIB_SLEEP, TRANSPORT, HEALTH, HOSPITAL_BAG.
                5. "talla_etapa": Talla, peso o etapa recomendada (ej: RN, 0-3M, 3-6M, Etapa 1, 0M+).
                6. "cantidad": Cantidad de unidades o piezas contenidas en el empaque (ej: 36 pañales -> 36, pack 5 bodies -> 5, 1 frasco -> 1).
                7. "ingredientes_materiales": Lista detallada de ingredientes activos/inactivos (en cosméticos, higiene o alimentos) o composición textil/material (ej: 100% Algodón Pima, polipropileno libre de BPA, óxido de zinc, agua purificada).
                8. "riesgos_advertencias": Análisis detallado de posibles riesgos, alérgenos cutáneos (fragancias, alcohol, parabenos, sulfatos, fenoxietanol), precauciones de seguridad (riesgo de asfixia, lavado previo, temperatura máxima) o indica "Sin riesgos detectados; fórmula hipoalergénica segura para recién nacidos".
                9. "nivel_seguridad": Uno de: "SAFE" (100% seguro/hipoalergénico para RN), "CAUTION" (usar con precaución, revisar fragancia o edad mínima), "WARNING" (contiene componentes irritantes o no recomendados para recién nacidos).
                10. "fabricado_en": País o lugar de fabricación si aparece en el empaque (ej: Perú, Brasil, Irlanda, EE.UU., Colombia, o "No especificado en cara visible").
                11. "fecha_vencimiento": Fecha de vencimiento o caducidad visible (ej: 12/2027) o "N/A" si no vence.
                12. "opinion_consejo": Explicación detallada y afectuosa para Edward y Britney sobre para qué sirve, cómo aprovecharlo mejor con su bebé y recomendaciones prácticas de uso.
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

                put("generationConfig", JSONObject().apply {
                    put("response_mime_type", "application/json")
                    put("temperature", 0.2)
                })
            }

            val responseString = executePost(requestBody.toString(), apiKey)
            val jsonResponse = JSONObject(responseString)
            val rawTextContent = jsonResponse
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val cleanJsonText = rawTextContent
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val productJson = JSONObject(cleanJsonText)

            val categoryString = productJson.optString("categoria", productJson.optString("category", "CLOTHING"))
            val resolvedCategory = runCatching {
                ProductCategory.valueOf(categoryString.uppercase().trim())
            }.getOrDefault(ProductCategory.CLOTHING)

            val safetyString = productJson.optString("nivel_seguridad", "SAFE")
            val resolvedSafety = runCatching {
                ProductSafetyLevel.valueOf(safetyString.uppercase().trim())
            }.getOrDefault(ProductSafetyLevel.SAFE)

            val product = BabyProduct(
                id = UUID.randomUUID().toString(),
                name = productJson.optString("nombre", productJson.optString("name", "Producto de Bebé")),
                brand = productJson.optString("marca", productJson.optString("brand", "")),
                model = productJson.optString("modelo", productJson.optString("model", "")),
                category = resolvedCategory,
                stageSize = productJson.optString("talla_etapa", productJson.optString("stageSize", "")),
                quantityCurrent = productJson.optInt("cantidad", productJson.optInt("quantity", 1)).coerceAtLeast(1),
                quantityTarget = productJson.optInt("cantidad_meta", productJson.optInt("cantidad", 1)).coerceAtLeast(1),
                status = ProductStatus.PURCHASED,
                ingredients = productJson.optString("ingredientes_materiales", productJson.optString("ingredients", "")),
                risksAndWarnings = productJson.optString("riesgos_advertencias", "Fórmula segura para uso neonatal según etiqueta."),
                safetyLevel = resolvedSafety,
                manufacturedIn = productJson.optString("fabricado_en", productJson.optString("manufacturedIn", "")),
                expirationDate = productJson.optString("fecha_vencimiento", productJson.optString("expirationDate", "N/A")),
                photoBase64 = thumbnailBase64,
                geminiOpinion = productJson.optString("opinion_consejo", productJson.optString("opinion", "")),
                notes = productJson.optString("detalles", "")
            )

            Result.success(product)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Consulta a Gemini para obtener un diagnóstico inteligente de "¿Qué Tenemos y Qué Nos Falta?"
     * basado en el inventario real de Edward y Britney.
     */
    suspend fun getConsumablesAuditAdvice(
        auditSummaryText: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Eres un asesor pediátrico y puericultor empático acompañando a Edward (Papá) y Britney (Mamá) en la espera de su bebé varón.
                Revisa el balance actual de lo que ya tienen en su inventario y lo que aún les hace falta:
                $auditSummaryText
                
                Proporciona en un tono cariñoso, claro y estructurado (máximo 3 párrafos breves):
                1. ✅ Lo que ya tienen excelente y bien cubierto.
                2. ⚠️ Qué artículos o tallas deben frenar de comprar para no acumular de más (ej. talla RN).
                3. 🛒 Lista priorizada de qué les hace falta comprar primero para estar 100% listos.
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

        val cleanKey = apiKey.trim()
        for (model in modelChain) {
            try {
                val url = URL("${getUrlForModel(model)}?key=$cleanKey")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("x-goog-api-key", cleanKey)
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
                connection.disconnect()

                if (responseCode in 200..299) {
                    return response
                } else if (responseCode in listOf(404, 429, 500, 502, 503, 504)) {
                    lastException = RuntimeException("Modelo $model con alta demanda ($responseCode)")
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
