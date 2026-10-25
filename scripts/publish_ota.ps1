param (
    [int]$versionCode = 1,
    [string]$versionName = "1.0.0",
    [string]$changelog = "Nueva actualización para la llegada del bebé.",
    [string]$scriptUrl = "https://script.google.com/macros/s/AKfycbwrrOvaR2CYUMhXkFnfXqk-NpdkW6vS5vjpVCJbGFLewLlgpW4nk0tdLowmHUSLhe1_/exec"
)

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "🚀 DISTRIBUIDOR AUTOMÁTICO OTA: APP LLEGADA DEL BEBÉ" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan

$apkPath = "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apkPath)) {
    Write-Host "🔨 Compilando nuevo APK con Gradle..." -ForegroundColor Yellow
    .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Error "❌ Error al compilar el APK."
        exit 1
    }
}

Write-Host "📦 Leyendo APK ($apkPath)..." -ForegroundColor Green
$apkBytes = [System.IO.File]::ReadAllBytes($apkPath)
$sizeMb = [math]::Round($apkBytes.Length / 1MB, 2)
Write-Host "📊 Tamaño del APK: $sizeMb MB" -ForegroundColor Gray

Write-Host "🔒 Codificando paquete en Base64..." -ForegroundColor Green
$base64Apk = [System.Convert]::ToBase64String($apkBytes)

$payload = @{
    action = "publishOta"
    versionCode = $versionCode
    versionName = $versionName
    changelog = $changelog
    apkBase64 = $base64Apk
} | ConvertTo-Json -Compress -Depth 2

Write-Host "☁️ Subiendo APK a tu Google Drive vía Apps Script..." -ForegroundColor Yellow

try {
    $response = Invoke-RestMethod -Uri $scriptUrl -Method Post -Body $payload -ContentType "application/json; charset=utf-8" -TimeoutSec 120
    if ($response.status -eq "success") {
        Write-Host "✅ ¡ÉXITO TOTAL!" -ForegroundColor Green
        Write-Host "🎉 $($response.message)" -ForegroundColor Green
        Write-Host "📥 Enlace Directo: $($response.downloadUrl)" -ForegroundColor Cyan
        Write-Host "📱 Los teléfonos de Papá y Britney recibirán el aviso de actualización automáticamente al abrir la app." -ForegroundColor Magenta
    } else {
        Write-Host "⚠️ Respuesta del servidor: $($response.message)" -ForegroundColor Red
    }
} catch {
    Write-Error "❌ Error al subir el APK: $($_.Exception.Message)"
}
