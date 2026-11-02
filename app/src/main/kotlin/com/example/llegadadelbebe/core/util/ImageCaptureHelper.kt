package com.example.llegadadelbebe.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Utilidad robusta para captura y procesamiento de imágenes de la cámara y galería.
 * Protege contra:
 * 1. Fallos de FileProvider (IllegalArgumentException).
 * 2. Desbordamientos de memoria (OutOfMemoryError) en sensores de alta resolución (50MP/200MP de HyperOS/ColorOS).
 * 3. Rotación invertida según metadatos EXIF.
 */
object ImageCaptureHelper {

    /**
     * Crea un archivo temporal seguro en la carpeta 'camera_photos' del caché y devuelve su content URI.
     */
    fun createCameraTempUri(context: Context, prefix: String = "camera_capture"): Uri {
        val photoDir = File(context.cacheDir, "camera_photos")
        if (!photoDir.exists()) {
            photoDir.mkdirs()
        }
        val photoFile = File(photoDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    /**
     * Convierte un Uri (de cámara o galería) a una cadena Base64 JPEG optimizada para Gemini.
     * Realiza sub-muestreo inteligente para evitar OOM y corrige la rotación EXIF.
     */
    fun uriToOptimizedBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280,
        quality: Int = 85
    ): String? {
        return try {
            // 1. Leer dimensiones sin cargar los bytes en RAM (inJustDecodeBounds)
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            }

            var sampleSize = 1
            val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxSide / sampleSize > 2048) {
                sampleSize *= 2
            }

            // 2. Decodificar con submuestreo seguro
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            } ?: return null

            // 3. Corregir orientación EXIF
            val rotationDegrees = getExifRotationDegrees(context, uri)
            val orientedBitmap = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }

            // 4. Escalar al tamaño máximo recomendado para OCR
            val scaledBitmap = scaleBitmapDown(orientedBitmap, maxDimension)

            // 5. Comprimir a JPEG
            val out = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()

            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Escala proporcionalmente el bitmap para que su dimensión mayor no supere maxDimension.
     */
    fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (width > height) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun getExifRotationDegrees(context: Context, uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Throwable) {
            0f
        }
    }
}
