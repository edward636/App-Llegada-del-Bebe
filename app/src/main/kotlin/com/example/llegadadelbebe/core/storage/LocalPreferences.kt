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
                    item.referencePriceUsd?.let { put("referencePriceUsd", it) }
                    if (item.referencePriceSource.isNotBlank()) {
                        put("referencePriceSource", item.referencePriceSource)
                    }
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
                        registeredBy = obj.optString("registeredBy", "Papá (Edward)"),
                        referencePriceUsd = if (obj.has("referencePriceUsd") && !obj.isNull("referencePriceUsd")) {
                            obj.optDouble("referencePriceUsd", 0.0).takeIf { it > 0.0 }
                        } else null,
                        referencePriceSource = obj.optString("referencePriceSource", "")
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

    fun getLastBcvRate(): Double {
        val raw = prefs.getString(KEY_LAST_BCV_RATE, "886.22") ?: "886.22"
        return raw.toDoubleOrNull() ?: 886.22
    }

    fun getLastBcvDate(): String = prefs.getString(KEY_LAST_BCV_DATE, "") ?: ""

    fun saveLastBcvRate(rate: Double, dateStr: String) {
        prefs.edit()
            .putString(KEY_LAST_BCV_RATE, rate.toString())
            .putString(KEY_LAST_BCV_DATE, dateStr)
            .apply()
    }

    fun quotesToJson(quotes: List<com.example.llegadadelbebe.domain.model.ProductPriceQuote>): String {
        val array = JSONArray()
        for (q in quotes) {
            val obj = JSONObject().apply {
                put("id", q.id)
                put("productName", q.productName)
                put("brand", q.brand)
                put("category", q.category.name)
                put("stageSize", q.stageSize)
                put("packageCount", q.packageCount)
                put("unitsPerPackage", q.unitsPerPackage)
                put("storeName", q.storeName)
                put("storeAddress", q.storeAddress)
                put("latitude", q.latitude)
                put("longitude", q.longitude)
                put("priceAmount", q.priceAmount)
                put("priceCurrency", q.priceCurrency.name)
                put("bcvRateApplied", q.bcvRateApplied)
                put("priceUsdEquivalent", q.priceUsdEquivalent)
                put("unitPriceUsd", q.unitPriceUsd)
                put("quotedAt", q.quotedAt)
                put("registeredBy", q.registeredBy)
                put("photoBase64", q.photoBase64)
                put("notes", q.notes)
                put("barcode", q.barcode)
                put("googleMapsName", q.googleMapsName)
                q.farmatodoBenchmark?.let { f ->
                    val fObj = JSONObject().apply {
                        put("id", f.id)
                        put("name", f.name)
                        put("brand", f.brand)
                        put("fullPriceVes", f.fullPriceVes)
                        put("offerPriceVes", f.offerPriceVes)
                        put("priceUsd", f.priceUsd)
                        put("offerPriceUsd", f.offerPriceUsd)
                        put("mediaImageUrl", f.mediaImageUrl)
                        put("urlSlug", f.urlSlug)
                        put("barcode", f.barcode)
                    }
                    put("farmatodo", fObj)
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun parseQuotesJson(raw: String): List<com.example.llegadadelbebe.domain.model.ProductPriceQuote> {
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<com.example.llegadadelbebe.domain.model.ProductPriceQuote>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val catName = obj.optString("category", ProductCategory.DIAPERS.name)
                val currName = obj.optString("priceCurrency", com.example.llegadadelbebe.domain.model.CurrencyType.USD.name)
                val farmatodoObj = obj.optJSONObject("farmatodo")
                val benchmark = farmatodoObj?.let { f ->
                    com.example.llegadadelbebe.domain.model.FarmatodoProductBenchmark(
                        id = f.optString("id"),
                        name = f.optString("name"),
                        brand = f.optString("brand"),
                        fullPriceVes = f.optDouble("fullPriceVes", 0.0),
                        offerPriceVes = f.optDouble("offerPriceVes", 0.0),
                        priceUsd = f.optDouble("priceUsd", 0.0),
                        offerPriceUsd = f.optDouble("offerPriceUsd", 0.0),
                        mediaImageUrl = f.optString("mediaImageUrl"),
                        urlSlug = f.optString("urlSlug"),
                        barcode = f.optString("barcode")
                    )
                }

                list.add(
                    com.example.llegadadelbebe.domain.model.ProductPriceQuote(
                        id = obj.optString("id"),
                        productName = obj.optString("productName"),
                        brand = obj.optString("brand"),
                        category = ProductCategory.entries.find { it.name == catName } ?: ProductCategory.DIAPERS,
                        stageSize = obj.optString("stageSize"),
                        packageCount = obj.optInt("packageCount", 1),
                        unitsPerPackage = obj.optInt("unitsPerPackage", 1),
                        storeName = obj.optString("storeName"),
                        storeAddress = obj.optString("storeAddress"),
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        priceAmount = obj.optDouble("priceAmount", 0.0),
                        priceCurrency = com.example.llegadadelbebe.domain.model.CurrencyType.entries.find { it.name == currName } ?: com.example.llegadadelbebe.domain.model.CurrencyType.USD,
                        bcvRateApplied = obj.optDouble("bcvRateApplied", 1.0),
                        priceUsdEquivalent = obj.optDouble("priceUsdEquivalent", 0.0),
                        unitPriceUsd = obj.optDouble("unitPriceUsd", 0.0),
                        quotedAt = obj.optString("quotedAt"),
                        registeredBy = obj.optString("registeredBy"),
                        photoBase64 = obj.optString("photoBase64"),
                        notes = obj.optString("notes"),
                        barcode = obj.optString("barcode"),
                        googleMapsName = obj.optString("googleMapsName", ""),
                        farmatodoBenchmark = benchmark
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRadarQuotesCache(quotes: List<com.example.llegadadelbebe.domain.model.ProductPriceQuote>) {
        try {
            val json = quotesToJson(quotes)
            prefs.edit().putString(KEY_RADAR_QUOTES_CACHE, json).apply()
        } catch (_: Exception) {}
    }

    fun getRadarQuotesCache(): List<com.example.llegadadelbebe.domain.model.ProductPriceQuote> {
        val raw = prefs.getString(KEY_RADAR_QUOTES_CACHE, null) ?: return emptyList()
        return parseQuotesJson(raw)
    }

    fun knownStoresToJson(stores: List<com.example.llegadadelbebe.domain.model.KnownStoreLocation>): String {
        val array = JSONArray()
        for (s in stores) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("address", s.address)
                put("latitude", s.latitude)
                put("longitude", s.longitude)
                put("visitCount", s.visitCount)
                put("lastVisitedAt", s.lastVisitedAt)
                put("googleMapsName", s.googleMapsName)
                put("isUserConfirmed", s.isUserConfirmed)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun parseKnownStoresJson(raw: String): List<com.example.llegadadelbebe.domain.model.KnownStoreLocation> {
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<com.example.llegadadelbebe.domain.model.KnownStoreLocation>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    com.example.llegadadelbebe.domain.model.KnownStoreLocation(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        address = obj.optString("address"),
                        latitude = obj.optDouble("latitude"),
                        longitude = obj.optDouble("longitude"),
                        visitCount = obj.optInt("visitCount", 1),
                        lastVisitedAt = obj.optString("lastVisitedAt"),
                        googleMapsName = obj.optString("googleMapsName", ""),
                        isUserConfirmed = obj.optBoolean("isUserConfirmed", true)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getKnownStoreLocations(): List<com.example.llegadadelbebe.domain.model.KnownStoreLocation> {
        val raw = prefs.getString(KEY_KNOWN_STORES_CACHE, null) ?: return emptyList()
        return parseKnownStoresJson(raw)
    }

    fun saveAllKnownStores(stores: List<com.example.llegadadelbebe.domain.model.KnownStoreLocation>) {
        try {
            val json = knownStoresToJson(stores)
            prefs.edit().putString(KEY_KNOWN_STORES_CACHE, json).apply()
        } catch (_: Exception) {}
    }

    fun saveKnownStoreLocation(store: com.example.llegadadelbebe.domain.model.KnownStoreLocation) {
        val current = getKnownStoreLocations().toMutableList()
        val index = current.indexOfFirst { it.name.equals(store.name, ignoreCase = true) || (Math.abs(it.latitude - store.latitude) < 0.0005 && Math.abs(it.longitude - store.longitude) < 0.0005) }
        if (index >= 0) {
            val existing = current[index]
            current[index] = store.copy(id = existing.id, visitCount = existing.visitCount + 1)
        } else {
            current.add(store)
        }
        saveAllKnownStores(current)
    }

    fun getDailyAiWisdom(): Pair<String, String>? {
        val date = prefs.getString(KEY_DAILY_AI_DATE, null) ?: return null
        val message = prefs.getString(KEY_DAILY_AI_MESSAGE, null) ?: return null
        return Pair(date, message)
    }

    fun saveDailyAiWisdom(date: String, message: String) {
        prefs.edit()
            .putString(KEY_DAILY_AI_DATE, date)
            .putString(KEY_DAILY_AI_MESSAGE, message)
            .apply()
    }

    fun medicationsToJson(medications: List<com.example.llegadadelbebe.domain.model.PrenatalMedication>): String {
        val array = JSONArray()
        for (m in medications) {
            val obj = JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("activeIngredient", m.activeIngredient)
                put("dose", m.dose)
                put("frequencyHours", m.frequencyHours)
                val timesArray = JSONArray()
                m.scheduleTimes.forEach { timesArray.put(it) }
                put("scheduleTimes", timesArray)
                put("foodInstructions", m.foodInstructions)
                put("clinicalWarnings", m.clinicalWarnings)
                put("pillsRemaining", m.pillsRemaining)
                put("totalPillsInPackage", m.totalPillsInPackage)
                put("isMandatory", m.isMandatory)
                put("photoBase64", m.photoBase64)
                put("notes", m.notes)
                put("lastTakenDate", m.lastTakenDate)
                put("lastTakenTime", m.lastTakenTime)
                put("lastConfirmedBy", m.lastConfirmedBy)
                put("updatedAt", m.updatedAt)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun parseMedicationsJson(jsonStr: String): List<com.example.llegadadelbebe.domain.model.PrenatalMedication> {
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<com.example.llegadadelbebe.domain.model.PrenatalMedication>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val timesArray = obj.optJSONArray("scheduleTimes")
                val times = mutableListOf<String>()
                if (timesArray != null) {
                    for (j in 0 until timesArray.length()) times.add(timesArray.optString(j))
                }
                if (times.isEmpty()) times.add("08:00")

                list.add(
                    com.example.llegadadelbebe.domain.model.PrenatalMedication(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        activeIngredient = obj.optString("activeIngredient"),
                        dose = obj.optString("dose", "1 tableta"),
                        frequencyHours = obj.optInt("frequencyHours", 24),
                        scheduleTimes = times,
                        foodInstructions = obj.optString("foodInstructions"),
                        clinicalWarnings = obj.optString("clinicalWarnings"),
                        pillsRemaining = obj.optInt("pillsRemaining", 30),
                        totalPillsInPackage = obj.optInt("totalPillsInPackage", 30),
                        isMandatory = obj.optBoolean("isMandatory", true),
                        photoBase64 = obj.optString("photoBase64"),
                        notes = obj.optString("notes"),
                        lastTakenDate = obj.optString("lastTakenDate"),
                        lastTakenTime = obj.optString("lastTakenTime"),
                        lastConfirmedBy = obj.optString("lastConfirmedBy"),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMedicationsCache(medications: List<com.example.llegadadelbebe.domain.model.PrenatalMedication>) {
        try {
            val json = medicationsToJson(medications)
            prefs.edit().putString(KEY_MEDICATIONS_CACHE, json).apply()
        } catch (_: Exception) {}
    }

    fun getMedicationsCache(): List<com.example.llegadadelbebe.domain.model.PrenatalMedication> {
        val raw = prefs.getString(KEY_MEDICATIONS_CACHE, null) ?: return getDefaultPrenatalMedications()
        val parsed = parseMedicationsJson(raw)
        return if (parsed.isEmpty()) getDefaultPrenatalMedications() else parsed
    }

    private fun getDefaultPrenatalMedications(): List<com.example.llegadadelbebe.domain.model.PrenatalMedication> {
        return listOf(
            com.example.llegadadelbebe.domain.model.PrenatalMedication(
                id = "MED_DEFAULT_FOLIC",
                name = "Ácido Fólico",
                activeIngredient = "Ácido Fólico 1 mg",
                dose = "1 tableta",
                frequencyHours = 24,
                scheduleTimes = listOf("08:00"),
                foodInstructions = "En ayunas o con el desayuno con abundante agua",
                clinicalWarnings = "Fundamental para el cierre del tubo neural y desarrollo del bebé",
                pillsRemaining = 30,
                totalPillsInPackage = 30
            ),
            com.example.llegadadelbebe.domain.model.PrenatalMedication(
                id = "MED_DEFAULT_IRON",
                name = "Hierro / Sulfato Ferroso",
                activeIngredient = "Hierro elemental 60 mg",
                dose = "1 tableta",
                frequencyHours = 24,
                scheduleTimes = listOf("13:00"),
                foodInstructions = "Con el almuerzo o jugo de naranja cítrico para máxima absorción",
                clinicalWarnings = "⚠️ NO tomar junto al Calcio o leche (separar al menos 2 horas)",
                pillsRemaining = 30,
                totalPillsInPackage = 30
            ),
            com.example.llegadadelbebe.domain.model.PrenatalMedication(
                id = "MED_DEFAULT_CALCIUM",
                name = "Calcio + Vitamina D3",
                activeIngredient = "Calcio 600 mg + Vit D3 400 UI",
                dose = "1 tableta",
                frequencyHours = 24,
                scheduleTimes = listOf("20:00"),
                foodInstructions = "En la cena",
                clinicalWarnings = "Separar del hierro por al menos 2 horas para evitar interferencia",
                pillsRemaining = 30,
                totalPillsInPackage = 30
            )
        )
    }

    fun recordAppOpen() {
        prefs.edit().putLong(KEY_LAST_APP_OPEN_MILLIS, System.currentTimeMillis()).apply()
    }

    fun getLastAppOpenMillis(): Long {
        return prefs.getLong(KEY_LAST_APP_OPEN_MILLIS, System.currentTimeMillis())
    }

    fun saveLastEngagementNotifEpochDay(epochDay: Long) {
        prefs.edit().putLong(KEY_LAST_ENGAGEMENT_NOTIF_EPOCH_DAY, epochDay).apply()
    }

    fun getLastEngagementNotifEpochDay(): Long {
        return prefs.getLong(KEY_LAST_ENGAGEMENT_NOTIF_EPOCH_DAY, 0L)
    }

    fun addDeletedProductId(id: String) {
        val current = getDeletedProductIds().toMutableSet()
        current.add(id)
        prefs.edit().putStringSet(KEY_DELETED_PRODUCT_IDS, current).apply()
    }

    fun getDeletedProductIds(): Set<String> {
        return prefs.getStringSet(KEY_DELETED_PRODUCT_IDS, emptySet()) ?: emptySet()
    }

    fun clearDeletedProductIds() {
        prefs.edit().remove(KEY_DELETED_PRODUCT_IDS).apply()
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
        private const val KEY_LAST_BCV_RATE = "last_bcv_rate"
        private const val KEY_LAST_BCV_DATE = "last_bcv_date"
        private const val KEY_RADAR_QUOTES_CACHE = "radar_quotes_cache_json"
        private const val KEY_KNOWN_STORES_CACHE = "known_stores_cache_json"
        private const val KEY_DAILY_AI_DATE = "daily_ai_date"
        private const val KEY_DAILY_AI_MESSAGE = "daily_ai_message"
        private const val KEY_MEDICATIONS_CACHE = "medications_cache_json"
        private const val KEY_LAST_APP_OPEN_MILLIS = "last_app_open_millis"
        private const val KEY_LAST_ENGAGEMENT_NOTIF_EPOCH_DAY = "last_engagement_notif_epoch_day"
        private const val KEY_DELETED_PRODUCT_IDS = "deleted_product_ids"
    }
}
