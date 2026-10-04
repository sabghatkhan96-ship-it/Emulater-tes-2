package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.VaultItem
import com.example.ui.screens.AddEditCredentialScreen
import com.example.ui.screens.AutofillPlaygroundScreen
import com.example.ui.screens.PasswordGeneratorScreen
import com.example.ui.screens.SecurityAuditScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VaultHomeScreen
import com.example.ui.screens.VaultLockScreen
import com.example.ui.theme.VaultPassTheme
import com.example.ui.viewmodel.VaultViewModel

sealed class AppScreen {
    object Home : AppScreen()
    data class AddEdit(val item: VaultItem? = null) : AppScreen()
    object Generator : AppScreen()
    object Audit : AppScreen()
    object Autofill : AppScreen()
    object Settings : AppScreen()
}

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VaultPassTheme {
                val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
                val isPinSetupRequired by viewModel.isPinSetupRequired.collectAsStateWithLifecycle()
                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

                // Auto-lock lifecycle observer
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    var backgroundTime = 0L
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                backgroundTime = System.currentTimeMillis()
                            }
                            Lifecycle.Event.ON_START -> {
                                val autoLockSec = viewModel.securityPrefs.autoLockSeconds
                                if (backgroundTime > 0L) {
                                    val elapsedSec = (System.currentTimeMillis() - backgroundTime) / 1000
                                    if (elapsedSec >= autoLockSec && viewModel.securityPrefs.isPinConfigured) {
                                        viewModel.lockVault()
                                    }
                                }
                                viewModel.refreshAutofillStatus()
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = isLocked || isPinSetupRequired,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(100)) togetherWith fadeOut(animationSpec = tween(100))
                        },
                        label = "lock_transition"
                    ) { locked ->
                        if (locked) {
                            VaultLockScreen(
                                viewModel = viewModel,
                                isSetupMode = isPinSetupRequired
                            )
                        } else {
                            when (val screen = currentScreen) {
                                is AppScreen.Home -> {
                                    VaultHomeScreen(
                                        viewModel = viewModel,
                                        onAddNewCredential = { currentScreen = AppScreen.AddEdit(null) },
                                        onEditCredential = { item -> currentScreen = AppScreen.AddEdit(item) },
                                        onNavigateAudit = { currentScreen = AppScreen.Audit },
                                        onNavigateAutofill = { currentScreen = AppScreen.Autofill },
                                        onNavigateGenerator = { currentScreen = AppScreen.Generator },
                                        onNavigateSettings = { currentScreen = AppScreen.Settings }
                                    )
                                }

                                is AppScreen.AddEdit -> {
                                    BackHandler { currentScreen = AppScreen.Home }
                                    AddEditCredentialScreen(
                                        initialItem = screen.item,
                                        viewModel = viewModel,
                                        onNavigateBack = { currentScreen = AppScreen.Home }
                                    )
                                }

                                is AppScreen.Generator -> {
                                    BackHandler { currentScreen = AppScreen.Home }
                                    PasswordGeneratorScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { currentScreen = AppScreen.Home }
                                    )
                                }

                                is AppScreen.Audit -> {
                                    BackHandler { currentScreen = AppScreen.Home }
                                    SecurityAuditScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { currentScreen = AppScreen.Home },
                                        onEditItem = { item -> currentScreen = AppScreen.AddEdit(item) }
                                    )
                                }

                                is AppScreen.Autofill -> {
                                    BackHandler { currentScreen = AppScreen.Home }
                                    AutofillPlaygroundScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { currentScreen = AppScreen.Home }
                                    )
                                }

                                is AppScreen.Settings -> {
                                    BackHandler { currentScreen = AppScreen.Home }
                                    SettingsScreen(
                                        viewModel = viewModel,
                                        onNavigateBack = { currentScreen = AppScreen.Home }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
