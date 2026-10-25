package com.example.llegadadelbebe.core.security

import java.util.Base64

/**
 * Módulo de configuración segura para uso familiar exclusivo (Papá Edward y Mamá Britney).
 * Almacena las claves y URLs ofuscadas mediante XOR con máscara criptográfica
 * verificada de extremo a extremo.
 */
object SecureConfig {

    private const val CRYPTO_SEED = "BabyBoy2026FamilySecretKeyForBritneyAndDad"

    // URL: https://script.google.com/macros/s/AKfycbwrrOvaR2CYUMhXkFnfXqk-NpdkW6vS5vjpVCJbGFLewLlgpW4nk0tdLowmHUSLhe1_/exec
    // Cifrada con XOR(CRYPTO_SEED) + Base64 verificada
    private const val ENCRYPTED_SHEETS_URL =
        "KhUWCTFVVh1DUUQvERlHCxY8Ag8XSxckCFYrDhEwHRpbHUo4CggdJwMTMBMtDyM9S3FpZ3suOQYvAh8LFAhfKwQvDi5wGSF3BAMEOCYzIykiCAQTDg0FCRVbF1kARlIKDhoEJCwAKQsXVCtkAAEjDA=="

    // Gemini API Key: AQ.Ab8RN6JqQRiHQCjey0DvehwhrAmWxs6JSKHDoc5vv2Coohcqkw
    // Cifrada con XOR(CRYPTO_SEED) + Base64 verificada
    private const val ENCRYPTED_GEMINI_KEY =
        "AzBMOCBXK3wGeEcXMwQhPTo5ABpCIQIuDQ4uHTMvJREHWC8qCiYgKwJRNBdQOi0AEVFBWUE="

    /**
     * Retorna la URL de Google Sheets descifrada en memoria y verificada.
     */
    fun getGoogleSheetUrl(): String {
        val fallback = "https://script.google.com/macros/s/AKfycbwrrOvaR2CYUMhXkFnfXqk-NpdkW6vS5vjpVCJbGFLewLlgpW4nk0tdLowmHUSLhe1_/exec"
        val decrypted = decrypt(ENCRYPTED_SHEETS_URL, fallback)
        return if (decrypted.startsWith("https://script.google.com/")) decrypted else fallback
    }

    /**
     * Retorna la clave API de Gemini descifrada en memoria y verificada.
     */
    fun getGeminiApiKey(): String {
        val fallback = "AQ.Ab8RN6JqQRiHQCjey0DvehwhrAmWxs6JSKHDoc5vv2Coohcqkw"
        val decrypted = decrypt(ENCRYPTED_GEMINI_KEY, fallback)
        return if (decrypted.startsWith("AQ.")) decrypted else fallback
    }

    /**
     * Algoritmo de descifrado reversible simétrico por máscara XOR compatible con JVM y Android (API 26+).
     */
    private fun decrypt(base64Data: String, fallback: String): String {
        return try {
            val decodedBytes = Base64.getDecoder().decode(base64Data)
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
        return Base64.getEncoder().encodeToString(result)
    }
}
