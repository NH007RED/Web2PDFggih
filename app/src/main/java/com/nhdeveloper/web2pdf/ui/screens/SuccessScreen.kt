@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.web2pdf.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.web2pdf.model.ConversionRecord
import com.nhdeveloper.web2pdf.model.FileStat
import com.nhdeveloper.web2pdf.model.RecordState
import com.nhdeveloper.web2pdf.ui.components.AppIcons
import com.nhdeveloper.web2pdf.ui.components.AppTopBar
import com.nhdeveloper.web2pdf.ui.components.PremiumCard
import com.nhdeveloper.web2pdf.ui.components.RenameDialog
import com.nhdeveloper.web2pdf.utils.FileNameUtils
import com.nhdeveloper.web2pdf.utils.IntentUtils
import com.nhdeveloper.web2pdf.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

@Composable
fun SuccessScreen(
    id: Long,
    vm: HistoryViewModel,
    onPreview: () -> Unit,
    onHome: () -> Unit
) {
    val state by remember(id) { vm.recordState(id) }.collectAsStateWithLifecycle(RecordState.Loading)
    Scaffold(
        topBar = { AppTopBar("PDF created") },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (val s = state) {
                is RecordState.Loading -> CircularProgressIndicator()
                is RecordState.NotFound -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("This entry no longer exists.")
                    TextButton(onClick = onHome) { Text("Back to Home") }
                }
                is RecordState.Found -> SuccessContent(s.record, vm, onPreview, onHome)
            }
        }
    }
}

@Composable
private fun SuccessContent(
    record: ConversionRecord,
    vm: HistoryViewModel,
    onPreview: () -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showRename by remember { mutableStateOf(false) }
    val stat by produceState<FileStat?>(null, record.fileUri) { value = vm.stat(record) }
    val missing = stat?.exists == false

    var appear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appear = true }
    val scale by animateFloatAsState(
        targetValue = if (appear) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "successScale"
    )

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = vm.exportTo(record, uri)
                snackbar.showSnackbar(if (ok) "PDF saved to the location you chose." else "Could not save the PDF.")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(96.dp).scale(scale).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                AppIcons.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Your PDF is ready", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        PremiumCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Pdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(record.fileName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        FileNameUtils.formatSize(stat?.takeIf { it.exists }?.size ?: record.sizeBytes) +
                            " • " + record.pageCount + (if (record.pageCount == 1) " page" else " pages"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showRename = true }, enabled = !missing) { Text("Rename") }
            }
        }

        if (missing) {
            Spacer(Modifier.height(12.dp))
            Text(
                "This file was moved or deleted outside the app.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onPreview, enabled = !missing, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(AppIcons.Pdf, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Preview PDF")
            }
            OutlinedButton(
                onClick = { saveLauncher.launch(record.fileName) },
                enabled = !missing,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download / Save a copy")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val f = vm.prepareFile(record, "share")
                            if (f == null) snackbar.showSnackbar("The PDF file could not be found.")
                            else IntentUtils.share(context, f, record.fileName)
                        }
                    },
                    enabled = !missing,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(AppIcons.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val f = vm.prepareFile(record, "share")
                            if (f == null) snackbar.showSnackbar("The PDF file could not be found.")
                            else IntentUtils.openExternally(context, f, record.fileName)
                        }
                    },
                    enabled = !missing,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(AppIcons.OpenIn, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open in…", maxLines = 1)
                }
            }
            TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Back to Home") }
        }
        SnackbarHost(snackbar)
    }

    if (showRename) {
        RenameDialog(
            currentName = record.fileName,
            onDismiss = { showRename = false },
            onConfirm = { name ->
                showRename = false
                scope.launch {
                    val err = vm.rename(record, name)
                    snackbar.showSnackbar(err ?: "Renamed.")
                }
            }
        )
    }
}
