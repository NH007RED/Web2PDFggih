@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.web2pdf.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.web2pdf.model.ConversionState
import com.nhdeveloper.web2pdf.model.PdfOptions
import com.nhdeveloper.web2pdf.pdf.SystemPrint
import com.nhdeveloper.web2pdf.ui.components.AppIcons
import com.nhdeveloper.web2pdf.ui.components.PdfOptionsPanel
import com.nhdeveloper.web2pdf.ui.components.PremiumCard
import com.nhdeveloper.web2pdf.utils.FileNameUtils
import com.nhdeveloper.web2pdf.utils.PageRanges
import com.nhdeveloper.web2pdf.utils.UrlUtils
import com.nhdeveloper.web2pdf.viewmodel.ConvertViewModel
import com.nhdeveloper.web2pdf.webview.BrowserState
import com.nhdeveloper.web2pdf.webview.createBrowserWebView
import kotlinx.coroutines.delay

private enum class PreviewStage { Browse, Settings, Converting }

@Composable
fun PreviewScreen(vm: ConvertViewModel, onBack: () -> Unit, onSuccess: (Long) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { BrowserState() }
    val webView = remember { createBrowserWebView(context, state) }
    val conversion by vm.conversion.collectAsStateWithLifecycle()
    var stage by remember { mutableStateOf(PreviewStage.Browse) }
    var options by remember { mutableStateOf(vm.settings.value.defaultOptions) }
    var fileName by remember { mutableStateOf("") }
    val startUrl = remember { vm.sessionUrl }

    LaunchedEffect(Unit) {
        if (startUrl.isBlank()) onBack() else webView.loadUrl(startUrl)
    }

    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.cancel()
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.webChromeClient = null
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    // Warn when a page is taking very long to load.
    LaunchedEffect(state.loading, state.currentUrl) {
        if (state.loading) {
            delay(45_000)
            if (state.loading && state.warning == null && state.error == null) {
                state.warning =
                    "This page is taking a long time to load. You can keep waiting, reload it, or tap Stop and convert what has loaded."
            }
        }
    }

    LaunchedEffect(conversion) {
        val c = conversion
        if (c is ConversionState.Success) {
            onSuccess(c.record.id)
            vm.reset()
        }
    }

    BackHandler {
        when (stage) {
            PreviewStage.Converting -> {
                vm.cancel()
                stage = PreviewStage.Settings
            }
            PreviewStage.Settings -> stage = PreviewStage.Browse
            PreviewStage.Browse -> if (webView.canGoBack()) webView.goBack() else onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            BrowserBar(
                state = state,
                onClose = onBack,
                onBackPage = { webView.goBack() },
                onForwardPage = { webView.goForward() },
                onReloadOrStop = {
                    if (state.loading) {
                        webView.stopLoading()
                        state.userStopped = true
                        state.loading = false
                    } else {
                        webView.reload()
                    }
                }
            )

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

                val error = state.error
                val warning = state.warning
                if (error != null) {
                    ErrorPanel(
                        message = error,
                        crashed = state.crashed,
                        onRetry = {
                            state.error = null
                            webView.loadUrl(startUrl)
                        },
                        onBack = onBack
                    )
                } else if (warning != null) {
                    WarningBanner(warning, onDismiss = { state.warning = null }, modifier = Modifier.align(Alignment.TopCenter))
                }
            }

            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        fileName = FileNameUtils.defaultName(state.title, state.currentUrl.ifBlank { startUrl })
                        stage = PreviewStage.Settings
                    },
                    enabled = state.error == null && !state.crashed,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                ) {
                    Icon(AppIcons.Pdf, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generate PDF")
                }
            }
        }

        AnimatedVisibility(
            visible = stage == PreviewStage.Settings,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut() + slideOutVertically { it / 6 }
        ) {
            SettingsOverlay(
                options = options,
                onOptions = { options = it },
                fileName = fileName,
                onFileName = { fileName = it.take(100) },
                onBack = { stage = PreviewStage.Browse },
                onGenerate = {
                    stage = PreviewStage.Converting
                    vm.convert(
                        webView = webView,
                        pageTitle = state.title,
                        options = options,
                        fileName = FileNameUtils.ensurePdf(fileName),
                        isPageReady = { webView.progress >= 100 || state.userStopped }
                    )
                }
            )
        }

        AnimatedVisibility(
            visible = stage == PreviewStage.Converting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ConversionOverlay(
                conversion = conversion,
                onCancel = {
                    vm.cancel()
                    stage = PreviewStage.Settings
                },
                onBackToSettings = {
                    vm.reset()
                    stage = PreviewStage.Settings
                },
                onSystemPrint = {
                    SystemPrint.print(context, webView, FileNameUtils.ensurePdf(fileName), options)
                }
            )
        }
    }
}

@Composable
private fun BrowserBar(
    state: BrowserState,
    onClose: () -> Unit,
    onBackPage: () -> Unit,
    onForwardPage: () -> Unit,
    onReloadOrStop: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(AppIcons.Close, contentDescription = "Close preview") }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title?.takeIf { it.isNotBlank() } ?: UrlUtils.hostOf(state.currentUrl),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (state.isSecure) AppIcons.Lock else AppIcons.Warning,
                            contentDescription = if (state.isSecure) "Secure connection" else "Not secure",
                            modifier = Modifier.size(12.dp),
                            tint = if (state.isSecure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            state.currentUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onBackPage, enabled = state.canGoBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = "Page back")
                }
                IconButton(onClick = onForwardPage, enabled = state.canGoForward) {
                    Icon(AppIcons.ArrowForward, contentDescription = "Page forward")
                }
                IconButton(onClick = onReloadOrStop) {
                    if (state.loading) Icon(AppIcons.Close, contentDescription = "Stop loading")
                    else Icon(AppIcons.Refresh, contentDescription = "Reload")
                }
            }
            if (state.loading) {
                LinearProgressIndicator(
                    progress = (state.progress / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            } else {
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun ErrorPanel(message: String, crashed: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        PremiumCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    AppIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("Page could not be loaded", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onBack) { Text("Go back") }
                    if (!crashed) Button(onClick = onRetry) { Text("Try again") }
                }
            }
        }
    }
}

@Composable
private fun WarningBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        IconButton(onClick = onDismiss) {
            Icon(AppIcons.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun SettingsOverlay(
    options: PdfOptions,
    onOptions: (PdfOptions) -> Unit,
    fileName: String,
    onFileName: (String) -> Unit,
    onBack: () -> Unit,
    onGenerate: () -> Unit
) {
    val canGenerate = fileName.isNotBlank() && PageRanges.isValid(options.pageRange)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = "Back to preview") }
            Text("PDF settings", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PremiumCard(Modifier.fillMaxWidth()) {
                PdfOptionsPanel(options = options, onChange = onOptions, showPageRange = true)
            }
            PremiumCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = onFileName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PDF file name") },
                    singleLine = true,
                    suffix = { Text(".pdf") },
                    isError = fileName.isBlank()
                )
            }
            Text(
                "Headers and footers are not offered because Android's print engine does not support custom ones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Button(
                onClick = onGenerate,
                enabled = canGenerate,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
            ) {
                Icon(AppIcons.Pdf, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Generate PDF")
            }
        }
    }
}

@Composable
private fun ConversionOverlay(
    conversion: ConversionState,
    onCancel: () -> Unit,
    onBackToSettings: () -> Unit,
    onSystemPrint: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (conversion) {
            is ConversionState.Failed -> {
                Icon(
                    AppIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("Conversion failed", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(conversion.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onBackToSettings) { Text("Back to settings") }
                if (conversion.canUseSystemPrint) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onSystemPrint) { Text("Use system print dialog") }
                }
            }
            else -> {
                val percent = (conversion as? ConversionState.Running)?.percent ?: 0
                val animated by animateFloatAsState(
                    targetValue = percent / 100f,
                    animationSpec = tween(400),
                    label = "convProgress"
                )
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                    CircularProgressIndicator(progress = animated, modifier = Modifier.fillMaxSize(), strokeWidth = 8.dp)
                    Text("$percent%", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(24.dp))
                Text("Converting to PDF", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                val status = (conversion as? ConversionState.Running)?.status ?: "Starting…"
                Text(
                    status,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(28.dp))
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
