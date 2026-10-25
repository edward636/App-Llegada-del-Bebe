package com.example.llegadadelbebe.core.util

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Escáner on-device mediante Google ML Kit para decodificar códigos de barra
 * (EAN-13, EAN-8, UPC-A, UPC-E, Code 128) con máxima exactitud y sin conexión a internet.
 */
object BarcodeScannerHelper {

    /**
     * Escanea el [bitmap] capturado por la cámara en búsqueda de códigos de barra.
     */
    suspend fun scanBarcode(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val detectedCode = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                    continuation.resume(detectedCode?.trim())
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        } catch (_: Exception) {
            continuation.resume(null)
        }
    }
}
