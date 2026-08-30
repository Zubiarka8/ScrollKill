package com.ikasle.scrollkill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.ikasle.scrollkill.service.accessibilitySettingsIntent
import com.ikasle.scrollkill.service.isScrollKillAccessibilityEnabled
import com.ikasle.scrollkill.ui.home.HomeScreen
import com.ikasle.scrollkill.ui.home.HomeViewModel
import com.ikasle.scrollkill.ui.settings.SettingsScreen
import com.ikasle.scrollkill.ui.settings.SettingsViewModel
import com.ikasle.scrollkill.ui.theme.ScrollKillTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScrollKillTheme {
                val context = LocalContext.current
                var showSettings by rememberSaveable { mutableStateOf(false) }

                val homeViewModel: HomeViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            val app = this[APPLICATION_KEY] as ScrollKillApp
                            HomeViewModel(app.sessionRepository, app.settingsRepository)
                        }
                    },
                )

                if (showSettings) {
                    BackHandler { showSettings = false }
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                val app = this[APPLICATION_KEY] as ScrollKillApp
                                SettingsViewModel(app.settingsRepository)
                            }
                        },
                    )
                    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                    SettingsScreen(
                        state = settingsState,
                        onBack = { showSettings = false },
                        onToggleIntervene = settingsViewModel::setInterveneEnabled,
                        onToggleApp = settingsViewModel::setAppBlockingEnabled,
                        onPickDefaultDailyLimit = settingsViewModel::setDefaultDailyLimit,
                        onPickAppDailyLimit = settingsViewModel::setAppDailyLimit,
                        onPickWindow = settingsViewModel::setStatsWindow,
                        onPickRetention = settingsViewModel::setHistoryRetention,
                    )
                } else {
                    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
                    LifecycleResumeEffect(Unit) {
                        homeViewModel.onResume(isScrollKillAccessibilityEnabled(context))
                        onPauseOrDispose {}
                    }
                    HomeScreen(
                        state = homeState,
                        onToggleIntervene = homeViewModel::setInterveneEnabled,
                        onOpenAccessibilitySettings = {
                            context.startActivity(accessibilitySettingsIntent())
                        },
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }
}
