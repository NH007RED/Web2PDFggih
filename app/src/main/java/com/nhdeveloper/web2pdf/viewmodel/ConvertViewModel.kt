package com.nhdeveloper.web2pdf.viewmodel

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhdeveloper.web2pdf.AppContainer
import com.nhdeveloper.web2pdf.model.AppSettings
import com.nhdeveloper.web2pdf.model.ConversionRecord
import com.nhdeveloper.web2pdf.model.ConversionState
import com.nhdeveloper.web2pdf.model.PdfOptions
import com.nhdeveloper.web2pdf.pdf.PdfGenerator
import com.nhdeveloper.web2pdf.utils.UrlResult
import com.nhdeveloper.web2pdf.utils.UrlUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ConvertViewModel(private val c: AppContainer) : ViewModel() {

    var urlText by mutableStateOf("")
        private set
    var urlResult by mutableStateOf<UrlResult?>(null)
        private set
    var sessionUrl by mutableStateOf("")
        private set

    val settings: StateFlow<AppSettings> = c.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val recent: StateFlow<List<ConversionRecord>> = c.historyRepository.records
        .map { it.take(3) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _conversion = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversion: StateFlow<ConversionState> = _conversion.asStateFlow()

    private var job: Job? = null

    fun onUrlChange(text: String) {
        urlText = text
        urlResult = null
    }

    fun validate(): UrlResult {
        val r = UrlUtils.check(urlText)
        urlResult = r
        return r
    }

    /** Validates the URL and, if valid, starts a browsing session. */
    fun startSession(): Boolean {
        return when (val r = validate()) {
            is UrlResult.Valid -> {
                sessionUrl = r.url
                true
            }
            is UrlResult.Invalid -> false
        }
    }

    fun convert(
        webView: WebView,
        pageTitle: String?,
        options: PdfOptions,
        fileName: String,
        isPageReady: () -> Boolean
    ) {
        if (_conversion.value is ConversionState.Running) return // prevent duplicate requests
        val originalUrl = sessionUrl
        job = viewModelScope.launch {
            try {
                _conversion.value = ConversionState.Running("Waiting for the page to finish loading…", 0)
                val ready = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                    while (!isPageReady()) {
                        val p = webView.progress.coerceIn(0, 100)
                        _conversion.value = ConversionState.Running("Loading the page… $p%", (p * 0.4f).toInt())
                        delay(150)
                    }
                    true
                } ?: false
                if (!ready) {
                    _conversion.value = ConversionState.Failed(
                        "The page did not finish loading within 30 seconds. Reload it, or tap Stop in the browser bar and convert what has loaded."
                    )
                    return@launch
                }
                delay(400) // let late JavaScript rendering settle

                val out = c.pdfGenerator.generate(webView, options, fileName) { percent, status ->
                    _conversion.value = ConversionState.Running(status, percent.coerceIn(0, 99))
                }
                try {
                    _conversion.value = ConversionState.Running("Saving the PDF…", 98)
                    val saved = c.fileStorage.save(out.file, fileName, settings.value.saveTreeUri)
                    val title = pageTitle?.takeIf { it.isNotBlank() } ?: UrlUtils.hostOf(originalUrl)
                    val record = ConversionRecord(
                        title = title,
                        url = originalUrl,
                        fileName = saved.name,
                        createdAt = System.currentTimeMillis(),
                        fileUri = saved.uri,
                        filePath = saved.filePath,
                        sizeBytes = saved.size,
                        pageCount = out.pageCount
                    )
                    val id = c.historyRepository.add(record)
                    _conversion.value = ConversionState.Success(record.copy(id = id))
                } finally {
                    out.file.delete()
                }
            } catch (e: CancellationException) {
                _conversion.value = ConversionState.Idle
                throw e
            } catch (e: PdfGenerator.PdfException) {
                _conversion.value = ConversionState.Failed(e.message ?: "Conversion failed.", e.canFallback)
            } catch (e: OutOfMemoryError) {
                _conversion.value = ConversionState.Failed("This page is too large to convert on this device.")
            } catch (e: Exception) {
                _conversion.value = ConversionState.Failed(
                    "Could not save the PDF: " + (e.message ?: "unknown error")
                )
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _conversion.value = ConversionState.Idle
    }

    fun reset() {
        if (_conversion.value !is ConversionState.Running) _conversion.value = ConversionState.Idle
    }

    companion object {
        private const val LOAD_TIMEOUT_MS = 30_000L
    }
}
