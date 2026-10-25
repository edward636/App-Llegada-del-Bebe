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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeSummary(
    val totalItems: Int,
    val purchasedCount: Int,
    val pendingCount: Int,
    val hospitalBagCount: Int
)

class HomeViewModel(
    private val repository: BabyInventoryRepository
) : ViewModel() {

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
