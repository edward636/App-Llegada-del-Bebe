package com.example.llegadadelbebe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.llegadadelbebe.core.security.SecureConfig
import com.example.llegadadelbebe.core.storage.LocalPreferences
import com.example.llegadadelbebe.core.update.OtaInstallOutcome
import com.example.llegadadelbebe.core.update.OtaUpdateManager
import com.example.llegadadelbebe.core.update.UpdateInfo
import com.example.llegadadelbebe.data.repository.BabyInventoryRepository
import com.example.llegadadelbebe.presentation.consumables.ConsumablesAuditScreen
import com.example.llegadadelbebe.presentation.consumables.ConsumablesAuditViewModel
import com.example.llegadadelbebe.presentation.home.HomeScreen
import com.example.llegadadelbebe.presentation.home.HomeViewModel
import com.example.llegadadelbebe.presentation.home.OtaUpdateDialog
import com.example.llegadadelbebe.presentation.inventory.InventoryScreen
import com.example.llegadadelbebe.presentation.inventory.InventoryViewModel
import com.example.llegadadelbebe.presentation.settings.SettingsScreen
import com.example.llegadadelbebe.presentation.zodiac.FamilySynastryScreen
import com.example.llegadadelbebe.presentation.zodiac.FamilySynastryViewModel
import kotlinx.coroutines.launch

const val APP_VERSION_CODE = 14
const val APP_VERSION_NAME = "1.2.0"

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(NavHome)

    // Persistencia local en el dispositivo (Offline-First)
    val localPreferences = remember { LocalPreferences(context) }

    // Repositorio central compartido en memoria, caché local y Google Sheets
    val sharedRepository = remember { BabyInventoryRepository(localPreferences = localPreferences) }

    // Estados de configuración del Bebé y FUR persistidos en el teléfono
    var babyNameState by remember { mutableStateOf(localPreferences.getBabyName()) }
    var lmpDateState by remember { mutableStateOf(localPreferences.getLmpDate()) }
    var activeUserState by remember { mutableStateOf(localPreferences.getActiveUser()) }

    val googleSheetUrlState = remember { SecureConfig.getGoogleSheetUrl() }
    val geminiApiKeyState = remember { SecureConfig.getGeminiApiKey() }

    // Estado de actualización OTA
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    var otaDialogStatus by remember { mutableStateOf("") }
    var awaitingInstallPermission by remember { mutableStateOf(false) }

    // Si el usuario concede el permiso de "Instalar apps desconocidas" y regresa a la app,
    // lanza el instalador automáticamente usando el APK ya descargado en caché.
    DisposableEffect(lifecycleOwner, awaitingInstallPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingInstallPermission) {
                if (OtaUpdateManager.canInstallPackages(context) && OtaUpdateManager.hasCachedApk(context)) {
                    awaitingInstallPermission = false
                    coroutineScope.launch {
                        otaDialogStatus = "🚀 Permiso concedido. Abriendo instalador..."
                        OtaUpdateManager.launchCachedApkInstaller(context)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Sincronización automática de inicio y chequeo OTA
    LaunchedEffect(Unit) {
        sharedRepository.initializeSheetUrl(googleSheetUrlState)

        val updateResult = OtaUpdateManager.checkForUpdate(
            currentVersionName = APP_VERSION_NAME,
            currentVersionCode = APP_VERSION_CODE,
            checkUrl = googleSheetUrlState
        )
        if (updateResult.isSuccess) {
            val info = updateResult.getOrNull()
            if (info != null && info.hasUpdate) {
                pendingUpdate = info
            }
        }
    }

    val homeViewModel: HomeViewModel = viewModel {
        HomeViewModel(sharedRepository).apply {
            updateBabySettings(babyNameState, lmpDateState)
        }
    }

    val inventoryViewModel: InventoryViewModel = viewModel {
        InventoryViewModel(sharedRepository).apply {
            setActiveUser(activeUserState)
        }
    }

    val consumablesViewModel: ConsumablesAuditViewModel = viewModel {
        ConsumablesAuditViewModel(sharedRepository)
    }

    val synastryViewModel: FamilySynastryViewModel = viewModel {
        FamilySynastryViewModel(localPreferences)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<NavHome> {
                HomeScreen(
                    viewModel = homeViewModel,
                    activeUser = activeUserState,
                    onNavigateToInventory = { backStack.add(NavInventory) },
                    onNavigateToConsumables = { backStack.add(NavConsumables) },
                    onNavigateToSynastry = { backStack.add(NavFamilySynastry) },
                    onNavigateToSettings = { backStack.add(NavSettings) }
                )
            }
            entry<NavInventory> {
                InventoryScreen(
                    viewModel = inventoryViewModel,
                    googleSheetUrl = googleSheetUrlState,
                    geminiApiKey = geminiApiKeyState,
                    onUserChanged = { newUser ->
                        activeUserState = newUser
                        localPreferences.saveActiveUser(newUser)
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<NavConsumables> {
                ConsumablesAuditScreen(
                    viewModel = consumablesViewModel,
                    apiKey = geminiApiKeyState,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<NavFamilySynastry> {
                FamilySynastryScreen(
                    viewModel = synastryViewModel,
                    apiKey = geminiApiKeyState,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<NavSettings> {
                SettingsScreen(
                    babyName = babyNameState,
                    currentLmp = lmpDateState,
                    currentUser = activeUserState,
                    onSaveBabySettings = { newName, newLmp, newUser ->
                        babyNameState = newName
                        lmpDateState = newLmp
                        activeUserState = newUser
                        localPreferences.saveBabyName(newName)
                        localPreferences.saveLmpDate(newLmp)
                        localPreferences.saveActiveUser(newUser)
                        homeViewModel.updateBabySettings(newName, newLmp)
                        inventoryViewModel.setActiveUser(newUser)
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )

    // Diálogo de actualización OTA cuando se detecta una nueva versión
    pendingUpdate?.let { update ->
        OtaUpdateDialog(
            updateInfo = update,
            downloadProgress = downloadProgress,
            statusMessage = otaDialogStatus,
            onDismiss = {
                pendingUpdate = null
                downloadProgress = -1
                otaDialogStatus = ""
            },
            onConfirmUpdate = {
                coroutineScope.launch {
                    downloadProgress = 0
                    otaDialogStatus = ""
                    val result = OtaUpdateManager.downloadAndInstallApk(
                        context = context,
                        apkUrl = update.apkUrl,
                        onProgress = { progress -> downloadProgress = progress }
                    )
                    if (result.isSuccess) {
                        val outcome = result.getOrNull()
                        if (outcome == OtaInstallOutcome.PERMISSION_REQUIRED) {
                            awaitingInstallPermission = true
                            otaDialogStatus = "⚠️ Activa 'Permitir desde esta fuente' en tu teléfono y regresa; se instalará al instante."
                        } else {
                            otaDialogStatus = "🚀 Instalador abierto. Toca 'Actualizar' en la ventana de Android."
                        }
                    } else {
                        val err = result.exceptionOrNull()?.localizedMessage ?: "Error de red"
                        otaDialogStatus = "❌ Error al descargar: $err. Intenta de nuevo."
                        downloadProgress = -1
                    }
                }
            },
            onInstallCached = {
                coroutineScope.launch {
                    val result = OtaUpdateManager.launchCachedApkInstaller(context)
                    if (result.getOrNull() == OtaInstallOutcome.PERMISSION_REQUIRED) {
                        awaitingInstallPermission = true
                        otaDialogStatus = "⚠️ Activa 'Confiar en esta fuente' y regresa aquí."
                    } else if (result.isSuccess) {
                        otaDialogStatus = "🚀 Instalador de Android abierto."
                    }
                }
            }
        )
    }
}
