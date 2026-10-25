package com.example.llegadadelbebe.presentation.consumables

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llegadadelbebe.data.remote.GeminiClient
import com.example.llegadadelbebe.data.repository.BabyInventoryRepository
import com.example.llegadadelbebe.domain.model.BabyProduct
import com.example.llegadadelbebe.domain.model.ConsumablesAuditSummary
import com.example.llegadadelbebe.domain.model.ProductStatus
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

    private val _geminiAdvice = MutableStateFlow("")
    val geminiAdvice: StateFlow<String> = _geminiAdvice.asStateFlow()

    private val _isLoadingAdvice = MutableStateFlow(false)
    val isLoadingAdvice: StateFlow<Boolean> = _isLoadingAdvice.asStateFlow()

    val products: StateFlow<List<BabyProduct>> = repository.products

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
            _geminiAdvice.value = "Clave API no disponible."
            return
        }

        viewModelScope.launch {
            _isLoadingAdvice.value = true
            val currentSummary = auditSummary.value
            val currentProducts = repository.products.value

            val ownedText = currentProducts
                .filter { it.status != ProductStatus.PENDING }
                .joinToString("\n") { "- ${it.name} (${it.brand} ${it.model}): ${it.quantityCurrent} uds (Meta: ${it.quantityTarget}), Talla ${it.stageSize.ifBlank { "Única" }}" }
                .ifBlank { "- Ningún producto comprado aún." }

            val missingText = currentProducts
                .filter { it.status == ProductStatus.PENDING || it.quantityCurrent < it.quantityTarget }
                .joinToString("\n") {
                    val faltan = (it.quantityTarget - it.quantityCurrent).coerceAtLeast(1)
                    "- ${it.name} (${it.brand}): Tenemos ${it.quantityCurrent}, faltan $faltan uds"
                }
                .ifBlank { "- Sin artículos marcados como pendientes." }

            val summaryText = """
                ARTÍCULOS QUE YA TENEMOS EN EL INVENTARIO:
                $ownedText

                ARTÍCULOS DEL INVENTARIO QUE NOS FALTAN O ESTÁN INCOMPLETOS:
                $missingText

                EVALUACIÓN DE METAS ESENCIALES:
                - Pañales RN: ${currentSummary.diapersRn.currentUnits} uds (Meta: ${currentSummary.diapersRn.recommendedUnits})
                - Pañales Talla 1: ${currentSummary.diapersSize1.currentUnits} uds (Meta: ${currentSummary.diapersSize1.recommendedUnits})
                - Toallitas húmedas: ${currentSummary.wipes.currentUnits} paquetes (Meta: ${currentSummary.wipes.recommendedUnits})
                - Jabón/Shampoo de bebé: ${currentSummary.babySoaps.currentUnits} frascos (Meta: ${currentSummary.babySoaps.recommendedUnits})
                - Ropita RN: ${currentSummary.clothingRn.currentUnits} prendas (Meta: ${currentSummary.clothingRn.recommendedUnits})
                - Ropita 0-3M: ${currentSummary.clothing0to3M.currentUnits} prendas (Meta: ${currentSummary.clothing0to3M.recommendedUnits})
            """.trimIndent()

            val result = geminiClient.getConsumablesAuditAdvice(summaryText, apiKey)
            _isLoadingAdvice.value = false

            if (result.isSuccess) {
                _geminiAdvice.value = result.getOrNull() ?: ""
            } else {
                _geminiAdvice.value = "Aviso: No se pudo conectar con Gemini (${result.exceptionOrNull()?.message}). Intenta de nuevo en unos segundos."
            }
        }
    }
}
