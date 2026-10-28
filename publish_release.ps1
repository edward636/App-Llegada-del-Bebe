# ==============================================================================
# Protocolo Automatizado de Compilacion, Publicacion y Verificacion OTA
# App "Llegada del Bebe" (Edward & Britney)
# ==============================================================================

param(
    [string]$CustomChangelog = ""
)

$ErrorActionPreference = "Stop"

Write-Host "`n[PASO 1/6] Leyendo version desde app/build.gradle.kts..." -ForegroundColor Cyan
$buildGradle = Get-Content "app/build.gradle.kts" -Raw
$versionCodeMatch = [regex]::Match($buildGradle, 'versionCode\s*=\s*(\d+)')
$versionNameMatch = [regex]::Match($buildGradle, 'versionName\s*=\s*"([^"]+)"')

if (-not $versionCodeMatch.Success -or -not $versionNameMatch.Success) {
    throw "No se pudo extraer versionCode o versionName de app/build.gradle.kts"
}

$versionCode = $versionCodeMatch.Groups[1].Value
$versionName = $versionNameMatch.Groups[1].Value
$tagName = "v$versionName"

Write-Host "   -> Version a publicar: $tagName (versionCode = $versionCode)" -ForegroundColor Green

Write-Host "`n[PASO 2/6] Obteniendo credenciales de GitHub..." -ForegroundColor Cyan
$token = $env:GITHUB_TOKEN
if (-not $token) {
    $credOutput = "protocol=https`nhost=github.com" | git credential fill 2>$null
    foreach ($line in ($credOutput -split "`n")) {
        if ($line -like "password=*") {
            $token = $line.Substring(9).Trim()
            break
        }
    }
}

if (-not $token) {
    throw "No se encontro token de GitHub en variables de entorno ni en git credential helper"
}
Write-Host "   -> Token de GitHub autenticado correctamente para Edward636." -ForegroundColor Green

Write-Host "`n[PASO 3/6] Compilando APKs de Produccion y Debug (Gradle)..." -ForegroundColor Cyan
& .\gradlew.bat assembleRelease assembleDebug
if ($LASTEXITCODE -ne 0) {
    throw "Fallo la compilacion de Gradle assembleRelease assembleDebug"
}

$releaseApk = "app/build/outputs/apk/release/app-release.apk"
$debugApk = "app/build/outputs/apk/debug/app-debug.apk"

if (-not (Test-Path $releaseApk) -or -not (Test-Path $debugApk)) {
    throw "No se encontraron los archivos APK generados en app/build/outputs/apk/"
}

$releaseSize = [math]::Round(((Get-Item $releaseApk).Length / 1MB), 2)
$debugSize = [math]::Round(((Get-Item $debugApk).Length / 1MB), 2)
Write-Host "   -> app-release.apk listo: $releaseSize MB" -ForegroundColor Green
Write-Host "   -> app-debug.apk listo: $debugSize MB" -ForegroundColor Green

Write-Host "`n[PASO 4/6] Creando/Actualizando Release $tagName en GitHub API..." -ForegroundColor Cyan
$headers = @{
    "Authorization" = "token $token"
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "AppLlegadaDelBebe-ReleaseBot"
}

$changelogText = if ($CustomChangelog) {
    $CustomChangelog
} else {
    "Actualizacion OTA $tagName para la llegada del bebe. Optimizada para Oppo Reno 12F (ColorOS) y Redmi Note 13 Pro+ 5G (HyperOS)."
}

$releasePayload = [PSCustomObject]@{
    tag_name = $tagName
    name = "$tagName - App Llegada del Bebe"
    body = $changelogText
    draft = $false
    prerelease = $false
} | ConvertTo-Json

$existingRelease = $null
try {
    $existingRelease = Invoke-RestMethod -Uri "https://api.github.com/repos/Edward636/App-Llegada-del-Bebe/releases/tags/$tagName" -Headers $headers -Method Get -ErrorAction SilentlyContinue
} catch {}

$releaseId = $null
if ($existingRelease) {
    $releaseId = $existingRelease.id
    Write-Host "   -> Release $tagName ya existia (ID: $releaseId). Actualizando metadata..." -ForegroundColor Yellow
    Invoke-RestMethod -Uri "https://api.github.com/repos/Edward636/App-Llegada-del-Bebe/releases/$releaseId" -Headers $headers -Method Patch -Body $releasePayload -ContentType "application/json; charset=utf-8" | Out-Null
} else {
    $newRelease = Invoke-RestMethod -Uri "https://api.github.com/repos/Edward636/App-Llegada-del-Bebe/releases" -Headers $headers -Method Post -Body $releasePayload -ContentType "application/json; charset=utf-8"
    $releaseId = $newRelease.id
    Write-Host "   -> Nuevo Release creado en GitHub (ID: $releaseId)" -ForegroundColor Green
}

Write-Host "`n[PASO 5/6] Subiendo APKs a los Assets de GitHub..." -ForegroundColor Cyan

# Eliminar assets previos si ya existian en el release
$assets = Invoke-RestMethod -Uri "https://api.github.com/repos/Edward636/App-Llegada-del-Bebe/releases/$releaseId/assets" -Headers $headers -Method Get
foreach ($asset in $assets) {
    if ($asset.name -in @("app-release.apk", "app-debug.apk")) {
        Write-Host "   -> Eliminando asset previo $($asset.name) (ID: $($asset.id))..." -ForegroundColor DarkGray
        Invoke-RestMethod -Uri "https://api.github.com/repos/Edward636/App-Llegada-del-Bebe/releases/assets/$($asset.id)" -Headers $headers -Method Delete | Out-Null
    }
}

# Subir app-release.apk
$uploadUrlRelease = "https://uploads.github.com/repos/Edward636/App-Llegada-del-Bebe/releases/$releaseId/assets?name=app-release.apk"
Write-Host "   -> Subiendo app-release.apk ($releaseSize MB)..." -ForegroundColor Yellow
$upRelease = Invoke-RestMethod -Uri $uploadUrlRelease -Method Post -Headers $headers -InFile $releaseApk -ContentType "application/vnd.android.package-archive"
Write-Host "   -> app-release.apk publicado: $($upRelease.browser_download_url)" -ForegroundColor Green

# Subir app-debug.apk
$uploadUrlDebug = "https://uploads.github.com/repos/Edward636/App-Llegada-del-Bebe/releases/$releaseId/assets?name=app-debug.apk"
Write-Host "   -> Subiendo app-debug.apk ($debugSize MB)..." -ForegroundColor Yellow
$upDebug = Invoke-RestMethod -Uri $uploadUrlDebug -Method Post -Headers $headers -InFile $debugApk -ContentType "application/vnd.android.package-archive"
Write-Host "   -> app-debug.apk publicado: $($upDebug.browser_download_url)" -ForegroundColor Green

Write-Host "`n[PASO 6/6] Verificando estado publico de la actualizacion..." -ForegroundColor Cyan
Start-Sleep -Seconds 2
$latest = Invoke-RestMethod -Uri "https://api.github.com/repos/Edward636/App-Llegada-del-Bebe/releases/latest" -Headers @{"User-Agent"="Mozilla/5.0"}
Write-Host "   -> Ultimo release publico en GitHub: $($latest.tag_name) - $($latest.name)" -ForegroundColor Green
Write-Host "   -> Assets disponibles:" -ForegroundColor Green
foreach ($asset in $latest.assets) {
    Write-Host "      * $($asset.name) ($([math]::Round($asset.size / 1MB, 2)) MB) -> $($asset.browser_download_url)" -ForegroundColor DarkGreen
}

if ($latest.tag_name -eq $tagName) {
    Write-Host "`n========================================================" -ForegroundColor Green
    Write-Host "PUBLICACION EXITOSA: Los telefonos detectaran $tagName" -ForegroundColor Green
    Write-Host "========================================================`n" -ForegroundColor Green
} else {
    Write-Host "`nADVERTENCIA: El ultimo release reportado es $($latest.tag_name), diferente a $tagName.`n" -ForegroundColor Yellow
}
