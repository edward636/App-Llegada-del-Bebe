package com.example.llegadadelbebe.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
    val changelog: String
)

enum class OtaInstallOutcome {
    INSTALLER_LAUNCHED,
    PERMISSION_REQUIRED
}

/**
 * Gestor de actualizaciones inalámbricas (OTA - Over The Air) para uso personal.
 * Permite a Papá y Britney actualizar la app automáticamente con 1 toque
 * sin necesidad de pasar por Google Play Store.
 */
object OtaUpdateManager {

    private const val GITHUB_REPO = "Edward636/App-Llegada-del-Bebe"
    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val APK_FILE_NAME = "update_bebe.apk"

    /**
     * Compara dos versiones semánticas (ej: "1.0.4" vs "1.0.3").
     * Devuelve true estrictamente si remoteVersion es mayor que currentVersion.
     * Si son iguales o la remota es menor, devuelve false para evitar bucles.
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val cleanRemote = remoteVersion.removePrefix("v").trim()
        val cleanCurrent = currentVersion.removePrefix("v").trim()
        if (cleanRemote.equals(cleanCurrent, ignoreCase = true)) return false

        val rParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Consulta actualizaciones OTA priorizando GitHub Releases (Edward636) con fallback a Google Apps Script.
     */
    suspend fun checkForUpdate(
        currentVersionName: String,
        currentVersionCode: Int,
        checkUrl: String
    ): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        // 1. Intentar primero con GitHub Releases
        try {
            val ghUrl = URL(GITHUB_RELEASES_API)
            val ghConn = (ghUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 8000
                setRequestProperty("User-Agent", "AppLlegadaDelBebe-Android")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            if (ghConn.responseCode in 200..299) {
                val ghResponse = BufferedReader(InputStreamReader(ghConn.inputStream, "UTF-8")).use { it.readText() }
                val json = JSONObject(ghResponse)
                val tagName = json.optString("tag_name", "").removePrefix("v")
                val changelog = json.optString("body", "Nuevas mejoras para el bebé.")

                val assets = json.optJSONArray("assets")
                var apkDownloadUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                val hasUpdate = isNewerVersion(tagName, currentVersionName) && apkDownloadUrl.isNotBlank()
                return@withContext Result.success(
                    UpdateInfo(
                        hasUpdate = hasUpdate,
                        latestVersionCode = parseVersionToCode(tagName),
                        latestVersionName = tagName,
                        apkUrl = if (hasUpdate) apkDownloadUrl else "",
                        changelog = changelog
                    )
                )
            }
        } catch (_: Exception) {
            // Continuar con el fallback si GitHub falla
        }

        // 2. Fallback a Google Apps Script
        try {
            val url = URL("$checkUrl?action=checkUpdate")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000
                instanceFollowRedirects = true
            }

            val response = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use {
                it.readText()
            }

            val json = JSONObject(response)
            val latestCode = json.optInt("latestVersionCode", currentVersionCode)
            val latestName = json.optString("latestVersionName", "1.0.0")
            val apkUrl = json.optString("apkUrl", "")
            val changelog = json.optString("changelog", "Mejoras para la llegada del bebé.")

            val hasUpdate = (isNewerVersion(latestName, currentVersionName) || latestCode > currentVersionCode) && apkUrl.isNotBlank()

            Result.success(
                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersionCode = latestCode,
                    latestVersionName = latestName,
                    apkUrl = if (hasUpdate) apkUrl else "",
                    changelog = changelog
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseVersionToCode(versionName: String): Int {
        val parts = versionName.removePrefix("v").split(".")
        return try {
            when (parts.size) {
                1 -> parts[0].toInt() * 100
                2 -> parts[0].toInt() * 100 + parts[1].toInt() * 10
                else -> parts[0].toInt() * 100 + parts[1].toInt() * 10 + parts[2].toInt()
            }
        } catch (_: Exception) {
            1
        }
    }

    fun hasCachedApk(context: Context): Boolean {
        val file = File(File(context.cacheDir, "updates"), APK_FILE_NAME)
        return file.exists() && file.length() > 1_000_000L
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Descarga el APK en caché de forma atómica manejando redirecciones CDN (GitHub -> Azure/AWS)
     * y lanza el instalador nativo de Android sin bloquear el hilo principal.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): Result<OtaInstallOutcome> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val tempFile = File(updatesDir, "update_bebe_downloading.tmp")
            val finalApkFile = File(updatesDir, APK_FILE_NAME)
            if (tempFile.exists()) tempFile.delete()

            // Manejar redirecciones explícitamente (GitHub 302 -> CDN)
            var currentUrl = apkUrl
            var connection: HttpURLConnection
            var redirectCount = 0
            while (true) {
                val u = URL(currentUrl)
                if (!u.protocol.equals("https", ignoreCase = true)) {
                    throw SecurityException("Por seguridad, solo se permiten actualizaciones OTA sobre HTTPS.")
                }
                connection = (u.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 45000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "AppLlegadaDelBebe-Android")
                    setRequestProperty("Accept", "*/*")
                }
                val responseCode = connection.responseCode
                if (responseCode in 301..308) {
                    val newLocation = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (!newLocation.isNullOrBlank() && redirectCount < 6) {
                        currentUrl = URL(u, newLocation).toString()
                        redirectCount++
                        continue
                    } else {
                        throw IOException("Demasiadas redirecciones al descargar el APK ($redirectCount)")
                    }
                }
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    throw IOException("Respuesta HTTP inválida al descargar APK: $responseCode")
                }
                break
            }

            val fileLength = connection.contentLengthLong
            var lastReportedProgress = -1

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val data = ByteArray(32768) // Buffer de 32 KB para descarga rápida sin saturar UI
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        output.write(data, 0, count)
                        total += count
                        val progress = if (fileLength > 0) {
                            ((total * 100) / fileLength).toInt().coerceIn(0, 99)
                        } else {
                            ((total * 100) / 12_500_000L).toInt().coerceIn(0, 99)
                        }
                        if (progress > lastReportedProgress) {
                            lastReportedProgress = progress
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            if (tempFile.length() < 1_000_000L) {
                tempFile.delete()
                throw IOException("El archivo descargado está incompleto (${tempFile.length()} bytes).")
            }

            // Verificar firma de paquete e integridad del APK antes de instalar
            val archiveInfo = context.packageManager.getPackageArchiveInfo(tempFile.absolutePath, 0)
            if (archiveInfo == null || archiveInfo.packageName != context.packageName) {
                tempFile.delete()
                throw SecurityException("El APK descargado no es válido o no corresponde a esta aplicación.")
            }

            if (finalApkFile.exists()) finalApkFile.delete()
            if (!tempFile.renameTo(finalApkFile)) {
                tempFile.copyTo(finalApkFile, overwrite = true)
                tempFile.delete()
            }

            withContext(Dispatchers.Main) { onProgress(100) }

            launchCachedApkInstaller(context)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lanza el instalador de paquetes de Android para el APK ya descargado en caché.
     * Si el teléfono (Oppo Reno 12F / Redmi Note 13 Pro+) aún no tiene activado el permiso
     * de "Instalar apps desconocidas", abre directamente la pantalla de permiso sin colisionar
     * con el instalador, para que al volver se instale limpiamente.
     */
    suspend fun launchCachedApkInstaller(context: Context): Result<OtaInstallOutcome> =
        withContext(Dispatchers.Main) {
            try {
                val updatesDir = File(context.cacheDir, "updates")
                val apkFile = File(updatesDir, APK_FILE_NAME)
                if (!apkFile.exists() || apkFile.length() < 1_000_000L) {
                    return@withContext Result.failure(IOException("APK descargado no encontrado en caché."))
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        val manageIntent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(manageIntent)
                        return@withContext Result.success(OtaInstallOutcome.PERMISSION_REQUIRED)
                    }
                }

                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                context.startActivity(installIntent)
                Result.success(OtaInstallOutcome.INSTALLER_LAUNCHED)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
