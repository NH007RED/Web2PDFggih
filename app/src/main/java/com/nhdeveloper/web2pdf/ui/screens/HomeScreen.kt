@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.web2pdf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.web2pdf.model.ConversionRecord
import com.nhdeveloper.web2pdf.ui.components.AppIcons
import com.nhdeveloper.web2pdf.ui.components.PremiumCard
import com.nhdeveloper.web2pdf.ui.components.SectionTitle
import com.nhdeveloper.web2pdf.utils.ClipboardUtils
import com.nhdeveloper.web2pdf.utils.FileNameUtils
import com.nhdeveloper.web2pdf.utils.UrlResult
import com.nhdeveloper.web2pdf.viewmodel.ConvertViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    vm: ConvertViewModel,
    onConvert: () -> Unit,
    onOpenInput: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onOpenRecord: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val recent by vm.recent.collectAsStateWithLifecycle()

    fun convertNow() {
        if (vm.urlText.isBlank()) onOpenInput() else if (vm.startSession()) onConvert()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { HomeHeader(onHistory = onHistory, onSettings = onSettings) }

            item {
                PremiumCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Text("Convert a website", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Paste a link, preview the page, then save it as a real PDF.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = vm.urlText,
                        onValueChange = vm::onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Website URL") },
                        placeholder = { Text("example.com/article") },
                        singleLine = true,
                        leadingIcon = { Icon(AppIcons.Link, contentDescription = null) },
                        trailingIcon = {
                            if (vm.urlText.isNotEmpty()) {
                                IconButton(onClick = { vm.onUrlChange("") }) {
                                    Icon(AppIcons.Close, contentDescription = "Clear URL")
                                }
                            }
                        },
                        isError = vm.urlResult is UrlResult.Invalid,
                        supportingText = {
                            val r = vm.urlResult
                            if (r is UrlResult.Invalid) Text(r.reason)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { convertNow() })
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val text = ClipboardUtils.readText(context)
                                if (text.isNullOrBlank()) {
                                    scope.launch { snackbar.showSnackbar("The clipboard is empty.") }
                                } else {
                                    vm.onUrlChange(text.trim())
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(AppIcons.Paste, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Paste URL")
                        }
                        Button(onClick = { convertNow() }, modifier = Modifier.weight(1f)) {
                            Icon(AppIcons.Pdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Convert to PDF", maxLines = 1)
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Recent conversions", Modifier.weight(1f))
                    TextButton(onClick = onHistory) { Text("View all") }
                }
            }

            if (recent.isEmpty()) {
                item {
                    PremiumCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Text(
                            "No conversions yet. Your converted PDFs will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(recent.size) { i ->
                    RecentItem(recent[i], Modifier.padding(horizontal = 16.dp)) { onOpenRecord(recent[i].id) }
                }
            }

            item { SectionTitle("Quick tips", Modifier.padding(horizontal = 16.dp)) }
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TipCard("Wait for the page to finish loading in the preview before generating the PDF.")
                    TipCard("Pages that need a login or block automated browsing may not convert correctly.")
                    TipCard("Use Landscape and Narrow margins for wide tables and dashboards.")
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(
    count: Int,
    content: @Composable (Int) -> Unit
) {
    for (i in 0 until count) {
        item { content(i) }
    }
}

@Composable
private fun HomeHeader(onHistory: () -> Unit, onSettings: () -> Unit) {
    val brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(brush)
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Pdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Web2PDF", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(
                    "Website to PDF Converter",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            IconButton(onClick = onHistory) {
                Icon(AppIcons.History, contentDescription = "History", tint = Color.White)
            }
            IconButton(onClick = onSettings) {
                Icon(AppIcons.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    }
}

@Composable
fun RecentItem(record: ConversionRecord, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PremiumCard(modifier.fillMaxWidth(), onClick = onClick) {
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
                    FileNameUtils.formatDate(record.createdAt) + " • " + FileNameUtils.formatSize(record.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TipCard(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(AppIcons.Bulb, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
