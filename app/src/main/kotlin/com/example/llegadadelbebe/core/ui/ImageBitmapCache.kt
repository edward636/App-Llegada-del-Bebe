package com.example.llegadadelbebe.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Caché en memoria para miniaturas decodificadas de Base64 a [Bitmap] y [ImageBitmap].
 * Evita la recodificación repetitiva en el hilo principal durante el scroll
 * de listas y mosaicos de productos.
 */
object ImageBitmapCache {

    private val bitmapCache = object : LruCache<String, Bitmap>(100) {}
    private val imageBitmapCache = object : LruCache<String, ImageBitmap>(100) {}

    fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        if (base64Str.isBlank()) return null
        val cached = bitmapCache.get(base64Str)
        if (cached != null) return cached

        return try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bmp?.also {
                bitmapCache.put(base64Str, it)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ToImageBitmap(base64Str: String): ImageBitmap? {
        if (base64Str.isBlank()) return null
        val cached = imageBitmapCache.get(base64Str)
        if (cached != null) return cached

        val bmp = decodeBase64ToBitmap(base64Str) ?: return null
        val imageBitmap = bmp.asImageBitmap()
        imageBitmapCache.put(base64Str, imageBitmap)
        return imageBitmap
    }

    fun clear() {
        bitmapCache.evictAll()
        imageBitmapCache.evictAll()
    }
}
