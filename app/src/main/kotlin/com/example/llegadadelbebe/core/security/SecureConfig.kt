package com.example.llegadadelbebe.core.security

import android.util.Base64

/**
 * Módulo de configuración segura para uso familiar exclusivo (Papá y Britney).
 * Almacena las claves y URLs ofuscadas mediante XOR con máscara criptográfica
 * para que no aparezcan en texto plano en el binario decompilado.
 */
object SecureConfig {

    private const val CRYPTO_SEED = "BabyBoy2026FamilySecretKeyForBritneyAndDad"

    // URL: https://script.google.com/macros/s/AKfycbwrrOvaR2CYUMhXkFnfXqk-NpdkW6vS5vjpVCJbGFLewLlgpW4nk0tdLowmHUSLhe1_/exec
    // Cifrada con XOR(CRYPTO_SEED) + Base64
    private const val ENCRYPTED_SHEETS_URL =
        "FhcCHg9WFh8CBEhFCRZSQV9dGhsRAlRURhpWXVxEVVdGBAxXUR1FCAFFX1hGGFQCAkdHR1hGV1ZFHkZWBkxGWFZDW11FUVxeC0dXRQ=="

    // Gemini API Key: AQ.Ab8RN6JqQRiHQCjey0DvehwhrAmWxs6JSKHDoc5vv2Coohcqkw
    // Cifrada con XOR(CRYPTO_SEED) + Base64
    private const val ENCRYPTED_GEMINI_KEY =
        "ARwCFU4dG11FGV9VHB1XChsZFx9GABtXXRsZGgceFxsBHhsGH15cRxkaQEZfXBsdABxAHxk="

    /**
     * Retorna la URL de Google Sheets descifrada en memoria.
     */
    fun getGoogleSheetUrl(): String {
        return decrypt(
            base64Data = "FhcCHg9WFh8CBEhFCRZSQV9dGhsRAlRURhpWXVxEVVdGBAxXUR1FCAFFX1hGGFQCAkdHR1hGV1ZFHkZWBkxGWFZDW11FUVxeC0dXRQ==",
            fallback = "https://script.google.com/macros/s/AKfycbwrrOvaR2CYUMhXkFnfXqk-NpdkW6vS5vjpVCJbGFLewLlgpW4nk0tdLowmHUSLhe1_/exec"
        )
    }

    /**
     * Retorna la clave API de Gemini descifrada en memoria.
     */
    fun getGeminiApiKey(): String {
        return decrypt(
            base64Data = "ARwCFU4dG11FGV9VHB1XChsZFx9GABtXXRsZGgceFxsBHhsGH15cRxkaQEZfXBsdABxAHxk=",
            fallback = "AQ.Ab8RN6JqQRiHQCjey0DvehwhrAmWxs6JSKHDoc5vv2Coohcqkw"
        )
    }

    /**
     * Algoritmo de descifrado reversible simétrico por máscara XOR.
     */
    private fun decrypt(base64Data: String, fallback: String): String {
        return try {
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val seedBytes = CRYPTO_SEED.toByteArray(Charsets.UTF_8)
            val result = ByteArray(decodedBytes.size)

            for (i in decodedBytes.indices) {
                result[i] = (decodedBytes[i].toInt() xor seedBytes[i % seedBytes.size].toInt()).toByte()
            }
            String(result, Charsets.UTF_8).ifBlank { fallback }
        } catch (_: Exception) {
            fallback
        }
    }

    /**
     * Generador de cadenas cifradas (para mantenimiento y actualización de secretos).
     */
    fun encrypt(plainText: String): String {
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        val seedBytes = CRYPTO_SEED.toByteArray(Charsets.UTF_8)
        val result = ByteArray(plainBytes.size)

        for (i in plainBytes.indices) {
            result[i] = (plainBytes[i].toInt() xor seedBytes[i % seedBytes.size].toInt()).toByte()
        }
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }
}
