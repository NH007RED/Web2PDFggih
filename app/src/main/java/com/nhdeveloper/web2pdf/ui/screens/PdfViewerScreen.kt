@file:OptIn(ExperimentalMaterial3Api::class)

package com.nhdeveloper.web2pdf.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nhdeveloper.web2pdf.model.ConversionRecord
import com.nhdeveloper.web2pdf.model.RecordState
import com.nhdeveloper.web2pdf.ocr.TextBubble
import com.nhdeveloper.web2pdf.ocr.TextBubbleOcr
import com.nhdeveloper.web2pdf.pdf.PdfDocumentReader
import com.nhdeveloper.web2pdf.tts.SpeechHelper
import com.nhdeveloper.web2pdf.ui.components.AppIcons
import com.nhdeveloper.web2pdf.ui.components.AppTopBar
import com.nhdeveloper.web2pdf.utils.FileNameUtils
import com.nhdeveloper.web2pdf.utils.IntentUtils
import com.nhdeveloper.web2pdf.viewmodel.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PdfViewerScreen(id: Long, vm: HistoryViewModel, onBack: () -> Unit) {
    val state by remember(id) { vm.recordState(id) }.collectAsStateWithLifecycle(RecordState.Loading)
    when (val s = state) {
        is RecordState.Found -> ViewerContent(s.record, vm, onBack)
        is RecordState.Loading -> Scaffold(
            topBar = { AppTopBar("PDF preview", onBack = onBack) }
        ) { p -> Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        is RecordState.NotFound -> Scaffold(
            topBar = { AppTopBar("PDF preview", onBack = onBack) }
        ) { p ->
            Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                Text("This PDF entry no longer exists.")
            }
        }
    }
}

private sealed interface ViewerLoad {
    data object Loading : ViewerLoad
    data class Error(val message: String) : ViewerLoad
    data object Ready : ViewerLoad
}

private val zoomLevels = listOf(1f, 1.5f, 2f, 3f)

/** Width (px) of the page image used for reading text; wide enough for small comic lettering. */
private const val OCR_WIDTH = 2000

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Hides the status and navigation bars while [enabled]; swipe from an edge shows them briefly. */
@Composable
private fun ImmersiveEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = view.context.findActivity()?.window
        if (window != null && enabled) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null && enabled) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
private fun ViewerContent(record: ConversionRecord, vm: HistoryViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var load by remember { mutableStateOf<ViewerLoad>(ViewerLoad.Loading) }
    var reader by remember { mutableStateOf<PdfDocumentReader?>(null) }
    var zoomIndex by remember { mutableStateOf(0) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var speakOn by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(record.fileUri) {
        load = ViewerLoad.Loading
        val file = vm.prepareFile(record, "viewer")
        if (file == null) {
            load = ViewerLoad.Error("The PDF file is missing. It may have been moved or deleted outside the app.")
        } else {
            try {
                val r = withContext(Dispatchers.IO) { PdfDocumentReader(file) }
                reader?.close()
                reader = r
                load = ViewerLoad.Ready
            } catch (e: Exception) {
                load = ViewerLoad.Error("This PDF could not be displayed.")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { reader?.close() }
    }

    val isFull = fullscreen && load is ViewerLoad.Ready
    ImmersiveEffect(isFull)
    BackHandler(enabled = isFull) { fullscreen = false }

    Scaffold(
        topBar = {
            if (!isFull) {
                AppTopBar(
                    title = record.fileName,
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                val f = vm.prepareFile(record, "share")
                                if (f == null) snackbar.showSnackbar("The PDF file could not be found.")
                                else IntentUtils.share(context, f, record.fileName)
                            }
                        }) { Icon(AppIcons.Share, contentDescription = "Share") }
                        IconButton(onClick = {
                            scope.launch {
                                val f = vm.prepareFile(record, "share")
                                if (f == null) snackbar.showSnackbar("The PDF file could not be found.")
                                else IntentUtils.openExternally(context, f, record.fileName)
                            }
                        }) { Icon(AppIcons.OpenIn, contentDescription = "Open in another app") }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = if (isFull) Color.Black else MaterialTheme.colorScheme.surfaceVariant
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val l = load) {
                is ViewerLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ViewerLoad.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.padding(6.dp))
                        Text(l.message, textAlign = TextAlign.Center)
                        TextButton(onClick = onBack) { Text("Go back") }
                    }
                }
                is ViewerLoad.Ready -> {
                    val r = reader
                    if (r != null) {
                        ViewerPages(
                            reader = r,
                            record = record,
                            zoomIndex = zoomIndex,
                            onZoom = { zoomIndex = it.coerceIn(0, zoomLevels.lastIndex) },
                            fullscreen = isFull,
                            onToggleFullscreen = { fullscreen = !fullscreen },
                            speakOn = speakOn,
                            onToggleSpeak = { speakOn = !speakOn },
                            snackbar = snackbar
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerPages(
    reader: PdfDocumentReader,
    record: ConversionRecord,
    zoomIndex: Int,
    onZoom: (Int) -> Unit,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    speakOn: Boolean,
    onToggleSpeak: () -> Unit,
    snackbar: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val zoom = zoomLevels[zoomIndex]
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current
    val pageWidthDp = screenWidthDp * zoom
    val widthPx = with(density) { pageWidthDp.toPx() }.toInt().coerceIn(200, 2000)
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    // Tap-to-speak state
    var speaking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf<Pair<Int, Rect>?>(null) }
    val speech = remember { SpeechHelper(context) { speaking = it } }
    val ocr = remember { TextBubbleOcr() }
    val cache = remember(reader) { HashMap<Int, List<TextBubble>>() }

    DisposableEffect(Unit) {
        onDispose {
            speech.shutdown()
            ocr.close()
        }
    }

    LaunchedEffect(speaking) {
        if (!speaking) highlight = null
    }

    LaunchedEffect(speakOn) {
        if (!speakOn) {
            speech.stop()
            highlight = null
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun offerVoiceInstall() {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(
                message = "Hindi voice is not installed on this phone.",
                actionLabel = "Install",
                duration = SnackbarDuration.Long
            )
            if (r == SnackbarResult.ActionPerformed) {
                try {
                    context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                } catch (e: Exception) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
    }

    suspend fun loadBubbles(index: Int): List<TextBubble>? {
        val cached = cache[index]
        if (cached != null) return cached
        val bmp = reader.render(index, OCR_WIDTH)
        if (bmp == null) {
            showMessage("Could not read this page.")
            return null
        }
        val found: List<TextBubble>? = try {
            ocr.recognize(bmp)
        } catch (e: Exception) {
            null
        } finally {
            bmp.recycle()
        }
        if (found == null) {
            showMessage("Could not read the text on this page.")
            return null
        }
        cache[index] = found
        return found
    }

    fun handleTap(index: Int, offset: Offset, pageSize: IntSize) {
        if (!speakOn || busy || pageSize.width <= 0) return
        scope.launch {
            busy = true
            try {
                val bubbles = loadBubbles(index) ?: return@launch
                if (bubbles.isEmpty()) {
                    showMessage("No readable text found on this page.")
                    return@launch
                }
                val scale = OCR_WIDTH.toFloat() / pageSize.width.toFloat()
                val hit = ocr.hit(bubbles, offset.x * scale, offset.y * scale, OCR_WIDTH / 40)
                if (hit == null) {
                    speech.stop()
                    highlight = null
                    return@launch
                }
                val current = highlight
                if (speaking && current != null && current.first == index && current.second == hit.box) {
                    speech.stop()
                    highlight = null
                    return@launch
                }
                if (speech.speak(hit.text)) {
                    highlight = Pair(index, Rect(hit.box))
                } else if (speech.status == SpeechHelper.Status.Failed) {
                    showMessage("Text-to-speech is not available on this phone.")
                } else if (speech.hindiMissing) {
                    offerVoiceInstall()
                } else {
                    showMessage("Voice is still starting. Please tap again.")
                }
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!fullscreen) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            FileNameUtils.formatSize(record.sizeBytes) + " • " + reader.pageCount +
                                (if (reader.pageCount == 1) " page" else " pages") +
                                (if (speakOn) " • Tap text to listen" else ""),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (speaking) {
                            IconButton(onClick = { speech.stop() }) {
                                Icon(AppIcons.Stop, contentDescription = "Stop voice")
                            }
                        }
                        IconButton(onClick = onToggleSpeak) {
                            Icon(
                                if (speakOn) AppIcons.VolumeUp else AppIcons.VolumeOff,
                                contentDescription = if (speakOn) "Turn off tap to listen" else "Turn on tap to listen"
                            )
                        }
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(AppIcons.Fullscreen, contentDescription = "Full screen")
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState())) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(pageWidthDp),
                    verticalArrangement = Arrangement.spacedBy(if (fullscreen) 2.dp else 8.dp)
                ) {
                    items(reader.pageCount) { index ->
                        val h = highlight
                        PdfPage(
                            reader = reader,
                            index = index,
                            widthPx = widthPx,
                            highlight = if (h != null && h.first == index) h.second else null,
                            tapEnabled = speakOn,
                            onTap = { off, size -> handleTap(index, off, size) }
                        )
                    }
                }
            }

            if (!fullscreen) {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { scope.launch { listState.animateScrollToItem((currentPage - 2).coerceAtLeast(0)) } },
                                enabled = currentPage > 1
                            ) { Icon(AppIcons.ArrowUp, contentDescription = "Previous page") }
                            Text("Page $currentPage / ${reader.pageCount}", style = MaterialTheme.typography.labelLarge)
                            IconButton(
                                onClick = {
                                    scope.launch { listState.animateScrollToItem(currentPage.coerceAtMost(reader.pageCount - 1)) }
                                },
                                enabled = currentPage < reader.pageCount
                            ) { Icon(AppIcons.ArrowDown, contentDescription = "Next page") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onZoom(zoomIndex - 1) }, enabled = zoomIndex > 0) {
                                Icon(AppIcons.Remove, contentDescription = "Zoom out")
                            }
                            Text((zoom * 100).toInt().toString() + "%", style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { onZoom(zoomIndex + 1) }, enabled = zoomIndex < zoomLevels.lastIndex) {
                                Icon(AppIcons.Add, contentDescription = "Zoom in")
                            }
                        }
                    }
                }
            }
        }

        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).size(28.dp),
                strokeWidth = 3.dp
            )
        }

        if (fullscreen) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shadowElevation = 6.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$currentPage / ${reader.pageCount}",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                    IconButton(onClick = { onZoom(zoomIndex - 1) }, enabled = zoomIndex > 0) {
                        Icon(AppIcons.Remove, contentDescription = "Zoom out")
                    }
                    IconButton(onClick = { onZoom(zoomIndex + 1) }, enabled = zoomIndex < zoomLevels.lastIndex) {
                        Icon(AppIcons.Add, contentDescription = "Zoom in")
                    }
                    if (speaking) {
                        IconButton(onClick = { speech.stop() }) {
                            Icon(AppIcons.Stop, contentDescription = "Stop voice")
                        }
                    }
                    IconButton(onClick = onToggleSpeak) {
                        Icon(
                            if (speakOn) AppIcons.VolumeUp else AppIcons.VolumeOff,
                            contentDescription = if (speakOn) "Turn off tap to listen" else "Turn on tap to listen"
                        )
                    }
                    IconButton(onClick = onToggleFullscreen) {
                        Icon(AppIcons.FullscreenExit, contentDescription = "Exit full screen")
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    reader: PdfDocumentReader,
    index: Int,
    widthPx: Int,
    highlight: Rect?,
    tapEnabled: Boolean,
    onTap: (Offset, IntSize) -> Unit
) {
    val bitmap by produceState<Bitmap?>(null, reader, index, widthPx) {
        value = reader.render(index, widthPx)
    }
    val currentOnTap by rememberUpdatedState(onTap)
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    val b = bitmap
    if (b == null) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.707f).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
    } else {
        Box(Modifier.fillMaxWidth()) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Page " + (index + 1),
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { pageSize = it }
                    .pointerInput(tapEnabled) {
                        if (tapEnabled) {
                            detectTapGestures(onTap = { off -> currentOnTap(off, pageSize) })
                        }
                    },
                contentScale = ContentScale.FillWidth
            )
            if (highlight != null && pageSize.width > 0) {
                Canvas(Modifier.matchParentSize()) {
                    val s = pageSize.width.toFloat() / OCR_WIDTH.toFloat()
                    val pad = 6f
                    val topLeft = Offset((highlight.left - pad) * s, (highlight.top - pad) * s)
                    val boxSize = Size((highlight.width() + 2 * pad) * s, (highlight.height() + 2 * pad) * s)
                    drawRect(color = Color(0x33FFC107), topLeft = topLeft, size = boxSize)
                    drawRect(color = Color(0xFFFFC107), topLeft = topLeft, size = boxSize, style = Stroke(width = 3.dp.toPx()))
                }
            }
        }
    }
}
