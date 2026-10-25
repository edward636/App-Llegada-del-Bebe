param (
    [int]$versionCode = 2,
    [string]$versionName = "1.0.1",
    [string]$changelog = "Icono de cochecito y correccion visual de inventario",
    [string]$scriptUrl = "https://script.google.com/macros/s/AKfycbwrrOvaR2CYUMhXkFnfXqk-NpdkW6vS5vjpVCJbGFLewLlgpW4nk0tdLowmHUSLhe1_/exec"
)

Write-Host "Iniciando publicacion OTA..." -ForegroundColor Cyan

$apkPath = "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path -Path $apkPath)) {
    Write-Host "Compilando APK..." -ForegroundColor Yellow
    & .\gradlew.bat assembleDebug
}

Write-Host "Leyendo archivo APK..." -ForegroundColor Green
$apkBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $apkPath))
$sizeMb = [math]::Round($apkBytes.Length / 1MB, 2)
Write-Host "Tamano del APK: $sizeMb MB" -ForegroundColor Gray

Write-Host "Codificando en Base64..." -ForegroundColor Green
$base64Apk = [System.Convert]::ToBase64String($apkBytes)

$payloadObj = @{
    action = "publishOta"
    versionCode = $versionCode
    versionName = $versionName
    changelog = $changelog
    apkBase64 = $base64Apk
}

$jsonBody = $payloadObj | ConvertTo-Json -Compress

Write-Host "Enviando APK a Google Apps Script..." -ForegroundColor Yellow

try {
    $res = Invoke-RestMethod -Uri $scriptUrl -Method Post -Body $jsonBody -ContentType "application/json; charset=utf-8" -TimeoutSec 120
    Write-Host "Respuesta del servidor:" -ForegroundColor Green
    $res | Format-List
    if ($res.status -eq "success") {
        Write-Host "OTA PUBLICADA CON EXITO!" -ForegroundColor Green
    }
} catch {
    Write-Host "Error al subir: $_" -ForegroundColor Red
}
