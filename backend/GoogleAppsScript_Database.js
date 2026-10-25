/**
 * ==============================================================================
 * GOOGLE APPS SCRIPT - BACKEND BASE DE DATOS Y DISTRIBUIDOR OTA 👶🍼💙
 * USO EXCLUSIVO: PAPÁ Y BRITNEY
 * ==============================================================================
 * FUNCIONALIDADES:
 * 1. Base de datos del inventario del bebé (doGet / doPost).
 * 2. Distribuidor Automático OTA (Over The Air):
 *    - Recibe el nuevo APK compilado desde tu computadora.
 *    - Lo guarda en tu Google Drive ("App_Bebe_Updates").
 *    - Genera el enlace de descarga directa.
 *    - Notifica a los teléfonos de Papá y Britney para actualizar con 1 toque.
 * ==============================================================================
 */

const SHEET_NAME_INVENTORY = "Inventario_Bebe";
const SHEET_NAME_CONFIG = "App_Config";
const DRIVE_FOLDER_NAME = "App_Bebe_Updates";

function getOrCreateSheet(sheetName) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(sheetName);
  if (!sheet) {
    sheet = ss.insertSheet(sheetName);
    if (sheetName === SHEET_NAME_INVENTORY) {
      sheet.appendRow([
        "id", "name", "brand", "model", "category", "stageSize", 
        "quantityCurrent", "quantityTarget", "status", "notes", 
        "geminiOpinion", "registeredBy", "updatedAt"
      ]);
      sheet.getRange(1, 1, 1, 13).setFontWeight("bold").setBackground("#E8F1F5");
    } else if (sheetName === SHEET_NAME_CONFIG) {
      sheet.appendRow(["versionCode", "versionName", "apkUrl", "changelog", "updatedAt"]);
      sheet.appendRow([1, "1.0.0", "", "Versión inicial", new Date().toISOString()]);
      sheet.getRange(1, 1, 1, 5).setFontWeight("bold").setBackground("#D8F3DC");
    }
  }
  return sheet;
}

/**
 * Endpoint GET: Recupera el inventario o comprueba actualizaciones OTA
 */
function doGet(e) {
  const action = (e && e.parameter && e.parameter.action) ? e.parameter.action : "getInventory";

  // 1. Comprobar actualización OTA
  if (action === "checkUpdate") {
    const configSheet = getOrCreateSheet(SHEET_NAME_CONFIG);
    const row = configSheet.getRange(2, 1, 1, 5).getValues()[0];
    return ContentService.createTextOutput(JSON.stringify({
      latestVersionCode: Number(row[0]) || 1,
      latestVersionName: String(row[1]) || "1.0.0",
      apkUrl: String(row[2]) || "",
      changelog: String(row[3]) || "Mejoras para la llegada del bebé."
    })).setMimeType(ContentService.MimeType.JSON);
  }

  // 2. Obtener todo el inventario
  const lock = LockService.getScriptLock();
  lock.tryLock(10000);

  try {
    const sheet = getOrCreateSheet(SHEET_NAME_INVENTORY);
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
        updatedAt: Number(row[12]) || Date.now()
      });
    }

    return ContentService.createTextOutput(JSON.stringify({ status: "success", data: items }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: error.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  } finally {
    lock.releaseLock();
  }
}

/**
 * Endpoint POST: Agrega producto al inventario O sube nueva versión OTA a Google Drive
 */
function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.tryLock(30000);

  try {
    const payload = JSON.parse(e.postData.contents);

    // CASO A: Publicar nueva versión OTA automáticamente desde tu PC
    if (payload.action === "publishOta") {
      const bytes = Utilities.base64Decode(payload.apkBase64);
      const blob = Utilities.newBlob(bytes, "application/vnd.android.package-archive", "LlegadaDelBebe_v" + payload.versionName + ".apk");

      // Carpeta en Google Drive
      const folders = DriveApp.getFoldersByName(DRIVE_FOLDER_NAME);
      const folder = folders.hasNext() ? folders.next() : DriveApp.createFolder(DRIVE_FOLDER_NAME);
      const file = folder.createFile(blob);
      file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);

      // Enlace directo de descarga para Android
      const fileId = file.getId();
      const directDownloadUrl = "https://drive.google.com/uc?export=download&id=" + fileId;

      // Actualizar registro en la pestaña App_Config
      const configSheet = getOrCreateSheet(SHEET_NAME_CONFIG);
      configSheet.getRange(2, 1, 1, 5).setValues([[
        Number(payload.versionCode) || 1,
        String(payload.versionName) || "1.0.0",
        directDownloadUrl,
        String(payload.changelog) || "Nueva actualización disponible.",
        new Date().toISOString()
      ]]);

      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        message: "¡APK subido a Google Drive y actualización OTA publicada!",
        downloadUrl: directDownloadUrl
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // CASO B: Guardar producto en Inventario
    const sheet = getOrCreateSheet(SHEET_NAME_INVENTORY);
    const item = payload.item;

    if (!item || !item.id) {
      throw new Error("Datos de producto inválidos");
    }

    const rows = sheet.getDataRange().getValues();
    let existingRowIndex = -1;

    for (let i = 1; i < rows.length; i++) {
      if (String(rows[i][0]) === String(item.id)) {
        existingRowIndex = i + 1;
        break;
      }
    }

    const rowData = [
      item.id,
      item.name || "",
      item.brand || "",
      item.model || "",
      item.category || "CLOTHING",
      item.stageSize || "",
      item.quantityCurrent || 1,
      item.quantityTarget || 1,
      item.status || "PURCHASED",
      item.notes || "",
      item.geminiOpinion || "",
      item.registeredBy || "",
      item.updatedAt || Date.now()
    ];

    if (existingRowIndex > 0) {
      sheet.getRange(existingRowIndex, 1, 1, 13).setValues([rowData]);
    } else {
      sheet.appendRow(rowData);
    }

    return ContentService.createTextOutput(JSON.stringify({ status: "success", itemId: item.id }))
      .setMimeType(ContentService.MimeType.JSON);

  } catch (error) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: error.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  } finally {
    lock.releaseLock();
  }
}
