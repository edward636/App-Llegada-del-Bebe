package com.example.llegadadelbebe.presentation.consumables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llegadadelbebe.data.remote.GeminiClient
import com.example.llegadadelbebe.data.repository.BabyInventoryRepository
import com.example.llegadadelbebe.domain.model.ConsumablesAuditSummary
import com.example.llegadadelbebe.domain.usecase.EvaluateConsumablesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConsumablesAuditViewModel(
    private val repository: BabyInventoryRepository,
    private val evaluateUseCase: EvaluateConsumablesUseCase = EvaluateConsumablesUseCase(),
    private val geminiClient: GeminiClient = GeminiClient()
) : ViewModel() {

    private val _geminiAdvice = MutableStateFlow<String>("")
    val geminiAdvice: StateFlow<String> = _geminiAdvice.asStateFlow()

    private val _isLoadingAdvice = MutableStateFlow(false)
    val isLoadingAdvice: StateFlow<Boolean> = _isLoadingAdvice.asStateFlow()

    val auditSummary: StateFlow<ConsumablesAuditSummary> = combine(
        repository.products,
        _geminiAdvice
    ) { products, advice ->
        evaluateUseCase.execute(products, advice)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = evaluateUseCase.execute(repository.products.value, "")
    )

    fun requestGeminiAdvice(apiKey: String) {
        if (apiKey.isBlank()) {
            _geminiAdvice.value = "Por favor, ingresa tu API Key gratuita de Google AI Studio en Ajustes para consultar al Asesor Gemini 3.8."
            return
        }

        viewModelScope.launch {
            _isLoadingAdvice.value = true
            val currentSummary = auditSummary.value
            val summaryText = """
                - Pañales RN: ${currentSummary.diapersRn.currentUnits} unidades registradas (Meta: ${currentSummary.diapersRn.recommendedUnits})
                - Pañales Talla 1: ${currentSummary.diapersSize1.currentUnits} unidades registradas (Meta: ${currentSummary.diapersSize1.recommendedUnits})
                - Toallitas húmedas: ${currentSummary.wipes.currentUnits} paquetes
                - Jabón de bebé: ${currentSummary.babySoaps.currentUnits} frascos
                - Ropita RN: ${currentSummary.clothingRn.currentUnits} prendas
                - Ropita 0-3M: ${currentSummary.clothing0to3M.currentUnits} prendas
            """.trimIndent()

            val result = geminiClient.getConsumablesAuditAdvice(summaryText, apiKey)
            _isLoadingAdvice.value = false

            if (result.isSuccess) {
                _geminiAdvice.value = result.getOrNull() ?: ""
            } else {
                _geminiAdvice.value = "Aviso: No se pudo conectar con Gemini 3.8 (${result.exceptionOrNull()?.message}). Verifica tu conexión o clave API."
            }
        }
    }
}
