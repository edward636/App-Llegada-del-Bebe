# App Llegada del Bebé 👶🍼💙

Aplicación móvil Android nativa moderna, diseñada bajo principios de **Clean Architecture**, **Material 3 (Material You)** y **Kotlin 2.0+ (K2)** para acompañar a los futuros padres en la dulce espera de su **bebé varón**.

---

## 🌟 Características Principales (Etapa 1: Pre-Parto)

1. **Calculador de Gestación y Cuenta Regresiva:**
   - Cálculo en tiempo real de semanas y días de gestación a partir de semanas actuales o Fecha de Última Menstruación (FUM).
   - Fecha Probable de Parto (FPP) mediante la Regla de Naegele.
   - Semanas y días restantes para la semana 40.
   - Hitos de crecimiento: comparativa visual de tamaño y peso estimado del bebé (ej: *Semana 28: Berenjena grande ~38 cm y ~1.2 kg*).

2. **Predictor de Signo Zodiacal del Bebé Niño:**
   - Determina automáticamente el signo astrológico del niño según la FPP.
   - Muestra elemento (Fuego, Tierra, Aire, Agua), constelación, colores afortunados y una tarjeta con sus rasgos tiernos de personalidad.

3. **Inventario Inteligente Compartido (Base de Datos en Google Sheets):**
   - Registro de lo que ya tienen (ropa, pañales, cochecito, cuna, biberones, toallas, jabón).
   - Clasificación por categorías con filtros rápidos y buscador.
   - Indicador de estado: `Comprado`, `Regalado (Baby Shower)`, `Falta Adquirir` o `En Maleta Clínica`.
   - Sincronización bidireccional en tiempo real con una hoja de cálculo en Google Drive para que **Mamá y Papá** compartan la misma información.

4. **Auditoría Pediátrica de Consumibles:**
   - Evalúa si las cantidades registradas son óptimas o si faltan productos esenciales.
   - **Control de Pañales:** Alerta inteligente si se acumulan más de 160-180 pañales talla RN para evitar compras innecesarias de una talla que el niño dejará rápido, recomendando pasar a Talla 1.
   - Cobertura de toallitas húmedas hipoalergénicas, jabones neutros y guardarropa por tallas (RN y 0-3M).

5. **Integración con Gemini 3.8 Flash (Google AI Studio - Capa Gratuita $0):**
   - Utiliza el modelo más reciente y veloz de Google: **`gemini-3.8-flash`**.
   - **Extracción de fotos:** Reconocimiento multimodal con *Structured Outputs* (JSON Schema) para auto-completar marca, modelo, cantidades y notas de seguridad al fotografiar productos.
   - **Asesor Inteligente:** Diagnóstico empático y profesional sobre el estado del inventario y compras prioritarias.

---

## 🏛️ Arquitectura y Buenas Prácticas (~200 líneas por archivo)

El proyecto sigue una estricta modularidad donde cada archivo tiene una única responsabilidad (*Single Responsibility Principle*):

```
app/src/main/kotlin/com/example/llegadadelbebe/
│
├── core/
│   └── util/
│       ├── PregnancyCalculator.kt    # Cálculos gestacionales y Naegele (~90 líneas)
│       └── ZodiacPredictor.kt        # Predictor zodiacal del niño (~100 líneas)
│
├── domain/
│   ├── model/
│   │   ├── BabyProduct.kt            # Entidad principal de producto (~45 líneas)
│   │   └── ConsumableAudit.kt        # Modelos de auditoría de consumibles (~40 líneas)
│   └── usecase/
│       └── EvaluateConsumablesUseCase.kt # Lógica de consumo neonatal (~140 líneas)
│
├── data/
│   ├── remote/
│   │   ├── GeminiClient.kt           # Cliente oficial Gemini 3.8 Flash (~180 líneas)
│   │   └── GoogleSheetsService.kt    # Sincronización REST con Sheets (~130 líneas)
│   └── repository/
│       └── BabyInventoryRepository.kt# Repositorio central con caché reactiva (~130 líneas)
│
├── presentation/
│   ├── home/
│   │   ├── HomeScreen.kt             # Dashboard gestacional y zodiacal (~190 líneas)
│   │   └── HomeViewModel.kt          # ViewModel con StateFlow (~70 líneas)
│   ├── inventory/
│   │   ├── InventoryScreen.kt        # Lista con chips y sincronización (~180 líneas)
│   │   ├── InventoryViewModel.kt     # ViewModel de búsqueda y filtros (~80 líneas)
│   │   └── AddProductDialog.kt       # Modal de registro (~170 líneas)
│   ├── consumables/
│   │   ├── ConsumablesAuditScreen.kt # Pantalla de diagnóstico y Asesor (~170 líneas)
│   │   └── ConsumablesAuditViewModel.kt # ViewModel de auditoría (~65 líneas)
│   └── settings/
│       └── SettingsScreen.kt         # Ajustes de semanas, Sheets y Gemini (~140 líneas)
│
└── ui/theme/
    ├── BabyBoyTheme.kt               # Paleta de colores bebé varón Material 3 (~50 líneas)
    └── Theme.kt                      # Tema Compose (~60 líneas)
```

---

## 📋 Guía de Configuración Rápida

### 1. Base de Datos en Google Sheets (Gratis y Compartida)
1. Abre [Google Sheets](https://sheets.new) y crea una hoja nueva (ej: *"Inventario Bebé"*).
2. Ve al menú superior: **Extensiones** $\rightarrow$ **Apps Script**.
3. Copia y pega el código contenido en [`backend/GoogleAppsScript_Database.js`](backend/GoogleAppsScript_Database.js).
4. Haz clic en **Implementar** (Deploy) $\rightarrow$ **Nueva implementación** $\rightarrow$ Tipo: **Aplicación web**.
   - Ejecutar como: **Yo**.
   - Quién tiene acceso: **Cualquiera** (Anyone).
5. Copia la URL generada (`https://script.google.com/macros/s/.../exec`) y pégala en los **Ajustes (⚙️)** de la app en los teléfonos de Mamá y Papá.

### 2. Clave Gratuita de Gemini 3.8 Flash ($0.00)
1. Ingresa en [Google AI Studio](https://aistudio.google.com/).
2. Inicia sesión con tu cuenta de Google y presiona **"Get API key"** (Obtener clave de API).
3. Pega tu clave en **Ajustes (⚙️)** dentro de la aplicación.
   - *Nota:* Dispones de hasta **1,500 solicitudes gratuitas al día**, lo que cubre sobradamente el registro familiar.

---

## 🧪 Pruebas Unitarias

El proyecto incluye pruebas automatizadas en `app/src/test/kotlin/com/example/llegadadelbebe/PregnancyAndZodiacTest.kt`:
- Verificación del cálculo gestacional (semanas, días, trimestre y tamaño).
- Verificación de signos zodiacales según la fecha estimada de parto.
- Verificación de las alertas de sobrecompra en pañales RN y faltantes en consumibles.

Para ejecutar las pruebas:
```powershell
.\gradlew.bat test
```
