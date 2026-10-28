package com.example.llegadadelbebe.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llegadadelbebe.core.util.PregnancyCalculator
import com.example.llegadadelbebe.core.util.PregnancyStats
import com.example.llegadadelbebe.core.util.ZodiacPredictor
import com.example.llegadadelbebe.core.util.ZodiacProfile
import com.example.llegadadelbebe.data.repository.BabyInventoryRepository
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ProductStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.data.remote.GeminiClient
import com.example.llegadadelbebe.domain.usecase.OmniDashboardSnapshot
import com.example.llegadadelbebe.domain.usecase.OmniPregnancyContextUseCase
import kotlinx.coroutines.launch

data class HomeSummary(
    val totalItems: Int,
    val purchasedCount: Int,
    val pendingCount: Int,
    val hospitalBagCount: Int
)

data class DailyAiWisdom(
    val date: String = "",
    val message: String = "",
    val isLoading: Boolean = false
)

class HomeViewModel(
    private val repository: BabyInventoryRepository,
    private val localPreferences: LocalPreferences? = null
) : ViewModel() {

    private val geminiClient: GeminiClient = GeminiClient()

    private val _babyName = MutableStateFlow("Nuestro Niño")
    val babyName: StateFlow<String> = _babyName.asStateFlow()

    // Fecha de Última Regla (FUR / FUM) - Naegele clínico
    // Por defecto calculada hacia atrás desde la semana 27 + 2 días actuales
    private val _lmpDate = MutableStateFlow<LocalDate>(
        LocalDate.now().minusWeeks(27).minusDays(2)
    )
    val lmpDate: StateFlow<LocalDate> = _lmpDate.asStateFlow()

    val pregnancyStats: StateFlow<PregnancyStats> = _lmpDate.map { lmp ->
        PregnancyCalculator.calculateFromLmp(lmp)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PregnancyCalculator.calculateFromLmp(_lmpDate.value)
    )

    val zodiacProfile: StateFlow<ZodiacProfile> = pregnancyStats.map { stats ->
        ZodiacPredictor.predictFromDate(stats.estimatedDueDate)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ZodiacPredictor.predictFromDate(pregnancyStats.value.estimatedDueDate)
    )

    val summary: StateFlow<HomeSummary> = repository.products.map { list ->
        HomeSummary(
            totalItems = list.size,
            purchasedCount = list.count { it.status == ProductStatus.PURCHASED || it.status == ProductStatus.GIFTED },
            pendingCount = list.count { it.status == ProductStatus.PENDING },
            hospitalBagCount = list.count { it.status == ProductStatus.HOSPITAL_PACKED }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeSummary(3, 3, 0, 0)
    )

    private val omniUseCase = OmniPregnancyContextUseCase()

    val omniSnapshot: StateFlow<OmniDashboardSnapshot> = combine(
        _lmpDate,
        repository.products
    ) { lmp, prods ->
        val meds = localPreferences?.getMedicationsCache() ?: emptyList()
        val quotes = localPreferences?.getRadarQuotesCache() ?: emptyList()
        val bagIds = localPreferences?.getHospitalBagCheckedIds() ?: emptySet()
        val bcv = localPreferences?.getLastBcvRate() ?: 1.0
        omniUseCase.computeSnapshot(lmp, prods, meds, quotes, bagIds, bcv)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = omniUseCase.computeSnapshot(
            _lmpDate.value,
            repository.products.value,
            localPreferences?.getMedicationsCache() ?: emptyList(),
            localPreferences?.getRadarQuotesCache() ?: emptyList(),
            localPreferences?.getHospitalBagCheckedIds() ?: emptySet(),
            localPreferences?.getLastBcvRate() ?: 1.0
        )
    )

    private val _isGeneratingDiagnosis = MutableStateFlow(false)
    val isGeneratingDiagnosis: StateFlow<Boolean> = _isGeneratingDiagnosis.asStateFlow()

    private val _diagnosisReport = MutableStateFlow<String?>(null)
    val diagnosisReport: StateFlow<String?> = _diagnosisReport.asStateFlow()

    fun generateOmniDiagnosis(apiKey: String) {
        if (apiKey.isBlank()) return
        viewModelScope.launch {
            _isGeneratingDiagnosis.value = true
            try {
                val prompt = omniUseCase.buildAiPrompt(omniSnapshot.value, _babyName.value)
                val result = geminiClient.generateTextDiagnosis(prompt, apiKey)
                _diagnosisReport.value = result.getOrNull() ?: "No se pudo generar el diagnóstico con Gemini. Revisa la conexión."
            } catch (e: Exception) {
                _diagnosisReport.value = "Error al consultar Gemini: ${e.localizedMessage}"
            } finally {
                _isGeneratingDiagnosis.value = false
            }
        }
    }

    private val _dailyWisdom = MutableStateFlow(
        DailyAiWisdom(
            date = LocalDate.now().toString(),
            message = localPreferences?.getDailyAiWisdom()?.second
                ?: "¡Buenos días, hermosa familia! El bebé sigue creciendo fuerte día tras día. Que hoy sea una jornada llena de paz, amor y alegría compartida en casa."
        )
    )
    val dailyWisdom: StateFlow<DailyAiWisdom> = _dailyWisdom.asStateFlow()

    fun refreshDailyWisdom(apiKey: String, activeUser: String, forceRefresh: Boolean = false) {
        val todayStr = LocalDate.now().toString()
        val stats = pregnancyStats.value
        val sum = summary.value

        // Si ya hay un mensaje en caché para hoy y no estamos forzando regeneración
        val cached = localPreferences?.getDailyAiWisdom()
        if (!forceRefresh && cached != null && cached.first == todayStr) {
            _dailyWisdom.value = DailyAiWisdom(todayStr, cached.second, false)
            return
        }

        if (apiKey.isBlank()) {
            val fallback = buildOfflineWisdom(activeUser, stats, sum)
            _dailyWisdom.value = DailyAiWisdom(todayStr, fallback, false)
            localPreferences?.saveDailyAiWisdom(todayStr, fallback)
            return
        }

        viewModelScope.launch {
            _dailyWisdom.value = _dailyWisdom.value.copy(isLoading = true)
            val result = geminiClient.generateDailyBabyWisdom(
                activeUser = activeUser,
                babyName = _babyName.value,
                gestationalWeeks = stats.currentWeeks,
                gestationalDays = stats.currentDays,
                babySizeComparison = stats.babySizeComparison,
                daysRemaining = stats.daysRemaining,
                purchasedCount = sum.purchasedCount,
                totalItems = sum.totalItems,
                apiKey = apiKey
            )

            val finalMsg = result.getOrElse {
                buildOfflineWisdom(activeUser, stats, sum)
            }

            _dailyWisdom.value = DailyAiWisdom(todayStr, finalMsg, false)
            localPreferences?.saveDailyAiWisdom(todayStr, finalMsg)
        }
    }

    private fun buildOfflineWisdom(user: String, stats: PregnancyStats, sum: HomeSummary): String {
        val isMom = user.contains("Mamá", ignoreCase = true)
        val name = if (isMom) "Mamita Britney 🌸" else "Papá Edward 💙"
        val partner = if (isMom) "Edward" else "Britney"

        return if (isMom) {
            "¡Buenos días, $name! Hoy estás en la semana ${stats.currentWeeks} + ${stats.currentDays} días y tu bebé tiene el tamaño de ${stats.babySizeComparison}. En casa ya tienen ${sum.purchasedCount} de ${sum.totalItems} artículos listos para su llegada. Recuerda tomar tus vitaminas, hidratarte y disfrutar cada patadita junto a $partner."
        } else {
            "¡Buenos días, $name! Tu bebé está en la semana ${stats.currentWeeks} + ${stats.currentDays} días, creciendo sano y midiendo como ${stats.babySizeComparison}. Tienen ${sum.purchasedCount} artículos listos en el inventario. Hoy es un gran día para consentir a $partner, recordarle sus vitaminas y soñar juntos con la llegada del pequeño."
        }
    }

    fun updateBabySettings(name: String, lmp: LocalDate) {
        if (name.isNotBlank()) _babyName.value = name
        _lmpDate.value = lmp
    }

    fun updateLmp(lmp: LocalDate) {
        _lmpDate.value = lmp
    }

    fun updateGestationalAge(weeks: Int, days: Int = 0) {
        _lmpDate.value = LocalDate.now().minusWeeks(weeks.toLong()).minusDays(days.toLong())
    }
}
