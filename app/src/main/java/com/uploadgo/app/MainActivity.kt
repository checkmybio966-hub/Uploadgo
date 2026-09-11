package com.uploadgo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uploadgo.app.data.prefs.Settings
import com.uploadgo.app.data.prefs.ThemeMode
import com.uploadgo.app.ui.UploadGoApp
import com.uploadgo.app.ui.theme.UploadGoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by AppGraph.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = Settings())

            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            UploadGoTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.themeMode == ThemeMode.SYSTEM,
            ) {
                UploadGoApp()
            }
        }
    }
}
