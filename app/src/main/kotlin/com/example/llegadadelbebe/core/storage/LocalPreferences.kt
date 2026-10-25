package com.example.llegadadelbebe.core.storage

import android.content.Context
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductCategory
import com.example.llegadadelbebe.domain.model.ProductSafetyLevel
import com.example.llegadadelbebe.domain.model.ProductStatus
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Persistencia local ligera en el dispositivo (SharedPreferences) para conservar:
 * - Nombre del bebé
 * - Fecha de Última Regla (FUR / FUM)
 * - Usuario activo en este teléfono (Papá Edward o Mamá Britney)
 * - Caché local del inventario (incluyendo foto miniatura, riesgos e ingredientes) para funcionamiento Offline instantáneo
 */
class LocalPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getBabyName(): String = prefs.getString(KEY_BABY_NAME, "Nuestro Niño") ?: "Nuestro Niño"

    fun saveBabyName(name: String) {
        prefs.edit().putString(KEY_BABY_NAME, name.trim().ifBlank { "Nuestro Niño" }).apply()
    }

    fun getLmpDate(): LocalDate {
        val defaultEpoch = LocalDate.now().minusWeeks(27).minusDays(2).toEpochDay()
        val savedEpoch = prefs.getLong(KEY_LMP_EPOCH_DAY, defaultEpoch)
        return LocalDate.ofEpochDay(savedEpoch)
    }

    fun saveLmpDate(date: LocalDate) {
        prefs.edit().putLong(KEY_LMP_EPOCH_DAY, date.toEpochDay()).apply()
    }

    fun getActiveUser(): String =
        prefs.getString(KEY_ACTIVE_USER, "Papá (Edward)") ?: "Papá (Edward)"

    fun saveActiveUser(user: String) {
        prefs.edit().putString(KEY_ACTIVE_USER, user.trim().ifBlank { "Papá (Edward)" }).apply()
    }

    fun saveInventoryCache(products: List<BabyProduct>) {
        try {
            val array = JSONArray()
            for (item in products) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("brand", item.brand)
                    put("model", item.model)
                    put("category", item.category.name)
                    put("stageSize", item.stageSize)
                    put("quantityCurrent", item.quantityCurrent)
                    put("quantityTarget", item.quantityTarget)
                    put("status", item.status.name)
                    put("ingredients", item.ingredients)
                    put("risksAndWarnings", item.risksAndWarnings)
                    put("safetyLevel", item.safetyLevel.name)
                    put("manufacturedIn", item.manufacturedIn)
                    put("expirationDate", item.expirationDate)
                    put("photoBase64", item.photoBase64)
                    put("rawPhotoBase64", item.rawPhotoBase64)
                    put("notes", item.notes)
                    put("geminiOpinion", item.geminiOpinion)
                    put("registeredBy", item.registeredBy)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_INVENTORY_CACHE, array.toString()).apply()
        } catch (_: Exception) {
            // Ignorar fallos de serialización en caché secundaria
        }
    }

    fun getInventoryCache(): List<BabyProduct>? {
        val raw = prefs.getString(KEY_INVENTORY_CACHE, null) ?: return null
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<BabyProduct>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val catName = obj.optString("category", ProductCategory.CLOTHING.name)
                val statusName = obj.optString("status", ProductStatus.PENDING.name)
                val safetyName = obj.optString("safetyLevel", ProductSafetyLevel.SAFE.name)
                list.add(
                    BabyProduct(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        brand = obj.optString("brand"),
                        model = obj.optString("model"),
                        category = runCatching { ProductCategory.valueOf(catName) }
                            .getOrDefault(ProductCategory.CLOTHING),
                        stageSize = obj.optString("stageSize", "RN"),
                        quantityCurrent = obj.optInt("quantityCurrent", 1),
                        quantityTarget = obj.optInt("quantityTarget", 1),
                        status = runCatching { ProductStatus.valueOf(statusName) }
                            .getOrDefault(ProductStatus.PENDING),
                        ingredients = obj.optString("ingredients", ""),
                        risksAndWarnings = obj.optString("risksAndWarnings", ""),
                        safetyLevel = runCatching { ProductSafetyLevel.valueOf(safetyName) }
                            .getOrDefault(ProductSafetyLevel.SAFE),
                        manufacturedIn = obj.optString("manufacturedIn", ""),
                        expirationDate = obj.optString("expirationDate", "N/A"),
                        photoBase64 = obj.optString("photoBase64", ""),
                        rawPhotoBase64 = obj.optString("rawPhotoBase64", ""),
                        notes = obj.optString("notes", ""),
                        geminiOpinion = obj.optString("geminiOpinion", ""),
                        registeredBy = obj.optString("registeredBy", "Papá (Edward)")
                    )
                )
            }
            if (list.isEmpty()) null else list
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "llegada_del_bebe_prefs"
        private const val KEY_BABY_NAME = "baby_name"
        private const val KEY_LMP_EPOCH_DAY = "lmp_epoch_day"
        private const val KEY_ACTIVE_USER = "active_user"
        private const val KEY_INVENTORY_CACHE = "inventory_cache_json"
    }
}
