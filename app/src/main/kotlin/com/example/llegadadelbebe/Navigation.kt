package com.example.llegadadelbebe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.llegadadelbebe.core.notification.AppEngagementScheduler
import com.example.llegadadelbebe.core.notification.NotificationHelper
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
import com.example.llegadadelbebe.presentation.medications.PrenatalMedicationsScreen
import com.example.llegadadelbebe.presentation.medications.PrenatalMedicationsViewModel
import com.example.llegadadelbebe.presentation.radar.ShoppingRadarScreen
import com.example.llegadadelbebe.presentation.radar.ShoppingRadarViewModel
import com.example.llegadadelbebe.presentation.settings.SettingsScreen
import com.example.llegadadelbebe.presentation.zodiac.FamilySynastryScreen
import com.example.llegadadelbebe.presentation.zodiac.FamilySynastryViewModel
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
    currentThemeMode: String = com.example.llegadadelbebe.ui.theme.ThemeModes.SYSTEM,
    onThemeModeChanged: (String) -> Unit = {}
) {
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

    // Lanzador para solicitar permisos de notificación en Android 13+ (Tiramisu)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            NotificationHelper.createNotificationChannels(context)
        }
    }

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

    // Sincronización automática de inicio, inicialización de notificaciones y chequeo OTA
    LaunchedEffect(Unit) {
        // Inicializar canales de notificaciones en el sistema
        NotificationHelper.createNotificationChannels(context)

        // Registrar apertura y programar chequeo de inactividad
        localPreferences.recordAppOpen()
        AppEngagementScheduler.scheduleNextCheck(context)

        // Solicitar permiso de notificaciones en Android 13+ si no está concedido
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        sharedRepository.initializeSheetUrl(googleSheetUrlState)

        val updateResult = OtaUpdateManager.checkForUpdate(
            context = context,
            checkUrl = googleSheetUrlState
        )
        if (updateResult.isSuccess) {
            val info = updateResult.getOrNull()
            if (info != null && info.hasUpdate) {
                pendingUpdate = info
                // Notificar en la barra de estado de Android
                NotificationHelper.showOtaUpdateNotification(context, info)
            }
        }
    }

    val homeViewModel: HomeViewModel = viewModel {
        HomeViewModel(sharedRepository, localPreferences).apply {
            updateBabySettings(babyNameState, lmpDateState)
        }
    }

    val inventoryViewModel: InventoryViewModel = viewModel {
        InventoryViewModel(sharedRepository).apply {
            setActiveUser(activeUserState)
        }
    }

    val consumablesViewModel: ConsumablesAuditViewModel = viewModel {
        ConsumablesAuditViewModel(sharedRepository, localPreferences = localPreferences)
    }

    val synastryViewModel: FamilySynastryViewModel = viewModel {
        FamilySynastryViewModel(localPreferences)
    }

    val radarViewModel: ShoppingRadarViewModel = viewModel {
        ShoppingRadarViewModel(
            application = context.applicationContext as android.app.Application,
            repository = sharedRepository
        )
    }

    val medicationsViewModel: PrenatalMedicationsViewModel = viewModel {
        PrenatalMedicationsViewModel(context.applicationContext as android.app.Application)
    }

    // Sincronización global reactiva multi-dispositivo en ON_RESUME (al abrir o regresar a la app)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sharedRepository.triggerSync(googleSheetUrlState)
                medicationsViewModel.syncWithCloud(googleSheetUrlState)
                radarViewModel.syncQuotesWithCloud(googleSheetUrlState)
                radarViewModel.syncStoresWithCloud(googleSheetUrlState)
                consumablesViewModel.syncHospitalBagWithCloud(googleSheetUrlState)
                homeViewModel.refreshSnapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun navigateToTab(destination: NavKey) {
        if (backStack.lastOrNull() == destination) return
        if (destination is NavHome) {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            homeViewModel.refreshSnapshot()
            sharedRepository.triggerSync(googleSheetUrlState)
        } else {
            val current = backStack.lastOrNull()
            val isTopLevel = current is NavInventory || current is NavConsumables || current is NavMedications || current is NavShoppingRadar
            if (isTopLevel) {
                backStack.removeLastOrNull()
            }
            backStack.add(destination)

            // Disparar sincronización inmediata de la pestaña seleccionada
            when (destination) {
                is NavInventory -> sharedRepository.triggerSync(googleSheetUrlState)
                is NavConsumables -> consumablesViewModel.syncHospitalBagWithCloud(googleSheetUrlState)
                is NavMedications -> medicationsViewModel.syncWithCloud(googleSheetUrlState)
                is NavShoppingRadar -> {
                    radarViewModel.syncQuotesWithCloud(googleSheetUrlState)
                    radarViewModel.syncStoresWithCloud(googleSheetUrlState)
                }
                else -> {}
            }
        }
    }

    val currentKey = backStack.lastOrNull()
    val showBottomBar = currentKey !is NavSettings

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BabyBottomNavigationBar(
                    currentKey = currentKey,
                    onNavigateToTab = { key -> navigateToTab(key) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<NavHome> {
                        HomeScreen(
                            viewModel = homeViewModel,
                            geminiApiKey = geminiApiKeyState,
                            activeUser = activeUserState,
                            onNavigateToInventory = { navigateToTab(NavInventory) },
                            onNavigateToConsumables = { navigateToTab(NavConsumables) },
                            onNavigateToSynastry = { backStack.add(NavFamilySynastry) },
                            onNavigateToRadar = { navigateToTab(NavShoppingRadar) },
                            onNavigateToMedications = { navigateToTab(NavMedications) },
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
                    entry<NavShoppingRadar> {
                        ShoppingRadarScreen(
                            viewModel = radarViewModel,
                            geminiApiKey = geminiApiKeyState,
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<NavMedications> {
                        PrenatalMedicationsScreen(
                            viewModel = medicationsViewModel,
                            geminiApiKey = geminiApiKeyState,
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<NavSettings> {
                        SettingsScreen(
                            babyName = babyNameState,
                            currentLmp = lmpDateState,
                            currentUser = activeUserState,
                            currentThemeMode = currentThemeMode,
                            onThemeModeChanged = onThemeModeChanged,
                            onSaveBabySettings = { newName, newLmp, newUser ->
                                babyNameState = newName
                                lmpDateState = newLmp
                                activeUserState = newUser
                                localPreferences.saveBabyName(newName)
                                localPreferences.saveLmpDate(newLmp)
                                localPreferences.saveActiveUser(newUser)
                                homeViewModel.updateBabySettings(newName, newLmp)
                                homeViewModel.refreshSnapshot()
                                inventoryViewModel.setActiveUser(newUser)
                                radarViewModel.setActiveUser(newUser)
                                medicationsViewModel.setActiveUser(newUser)
                                synastryViewModel.refreshCalculations()
                            },
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }
                }
            )
        }
    }

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

@Composable
fun BabyBottomNavigationBar(
    currentKey: Any?,
    onNavigateToTab: (NavKey) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        val items = listOf(
            Triple(NavHome, "🏠", "Inicio"),
            Triple(NavInventory, "📦", "Inventario"),
            Triple(NavConsumables, "🎒", "Maleta"),
            Triple(NavMedications, "💊", "Vitaminas"),
            Triple(NavShoppingRadar, "🛒", "Radar")
        )

        items.forEach { (key, icon, label) ->
            val isSelected = currentKey == key
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigateToTab(key) },
                icon = {
                    Text(
                        text = icon,
                        fontSize = if (isSelected) 22.sp else 19.sp
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
