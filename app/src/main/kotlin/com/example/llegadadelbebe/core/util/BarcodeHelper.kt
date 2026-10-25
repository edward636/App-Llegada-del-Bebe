package com.example.llegadadelbebe.core.util

/**
 * Utilidad para decodificar prefijos GS1 de códigos de barras (EAN-13, UPC)
 * e identificar variantes específicas para Venezuela o internacionales.
 */
object BarcodeHelper {

    /**
     * Resuelve el país o variante a partir de los dígitos iniciales del código de barras.
     */
    fun resolveCountryVariant(barcode: String, manufacturedIn: String = ""): String {
        val clean = barcode.filter { it.isDigit() }
        if (clean.length in 8..14) {
            when {
                clean.startsWith("759") -> return "Venezuela 🇻🇪 (GS1 Nacional)"
                clean.startsWith("770") -> return "Colombia 🇨🇴"
                clean.startsWith("775") -> return "Perú 🇵🇪"
                clean.startsWith("779") -> return "Argentina 🇦🇷"
                clean.startsWith("780") -> return "Chile 🇨🇱"
                clean.startsWith("786") -> return "Ecuador 🇪🇨"
                clean.startsWith("789") || clean.startsWith("790") -> return "Brasil 🇧🇷"
                clean.startsWith("0") || clean.startsWith("1") -> return "EE.UU. / Internacional 🇺🇸"
                clean.startsWith("30") || clean.startsWith("31") || clean.startsWith("32") ||
                clean.startsWith("33") || clean.startsWith("34") || clean.startsWith("35") ||
                clean.startsWith("36") || clean.startsWith("37") -> return "Francia 🇫🇷"
                clean.startsWith("80") || clean.startsWith("81") || clean.startsWith("82") ||
                clean.startsWith("83") -> return "Italia 🇮🇹"
                clean.startsWith("84") -> return "España 🇪🇸"
                clean.startsWith("40") || clean.startsWith("41") || clean.startsWith("42") ||
                clean.startsWith("43") || clean.startsWith("44") -> return "Alemania 🇩🇪"
                clean.startsWith("50") -> return "Reino Unido 🇬🇧"
                clean.startsWith("690") || clean.startsWith("691") || clean.startsWith("692") ||
                clean.startsWith("693") || clean.startsWith("694") || clean.startsWith("695") -> return "China 🇨🇳"
            }
        }

        val lowerOrigin = manufacturedIn.lowercase()
        return when {
            lowerOrigin.contains("venezuela") -> "Venezuela 🇻🇪 (Hecho local)"
            lowerOrigin.contains("colombia") -> "Colombia 🇨🇴"
            lowerOrigin.contains("peru") || lowerOrigin.contains("perú") -> "Perú 🇵🇪"
            lowerOrigin.contains("brasil") || lowerOrigin.contains("brazil") -> "Brasil 🇧🇷"
            lowerOrigin.contains("estados unidos") || lowerOrigin.contains("ee.uu") || lowerOrigin.contains("usa") -> "EE.UU. 🇺🇸"
            lowerOrigin.contains("francia") || lowerOrigin.contains("france") -> "Francia 🇫🇷"
            lowerOrigin.contains("italia") || lowerOrigin.contains("italy") -> "Italia 🇮🇹"
            manufacturedIn.isNotBlank() -> manufacturedIn
            else -> "Internacional"
        }
    }

    /**
     * Determina si el producto es una presentación registrada o distribuida en Venezuela.
     */
    fun isVenezuelanProduct(barcode: String, contextText: String = ""): Boolean {
        val clean = barcode.filter { it.isDigit() }
        if (clean.startsWith("759")) return true
        val lower = contextText.lowercase()
        return lower.contains("venezuela") || lower.contains("mpps") || lower.contains("sacs") || lower.contains("cpe")
    }
}
