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
                - REGLA DE ORO DE VERACIDAD (CERO ALUCINACIÓN): NUNCA inventes o supongas ingredientes, lote, unidades o fecha de vencimiento que no estén 100% legibles en la imagen. Si un producto de higiene, salud, pañales o alimentación no muestra los ingredientes o la fecha de caducidad en esta cara frontal, deja "ingredientes_materiales": "", marca "informacion_incompleta": true, y en "que_falta" redacta: "Falta fotografiar el reverso o lateral del empaque para leer la tabla de ingredientes (en inglés o español) y la fecha de caducidad con exactitud."
                - INGREDIENTES EN INGLÉS / NOMENCLATURA INCI (CRUCIAL PARA JABONES, CHAMPÚS, CREMAS Y TOALLITAS): En la gran mayoría de jabones (en barra o líquidos), champús, geles de baño, cremas y lociones para bebés, la lista de ingredientes está obligatoriamente redactada en INGLÉS o bajo la norma internacional INCI (ej: 'INGREDIENTS: Sodium Palmate, Aqua, Glycerin, Sodium Palm Kernelate, Parfum, Titanium Dioxide, Tetrasodium EDTA...', o 'Water (Aqua), Cocamidopropyl Betaine, Sodium Cocoyl Isethionate, Stearic Acid...'). DEBES DETECTARLA Y EXTRAERLA OBLIGATORIAMENTE sin importar si está en INGLÉS, LATÍN, ESPAÑOL o bilingüe. Busca palabras clave: 'INGREDIENTS:', 'INGREDIENTES:', 'Aqua', 'Water', 'Sodium', 'Glycerin', 'Acid', 'Oil', 'Extract'. Transcribe todos los ingredientes en 'ingredientes_materiales'. En 'riesgos_advertencias', traduce y explica en español su función y perfil de seguridad pediátrica para recién nacidos (ej: 'Jabón vegetal neutro con glicerina hidratante. Libre de parabenos y sulfatos agresivos').
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
                Eres un pediatra neonatólogo y químico cosmético analizando una fotografía ADICIONAL (reverso, lateral, tabla de ingredientes o código de barra) del producto de bebé: "${existingProduct.brand} ${existingProduct.name} ${existingProduct.model}".
                
                ESTADO ACTUAL DEL PRODUCTO:
                - Nombre: ${existingProduct.name}
                - Marca: ${existingProduct.brand}
                - Ingredientes detectados hasta ahora: ${existingProduct.ingredients.ifBlank { "NINGUNO DETECTADO AÚN" }}
                - Vencimiento previo: ${existingProduct.expirationDate.ifBlank { "NO DETECTADO AÚN" }}
                - Código de barras previo: ${existingProduct.barcode.ifBlank { "NO DETECTADO AÚN" }}
                
                DIRECTIVAS OBLIGATORIAS:
                1. INGREDIENTES EN INGLÉS / INCI (MÁXIMA PRIORIDAD PARA JABONES, CHAMPÚS, LOCIONES, POMADAS Y TOALLITAS):
                   En jabones de bebé (en barra o líquido), geles, champús y cremas corporales, casi el 100% de los envases imprimen la lista de ingredientes en INGLÉS o según el estándar cosmético internacional INCI.
                   Palabras clave a buscar: 'INGREDIENTS:', 'INGREDIENTES:', 'CONTIENE:', 'COMPOSITION:', 'FORMULA:', 'ACTIVE INGREDIENTS:', 'INACTIVE INGREDIENTS:'.
                   Componentes típicos: Sodium Palmate, Sodium Palm Kernelate, Aqua/Water, Glycerin, Stearic Acid, Sodium Tallowate, Titanium Dioxide, Tetrasodium EDTA, Etidronic Acid, Cocamidopropyl Betaine, Tocopherol, Avena Sativa, Chamomilla Recutita, etc.
                   INSTRUCCIÓN ESTRICTA: Si la imagen muestra cualquier lista de ingredientes o componentes en INGLÉS, DEBES TRANSCRIBIRLOS ÍNTEGRAMENTE en "ingredientes_materiales". NUNCA los descartes por estar en inglés. En "riesgos_advertencias", analiza y explica en español su compatibilidad dérmica con la piel de un recién nacido.
                2. CONTINUIDAD MULTI-FOTO (NO LIMITARSE):
                   - Si esta foto muestra los ingredientes (en inglés o español), extrae la lista completa en "ingredientes_materiales".
                   - Si a pesar de esta toma AÚN NO se logran leer los ingredientes o vencimiento con claridad, marca "informacion_incompleta": true y en "que_falta" redacta una guía clara (ej: "Aún no se aprecian los ingredientes con claridad. Toma una foto con mayor acercamiento al lateral donde dice 'INGREDIENTS:'").
                   - Si ya se leyeron con éxito los ingredientes y datos esenciales, marca "informacion_incompleta": false y "que_falta": "".
                
                Devuelve únicamente un objeto JSON con las siguientes claves:
                1. "ingredientes_materiales": Lista completa de ingredientes detectados (en inglés o español).
                2. "fecha_vencimiento": Fecha de vencimiento o caducidad visible (ej: 05/2027) o "N/A" si no vence.
                3. "codigo_barra": Números del código de barras si son visibles en esta toma.
                4. "fabricado_en": País de origen, laboratorio o distribuidor.
                5. "riesgos_advertencias": Análisis de riesgos, fragancias, sulfatos o confirmación de fórmula hipoalergénica en español.
                6. "nivel_seguridad": "SAFE", "CAUTION" o "WARNING".
                7. "informacion_incompleta": true o false.
                8. "que_falta": Explicación si se requiere otra foto adicional más cercana o de otro lateral.
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

            val isStillIncomplete = extraJson.optBoolean("informacion_incompleta", false)
            val whatIsMissing = if (isStillIncomplete) {
                extraJson.optString("que_falta", "Se sugiere tomar otra foto con mayor acercamiento al lateral donde se aprecian los ingredientes.")
            } else if (newIngredients.isBlank() && existingProduct.category in listOf(ProductCategory.WIPES_HYGIENE, ProductCategory.HEALTH, ProductCategory.FEEDING)) {
                "Aún no se detectaron los ingredientes en inglés o español. Toma otra foto más de cerca de la lista 'INGREDIENTS:'."
            } else ""

            val mergedProduct = existingProduct.copy(
                ingredients = newIngredients,
                expirationDate = newExpiration,
                barcode = newBarcode,
                manufacturedIn = newOrigin,
                risksAndWarnings = newRisks,
                safetyLevel = newSafety,
                missingInfoPrompt = whatIsMissing
            )

            Result.success(mergedProduct)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Busca información de catálogo precisa para un producto de bebé a partir de su código de barras (EAN-13, UPC).
     */
    suspend fun lookupProductByBarcode(
        barcode: String,
        apiKey: String
    ): Result<BabyProduct> = withContext(Dispatchers.IO) {
        try {
            val promptText = """
                Eres un experto en catálogo de productos infantiles, farmacia pediátrica y artículos de bebé para Venezuela e importaciones.
                Identifica el producto de bebé que corresponde exactamente al siguiente código de barras (EAN-13 o UPC): "$barcode".
                Si reconoces el producto con exactitud, extrae sus datos fidedignos. Si no lo reconoces al 100%, deduce la variante regional (ej: prefijo 759 de Venezuela) y devuelve datos coherentes para su posterior verificación.
                
                Devuelve únicamente un objeto JSON con:
                1. "nombre": Nombre comercial del producto.
                2. "marca": Marca.
                3. "modelo": Presentación o línea.
                4. "categoria": CLOTHING, DIAPERS, WIPES_HYGIENE, FEEDING, CRIB_SLEEP, TRANSPORT, HEALTH o HOSPITAL_BAG.
                5. "talla_etapa": RN, P, M, G, XG o según aplique.
                6. "paquetes": 1.
                7. "unidades_por_paquete": Unidades típicas del empaque.
                8. "unidades_totales": unidades_por_paquete.
                9. "ingredientes_materiales": Ingredientes o materiales típicos.
                10. "riesgos_advertencias": Advertencias pediátricas.
                11. "nivel_seguridad": "SAFE", "CAUTION" o "WARNING".
                12. "fabricado_en": País de origen.
                13. "fecha_vencimiento": "".
                14. "codigo_barra": "$barcode".
                15. "variante_pais": Si es prefijo 759 "Venezuela 🇻🇪 (GS1 Nacional)", u otro país.
                16. "edad_util": Rango de edad útil si aplica.
                17. "opinion_consejo": Breve consejo pediátrico.
            """.trimIndent()

            val requestBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", promptText))
                        }
                        put("parts", parts)
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("response_mime_type", "application/json")
                    put("temperature", 0.1)
                })
            }

            val responseString = executePost(requestBody.toString(), apiKey)
            val jsonResponse = JSONObject(responseString)
            val rawText = jsonResponse.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")

            val cleanJson = rawText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val productJson = JSONObject(cleanJson)

            val categoryString = productJson.optString("categoria", "DIAPERS")
            val resolvedCategory = runCatching {
                ProductCategory.valueOf(categoryString.uppercase().trim())
            }.getOrDefault(ProductCategory.DIAPERS)

            val safetyString = productJson.optString("nivel_seguridad", "SAFE")
            val resolvedSafety = runCatching {
                ProductSafetyLevel.valueOf(safetyString.uppercase().trim())
            }.getOrDefault(ProductSafetyLevel.SAFE)

            val parsedPacks = productJson.optInt("paquetes", 1).coerceAtLeast(1)
            val parsedUnitsPerPack = productJson.optInt("unidades_por_paquete", 1).coerceAtLeast(1)

            val product = BabyProduct(
                id = UUID.randomUUID().toString(),
                name = productJson.optString("nombre", "Producto escaneado"),
                brand = productJson.optString("marca", ""),
                model = productJson.optString("modelo", ""),
                category = resolvedCategory,
                stageSize = productJson.optString("talla_etapa", ""),
                packageCount = parsedPacks,
                unitsPerPackage = parsedUnitsPerPack,
                quantityCurrent = parsedPacks * parsedUnitsPerPack,
                ingredients = productJson.optString("ingredientes_materiales", ""),
                risksAndWarnings = productJson.optString("riesgos_advertencias", ""),
                safetyLevel = resolvedSafety,
                manufacturedIn = productJson.optString("fabricado_en", ""),
                expirationDate = productJson.optString("fecha_vencimiento", ""),
                barcode = barcode,
                countryVariant = productJson.optString("variante_pais", com.example.llegadadelbebe.core.util.BarcodeHelper.resolveCountryVariant(barcode, "")),
                usefulUntilAge = productJson.optString("edad_util", ""),
                geminiOpinion = productJson.optString("opinion_consejo", "")
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

    /**
     * Genera una lectura profunda de sinastría familiar para Edward, Britney y su bebé
     * combinando los 9 puntos astrales (Sol, Luna y Ascendente de los tres).
     */
    suspend fun getFamilySynastryReading(
        synastrySummaryText: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Eres un astrólogo humanista, psicólogo familiar y puericultor empático acompañando a Edward (Papá) y Britney (Mamá) ante la llegada de su bebé varón.
                Analiza la siguiente configuración astral de la familia:
                $synastrySummaryText
                
                Redacta una lectura profunda, afectuosa, bien estructurada con subtítulos y emojis:
                1. 💑 Sinastría Parental (Edward ↔ Britney): El regalo cósmico de su unión (destacando la Conjunción Sol Géminis - Luna Géminis, el trígono de Aire y cómo complementan el orden de Virgo con la valentía de Aries).
                2. 💙👶 El Vínculo Padre-Hijo (Luna y Ascendente de Edward con el Bebé): Cómo Edward calmará, cuidará y guiará al niño varón desde su Luna protectora en Virgo.
                3. 🌸👶 El Vínculo Madre-Hijo (Britney con el Bebé): La nutrición emocional de su Luna en Géminis y la paz y armonía de su Ascendente en Libra.
                4. 👨‍👩‍👦 Consejo Práctico para el Hogar: Claves para apoyarse durante el posparto y mantener la alegría en casa.
                
                Tono: Cercano, inspirador, maduro y lleno de luz para su nueva etapa como padres.
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
            val readingText = jsonResponse
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            Result.success(readingText.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Analiza una fotografía de un producto en un anaquel de tienda junto con su hablador o etiqueta de precio.
     * Extrae de forma estructurada el nombre del producto, marca, categoría, unidades, precio numérico,
     * moneda del comercio (USD o Bolívares) y sugerencia del nombre de la tienda si es visible.
     */
    suspend fun extractPriceQuoteFromImage(
        base64Image: String,
        mimeType: String = "image/jpeg",
        apiKey: String
    ): Result<ParsedPriceQuote> = withContext(Dispatchers.IO) {
        try {
            val promptText = """
                Eres un asistente de compras de supermercado y farmacias en Venezuela para Edward y Britney.
                Analiza con precisión milimétrica la imagen donde se aprecia un producto para bebé (pañales, toallas, fórmula, etc.) y/o el hablador de precio en el anaquel o etiqueta.

                Debes extraer:
                1. "nombre_producto": Nombre claro del producto (ej: "Pañales Huggies Active Sec", "Fórmula Enfamil 1", "Toallitas Babysec").
                2. "marca": Marca (ej: "Huggies", "Pampers", "Johnson's", "WaterWipes", "Enfamil").
                3. "categoria": Una de: CLOTHING, DIAPERS, WIPES_HYGIENE, FEEDING, CRIB_SLEEP, TRANSPORT, HEALTH, HOSPITAL_BAG.
                4. "talla_etapa": Talla si aplica (ej: "RN", "P", "M", "G", "XG", "0-3M").
                5. "paquetes": Número de paquetes (entero, por defecto 1).
                6. "unidades_por_paquete": Cantidad de unidades dentro del paquete (ej: 32 pañales, 80 toallitas, 400g fórmula).
                7. "monto_precio": Valor numérico del precio (ej: 12.50, 480.00). Si hay coma decimal, conviértela a número con punto. Si hay precio en Bs y en $, extrae el precio en $ preferentemente o el principal.
                8. "moneda_precio": "USD" si el precio está en dólares ('$', 'USD', 'REF') o "VES" si está en bolívares ('Bs.', 'Bs', 'VES').
                9. "tienda_sugerida": Nombre de la tienda si es identificable por el hablador o logo (ej: "Farmatodo", "Locatel", "Gama", "Plaza's", "Makro") o "".
                10. "codigo_barra": Dígitos del código de barra si son legibles, o "".
                11. "observaciones": Notas breves de la oferta o presentación especial (ej: "Promoción 2x1", "Precio de afiliado").

                Responde ÚNICAMENTE un objeto JSON válido con estas claves exactas.
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
                    put("temperature", 0.1)
                    put("response_mime_type", "application/json")
                })
            }

            val responseString = executePost(requestBody.toString(), apiKey)
            val root = JSONObject(responseString)
            val candidates = root.optJSONArray("candidates")
                ?: throw RuntimeException("Respuesta sin candidatos de Gemini")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val text = parts.getJSONObject(0).getString("text")

            val json = JSONObject(text.trim())

            val rawCategory = json.optString("categoria", "DIAPERS")
            val cat = ProductCategory.entries.find { it.name.equals(rawCategory, ignoreCase = true) }
                ?: ProductCategory.DIAPERS

            val quote = ParsedPriceQuote(
                productName = json.optString("nombre_producto", "Producto sin nombre").trim(),
                brand = json.optString("marca", "").trim(),
                category = cat,
                stageSize = json.optString("talla_etapa", "").trim(),
                packageCount = json.optInt("paquetes", 1).coerceAtLeast(1),
                unitsPerPackage = json.optInt("unidades_por_paquete", 1).coerceAtLeast(1),
                priceAmount = json.optDouble("monto_precio", 0.0),
                priceCurrency = if (json.optString("moneda_precio", "USD").contains("VES", ignoreCase = true) || json.optString("moneda_precio").contains("BS", ignoreCase = true)) "VES" else "USD",
                storeNameHint = json.optString("tienda_sugerida", "").trim(),
                barcode = json.optString("codigo_barra", "").trim(),
                notes = json.optString("observaciones", "").trim()
            )

            Result.success(quote)
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

    /**
     * Genera un mensaje tierno, personalizado y sabio para el día de hoy, combinando:
     * - El desarrollo del bebé para este día específico (semana, día, tamaño).
     * - Los datos de la base de datos (progreso de compras, pañales, preparación).
     * - Saludo amoroso dirigido a Britney o Edward.
     */
    suspend fun generateDailyBabyWisdom(
        activeUser: String,
        babyName: String,
        gestationalWeeks: Int,
        gestationalDays: Int,
        babySizeComparison: String,
        daysRemaining: Long,
        purchasedCount: Int,
        totalItems: Int,
        apiKey: String,
        lmpDate: String = "05/06/2026"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val isMom = activeUser.contains("Mamá", ignoreCase = true)
            val prompt = """
                Eres el asesor pediátrico y de apego más cariñoso para los padres Britney y Edward en la dulce espera de su bebé '$babyName'.
                Hoy están en:
                - Fecha de Última Regla (FUR / FUM): $lmpDate.
                - Semana gestacional: $gestationalWeeks semanas + $gestationalDays días.
                - Tamaño de referencia del bebé hoy: $babySizeComparison.
                - Días restantes para el parto: $daysRemaining días.
                - Base de datos de preparativos: Llevan $purchasedCount de $totalItems artículos ya listos en casa.
                - Lector del mensaje en este momento: ${if (isMom) "Mamita Britney 🌸" else "Papá Edward 💙"}.

                Escribe un mensaje de bienvenida para el DÍA DE HOY:
                1. Saludo breve y cariñoso mencionando el nombre del lector.
                2. Un hecho fascinante o hito del desarrollo del bebé hoy (ej: sus sentidos, sus manitas, latidos, o tamaño).
                3. Una mención motivadora y cariñosa sobre cómo avanzan sus preparativos en la base de datos de casa.
                4. Un consejo de amor o bienestar práctico (si es para Britney: hidratación, descanso, acariciar la pancita; si es para Edward: consentir a Britney, apoyo amoroso en el hogar).

                Longitud: 2 párrafos cortos (máximo 80-90 palabras). Tono cálido, alegre, esperanzador y familiar. Formato de texto limpio sin tecnicismos fríos.
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
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                })
            }

            val responseString = executePost(requestBody.toString(), apiKey)
            val root = JSONObject(responseString)
            val candidates = root.optJSONArray("candidates")
                ?: throw RuntimeException("Respuesta vacía de Gemini")
            val text = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            Result.success(text.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Analiza por IA multimodal la fotografía de una caja de medicamento, vitamina o récipe médico prenatal.
     */
    suspend fun extractMedicationFromImage(
        base64Image: String,
        mimeType: String = "image/jpeg",
        apiKey: String
    ): Result<ParsedMedicationInfo> = withContext(Dispatchers.IO) {
        try {
            val promptText = """
                Eres un asistente farmacéutico pediátrico y obstétrico de máxima precisión para el embarazo de Britney.
                Analiza la fotografía de la caja de medicamento/vitamina prenatal o del récipe médico.

                Debes extraer en un objeto JSON con estas claves exactas:
                1. "nombre_comercial": Nombre del medicamento o vitamina (ej: "Sulfato Ferroso", "Ácido Fólico", "Caltrate 600+D", "GestaDHA", "Progesterona").
                2. "principio_activo": Principio activo o composición principal (ej: "Hierro elemental 60mg", "Ácido Fólico 1mg", "Calcio 600mg + Vitamina D3 400UI").
                3. "dosis": Dosis unitaria (ej: "1 tableta", "1 cápsula blanda", "5 ml").
                4. "frecuencia_horas": Frecuencia en horas (entero: 24 para una vez al día, 12 para cada 12h, 8 para cada 8h). Por defecto 24.
                5. "horarios_sugeridos": Arreglo de strings en formato HH:mm según la frecuencia (ej: ["08:00"] para 24h, ["08:00", "20:00"] para 12h).
                6. "instrucciones_comidas": Cómo tomarlo con alimentos (ej: "En ayunas con jugo de naranja para mejor absorción", "Con el almuerzo", "Lejos de lácteos y café").
                7. "advertencias_clinicas": Interacciones o advertencias (ej: "No tomar junto al Calcio; esperar al menos 2 horas de separación", "Consumir abundante agua").
                8. "pastillas_en_caja": Total de pastillas o cápsulas que trae la caja (entero, ej: 30, 60, 100). Si no se aprecia, 30.
                9. "notas": Breve nota relevante del laboratorio o indicación obstétrica.

                Responde ÚNICAMENTE un objeto JSON válido con estas claves exactas.
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
                    put("temperature", 0.1)
                    put("response_mime_type", "application/json")
                })
            }

            val responseString = executePost(requestBody.toString(), apiKey)
            val root = JSONObject(responseString)
            val candidates = root.optJSONArray("candidates")
                ?: throw RuntimeException("Respuesta sin candidatos de Gemini")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val text = parts.getJSONObject(0).getString("text")

            val json = JSONObject(text.trim())

            val rawTimes = json.optJSONArray("horarios_sugeridos")
            val scheduleTimes = mutableListOf<String>()
            if (rawTimes != null) {
                for (i in 0 until rawTimes.length()) {
                    val t = rawTimes.optString(i, "")
                    if (t.isNotBlank()) scheduleTimes.add(t)
                }
            }
            if (scheduleTimes.isEmpty()) {
                val freq = json.optInt("frecuencia_horas", 24)
                if (freq <= 12) scheduleTimes.addAll(listOf("08:00", "20:00"))
                else scheduleTimes.add("08:00")
            }

            val parsed = ParsedMedicationInfo(
                name = json.optString("nombre_comercial", "Medicamento Prenatal").trim(),
                activeIngredient = json.optString("principio_activo", "").trim(),
                dose = json.optString("dosis", "1 tableta").trim(),
                frequencyHours = json.optInt("frecuencia_horas", 24).coerceIn(4, 48),
                scheduleTimes = scheduleTimes,
                foodInstructions = json.optString("instrucciones_comidas", "Con abundante agua").trim(),
                clinicalWarnings = json.optString("advertencias_clinicas", "").trim(),
                pillsInBox = json.optInt("pastillas_en_caja", 30).coerceAtLeast(1),
                notes = json.optString("notas", "").trim()
            )

            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Genera un diagnóstico o reporte textual completo a partir de un prompt estructurado.
     */
    suspend fun generateTextDiagnosis(
        prompt: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                })
            }
            val response = executePost(jsonPayload.toString(), apiKey)
            val jsonObject = JSONObject(response)
            val candidates = jsonObject.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    if (text.isNotBlank()) {
                        return@withContext Result.success(text)
                    }
                }
            }
            Result.failure(Exception("Sin respuesta de Gemini"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class ParsedMedicationInfo(
    val name: String,
    val activeIngredient: String,
    val dose: String,
    val frequencyHours: Int,
    val scheduleTimes: List<String>,
    val foodInstructions: String,
    val clinicalWarnings: String,
    val pillsInBox: Int,
    val notes: String
)

data class ParsedPriceQuote(
    val productName: String,
    val brand: String,
    val category: ProductCategory,
    val stageSize: String,
    val packageCount: Int,
    val unitsPerPackage: Int,
    val priceAmount: Double,
    val priceCurrency: String,
    val storeNameHint: String,
    val barcode: String,
    val notes: String
)
