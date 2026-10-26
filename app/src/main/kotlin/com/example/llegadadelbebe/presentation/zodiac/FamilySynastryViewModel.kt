package com.example.llegadadelbebe.presentation.zodiac

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.core.util.AstronomicalEphemerisCalculator
import com.example.llegadadelbebe.core.util.PregnancyCalculator
import com.example.llegadadelbebe.data.remote.GeminiClient
import com.example.llegadadelbebe.domain.model.BabyProjectedChart
import com.example.llegadadelbebe.domain.model.DayMoonProjection
import com.example.llegadadelbebe.domain.model.FamilyElementalBalance
import com.example.llegadadelbebe.domain.model.FatherBabyFusionReport
import com.example.llegadadelbebe.domain.model.NatalChartProfile
import com.example.llegadadelbebe.domain.model.ParentCompatibilityReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class FamilySynastryViewModel(
    private val localPreferences: LocalPreferences,
    private val geminiClient: GeminiClient = GeminiClient()
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _simulatedBirthHour = MutableStateFlow(14)
    val simulatedBirthHour: StateFlow<Int> = _simulatedBirthHour.asStateFlow()

    private val _fatherProfile = MutableStateFlow(
        AstronomicalEphemerisCalculator.getFatherProfile(
            localPreferences.getFatherBirthDate(),
            localPreferences.getFatherBirthTime()
        )
    )
    val fatherProfile: StateFlow<NatalChartProfile> = _fatherProfile.asStateFlow()

    private val _motherProfile = MutableStateFlow(
        AstronomicalEphemerisCalculator.getMotherProfile(
            localPreferences.getMotherBirthDate(),
            localPreferences.getMotherBirthTime()
        )
    )
    val motherProfile: StateFlow<NatalChartProfile> = _motherProfile.asStateFlow()

    private val _babyChart = MutableStateFlow(calculateCurrentBabyChart(14))
    val babyChart: StateFlow<BabyProjectedChart> = _babyChart.asStateFlow()

    private val _moonWindow = MutableStateFlow(
        AstronomicalEphemerisCalculator.calculateMoonWindow(
            _babyChart.value.fppDate,
            _fatherProfile.value.moon.sign,
            _motherProfile.value.moon.sign
        )
    )
    val moonWindow: StateFlow<List<DayMoonProjection>> = _moonWindow.asStateFlow()

    private val _parentReport = MutableStateFlow(
        AstronomicalEphemerisCalculator.evaluateParentCompatibility(
            _fatherProfile.value,
            _motherProfile.value
        )
    )
    val parentReport: StateFlow<ParentCompatibilityReport> = _parentReport.asStateFlow()

    private val _fatherFusionReport = MutableStateFlow(
        AstronomicalEphemerisCalculator.evaluateFatherBabyFusion(
            _fatherProfile.value,
            _babyChart.value
        )
    )
    val fatherFusionReport: StateFlow<FatherBabyFusionReport> = _fatherFusionReport.asStateFlow()

    private val _elementalBalance = MutableStateFlow(
        AstronomicalEphemerisCalculator.calculateFamilyElementalBalance(
            _fatherProfile.value,
            _motherProfile.value,
            _babyChart.value
        )
    )
    val elementalBalance: StateFlow<FamilyElementalBalance> = _elementalBalance.asStateFlow()

    private val _geminiReading = MutableStateFlow("")
    val geminiReading: StateFlow<String> = _geminiReading.asStateFlow()

    private val _isLoadingReading = MutableStateFlow(false)
    val isLoadingReading: StateFlow<Boolean> = _isLoadingReading.asStateFlow()

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun setSimulatedHour(hour: Int) {
        _simulatedBirthHour.value = hour
        val newBabyChart = calculateCurrentBabyChart(hour)
        _babyChart.value = newBabyChart
        _fatherFusionReport.value = AstronomicalEphemerisCalculator.evaluateFatherBabyFusion(
            _fatherProfile.value,
            newBabyChart
        )
        _elementalBalance.value = AstronomicalEphemerisCalculator.calculateFamilyElementalBalance(
            _fatherProfile.value,
            _motherProfile.value,
            newBabyChart
        )
    }

    fun updateFatherBirth(date: LocalDate, time: LocalTime) {
        localPreferences.saveFatherBirth(date, time)
        val profile = AstronomicalEphemerisCalculator.getFatherProfile(date, time)
        _fatherProfile.value = profile
        recalculateAll()
    }

    fun updateMotherBirth(date: LocalDate, time: LocalTime) {
        localPreferences.saveMotherBirth(date, time)
        val profile = AstronomicalEphemerisCalculator.getMotherProfile(date, time)
        _motherProfile.value = profile
        recalculateAll()
    }

    private fun recalculateAll() {
        val baby = calculateCurrentBabyChart(_simulatedBirthHour.value)
        _babyChart.value = baby
        _moonWindow.value = AstronomicalEphemerisCalculator.calculateMoonWindow(
            baby.fppDate,
            _fatherProfile.value.moon.sign,
            _motherProfile.value.moon.sign
        )
        _parentReport.value = AstronomicalEphemerisCalculator.evaluateParentCompatibility(
            _fatherProfile.value,
            _motherProfile.value
        )
        _fatherFusionReport.value = AstronomicalEphemerisCalculator.evaluateFatherBabyFusion(
            _fatherProfile.value,
            baby
        )
        _elementalBalance.value = AstronomicalEphemerisCalculator.calculateFamilyElementalBalance(
            _fatherProfile.value,
            _motherProfile.value,
            baby
        )
    }

    private fun calculateCurrentBabyChart(simulatedHour: Int): BabyProjectedChart {
        val lmp = localPreferences.getLmpDate()
        val stats = PregnancyCalculator.calculateFromLmp(lmp)
        val babyName = localPreferences.getBabyName()
        return AstronomicalEphemerisCalculator.calculateBabyProjectedChart(
            babyName = babyName,
            fppDate = stats.estimatedDueDate,
            simulatedHour = simulatedHour
        )
    }

    fun requestGeminiReading(apiKey: String) {
        if (apiKey.isBlank()) {
            _geminiReading.value = "Clave API no disponible."
            return
        }

        viewModelScope.launch {
            _isLoadingReading.value = true

            val dad = _fatherProfile.value
            val mom = _motherProfile.value
            val baby = _babyChart.value
            val parent = _parentReport.value
            val fusion = _fatherFusionReport.value

            val summary = """
                CONFIGURACIÓN NATAL DE LA FAMILIA:
                
                💙 PAPÁ: ${dad.personRole}
                - Nacimiento: ${dad.birthDate} a las ${dad.birthTime}
                - Sol Natal: ${dad.sun.formatted} (Elemento: ${dad.sun.elementLabel})
                - Luna Natal: ${dad.moon.formatted} (Elemento: ${dad.moon.elementLabel})
                - Ascendente Natal: ${dad.ascendant.formatted} (Elemento: ${dad.ascendant.elementLabel})
                
                🌸 MAMÁ: ${mom.personRole}
                - Nacimiento: ${mom.birthDate} a las ${mom.birthTime}
                - Sol Natal: ${mom.sun.formatted} (Elemento: ${mom.sun.elementLabel})
                - Luna Natal: ${mom.moon.formatted} (Elemento: ${mom.moon.elementLabel})
                - Ascendente Natal: ${mom.ascendant.formatted} (Elemento: ${mom.ascendant.elementLabel})
                
                👶 BEBÉ: ${baby.babyName} (Varón en gestación)
                - Fecha Probable de Parto (FPP): ${baby.fppDate}
                - Sol Proyectado del Bebé: ${baby.sun.formatted} (Elemento: ${baby.sun.elementLabel})
                - Luna Proyectada en FPP: ${baby.projectedMoon.formatted} (${baby.moonPhaseEmoji} ${baby.moonPhaseName}, ${baby.moonIlluminationPercent}% iluminada)
                - Ascendente Simulado (Turno ${baby.simulatedShiftName}): ${baby.simulatedAscendant.formatted}
                
                ASPECTOS MAESTROS DETECTADOS:
                - Vínculo Central: ${parent.masterBondTitle}
                - Conexión Sol-Sol: ${parent.sunConnectionDescription}
                - Conexión Luna-Luna: ${parent.moonConnectionDescription}
                - Conexión Ascendentes: ${parent.ascendantConnectionDescription}
                - Fusión Luna Papá + Bebé: ${fusion.moonFusionExplanation}
                - Fusión Ascendente Papá + Bebé: ${fusion.ascendantFusionExplanation}
            """.trimIndent()

            val result = geminiClient.getFamilySynastryReading(summary, apiKey)
            _isLoadingReading.value = false

            if (result.isSuccess) {
                _geminiReading.value = result.getOrNull() ?: ""
            } else {
                _geminiReading.value = "Aviso: No se pudo conectar con Gemini (${result.exceptionOrNull()?.message}). Intenta de nuevo en unos segundos."
            }
        }
    }
}
