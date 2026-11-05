/**
 * ==============================================================================
 * GOOGLE APPS SCRIPT - BACKEND BASE DE DATOS Y DISTRIBUIDOR OTA (v2.1) 👶🍼💙🇻🇪
 * PROYECTO: APP "LLEGADA DEL BEBÉ" (EDWARD & BRITNEY)
 * ==============================================================================
 * ARQUITECTURA TÉCNICA Y AUDITORÍA:
 * 1. Base de Datos Multi-Hoja Limpia, Visible y Sincronizada:
 *    - 'Inventario_Bebe': Productos, ropita, pañales, higiene, alimentación y registros SYS_*.
 *    - 'Medicamentos_Prenatales': Espejo tabular automático de cada vitamina y toma de Britney.
 *    - 'Radar_Cotizaciones': Espejo tabular automático de precios en anaquel, tasa BCV y tiendas.
 *    - 'Comercios_GPS': Espejo tabular automático de farmacias y comercios detectados por GPS.
 *    - 'Papelera_Auditoria': Historial seguro de ítems eliminados (sin perder trazabilidad).
 *    - 'App_Config': Parámetros del sistema y distribuidor inalámbrico OTA.
 * 2. Sincronización Dual Automática (JSON <-> Tablas Dedicadas):
 *    - Cada vez que la app guarda SYS_PRENATAL_MEDICATIONS, SYS_RADAR_QUOTES o SYS_KNOWN_STORES,
 *      el script desempaqueta automáticamente el JSON y llena las filas de las pestañas
 *      'Medicamentos_Prenatales', 'Radar_Cotizaciones' y 'Comercios_GPS' para que siempre
 *      sean visibles en la hoja de cálculo.
 *    - Incluye auto-rellenado (backfill) automático en doGet() si las pestañas dedicadas están vacías.
 * 3. Búsqueda O(1) Ultrarrápida en getRecord:
 *    - Lee únicamente la Columna A (IDs) para ubicar la fila exacta sin cargar los ~575 KB de
 *      miniaturas Base64 del inventario en memoria RAM.
 * ==============================================================================
 */

const SHEET_INVENTORY = "Inventario_Bebe";
const SHEET_MEDICATIONS = "Medicamentos_Prenatales";
const SHEET_QUOTES = "Radar_Cotizaciones";
const SHEET_STORES = "Comercios_GPS";
const SHEET_TRASH = "Papelera_Auditoria";
const SHEET_CONFIG = "App_Config";
const META_TAG = "||META_JSON:";

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
          29,
          "1.6.6",
          "https://github.com/edward636/App-Llegada-del-Bebe/releases/download/v1.6.6/app-release.apk",
          "Actualización v1.6.6: Sincronización instantánea con caché unificada de registros SYS_*, reflejo tabular automático en Medicamentos_Prenatales y botón X para cerrar advertencias en vitaminas.",
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
      let vCode = Number(configSheet.getRange(2, 1).getValue()) || 0;
      if (vCode < 29) {
        configSheet.getRange(2, 1, 1, 5).setValues([[
          29,
          "1.6.6",
          "https://github.com/edward636/App-Llegada-del-Bebe/releases/download/v1.6.6/app-release.apk",
          "Actualización v1.6.6: Sincronización instantánea con caché unificada de registros SYS_*, reflejo tabular automático en Medicamentos_Prenatales y botón X para cerrar advertencias en vitaminas.",
          new Date().toISOString()
        ]]);
      }
      const row = configSheet.getRange(2, 1, 1, 5).getValues()[0];
      return jsonResponse({
        latestVersionCode: Number(row[0]) || 29,
        latestVersionName: String(row[1]) || "1.6.6",
        apkUrl: String(row[2]) || "https://github.com/edward636/App-Llegada-del-Bebe/releases/download/v1.6.6/app-release.apk",
        changelog: String(row[3]) || "Actualización v1.6.6 oficial disponible."
      });
    } catch (err) {
      return jsonResponse({
        latestVersionCode: 29,
        latestVersionName: "1.6.6",
        apkUrl: "https://github.com/edward636/App-Llegada-del-Bebe/releases/download/v1.6.6/app-release.apk",
        changelog: "Actualización v1.6.6 oficial disponible."
      });
    }
  }

  // 2. Consulta rápida de Vitaminas Prenatales (con auto-sincronización desde SYS_PRENATAL_MEDICATIONS)
  if (action === "getMedications") {
    ensureDedicatedSheetsSynced("SYS_PRENATAL_MEDICATIONS");
    return jsonResponse(getRowsFromSheet(SHEET_MEDICATIONS));
  }

  // 3. Consulta rápida de Cotizaciones del Radar (con auto-sincronización desde SYS_RADAR_QUOTES)
  if (action === "getQuotes") {
    ensureDedicatedSheetsSynced("SYS_RADAR_QUOTES");
    return jsonResponse(getRowsFromSheet(SHEET_QUOTES));
  }

  // 4. Consulta rápida de Comercios GPS (con auto-sincronización desde SYS_KNOWN_STORES)
  if (action === "getStores") {
    ensureDedicatedSheetsSynced("SYS_KNOWN_STORES");
    return jsonResponse(getRowsFromSheet(SHEET_STORES));
  }

  // 5. Búsqueda O(1) de un registro específico de sistema por ID (ej: SYS_PRENATAL_MEDICATIONS)
  // Lee únicamente la Columna A para encontrar la fila sin cargar todas las imágenes Base64 en RAM.
  if (action === "getRecord" && e.parameter.id) {
    const targetId = String(e.parameter.id);
    const sheet = getOrCreateSheet(SHEET_INVENTORY);
    const lastRow = sheet.getLastRow();
    if (lastRow < 2) {
      return jsonResponse({ status: "success", found: false });
    }

    const idCol = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
    let bestRowIndex = -1;
    let bestUpdatedAt = -1;

    for (let i = 0; i < idCol.length; i++) {
      if (String(idCol[i][0]) === targetId) {
        const candidateRow = sheet.getRange(i + 2, 1, 1, 16).getValues()[0];
        const candidateUpdated = Number(candidateRow[12]) || 0;
        if (candidateUpdated >= bestUpdatedAt) {
          bestUpdatedAt = candidateUpdated;
          bestRowIndex = i + 2;
        }
      }
    }

    if (bestRowIndex > 0) {
      const rowData = sheet.getRange(bestRowIndex, 1, 1, 16).getValues()[0];
      // Reflejar automáticamente en la pestaña dedicada si aún estaba vacía
      try {
        ensureDedicatedSheetsSynced(targetId, String(rowData[9] || ""));
      } catch (_) {}

      return jsonResponse({
        status: "success",
        found: true,
        item: {
          id: String(rowData[0]),
          name: String(rowData[1]),
          notes: String(rowData[9]),
          updatedAt: Number(rowData[12]) || Date.now()
        }
      });
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
    const lastRow = sheet.getLastRow();
    let existingRowIndex = -1;

    if (lastRow >= 2) {
      const idCol = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
      for (let i = 0; i < idCol.length; i++) {
        if (String(idCol[i][0]) === String(item.id)) {
          existingRowIndex = i + 2;
          break;
        }
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

    // 3. Si es un registro de sistema (SYS_PRENATAL_MEDICATIONS, SYS_RADAR_QUOTES, SYS_KNOWN_STORES),
    // reflejar automáticamente las filas desglosadas en su pestaña dedicada de Google Sheets.
    if (String(item.id).indexOf("SYS_") === 0) {
      try {
        syncSystemRecordToDedicatedSheet(String(item.id), String(item.notes || ""));
      } catch (_) {}
    }

    return jsonResponse({ status: "success", itemId: item.id });

  } catch (error) {
    return jsonResponse({ status: "error", message: error.toString() });
  } finally {
    lock.releaseLock();
  }
}

/**
 * Extrae el JSON empaquetado dentro de un registro SYS_* y escribe cada elemento
 * como una fila limpia y legible en su pestaña dedicada ('Medicamentos_Prenatales',
 * 'Radar_Cotizaciones' o 'Comercios_GPS').
 */
function syncSystemRecordToDedicatedSheet(systemId, rawNotes) {
  const idx = rawNotes.indexOf(META_TAG);
  if (idx < 0) return;

  const metaStr = rawNotes.substring(idx + META_TAG.length);
  const metaObj = JSON.parse(metaStr);
  if (!metaObj || !metaObj.payload) return;

  const list = JSON.parse(metaObj.payload);
  if (!Array.isArray(list)) return;

  if (systemId === "SYS_PRENATAL_MEDICATIONS") {
    const medSheet = getOrCreateSheet(SHEET_MEDICATIONS);
    const lastRow = medSheet.getLastRow();
    if (lastRow > 1) {
      medSheet.getRange(2, 1, lastRow - 1, 15).clearContent();
    }
    if (list.length > 0) {
      const rows = list.map(function(m) {
        const times = Array.isArray(m.scheduleTimes) ? m.scheduleTimes.join(", ") : String(m.scheduleTimes || "08:00");
        return [
          String(m.id || ""),
          String(m.name || ""),
          String(m.activeIngredient || ""),
          String(m.dose || ""),
          Number(m.frequencyHours) || 24,
          times,
          String(m.foodInstructions || ""),
          String(m.clinicalWarnings || ""),
          Number(m.pillsRemaining !== undefined ? m.pillsRemaining : 30),
          Number(m.totalPillsInPackage || 30),
          Boolean(m.isMandatory !== false),
          String(m.lastTakenDate || ""),
          String(m.lastTakenTime || ""),
          String(m.lastConfirmedBy || ""),
          Number(m.updatedAt) || Date.now()
        ];
      });
      medSheet.getRange(2, 1, rows.length, 15).setValues(rows);
    }
  } else if (systemId === "SYS_RADAR_QUOTES") {
    const quoteSheet = getOrCreateSheet(SHEET_QUOTES);
    const lastRow = quoteSheet.getLastRow();
    if (lastRow > 1) {
      quoteSheet.getRange(2, 1, lastRow - 1, 20).clearContent();
    }
    if (list.length > 0) {
      const rows = list.map(function(q) {
        return [
          String(q.id || ""),
          String(q.productName || ""),
          String(q.brand || ""),
          String(q.category || ""),
          String(q.stageSize || ""),
          Number(q.packageCount) || 1,
          Number(q.unitsPerPackage) || 1,
          String(q.storeName || ""),
          String(q.storeAddress || ""),
          Number(q.latitude) || 0,
          Number(q.longitude) || 0,
          Number(q.priceAmount) || 0,
          String(q.priceCurrency || "USD"),
          Number(q.bcvRateApplied) || 1,
          Number(q.priceUsdEquivalent) || 0,
          Number(q.unitPriceUsd) || 0,
          String(q.quotedAt || ""),
          String(q.registeredBy || ""),
          String(q.barcode || ""),
          String(q.notes || "")
        ];
      });
      quoteSheet.getRange(2, 1, rows.length, 20).setValues(rows);
    }
  } else if (systemId === "SYS_KNOWN_STORES") {
    const storeSheet = getOrCreateSheet(SHEET_STORES);
    const lastRow = storeSheet.getLastRow();
    if (lastRow > 1) {
      storeSheet.getRange(2, 1, lastRow - 1, 9).clearContent();
    }
    if (list.length > 0) {
      const rows = list.map(function(s) {
        return [
          String(s.id || ""),
          String(s.name || ""),
          String(s.address || ""),
          Number(s.latitude) || 0,
          Number(s.longitude) || 0,
          Number(s.visitCount) || 1,
          String(s.lastVisitedAt || ""),
          String(s.googleMapsName || ""),
          Boolean(s.isUserConfirmed !== false)
        ];
      });
      storeSheet.getRange(2, 1, rows.length, 9).setValues(rows);
    }
  }
}

/**
 * Garantiza que si la pestaña dedicada está vacía pero el registro SYS_* existe en Inventario_Bebe,
 * se rellene automáticamente la pestaña dedicada sin intervención manual.
 */
function ensureDedicatedSheetsSynced(systemId, optionalRawNotes) {
  let targetSheetName = null;
  if (systemId === "SYS_PRENATAL_MEDICATIONS") targetSheetName = SHEET_MEDICATIONS;
  else if (systemId === "SYS_RADAR_QUOTES") targetSheetName = SHEET_QUOTES;
  else if (systemId === "SYS_KNOWN_STORES") targetSheetName = SHEET_STORES;
  if (!targetSheetName) return;

  const targetSheet = getOrCreateSheet(targetSheetName);
  if (targetSheet.getLastRow() > 1) return; // Ya tiene filas pobladas

  if (optionalRawNotes) {
    syncSystemRecordToDedicatedSheet(systemId, optionalRawNotes);
    return;
  }

  const invSheet = getOrCreateSheet(SHEET_INVENTORY);
  const lastRow = invSheet.getLastRow();
  if (lastRow < 2) return;

  const idCol = invSheet.getRange(2, 1, lastRow - 1, 1).getValues();
  for (let i = 0; i < idCol.length; i++) {
    if (String(idCol[i][0]) === systemId) {
      const notes = String(invSheet.getRange(i + 2, 10).getValue() || "");
      syncSystemRecordToDedicatedSheet(systemId, notes);
      break;
    }
  }
}

/**
 * Mueve un producto a la Papelera de Auditoría y limpia la fila de Inventario.
 */
function archiveToTrash(itemId, deletedBy) {
  const invSheet = getOrCreateSheet(SHEET_INVENTORY);
  const trashSheet = getOrCreateSheet(SHEET_TRASH);
  const lastRow = invSheet.getLastRow();
  if (lastRow < 2) return;

  const idCol = invSheet.getRange(2, 1, lastRow - 1, 1).getValues();
  for (let i = 0; i < idCol.length; i++) {
    if (String(idCol[i][0]) === String(itemId)) {
      const row = invSheet.getRange(i + 2, 1, 1, 16).getValues()[0];
      trashSheet.appendRow([
        String(row[0]),
        String(row[1]),
        String(row[4]),
        deletedBy,
        new Date().toISOString(),
        JSON.stringify(row)
      ]);
      invSheet.deleteRow(i + 2);
      break;
    }
  }
}

/**
 * Función de mantenimiento ejecutada manualmente o por trigger:
 * 1. Limpia filas DELETED de Inventario_Bebe.
 * 2. Sincroniza todas las pestañas dedicadas (Medicamentos_Prenatales, Radar_Cotizaciones, Comercios_GPS).
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
    } else if (id.indexOf("SYS_") === 0) {
      try {
        syncSystemRecordToDedicatedSheet(id, String(rows[i][9] || ""));
      } catch (_) {}
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
