package com.nhdeveloper.web2pdf

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.web2pdf.model.ThemeMode
import com.nhdeveloper.web2pdf.navigation.AppNavigation
import com.nhdeveloper.web2pdf.ui.theme.Web2PdfTheme
import com.nhdeveloper.web2pdf.viewmodel.ConvertViewModel
import com.nhdeveloper.web2pdf.viewmodel.HistoryViewModel
import com.nhdeveloper.web2pdf.viewmodel.SettingsViewModel
import com.nhdeveloper.web2pdf.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {

    private val factory by lazy { ViewModelFactory((application as Web2PdfApp).container) }
    private val convertVm: ConvertViewModel by viewModels { factory }
    private val historyVm: HistoryViewModel by viewModels { factory }
    private val settingsVm: SettingsViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by settingsVm.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            Web2PdfTheme(darkTheme = dark) {
                AppNavigation(convertVm, historyVm, settingsVm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Accepts a link shared from another app (Share > Web2PDF). */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val match = Regex("https?://\\S+").find(text) ?: return
            convertVm.onUrlChange(match.value)
        }
    }
}
