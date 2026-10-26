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
                    put("packageCount", item.packageCount)
                    put("unitsPerPackage", item.unitsPerPackage)
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
                    put("barcode", item.barcode)
                    put("countryVariant", item.countryVariant)
                    put("usefulUntilAge", item.usefulUntilAge)
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
                        packageCount = obj.optInt("packageCount", 1),
                        unitsPerPackage = obj.optInt("unitsPerPackage", 1),
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
                        barcode = obj.optString("barcode", ""),
                        countryVariant = obj.optString("countryVariant", ""),
                        usefulUntilAge = obj.optString("usefulUntilAge", ""),
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

    fun getHospitalBagCheckedIds(): Set<String> {
        return prefs.getStringSet(KEY_HOSPITAL_BAG_CHECKED, emptySet()) ?: emptySet()
    }

    fun toggleHospitalBagItemChecked(itemId: String): Set<String> {
        val current = getHospitalBagCheckedIds().toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        prefs.edit().putStringSet(KEY_HOSPITAL_BAG_CHECKED, current).apply()
        return current
    }

    fun getFatherBirthDate(): LocalDate {
        val defaultEpoch = LocalDate.of(1990, 5, 31).toEpochDay()
        return LocalDate.ofEpochDay(prefs.getLong(KEY_FATHER_BIRTH_EPOCH, defaultEpoch))
    }

    fun getFatherBirthTime(): java.time.LocalTime {
        val defaultMinutes = 13 * 60 + 45 // 13:45
        val totalMinutes = prefs.getInt(KEY_FATHER_BIRTH_MINUTES, defaultMinutes)
        return java.time.LocalTime.of(totalMinutes / 60, totalMinutes % 60)
    }

    fun saveFatherBirth(date: LocalDate, time: java.time.LocalTime) {
        prefs.edit()
            .putLong(KEY_FATHER_BIRTH_EPOCH, date.toEpochDay())
            .putInt(KEY_FATHER_BIRTH_MINUTES, time.hour * 60 + time.minute)
            .apply()
    }

    fun getMotherBirthDate(): LocalDate {
        val defaultEpoch = LocalDate.of(2004, 4, 7).toEpochDay()
        return LocalDate.ofEpochDay(prefs.getLong(KEY_MOTHER_BIRTH_EPOCH, defaultEpoch))
    }

    fun getMotherBirthTime(): java.time.LocalTime {
        val defaultMinutes = 18 * 60 + 45 // 18:45
        val totalMinutes = prefs.getInt(KEY_MOTHER_BIRTH_MINUTES, defaultMinutes)
        return java.time.LocalTime.of(totalMinutes / 60, totalMinutes % 60)
    }

    fun saveMotherBirth(date: LocalDate, time: java.time.LocalTime) {
        prefs.edit()
            .putLong(KEY_MOTHER_BIRTH_EPOCH, date.toEpochDay())
            .putInt(KEY_MOTHER_BIRTH_MINUTES, time.hour * 60 + time.minute)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "llegada_del_bebe_prefs"
        private const val KEY_BABY_NAME = "baby_name"
        private const val KEY_LMP_EPOCH_DAY = "lmp_epoch_day"
        private const val KEY_ACTIVE_USER = "active_user"
        private const val KEY_INVENTORY_CACHE = "inventory_cache_json"
        private const val KEY_HOSPITAL_BAG_CHECKED = "hospital_bag_checked_ids"
        private const val KEY_FATHER_BIRTH_EPOCH = "father_birth_epoch"
        private const val KEY_FATHER_BIRTH_MINUTES = "father_birth_minutes"
        private const val KEY_MOTHER_BIRTH_EPOCH = "mother_birth_epoch"
        private const val KEY_MOTHER_BIRTH_MINUTES = "mother_birth_minutes"
    }
}
