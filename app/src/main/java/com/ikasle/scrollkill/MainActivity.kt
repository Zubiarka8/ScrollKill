package com.ikasle.scrollkill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
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
import com.ikasle.scrollkill.ui.theme.ScrollKillTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScrollKillTheme {
                val context = LocalContext.current
                val vm: HomeViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            val app = this[APPLICATION_KEY] as ScrollKillApp
                            HomeViewModel(app.sessionRepository, app.settingsRepository)
                        }
                    },
                )
                val state by vm.uiState.collectAsStateWithLifecycle()

                LifecycleResumeEffect(Unit) {
                    vm.onResume(isScrollKillAccessibilityEnabled(context))
                    onPauseOrDispose {}
                }

                HomeScreen(
                    state = state,
                    onToggleIntervene = vm::setInterveneEnabled,
                    onOpenAccessibilitySettings = {
                        context.startActivity(accessibilitySettingsIntent())
                    },
                )
            }
        }
    }
}
