@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.web2pdf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nhdeveloper.web2pdf.ui.components.AppIcons
import com.nhdeveloper.web2pdf.ui.components.AppTopBar
import com.nhdeveloper.web2pdf.ui.components.PremiumCard
import com.nhdeveloper.web2pdf.utils.ClipboardUtils
import com.nhdeveloper.web2pdf.utils.UrlResult
import com.nhdeveloper.web2pdf.viewmodel.ConvertViewModel
import kotlinx.coroutines.launch

@Composable
fun UrlInputScreen(vm: ConvertViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val result = vm.urlResult

    Scaffold(
        topBar = { AppTopBar("Enter website address", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PremiumCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = vm.urlText,
                    onValueChange = vm::onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Website URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    leadingIcon = { Icon(AppIcons.Link, contentDescription = null) },
                    trailingIcon = {
                        if (vm.urlText.isNotEmpty()) {
                            IconButton(onClick = { vm.onUrlChange("") }) {
                                Icon(AppIcons.Close, contentDescription = "Clear URL")
                            }
                        }
                    },
                    isError = result is UrlResult.Invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.validate() })
                )
                Spacer(Modifier.height(12.dp))
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
                        Text("Paste")
                    }
                    OutlinedButton(
                        onClick = { vm.onUrlChange("") },
                        enabled = vm.urlText.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AppIcons.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { vm.validate() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Validate")
                }
            }

            when (result) {
                is UrlResult.Valid -> StatusCard(
                    ok = true,
                    title = "Valid address",
                    message = result.url
                )
                is UrlResult.Invalid -> StatusCard(
                    ok = false,
                    title = "Check the address",
                    message = result.reason
                )
                null -> Unit
            }

            Button(
                onClick = { if (vm.startSession()) onContinue() },
                enabled = vm.urlText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Continue")
                Spacer(Modifier.width(8.dp))
                Icon(AppIcons.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }

            Text(
                "https:// is added automatically when you leave it out. Some websites block automated rendering or need a login; those pages may not convert correctly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusCard(ok: Boolean, title: String, message: String) {
    val container = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (ok) AppIcons.CheckCircle else AppIcons.Warning, contentDescription = null, tint = content)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = content)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}
