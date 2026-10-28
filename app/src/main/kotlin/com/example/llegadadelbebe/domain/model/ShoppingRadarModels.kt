package com.example.llegadadelbebe.domain.model

import java.util.Locale
import java.util.UUID

enum class CurrencyType(val symbol: String, val label: String) {
    VES("Bs.", "Bolívares"),
    USD("$", "Dólares Americanos")
}

data class StoreLocation(
    val name: String,
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val googleMapsName: String = "",
    val isGoogleMapsMatch: Boolean = false
) {
    val hasValidCoordinates: Boolean get() = latitude != 0.0 && longitude != 0.0
    val googleMapsUrl: String
        get() = if (hasValidCoordinates) "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude" else ""
}

data class GoogleMapsPlaceInfo(
    val placeName: String = "",
    val fullAddress: String = "",
    val hasPlaceName: Boolean = false
)

data class BcvExchangeRate(
    val rate: Double,
    val lastUpdated: String = "",
    val isFallback: Boolean = false
) {
    val formattedRate: String get() = String.format(Locale.US, "%.2f Bs./$", rate)
}

data class FarmatodoProductBenchmark(
    val id: String,
    val name: String,
    val brand: String,
    val fullPriceVes: Double,
    val offerPriceVes: Double,
    val priceUsd: Double,
    val offerPriceUsd: Double,
    val mediaImageUrl: String = "",
    val urlSlug: String = "",
    val barcode: String = ""
) {
    val hasOffer: Boolean get() = offerPriceVes in 0.01..fullPriceVes
    val effectivePriceVes: Double get() = if (hasOffer) offerPriceVes else fullPriceVes
    val effectivePriceUsd: Double get() = if (hasOffer) offerPriceUsd else priceUsd
    val webProductUrl: String
        get() = if (urlSlug.isNotBlank()) "https://www.farmatodo.com.ve/producto/$urlSlug" else "https://www.farmatodo.com.ve/buscar?product=$name"
}

data class ProductPriceQuote(
    val id: String = UUID.randomUUID().toString(),
    val productName: String,
    val brand: String = "",
    val category: ProductCategory = ProductCategory.DIAPERS,
    val stageSize: String = "",
    val packageCount: Int = 1,
    val unitsPerPackage: Int = 1,
    val storeName: String,
    val storeAddress: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val priceAmount: Double, // Precio registrado en tienda para 1 paquete (o 1 unidad si es unitario)
    val priceCurrency: CurrencyType = CurrencyType.USD,
    val bcvRateApplied: Double = 1.0,
    val priceUsdEquivalent: Double, // Precio de 1 paquete en USD (referencia BCV)
    val unitPriceUsd: Double,       // Costo por unidad individual (ej: por pañal o toallita)
    val quotedAt: String,
    val registeredBy: String = "Papá (Edward)",
    val photoBase64: String = "",
    val notes: String = "",
    val barcode: String = "",
    val googleMapsName: String = "",
    val farmatodoBenchmark: FarmatodoProductBenchmark? = null
) {
    val totalUnits: Int get() = (packageCount.coerceAtLeast(1)) * (unitsPerPackage.coerceAtLeast(1))
    val isMultiUnitPackage: Boolean get() = unitsPerPackage > 1

    // Precio por paquete (1 paquete en USD y VES)
    val packagePriceUsd: Double get() = priceUsdEquivalent
    val packagePriceVes: Double get() = packagePriceUsd * bcvRateApplied

    // Costo total si se adquieren varios paquetes (ej: 2 paquetes a $10 = $20)
    val totalPriceUsd: Double get() = packagePriceUsd * packageCount.coerceAtLeast(1)
    val totalPriceVes: Double get() = packagePriceVes * packageCount.coerceAtLeast(1)

    // Costo por unidad individual (ej: por pañal o toallita)
    val effectiveUnitPriceUsd: Double
        get() = if (unitsPerPackage > 1) packagePriceUsd / unitsPerPackage else packagePriceUsd

    val effectiveUnitPriceVes: Double
        get() = effectiveUnitPriceUsd * bcvRateApplied

    val priceVesEquivalent: Double get() = packagePriceVes

    val formattedPriceStore: String
        get() = if (priceCurrency == CurrencyType.VES) {
            if (isMultiUnitPackage) String.format(Locale.US, "Bs. %.2f / pqte", priceAmount)
            else String.format(Locale.US, "Bs. %.2f", priceAmount)
        } else {
            if (isMultiUnitPackage) String.format(Locale.US, "$%.2f / pqte", priceAmount)
            else String.format(Locale.US, "$%.2f", priceAmount)
        }

    val formattedUsdEquivalent: String
        get() = if (isMultiUnitPackage) {
            String.format(Locale.US, "$%.2f / pqte", packagePriceUsd)
        } else {
            String.format(Locale.US, "$%.2f", packagePriceUsd)
        }

    val formattedVesEquivalent: String
        get() = if (isMultiUnitPackage) {
            String.format(Locale.US, "Bs. %.2f / pqte", packagePriceVes)
        } else {
            String.format(Locale.US, "Bs. %.2f", packagePriceVes)
        }

    val formattedUnitPriceUsd: String
        get() = if (isMultiUnitPackage) {
            String.format(Locale.US, "$%.3f / ud", effectiveUnitPriceUsd)
        } else {
            String.format(Locale.US, "$%.2f", effectiveUnitPriceUsd)
        }

    val formattedTotalUsd: String
        get() = String.format(Locale.US, "$%.2f", totalPriceUsd)

    val formattedTotalVes: String
        get() = String.format(Locale.US, "Bs. %.2f", totalPriceVes)
}

data class PriceComparisonVerdict(
    val quoteCount: Int,
    val bestQuote: ProductPriceQuote,
    val highestQuote: ProductPriceQuote?,
    val maxSavingsUsd: Double,
    val maxSavingsPercent: Double,
    val summaryMessage: String,
    val farmatodoComparisonMessage: String? = null
)

/**
 * Memoria GPS de comercios conocidos (Mercafácil, Locatel, Farmatodo, etc.).
 * Almacena la coordenada geográfica exacta y el nombre para auto-detectarlo
 * por proximidad geográfica en futuras visitas.
 */
data class KnownStoreLocation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val visitCount: Int = 1,
    val lastVisitedAt: String = "",
    val googleMapsName: String = "",
    val isUserConfirmed: Boolean = true
) {
    val googleMapsUrl: String
        get() = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"

    val hasGoogleMapsMatch: Boolean
        get() = googleMapsName.isNotBlank() && (
            name.contains(googleMapsName, ignoreCase = true) ||
            googleMapsName.contains(name, ignoreCase = true)
        )
}

/**
 * Línea de costo individual vinculando inventario, cotizaciones de tiendas y Farmatodo.
 */
data class ProductBudgetLine(
    val productId: String,
    val productName: String,
    val brand: String,
    val category: ProductCategory,
    val isPurchased: Boolean,
    val quantity: Int,
    val unitPriceUsd: Double,
    val totalPriceUsd: Double,
    val sourcePriceLabel: String, // ej: "Mercafácil ($10.50/pqte • $0.292/ud)", "Farmatodo Oficial ($11.33)", "Estimado"
    val packageCount: Int = 1,
    val unitsPerPackage: Int = 1,
    val isPackageProduct: Boolean = false,
    val quantityDisplayLabel: String = ""
) {
    val formattedTotalUsd: String get() = String.format(Locale.US, "$%.2f", totalPriceUsd)
    fun formattedTotalVes(bcvRate: Double): String = String.format(Locale.US, "Bs. %.2f", totalPriceUsd * bcvRate)
}

/**
 * Presupuesto Integral Familiar:
 * Cuánto se ha gastado en lo que ya tenemos y cuánto falta por gastar para completar el 100%
 * de la llegada del bebé, cruzando precios de tiendas registradas con GPS y el catálogo de Farmatodo.
 */
data class FamilyBudgetReport(
    val totalSpentUsd: Double,
    val totalPendingUsd: Double,
    val bcvRate: Double,
    val purchasedCount: Int,
    val pendingCount: Int,
    val lines: List<ProductBudgetLine>
) {
    val totalProjectedUsd: Double get() = totalSpentUsd + totalPendingUsd
    val totalSpentVes: Double get() = totalSpentUsd * bcvRate
    val totalPendingVes: Double get() = totalPendingUsd * bcvRate
    val totalProjectedVes: Double get() = totalProjectedUsd * bcvRate

    val spentPercentage: Float
        get() = if (totalProjectedUsd > 0.0) (totalSpentUsd / totalProjectedUsd).toFloat().coerceIn(0f, 1f) else 0f

    val formattedSpentUsd: String get() = String.format(Locale.US, "$%.2f", totalSpentUsd)
    val formattedPendingUsd: String get() = String.format(Locale.US, "$%.2f", totalPendingUsd)
    val formattedProjectedUsd: String get() = String.format(Locale.US, "$%.2f", totalProjectedUsd)

    val formattedSpentVes: String get() = String.format(Locale.US, "Bs. %.2f", totalSpentVes)
    val formattedPendingVes: String get() = String.format(Locale.US, "Bs. %.2f", totalPendingVes)
    val formattedProjectedVes: String get() = String.format(Locale.US, "Bs. %.2f", totalProjectedVes)
}
