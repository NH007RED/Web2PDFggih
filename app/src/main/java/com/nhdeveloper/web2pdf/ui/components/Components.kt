@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.web2pdf.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nhdeveloper.web2pdf.model.MarginSize
import com.nhdeveloper.web2pdf.model.Orientation
import com.nhdeveloper.web2pdf.model.PaperSize
import com.nhdeveloper.web2pdf.model.PdfOptions
import com.nhdeveloper.web2pdf.utils.PageRanges

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val shape = RoundedCornerShape(24.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    val elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, elevation = elevation) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, elevation = elevation) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = "Back") }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun <T> OptionRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Paper size, orientation, margins, background graphics (and optional page range). */
@Composable
fun PdfOptionsPanel(
    options: PdfOptions,
    onChange: (PdfOptions) -> Unit,
    showPageRange: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Paper size", style = MaterialTheme.typography.labelLarge)
        OptionRow(PaperSize.values().toList(), options.paper, { it.label }) { onChange(options.copy(paper = it)) }

        Text("Orientation", style = MaterialTheme.typography.labelLarge)
        OptionRow(Orientation.values().toList(), options.orientation, { it.label }) {
            onChange(options.copy(orientation = it))
        }

        Text("Page margins", style = MaterialTheme.typography.labelLarge)
        OptionRow(MarginSize.values().toList(), options.margin, { it.label }) { onChange(options.copy(margin = it)) }

        SwitchRow(
            title = "Background graphics",
            subtitle = "Keep page colors and background images. Turn off to remove backgrounds (saves ink).",
            checked = options.backgroundGraphics,
            onChange = { onChange(options.copy(backgroundGraphics = it)) }
        )

        if (showPageRange) {
            val valid = PageRanges.isValid(options.pageRange)
            OutlinedTextField(
                value = options.pageRange,
                onValueChange = { onChange(options.copy(pageRange = it.take(60))) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Page range (optional)") },
                placeholder = { Text("All pages, or e.g. 1-3, 5") },
                singleLine = true,
                isError = !valid,
                supportingText = {
                    Text(
                        if (valid) "Leave empty to include every page."
                        else "Use formats like 1-3, 5 (page numbers start at 1)."
                    )
                }
            )
        }
    }
}

@Composable
fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(currentName.removeSuffix(".pdf")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename PDF") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("File name") },
                suffix = { Text(".pdf") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DeleteDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onHistoryOnly: () -> Unit,
    onDeleteFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete PDF?") },
        text = {
            Text(
                "\"$fileName\"\n\nDelete the PDF file from your device, or only remove it from the history list?"
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onDeleteFile) {
                    Text("Delete PDF file", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onHistoryOnly) { Text("Remove from history only") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
