package com.example.llegadadelbebe.core.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
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

/**
 * Gestor de actualizaciones inalámbricas (OTA - Over The Air) para uso personal.
 * Permite a Papá y Britney actualizar la app automáticamente con 1 toque
 * sin necesidad de pasar por Google Play Store.
 */
object OtaUpdateManager {

    private const val GITHUB_REPO = "Edward636/App-Llegada-del-Bebe"
    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    /**
     * Consulta actualizaciones OTA priorizando GitHub Releases (Edward636) con fallback a Google Apps Script.
     */
    suspend fun checkForUpdate(
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

                // Parsear versión tipo 1.1.0 -> 110
                val remoteCode = parseVersionToCode(tagName)
                if (remoteCode > currentVersionCode && apkDownloadUrl.isNotBlank()) {
                    return@withContext Result.success(
                        UpdateInfo(
                            hasUpdate = true,
                            latestVersionCode = remoteCode,
                            latestVersionName = tagName,
                            apkUrl = apkDownloadUrl,
                            changelog = changelog
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Continuar con el fallback si GitHub aún no tiene release creada
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
            val latestName = json.optString("latestVersionName", "1.0")
            val apkUrl = json.optString("apkUrl", "")
            val changelog = json.optString("changelog", "Mejoras para la llegada del bebé.")

            val hasUpdate = latestCode > currentVersionCode && apkUrl.isNotBlank()

            Result.success(
                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersionCode = latestCode,
                    latestVersionName = latestName,
                    apkUrl = apkUrl,
                    changelog = changelog
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseVersionToCode(versionName: String): Int {
        val parts = versionName.split(".")
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

    /**
     * Descarga el APK en caché y lanza el instalador nativo del sistema Android.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val apkFile = File(updatesDir, "update_bebe.apk")
            if (apkFile.exists()) apkFile.delete()

            val url = URL(apkUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
            }

            val fileLength = connection.contentLength
            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val data = ByteArray(4096)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            val progress = ((total * 100) / fileLength).toInt()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                        output.write(data, 0, count)
                    }
                    output.flush()
                }
            }

            // Lanzar el instalador nativo de Android usando FileProvider
            withContext(Dispatchers.Main) {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(installIntent)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
