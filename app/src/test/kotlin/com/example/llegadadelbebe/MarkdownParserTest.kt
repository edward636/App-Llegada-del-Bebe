package com.example.llegadadelbebe

import com.example.llegadadelbebe.core.ui.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun testBoldAsterisksAreConvertedWithoutRawSymbols() {
        val raw = "1. **Radar de Compras:** Escaneo de precios en anaquel."
        val result = MarkdownParser.parse(raw)

        // No deben quedar asteriscos crudos en el texto renderizado
        assertFalse("No deben quedar asteriscos crudos '**'", result.text.contains("**"))
        assertEquals("1. Radar de Compras: Escaneo de precios en anaquel.", result.text)

        // Debe haber estilos de negrita aplicados
        assertTrue("Debe contener estilos de texto", result.spanStyles.isNotEmpty())
    }

    @Test
    fun testHeadersAndBulletPoints() {
        val raw = """
            ## Novedades de la Versión
            - Primer punto importante
            - Segundo punto con **resaltado**
        """.trimIndent()

        val result = MarkdownParser.parse(raw)
        assertFalse(result.text.contains("##"))
        assertFalse(result.text.contains("**"))
        assertTrue("Debe contener viñetas limpias", result.text.contains("• Primer punto"))
        assertTrue("Debe contener viñetas limpias", result.text.contains("• Segundo punto con resaltado"))
    }
}
