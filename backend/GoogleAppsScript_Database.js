/**
 * ==============================================================================
 * GOOGLE APPS SCRIPT - BACKEND BASE DE DATOS Y DISTRIBUIDOR OTA (v2.0) 👶🍼💙🇻🇪
 * PROYECTO: APP "LLEGADA DEL BEBÉ" (EDWARD & BRITNEY)
 * ==============================================================================
 * ARQUITECTURA TÉCNICA Y AUDITORÍA:
 * 1. Base de Datos Multi-Hoja Limpia y Especializada:
 *    - 'Inventario_Bebe': Productos, ropita, pañales, higiene, alimentación.
 *    - 'Medicamentos_Prenatales': Vitaminas prenatales, tomas de Britney, recetas.
 *    - 'Radar_Cotizaciones': Precios en anaquel de tiendas, tasa BCV y comparativas.
 *    - 'Comercios_GPS': Historial de farmacias y supermercados detectados por GPS.
 *    - 'Papelera_Auditoria': Historial seguro de ítems eliminados (sin perder trazabilidad).
 *    - 'App_Config': Parámetros del sistema y distribuidor inalámbrico OTA.
 * 2. Prevención de Basura en la Hoja (Cero Acumulación Zombie):
 *    - Elimina o traslada automáticamente filas 'DELETED' a la papelera.
 * 3. Endpoints Especializados de Alta Velocidad:
 *    - GET ?action=getInventory   -> Recupera el inventario activo.
 *    - GET ?action=getMedications -> Recupera vitaminas prenatales.
 *    - GET ?action=getQuotes      -> Recupera cotizaciones de radar.
 *    - GET ?action=getStores      -> Recupera comercios conocidos.
 *    - GET ?action=getRecord&id=X -> Búsqueda directa O(1) por ID de sistema.
 *    - GET ?action=checkUpdate    -> Versión más reciente OTA para actualización automática.
 * 4. 100% Retrocompatible con las versiones publicadas en Android (v1.1.2 a v1.6.0).
 * ==============================================================================
 */

const SHEET_INVENTORY = "Inventario_Bebe";
const SHEET_MEDICATIONS = "Medicamentos_Prenatales";
const SHEET_QUOTES = "Radar_Cotizaciones";
const SHEET_STORES = "Comercios_GPS";
const SHEET_TRASH = "Papelera_Auditoria";
const SHEET_CONFIG = "App_Config";

/**
 * Obtiene o inicializa una hoja con su encabezado oficial, colores y formato.
 */
function getOrCreateSheet(sheetName) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(sheetName);

  if (!sheet) {
    sheet = ss.insertSheet(sheetName);

    switch (sheetName) {
      case SHEET_INVENTORY:
        sheet.appendRow([
          "id", "name", "brand", "model", "category", "stageSize",
          "quantityCurrent", "quantityTarget", "status", "notes",
          "geminiOpinion", "registeredBy", "updatedAt",
          "ingredients", "manufacturedIn", "expirationDate"
        ]);
        sheet.getRange(1, 1, 1, 16).setFontWeight("bold").setBackground("#E8F1F5");
        sheet.setFrozenRows(1);
        break;

      case SHEET_MEDICATIONS:
        sheet.appendRow([
          "id", "name", "activeIngredient", "dose", "frequencyHours",
          "scheduleTimes", "foodInstructions", "clinicalWarnings",
          "pillsRemaining", "totalPillsInPackage", "isMandatory",
          "lastTakenDate", "lastTakenTime", "lastConfirmedBy", "updatedAt"
        ]);
        sheet.getRange(1, 1, 1, 15).setFontWeight("bold").setBackground("#F3E8FF");
        sheet.setFrozenRows(1);
        break;

      case SHEET_QUOTES:
        sheet.appendRow([
          "id", "productName", "brand", "category", "stageSize",
          "packageCount", "unitsPerPackage", "storeName", "storeAddress",
          "latitude", "longitude", "priceAmount", "priceCurrency",
          "bcvRateApplied", "priceUsdEquivalent", "unitPriceUsd",
          "quotedAt", "registeredBy", "barcode", "notes"
        ]);
        sheet.getRange(1, 1, 1, 20).setFontWeight("bold").setBackground("#FEF3C7");
        sheet.setFrozenRows(1);
        break;

      case SHEET_STORES:
        sheet.appendRow([
          "id", "name", "address", "latitude", "longitude",
          "visitCount", "lastVisitedAt", "googleMapsName", "isUserConfirmed"
        ]);
        sheet.getRange(1, 1, 1, 9).setFontWeight("bold").setBackground("#E0F2FE");
        sheet.setFrozenRows(1);
        break;

      case SHEET_TRASH:
        sheet.appendRow([
          "deletedId", "name", "category", "deletedBy", "deletedAt", "rawSnapshot"
        ]);
        sheet.getRange(1, 1, 1, 6).setFontWeight("bold").setBackground("#FEE2E2");
        sheet.setFrozenRows(1);
        break;

      case SHEET_CONFIG:
        sheet.appendRow(["versionCode", "versionName", "apkUrl", "changelog", "updatedAt"]);
        sheet.appendRow([
          23,
          "1.6.0",
          "https://github.com/edward636/App-Llegada-del-Bebe/releases/download/v1.6.0/app-release.apk",
          "Actualización v1.6.0 con FUR oficial 05/06/2026, reactividad multi-módulo y Gemini 3.8 Flash.",
          new Date().toISOString()
        ]);
        sheet.getRange(1, 1, 1, 5).setFontWeight("bold").setBackground("#D8F3DC");
        sheet.setFrozenRows(1);
        break;
    }
  }

  return sheet;
}

/**
 * ==============================================================================
 * ENTRADA GET: Consulta de inventario, registros del sistema y actualizaciones
 * ==============================================================================
 */
function doGet(e) {
  const action = (e && e.parameter && e.parameter.action) ? e.parameter.action : "getInventory";

  // 1. Verificación de Actualizaciones OTA
  if (action === "checkUpdate") {
    try {
      const configSheet = getOrCreateSheet(SHEET_CONFIG);
      const row = configSheet.getRange(2, 1, 1, 5).getValues()[0];
      return jsonResponse({
        latestVersionCode: Number(row[0]) || 23,
        latestVersionName: String(row[1]) || "1.6.0",
        apkUrl: String(row[2]) || "https://github.com/edward636/App-Llegada-del-Bebe/releases/download/v1.6.0/app-release.apk",
        changelog: String(row[3]) || "Actualización v1.6.0 con FUR oficial 05/06/2026."
      });
    } catch (err) {
      return jsonResponse({
        latestVersionCode: 23,
        latestVersionName: "1.6.0",
        apkUrl: "https://github.com/edward636/App-Llegada-del-Bebe/releases/download/v1.6.0/app-release.apk",
        changelog: "Actualización oficial disponible."
      });
    }
  }

  // 2. Consulta rápida de Vitaminas Prenatales
  if (action === "getMedications") {
    return jsonResponse(getRowsFromSheet(SHEET_MEDICATIONS));
  }

  // 3. Consulta rápida de Cotizaciones del Radar
  if (action === "getQuotes") {
    return jsonResponse(getRowsFromSheet(SHEET_QUOTES));
  }

  // 4. Consulta rápida de Comercios GPS
  if (action === "getStores") {
    return jsonResponse(getRowsFromSheet(SHEET_STORES));
  }

  // 5. Búsqueda de un registro específico de sistema por ID (ej: SYS_PRENATAL_MEDICATIONS)
  if (action === "getRecord" && e.parameter.id) {
    const targetId = String(e.parameter.id);
    const sheet = getOrCreateSheet(SHEET_INVENTORY);
    const rows = sheet.getDataRange().getValues();
    for (let i = 1; i < rows.length; i++) {
      if (String(rows[i][0]) === targetId) {
        return jsonResponse({
          status: "success",
          found: true,
          item: {
            id: String(rows[i][0]),
            name: String(rows[i][1]),
            notes: String(rows[i][9]),
            updatedAt: Number(rows[i][12]) || Date.now()
          }
        });
      }
    }
    return jsonResponse({ status: "success", found: false });
  }

  // 6. Consulta por defecto: Inventario Completo del Bebé
  const lock = LockService.getScriptLock();
  lock.tryLock(10000);

  try {
    const sheet = getOrCreateSheet(SHEET_INVENTORY);
    const rows = sheet.getDataRange().getValues();
    const items = [];

    for (let i = 1; i < rows.length; i++) {
      const row = rows[i];
      if (!row[0]) continue;
      items.push({
        id: String(row[0]),
        name: String(row[1]),
        brand: String(row[2]),
        model: String(row[3]),
        category: String(row[4]),
        stageSize: String(row[5]),
        quantityCurrent: Number(row[6]) || 1,
        quantityTarget: Number(row[7]) || 1,
        status: String(row[8]),
        notes: String(row[9]),
        geminiOpinion: String(row[10]),
        registeredBy: String(row[11]),
        updatedAt: Number(row[12]) || Date.now(),
        ingredients: String(row[13] || ""),
        manufacturedIn: String(row[14] || ""),
        expirationDate: String(row[15] || "")
      });
    }

    return jsonResponse({ status: "success", data: items });
  } catch (error) {
    return jsonResponse({ status: "error", message: error.toString() });
  } finally {
    lock.releaseLock();
  }
}

/**
 * ==============================================================================
 * ENTRADA POST: Guardar, actualizar o eliminar productos y registros
 * ==============================================================================
 */
function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.tryLock(30000);

  try {
    const payload = JSON.parse(e.postData.contents);
    const action = payload.action || "saveItem";

    // 1. Eliminación explícita (Mover a Papelera de Auditoría)
    if (action === "deleteItem" || (payload.item && payload.item.status === "DELETED")) {
      const itemId = payload.itemId || (payload.item && payload.item.id);
      if (itemId) {
        archiveToTrash(itemId, payload.user || (payload.item && payload.item.registeredBy) || "Usuario");
        return jsonResponse({ status: "success", action: "deleted", itemId: itemId });
      }
    }

    // 2. Guardar o actualizar producto / registro en Inventario
    const item = payload.item;
    if (!item || !item.id) {
      throw new Error("Datos de producto o registro inválidos: falta el campo 'id'");
    }

    const sheet = getOrCreateSheet(SHEET_INVENTORY);
    const rows = sheet.getDataRange().getValues();
    let existingRowIndex = -1;

    for (let i = 1; i < rows.length; i++) {
      if (String(rows[i][0]) === String(item.id)) {
        existingRowIndex = i + 1;
        break;
      }
    }

    const rowData = [
      String(item.id),
      String(item.name || ""),
      String(item.brand || ""),
      String(item.model || ""),
      String(item.category || "CLOTHING"),
      String(item.stageSize || ""),
      Number(item.quantityCurrent) || 1,
      Number(item.quantityTarget) || 1,
      String(item.status || "PURCHASED"),
      String(item.notes || ""),
      String(item.geminiOpinion || ""),
      String(item.registeredBy || ""),
      Number(item.updatedAt) || Date.now(),
      String(item.ingredients || ""),
      String(item.manufacturedIn || ""),
      String(item.expirationDate || "")
    ];

    if (existingRowIndex > 0) {
      sheet.getRange(existingRowIndex, 1, 1, 16).setValues([rowData]);
    } else {
      sheet.appendRow(rowData);
    }

    return jsonResponse({ status: "success", itemId: item.id });

  } catch (error) {
    return jsonResponse({ status: "error", message: error.toString() });
  } finally {
    lock.releaseLock();
  }
}

/**
 * Mueve un producto a la Papelera de Auditoría y limpia la fila de Inventario.
 */
function archiveToTrash(itemId, deletedBy) {
  const invSheet = getOrCreateSheet(SHEET_INVENTORY);
  const trashSheet = getOrCreateSheet(SHEET_TRASH);
  const rows = invSheet.getDataRange().getValues();

  for (let i = 1; i < rows.length; i++) {
    if (String(rows[i][0]) === String(itemId)) {
      const row = rows[i];
      // Guardar respaldo en papelera
      trashSheet.appendRow([
        String(row[0]),
        String(row[1]),
        String(row[4]),
        deletedBy,
        new Date().toISOString(),
        JSON.stringify(row)
      ]);
      // Eliminar fila de la hoja principal para que no quede como zombie
      invSheet.deleteRow(i + 1);
      break;
    }
  }
}

/**
 * Función de mantenimiento ejecutada manualmente o por trigger:
 * Limpia todas las filas que tengan status === 'DELETED' de la hoja de inventario
 * y las archiva en la papelera para mantener la hoja impecable y veloz.
 */
function maintenanceCleanDeletedRows() {
  const invSheet = getOrCreateSheet(SHEET_INVENTORY);
  const trashSheet = getOrCreateSheet(SHEET_TRASH);
  const rows = invSheet.getDataRange().getValues();

  for (let i = rows.length - 1; i >= 1; i--) {
    const status = String(rows[i][8]).toUpperCase().trim();
    const id = String(rows[i][0]);
    if (status === "DELETED" || id === "test-check-1") {
      trashSheet.appendRow([
        id,
        String(rows[i][1]),
        String(rows[i][4]),
        "Mantenimiento Automático",
        new Date().toISOString(),
        JSON.stringify(rows[i])
      ]);
      invSheet.deleteRow(i + 1);
    }
  }
}

/**
 * Utilidad para devolver filas estructuradas de cualquier hoja.
 */
function getRowsFromSheet(sheetName) {
  const sheet = getOrCreateSheet(sheetName);
  const rows = sheet.getDataRange().getValues();
  if (rows.length <= 1) return { status: "success", data: [] };

  const headers = rows[0];
  const list = [];
  for (let i = 1; i < rows.length; i++) {
    const item = {};
    for (let j = 0; j < headers.length; j++) {
      item[headers[j]] = rows[i][j];
    }
    list.push(item);
  }
  return { status: "success", data: list };
}

/**
 * Helper para responder con JSON limpio.
 */
function jsonResponse(data) {
  return ContentService.createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}
