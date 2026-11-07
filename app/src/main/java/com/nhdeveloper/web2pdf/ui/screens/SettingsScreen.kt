@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.web2pdf.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.web2pdf.model.ThemeMode
import com.nhdeveloper.web2pdf.ui.components.AppIcons
import com.nhdeveloper.web2pdf.ui.components.AppTopBar
import com.nhdeveloper.web2pdf.ui.components.InfoDialog
import com.nhdeveloper.web2pdf.ui.components.OptionRow
import com.nhdeveloper.web2pdf.ui.components.PdfOptionsPanel
import com.nhdeveloper.web2pdf.ui.components.PremiumCard
import com.nhdeveloper.web2pdf.ui.components.SectionTitle
import com.nhdeveloper.web2pdf.utils.FileNameUtils
import com.nhdeveloper.web2pdf.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private const val PRIVACY_TEXT =
    "Web2PDF does not collect, upload or share any personal data. There are no accounts, ads or analytics.\n\n" +
        "The web addresses you convert, the conversion history and the generated PDFs stay on your device. " +
        "The app only needs the Internet permission to load the pages you choose; those websites may collect " +
        "data according to their own privacy policies.\n\n" +
        "You can delete any PDF or history entry at any time from the History screen."

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { cacheBytes = vm.cacheSize() }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = vm.setSaveFolder(uri)
                snackbar.showSnackbar(if (ok) "Save folder updated." else "Could not get access to that folder.")
            }
        }
    }

    val versionName = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    Scaffold(
        topBar = { AppTopBar("Settings", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionTitle("Appearance")
            PremiumCard(Modifier.fillMaxWidth()) {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                OptionRow(ThemeMode.values().toList(), settings.theme, { it.label }) { vm.setTheme(it) }
            }

            SectionTitle("Default PDF settings")
            PremiumCard(Modifier.fillMaxWidth()) {
                PdfOptionsPanel(
                    options = settings.defaultOptions,
                    onChange = { vm.setDefaults(it) },
                    showPageRange = false
                )
            }

            SectionTitle("Storage")
            PremiumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Save location", style = MaterialTheme.typography.bodyLarge)
                        val tree = settings.saveTreeUri
                        val label = when {
                            tree == null -> "Downloads/Web2PDF (default)"
                            vm.folderAvailable(tree) -> "Custom folder: " + (android.net.Uri.parse(tree).lastPathSegment ?: tree)
                            else -> "Chosen folder is no longer available; using the default"
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.weight(1f)) {
                        Text("Choose folder")
                    }
                    OutlinedButton(
                        onClick = { vm.resetSaveFolder() },
                        enabled = settings.saveTreeUri != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Use default") }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Saved PDFs: " + stats.first + " • " + FileNameUtils.formatSize(stats.second),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Temporary cache: " + FileNameUtils.formatSize(cacheBytes),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            val freed = vm.clearCache()
                            cacheBytes = vm.cacheSize()
                            snackbar.showSnackbar("Cache cleared (" + FileNameUtils.formatSize(freed) + ").")
                        }
                    }
                ) { Text("Clear cache") }
            }

            SectionTitle("About")
            PremiumCard(Modifier.fillMaxWidth()) {
                SettingLink("Privacy policy") { showPrivacy = true }
                SettingLink("About Web2PDF") { showAbout = true }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Version $versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showPrivacy) InfoDialog("Privacy policy", PRIVACY_TEXT) { showPrivacy = false }
    if (showAbout) {
        InfoDialog(
            "About Web2PDF",
            "Web2PDF converts web pages into real PDF files on your device using Android's WebView and print framework.\n\n" +
                "No account, no ads, no hidden paid features.\n\nVersion $versionName\nBy NH Developer"
        ) { showAbout = false }
    }
}

@Composable
private fun SettingLink(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(AppIcons.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}
