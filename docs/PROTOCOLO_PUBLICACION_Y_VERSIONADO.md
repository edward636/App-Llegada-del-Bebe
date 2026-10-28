# 🚀 Protocolo Oficial de Publicación, Versionado y Optimizaciones (HyperOS & ColorOS)
### App "Llegada del Bebé" (Edward & Britney)

---

## 1. 🔍 Diagnóstico del Fallo de Producción en GitHub (Por qué el teléfono decía "Ya está al día")

Durante el despliegue de versiones anteriores, se detectó la siguiente falla silenciosa en la nube de GitHub:
1. **Bloqueo de GitHub Actions (`release_ota.yml`):**
   - Al ejecutar `git push origin --tags`, el flujo de CI/CD intentaba arrancar en los servidores de GitHub.
   - Sin embargo, la cuenta reportaba el error:
     > `The job was not started because your account is locked due to a billing issue.`
   - Esto provocaba que el flujo fallara a los 4 segundos sin haber creado el release ni haber subido los APKs binarios a GitHub Releases.
2. **Consecuencia en la App Móvil:**
   - La aplicación móvil consulta la API pública: `https://api.github.com/repos/Edward636/App-Llegada-del-Bebe/releases/latest`.
   - Al haber fallado la acción de GitHub, la última release pública registrada seguía siendo la `v1.3.1`.
   - La app en el teléfono comparaba su versión instalada (`1.3.1`) contra la última registrada (`1.3.1`) y concluía correctamente: *"¡Tu app ya está al día!"*.

---

## 2. 🛡️ Nuevo Protocolo Automatizado de Publicación (`publish_release.ps1`)

Para eliminar cualquier dependencia de runners externos o cuentas de facturación de GitHub Actions, el proyecto ahora cuenta con el script ejecutable:
📁 [`publish_release.ps1`](file:///c:/Users/User/Documents/App%20Llegada%20del%20Bebe/publish_release.ps1)

### Pasos que ejecuta automáticamente:
1. **Lectura Determinista de Versión:** Extrae `versionCode` y `versionName` directamente de [`app/build.gradle.kts`](file:///c:/Users/User/Documents/App%20Llegada%20del%20Bebe/app/build.gradle.kts).
2. **Autenticación Segura:** Extrae el token de GitHub desde el Administrador de Credenciales local (`git credential fill`).
3. **Compilación de Artefactos:** Ejecuta `./gradlew.bat assembleRelease assembleDebug` y valida la integridad de los binarios APK generados.
4. **Creación Directa del Release:** Se comunica por REST API con GitHub para crear o actualizar el Release oficial `vX.X.X`.
5. **Subida de APKs:** Sube directamente `app-release.apk` y `app-debug.apk` como assets del release mediante streams HTTP multipart/octet-stream.
6. **Verificación de Producción:** Consulta de inmediato el endpoint público `releases/latest` y confirma que la nueva versión ya está disponible para todos los teléfonos.

---

## 3. ⚡ Optimizaciones Nativas para Xiaomi HyperOS y Oppo ColorOS

El ecosistema familiar utiliza dos dispositivos insignia con capas de personalización avanzadas:
- 📱 **Oppo Reno 12F:** Capa **ColorOS (Android 14/15)**.
- 📱 **Redmi Note 13 Pro+ 5G:** Capa **Xiaomi HyperOS (Android 14)**.

### Optimizaciones Técnicas Aplicadas en el Código:

| Optimización | Archivo Afectado | Impacto en HyperOS / ColorOS |
| :--- | :--- | :--- |
| **Recorte de Pantalla (`Display Cutout`)** | [`MainActivity.kt`](file:///c:/Users/User/Documents/App%20Llegada%20del%20Bebe/app/src/main/kotlin/com/example/llegadadelbebe/MainActivity.kt) | Activa `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` para aprovechar los marcos ultra finos y rodear de forma nativa la cámara selfie frontal punch-hole sin bandas negras. |
| **Aceleración por Hardware y 120Hz** | [`AndroidManifest.xml`](file:///c:/Users/User/Documents/App%20Llegada%20del%20Bebe/app/src/main/AndroidManifest.xml) | Habilita `hardwareAccelerated="true"` forzando la GPU para animaciones fluidas a 120 Hz en los paneles AMOLED. |
| **Ventanas Flotantes y Multitarea** | [`AndroidManifest.xml`](file:///c:/Users/User/Documents/App%20Llegada%20del%20Bebe/app/src/main/AndroidManifest.xml) | Habilita `resizeableActivity="true"` para que las "Ventanas Flotantes" de HyperOS y la barra lateral de ColorOS operen sin reiniciar la app. |
| **Protección contra Cierre en Segundo Plano** | [`OemOptimizationHelper.kt`](file:///c:/Users/User/Documents/App%20Llegada%20del%20Bebe/app/src/main/kotlin/com/example/llegadadelbebe/core/util/OemOptimizationHelper.kt) | Permiso `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` y acceso directo al gestor de "Inicio Automático" de MIUI (`com.miui.securitycenter`) y ColorOS (`com.coloros.safecenter`). |
| **Prioridad de Notificaciones en Bloqueo** | [`NotificationHelper.kt`](file:///c:/Users/User/Documents/App%20Llegada%20del%20Bebe/app/src/main/kotlin/com/example/llegadadelbebe/core/notification/NotificationHelper.kt) | Canales configurados con `VISIBILITY_PUBLIC`, luces de aviso, patrones de vibración específicos (`longArrayOf(0, 300, 200, 300)`) y bypass DND para que no se silencien las vitaminas. |
| **Panel de Ajustes de Sistema** | [`SettingsScreen.kt`](file:///c:/Users/User/Documents/App%20Llegada%20del%20Bebe/app/src/main/kotlin/com/example/llegadadelbebe/presentation/settings/SettingsScreen.kt) | Tarjeta interactiva que detecta el fabricante y ofrece botones directos para conceder "Batería Sin Restricciones" e "Inicio Automático". |

---

## 4. 📋 Procedimiento para Futuras Publicaciones

Para cada nueva versión futura:
1. Cambiar versión en `app/build.gradle.kts` y `Navigation.kt` (ej: `1.4.1`, `versionCode = 19`).
2. Ejecutar en PowerShell:
   ```powershell
   .\publish_release.ps1 -CustomChangelog "Descripción de cambios..."
   ```
3. El script compilará, subirá y validará la actualización en menos de 2 minutos.
