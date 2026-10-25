package com.example.llegadadelbebe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.llegadadelbebe.core.security.SecureConfig
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
import kotlinx.coroutines.launch
import java.time.LocalDate

const val APP_VERSION_CODE = 4
const val APP_VERSION_NAME = "1.0.3"

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(NavHome)

    // Repositorio central compartido en memoria y Sheets
    val sharedRepository = remember { BabyInventoryRepository() }

    // Estados de configuración del Bebé y FUR
    var babyNameState by remember { mutableStateOf("Nuestro Niño") }
    var lmpDateState by remember { mutableStateOf(LocalDate.now().minusWeeks(27).minusDays(2)) }
    var activeUserState by remember { mutableStateOf("Papá (Edward)") }

    val googleSheetUrlState = remember { SecureConfig.getGoogleSheetUrl() }
    val geminiApiKeyState = remember { SecureConfig.getGeminiApiKey() }

    // Estado de actualización OTA
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadProgress by remember { mutableIntStateOf(-1) }

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
            updateLmp(lmpDateState)
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

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<NavHome> {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToInventory = { backStack.add(NavInventory) },
                    onNavigateToConsumables = { backStack.add(NavConsumables) },
                    onNavigateToSettings = { backStack.add(NavSettings) }
                )
            }
            entry<NavInventory> {
                InventoryScreen(
                    viewModel = inventoryViewModel,
                    googleSheetUrl = googleSheetUrlState,
                    geminiApiKey = geminiApiKeyState,
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
            entry<NavSettings> {
                SettingsScreen(
                    babyName = babyNameState,
                    currentLmp = lmpDateState,
                    currentUser = activeUserState,
                    onSaveBabySettings = { newName, newLmp, newUser ->
                        babyNameState = newName
                        lmpDateState = newLmp
                        activeUserState = newUser
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
            onDismiss = { pendingUpdate = null },
            onConfirmUpdate = {
                coroutineScope.launch {
                    downloadProgress = 0
                    OtaUpdateManager.downloadAndInstallApk(
                        context = context,
                        apkUrl = update.apkUrl,
                        onProgress = { progress -> downloadProgress = progress }
                    )
                }
            }
        )
    }
}
