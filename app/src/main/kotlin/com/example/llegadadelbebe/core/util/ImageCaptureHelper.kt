package com.example.llegadadelbebe.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class ProcessedPhotoData(
    val highResBase64: String,
    val thumbBase64: String,
    val scaledBitmap: Bitmap,
    val exifGps: Pair<Double, Double>? = null
)

/**
 * Utilidad centralizada para captura y procesamiento de imágenes de cámara y galería.
 * Protege contra:
 * 1. Fallos de FileProvider (IllegalArgumentException).
 * 2. Pérdida de referencia URI al recrearse la actividad en HyperOS / ColorOS (archivos deterministas).
 * 3. Desbordamientos de memoria (OutOfMemoryError) en sensores de 50MP/200MP mediante inSampleSize.
 * 4. Bloqueo del hilo principal (procesamiento en Dispatchers.IO).
 * 5. Límite de 50,000 caracteres por celda en Google Sheets (separa highRes para Gemini y thumbBase64 ligera para persistencia).
 */
object ImageCaptureHelper {

    /**
     * Crea o reutiliza un archivo temporal determinista en la carpeta 'camera_photos' del caché.
     * Al ser determinista por prefijo, incluso si el sistema recrea el Composable mientras la cámara
     * externa está abierta, se puede recuperar exactamente el mismo Uri.
     */
    fun createCameraTempUri(context: Context, prefix: String = "camera_capture"): Uri {
        val photoDir = File(context.cacheDir, "camera_photos")
        if (!photoDir.exists()) {
            photoDir.mkdirs()
        }
        val photoFile = File(photoDir, "${prefix}_capture.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    /**
     * Procesa un Uri en segundo plano (Dispatchers.IO) devolviendo:
     * - highResBase64 (1280px, JPEG 82%) para OCR multimodal en Gemini.
     * - thumbBase64 (260px, JPEG 68%, ~8KB) para guardar en caché y Google Sheets sin exceder límites de celda.
     * - scaledBitmap (orientado y escalado) listo para escanear códigos de barras con MLKit sin re-decodificar.
     * - exifGps (latitud, longitud) si la fotografía contiene metadatos GPS.
     */
    suspend fun processUriInBackground(
        context: Context,
        uri: Uri,
        highResMaxDim: Int = 1280,
        thumbMaxDim: Int = 260
    ): ProcessedPhotoData? = withContext(Dispatchers.IO) {
        try {
            // 1. Leer dimensiones sin cargar el bitmap en RAM
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            }

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return@withContext null
            }

            var sampleSize = 1
            val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxSide / sampleSize > 2048) {
                sampleSize *= 2
            }

            // 2. Decodificar con submuestreo seguro en RGB_565/ARGB_8888
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            } ?: return@withContext null

            // 3. Extraer orientación EXIF y coordenadas GPS en una sola lectura
            var rotationDegrees = 0f
            var exifGps: Pair<Double, Double>? = null
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    exifGps = StoreLocationHelper.extractGpsFromExif(exif)
                    rotationDegrees = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }
            }

            val orientedBitmap = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }

            // 4. Escalar para OCR de alta nitidez (1280px)
            val highResBitmap = scaleBitmapDown(orientedBitmap, highResMaxDim)
            val highResOut = ByteArrayOutputStream()
            highResBitmap.compress(Bitmap.CompressFormat.JPEG, 82, highResOut)
            val highResBase64 = Base64.encodeToString(highResOut.toByteArray(), Base64.NO_WRAP)

            // 5. Escalar miniatura ligera (260px) para mosaico y sincronización en Google Sheets
            val thumbBitmap = scaleBitmapDown(orientedBitmap, thumbMaxDim)
            val thumbOut = ByteArrayOutputStream()
            thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 68, thumbOut)
            val thumbBase64 = Base64.encodeToString(thumbOut.toByteArray(), Base64.NO_WRAP)

            ProcessedPhotoData(
                highResBase64 = highResBase64,
                thumbBase64 = thumbBase64,
                scaledBitmap = highResBitmap,
                exifGps = exifGps
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Convierte un Uri a Base64 optimizado de forma síncrona segura.
     */
    fun uriToOptimizedBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280,
        quality: Int = 82
    ): String? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            var sampleSize = 1
            val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxSide / sampleSize > 2048) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            } ?: return null

            val rotationDegrees = getExifRotationDegrees(context, uri)
            val orientedBitmap = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }

            val scaledBitmap = scaleBitmapDown(orientedBitmap, maxDimension)
            val out = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Throwable) {
            null
        }
    }

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
