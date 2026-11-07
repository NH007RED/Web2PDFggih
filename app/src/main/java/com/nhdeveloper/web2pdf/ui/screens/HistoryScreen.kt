@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.web2pdf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.web2pdf.model.ConversionRecord
import com.nhdeveloper.web2pdf.model.FileStat
import com.nhdeveloper.web2pdf.model.SortOrder
import com.nhdeveloper.web2pdf.ui.components.AppIcons
import com.nhdeveloper.web2pdf.ui.components.AppTopBar
import com.nhdeveloper.web2pdf.ui.components.DeleteDialog
import com.nhdeveloper.web2pdf.ui.components.PremiumCard
import com.nhdeveloper.web2pdf.ui.components.RenameDialog
import com.nhdeveloper.web2pdf.utils.FileNameUtils
import com.nhdeveloper.web2pdf.utils.IntentUtils
import com.nhdeveloper.web2pdf.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(vm: HistoryViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val records by vm.items.collectAsStateWithLifecycle()
    val total by vm.totalCount.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<ConversionRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversionRecord?>(null) }

    Scaffold(
        topBar = { AppTopBar("History", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search title, URL or file name") },
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.query.value = "" }) {
                                Icon(AppIcons.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = sort == SortOrder.NEWEST,
                        onClick = { vm.sort.value = SortOrder.NEWEST },
                        label = { Text("Newest first") }
                    )
                    FilterChip(
                        selected = sort == SortOrder.OLDEST,
                        onClick = { vm.sort.value = SortOrder.OLDEST },
                        label = { Text("Oldest first") }
                    )
                }
            }

            if (records.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (total == 0) "No conversions yet." else "No results for your search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        HistoryItem(
                            record = record,
                            vm = vm,
                            onOpen = { onOpen(record.id) },
                            onShare = {
                                scope.launch {
                                    val f = vm.prepareFile(record, "share")
                                    if (f == null) snackbar.showSnackbar("The PDF file could not be found.")
                                    else IntentUtils.share(context, f, record.fileName)
                                }
                            },
                            onRename = { renameTarget = record },
                            onDelete = { deleteTarget = record }
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            currentName = target.fileName,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                scope.launch {
                    val err = vm.rename(target, name)
                    snackbar.showSnackbar(err ?: "Renamed.")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        DeleteDialog(
            fileName = target.fileName,
            onDismiss = { deleteTarget = null },
            onHistoryOnly = {
                deleteTarget = null
                scope.launch {
                    vm.remove(target, deleteFile = false)
                    snackbar.showSnackbar("Removed from history.")
                }
            },
            onDeleteFile = {
                deleteTarget = null
                scope.launch {
                    val ok = vm.remove(target, deleteFile = true)
                    snackbar.showSnackbar(
                        if (ok) "PDF deleted." else "The file could not be deleted, so the history entry was kept."
                    )
                }
            }
        )
    }
}

@Composable
private fun HistoryItem(
    record: ConversionRecord,
    vm: HistoryViewModel,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val stat by produceState<FileStat?>(null, record.fileUri) { value = vm.stat(record) }
    val missing = stat?.exists == false

    PremiumCard(Modifier.fillMaxWidth(), onClick = if (missing) null else onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Pdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    record.url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    FileNameUtils.formatDate(record.createdAt) + " • " + FileNameUtils.formatSize(record.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    record.fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (missing) {
            Text(
                "File missing: it was moved or deleted outside the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!missing) {
                IconButton(onClick = onOpen) { Icon(AppIcons.Pdf, contentDescription = "Open") }
                IconButton(onClick = onShare) { Icon(AppIcons.Share, contentDescription = "Share") }
                IconButton(onClick = onRename) { Icon(AppIcons.Edit, contentDescription = "Rename") }
            }
            IconButton(onClick = onDelete) {
                Icon(AppIcons.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
