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
     * lugar de fabricación, vencimiento, paquetes, unidades, código de barras y compatibilidad para Venezuela.
     */
    suspend fun extractProductFromImage(
        base64Image: String,
        thumbnailBase64: String = "",
        mimeType: String = "image/jpeg",
        apiKey: String
    ): Result<BabyProduct> = withContext(Dispatchers.IO) {
        try {
            val promptText = """
                Eres un pediatra neonatólogo, dermatólogo infantil y asesor de compras para bebés acompañando a Edward (Papá) y Britney (Mamá) en Venezuela ante la llegada de su bebé varón.
                Analiza con máxima precisión la fotografía del producto o su empaque (pañales, ropita, toallitas, champús, colonias, cortauñas, termómetro, cremas, teteros/biberones, medicinas, etc.).

                Pautas específicas:
                - En PAÑALES: En Venezuela se usan las tallas RN (Recién Nacido / hasta 4.5kg), P (Pequeño / 4-6kg), M (Mediano / 6-9.5kg), G (Grande / 9-12.5kg), XG, XXG. Si el paquete indica Etapa 1 asócialo a Talla P, Etapa 2 a Talla M, Etapa 3 a Talla G.
                - En CONTEO: Identifica si viene por paquete (ej: 1 paquete de 24 pañales -> paquetes: 1, unidades_por_paquete: 24, unidades_totales: 24; toallitas de 80 unidades -> paquetes: 1, unidades_por_paquete: 80).
                - En ROPITA: Identifica talla (RN, 0-3M, 3-6M, etc.), tipo de tela (ej: 100% Algodón Pima) y analiza "edad_util": hasta qué edad en meses o peso estimado puede usarla el bebé antes de que le quede pequeña.
                - En HIGIENE/SALUD:
                  * Colonias: Revisa si contiene alcohol (Alcohol Denat). Si tiene alcohol, marca CAUTION/WARNING (nunca aplicar directo a piel de RN). Si es base agua sin alcohol, marca SAFE.
                  * Champús/Jabones: Verifica si es libre de lágrimas (No Tears), libre de sulfatos/parabenos y pH neutro.
                  * Cortauñas/Tijeras: Indica si tiene puntas romas/redondeadas de seguridad para RN.
                  * Termómetro: Indica calibración y rango normal neonatal (36.5°C a 37.5°C axilar).
                - En CÓDIGO DE BARRA Y VARIANTE: Si se aprecian los números bajo el código de barra (EAN-13 / UPC), extráelos. Indica si es variante para Venezuela (prefijo 759, texto en español venezolano, registro MPPS/SACS) o importado.
                - INFORMACIÓN INCOMPLETA: Si es un cosmético, crema, champú o alimento y NO se aprecian los ingredientes o fecha de vencimiento en esta cara, indica "informacion_incompleta": true y explica qué cara falta fotografiar.

                Devuelve únicamente un objeto JSON válido con las siguientes propiedades exactas:
                1. "nombre": Nombre claro y descriptivo del producto.
                2. "marca": Marca visible (ej: Pampers, Huggies, Carter's, Avent, Mustela, WaterWipes, Chicco, Johnson's).
                3. "modelo": Línea o presentación específica que lo hace único (ej: Natural Care Puro y Natural, Hydra Bebé, Natural Response).
                4. "categoria": Una de: CLOTHING, DIAPERS, WIPES_HYGIENE, FEEDING, CRIB_SLEEP, TRANSPORT, HEALTH, HOSPITAL_BAG.
                5. "talla_etapa": Talla precisa (ej: RN, P, M, G, XG para pañales; RN, 0-3M, 3-6M para ropita).
                6. "paquetes": Número de paquetes (entero, ej: 1).
                7. "unidades_por_paquete": Cantidad de unidades dentro de cada paquete (entero, ej: 24 pañales, 80 toallitas, 1 frasco).
                8. "unidades_totales": Total de unidades = paquetes * unidades_por_paquete (entero).
                9. "ingredientes_materiales": Lista detallada de ingredientes químicos/activos o composición textil (ej: 100% Algodón Pima, óxido de zinc 40%, libre de BPA).
                10. "riesgos_advertencias": Análisis de riesgos, fragancias, alcohol, parabenos, asfixia o "Sin riesgos detectados; fórmula hipoalergénica segura para recién nacidos".
                11. "nivel_seguridad": "SAFE", "CAUTION" o "WARNING".
                12. "fabricado_en": País de fabricación visible o "No especificado en cara visible".
                13. "fecha_vencimiento": Fecha de vencimiento visible (ej: 12/2027) o "N/A" si no vence.
                14. "codigo_barra": Dígitos del código de barra si son legibles, o "".
                15. "variante_pais": Si es específico para Venezuela o importado (ej: "Variante Venezuela 🇻🇪", "Internacional").
                16. "edad_util": Para ropita o accesorios, hasta qué edad/peso es útil (ej: "Útil hasta los 3 meses (~6 kg)").
                17. "informacion_incompleta": true o false.
                18. "que_falta": Explicación si falta foto de reverso/ingredientes, o "".
                19. "opinion_consejo": Consejo cariñoso y clínico para Edward y Britney sobre su uso óptimo con su bebé.
                20. "busqueda_catalogo": Término optimizado para buscar la foto limpia de catálogo en internet (ej: "Pampers Natural Care Talla P").
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

            val parsedPacks = productJson.optInt("paquetes", 1).coerceAtLeast(1)
            val parsedUnitsPerPack = productJson.optInt("unidades_por_paquete", productJson.optInt("cantidad", 1)).coerceAtLeast(1)
            val parsedTotal = productJson.optInt("unidades_totales", parsedPacks * parsedUnitsPerPack).coerceAtLeast(1)

            val isInfoMissing = productJson.optBoolean("informacion_incompleta", false)
            val missingPrompt = if (isInfoMissing) {
                productJson.optString("que_falta", "No se observan los ingredientes o vencimiento en esta cara. Se sugiere fotografiar el reverso.")
            } else ""

            val product = BabyProduct(
                id = UUID.randomUUID().toString(),
                name = productJson.optString("nombre", productJson.optString("name", "Producto de Bebé")),
                brand = productJson.optString("marca", productJson.optString("brand", "")),
                model = productJson.optString("modelo", productJson.optString("model", "")),
                category = resolvedCategory,
                stageSize = productJson.optString("talla_etapa", productJson.optString("stageSize", "")),
                packageCount = parsedPacks,
                unitsPerPackage = parsedUnitsPerPack,
                quantityCurrent = parsedTotal,
                quantityTarget = productJson.optInt("cantidad_meta", parsedTotal).coerceAtLeast(1),
                status = ProductStatus.PURCHASED,
                ingredients = productJson.optString("ingredientes_materiales", productJson.optString("ingredients", "")),
                risksAndWarnings = productJson.optString("riesgos_advertencias", "Fórmula segura para uso neonatal según etiqueta."),
                safetyLevel = resolvedSafety,
                manufacturedIn = productJson.optString("fabricado_en", productJson.optString("manufacturedIn", "")),
                expirationDate = productJson.optString("fecha_vencimiento", productJson.optString("expirationDate", "N/A")),
                photoBase64 = thumbnailBase64,
                rawPhotoBase64 = thumbnailBase64,
                barcode = productJson.optString("codigo_barra", ""),
                countryVariant = productJson.optString("variante_pais", ""),
                usefulUntilAge = productJson.optString("edad_util", ""),
                missingInfoPrompt = missingPrompt,
                geminiOpinion = productJson.optString("opinion_consejo", productJson.optString("opinion", "")),
                notes = productJson.optString("busqueda_catalogo", productJson.optString("detalles", ""))
            )

            Result.success(product)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Analiza una fotografía complementaria (reverso, tabla de ingredientes o código de barra)
     * y fusiona los nuevos datos en el producto existente sin perder la foto de catálogo ni el nombre.
     */
    suspend fun extractAdditionalDetailsFromImage(
        base64Image: String,
        existingProduct: BabyProduct,
        apiKey: String
    ): Result<BabyProduct> = withContext(Dispatchers.IO) {
        try {
            val promptText = """
                Eres un pediatra neonatólogo y químico cosmético analizando la fotografía del REVERSO, LATERAL o TABLA DE INGREDIENTES del producto de bebé: "${existingProduct.brand} ${existingProduct.name} ${existingProduct.model}".
                Extrae con máxima precisión los datos complementarios que estaban ausentes o incompletos:
                Devuelve únicamente un objeto JSON con las siguientes claves:
                1. "ingredientes_materiales": Lista detallada de ingredientes activos/inactivos o composición textil visible.
                2. "fecha_vencimiento": Fecha de vencimiento o caducidad visible (ej: 05/2027) o "N/A" si no vence.
                3. "codigo_barra": Números del código de barras si son visibles en esta toma.
                4. "fabricado_en": País de origen, laboratorio o distribuidor.
                5. "riesgos_advertencias": Análisis de riesgos, fragancias, alcohol, parabenos, o confirmación de fórmula hipoalergénica.
                6. "nivel_seguridad": "SAFE", "CAUTION" o "WARNING".
                7. "opinion_complementaria": Breve evaluación pediátrica del reverso.
            """.trimIndent()

            val requestBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", promptText))
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
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
            val rawText = jsonResponse.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")

            val cleanJson = rawText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val extraJson = JSONObject(cleanJson)

            val newIngredients = extraJson.optString("ingredientes_materiales").ifBlank { existingProduct.ingredients }
            val newExpiration = extraJson.optString("fecha_vencimiento").ifBlank { existingProduct.expirationDate }
            val newBarcode = extraJson.optString("codigo_barra").ifBlank { existingProduct.barcode }
            val newOrigin = extraJson.optString("fabricado_en").ifBlank { existingProduct.manufacturedIn }
            val newRisks = extraJson.optString("riesgos_advertencias").ifBlank { existingProduct.risksAndWarnings }

            val safetyStr = extraJson.optString("nivel_seguridad", existingProduct.safetyLevel.name)
            val newSafety = runCatching { ProductSafetyLevel.valueOf(safetyStr.uppercase()) }.getOrDefault(existingProduct.safetyLevel)

            val mergedProduct = existingProduct.copy(
                ingredients = newIngredients,
                expirationDate = newExpiration,
                barcode = newBarcode,
                manufacturedIn = newOrigin,
                risksAndWarnings = newRisks,
                safetyLevel = newSafety,
                missingInfoPrompt = "" // Datos complementados exitosamente
            )

            Result.success(mergedProduct)
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
