param (
    [string]$version = "1.0.0",
    [string]$message = "Versión inicial para la llegada del bebé"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "🐙 PUBLICADOR GITHUB OTA: Edward636/App-Llegada-del-Bebe" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Comprobar si hay cambios para hacer commit
git add .
git commit -m "$message"

$tagName = "v$version"
Write-Host "🏷️ Creando etiqueta de versión: $tagName..." -ForegroundColor Yellow
git tag -a $tagName -m "Release $tagName: $message"

Write-Host "🚀 Para subir a tu GitHub, asegúrate de haber creado el repositorio en github.com/Edward636/App-Llegada-del-Bebe y ejecuta:" -ForegroundColor Green
Write-Host "git remote add origin https://github.com/Edward636/App-Llegada-del-Bebe.git" -ForegroundColor White
Write-Host "git push -u origin main --tags" -ForegroundColor White
Write-Host ""
Write-Host "¡GitHub Actions compilará y publicará la actualización OTA automáticamente!" -ForegroundColor Magenta
