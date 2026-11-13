package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainRootScreen
import com.example.ui.effects.LocalLuxReduceEffects
import com.example.ui.effects.LuxSplashOverlay
import com.example.ui.effects.rememberEffectiveReduceEffects
import com.example.ui.navigation.Screen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.PlayLuxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Android 12+ SplashScreen
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as PlayLuxApplication).container

        setContent {
            val themeMode by appContainer.settingsStore.themeMode.collectAsStateWithLifecycle(
                initialValue = AppThemeMode.DARK
            )
            val userReduceEffects by appContainer.settingsStore.reduceEffects.collectAsStateWithLifecycle(
                initialValue = false
            )
            val onboardingCompleted by appContainer.settingsStore.isOnboardingCompleted.collectAsStateWithLifecycle(
                initialValue = null
            )

            val effectiveReduceEffects = rememberEffectiveReduceEffects(userReduceEffects)
            var showSplashOverlay by remember { mutableStateOf(true) }

            CompositionLocalProvider(LocalLuxReduceEffects provides effectiveReduceEffects) {
                PlayLuxTheme(themeMode = themeMode) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        onboardingCompleted?.let { completed ->
                            MainRootScreen(
                                appContainer = appContainer,
                                startDestination = if (completed) Screen.Vault.route else Screen.Onboarding.route
                            )
                        }

                        LuxSplashOverlay(
                            visible = showSplashOverlay,
                            onFinished = { showSplashOverlay = false }
                        )
                    }
                }
            }
        }
    }
}

