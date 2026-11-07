package com.nhdeveloper.web2pdf.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PdfPrinter
import android.print.PrintAttributes
import android.webkit.WebView
import com.nhdeveloper.web2pdf.model.Orientation
import com.nhdeveloper.web2pdf.model.PaperSize
import com.nhdeveloper.web2pdf.model.PdfOptions
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.exp
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Creates a real PDF from a loaded WebView using Android's native print pipeline. */
class PdfGenerator(private val context: Context) {

    class PdfException(message: String, val canFallback: Boolean = false) : Exception(message)

    data class Output(val file: File, val pageCount: Int)

    @Volatile
    private var inFlight = false

    /** Must be called on the main thread. [onProgress] receives (percent 0-100, status text). */
    suspend fun generate(
        webView: WebView,
        options: PdfOptions,
        jobName: String,
        onProgress: (Int, String) -> Unit
    ): Output {
        // WebView can run only one print job at a time. A cancelled/slow job may still be finishing natively.
        var waited = 0
        while (inFlight) {
            if (waited >= 30_000) {
                throw PdfException(
                    "The previous conversion is still finishing. Wait a few seconds, reload the page and try again."
                )
            }
            onProgress(40, "Finishing the previous conversion…")
            delay(500)
            waited += 500
        }

        val dir = File(context.cacheDir, "pdf_tmp").apply { mkdirs() }
        val file = File(dir, "job_" + System.currentTimeMillis() + ".pdf")
        try {
            onProgress(42, "Preparing the page for printing…")
            runJs(webView, styleScript(options.backgroundGraphics))
            delay(300)

            onProgress(50, "Laying out pages…")
            val attributes = buildAttributes(options)
            var attempt = 0
            while (true) {
                try {
                    printOnce(webView, attributes, jobName, options, file, onProgress)
                    break
                } catch (e: PdfException) {
                    val pending = e.message?.contains("pending", ignoreCase = true) == true
                    if (pending && attempt < 8) {
                        attempt++
                        onProgress(45, "Waiting for the previous print job to finish…")
                        delay(2000)
                        continue
                    }
                    throw e
                }
            }

            onProgress(96, "Checking the PDF…")
            if (!file.exists() || file.length() < 100) {
                throw PdfException("The page produced an empty PDF. Some pages block printing or need a login.")
            }
            val header = ByteArray(4)
            val read = file.inputStream().use { it.read(header) }
            if (read < 4 || String(header, Charsets.US_ASCII) != "%PDF") {
                throw PdfException("The generated file is not a valid PDF.")
            }
            val pages = readPageCount(file)
            if (pages <= 0) throw PdfException("The generated PDF has no pages.")
            return Output(file, pages)
        } catch (t: Throwable) {
            file.delete()
            throw t
        } finally {
            try {
                webView.evaluateJavascript(removeScript(), null)
            } catch (ignored: Exception) {
            }
        }
    }

    private suspend fun printOnce(
        webView: WebView,
        attributes: PrintAttributes,
        jobName: String,
        options: PdfOptions,
        file: File,
        onProgress: (Int, String) -> Unit
    ) {
        val adapter = webView.createPrintDocumentAdapter(jobName)
        val pfd = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_WRITE or
                ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE
        )
        val signal = CancellationSignal()
        var pageNote = ""
        try {
            val result: PdfPrinter.Result? = coroutineScope {
                val ticker = launch {
                    var t = 0
                    while (true) {
                        delay(500)
                        t++
                        // Moves smoothly from 65% towards 95% while the engine writes pages.
                        val pct = 65 + (30 * (1 - exp(-t / 24.0))).toInt()
                        val kb = file.length() / 1024
                        onProgress(pct, "Writing PDF" + pageNote + "… " + kb + " KB • " + (t / 2) + "s")
                    }
                }
                try {
                    withTimeoutOrNull(PRINT_TIMEOUT_MS) {
                        suspendCancellableCoroutine<PdfPrinter.Result> { cont ->
                            cont.invokeOnCancellation { signal.cancel() }
                            inFlight = true
                            try {
                                PdfPrinter.print(
                                    adapter, attributes, pfd, options.pageRange, signal,
                                    onLayoutDone = { count ->
                                        pageNote = if (count > 0) " ($count pages)" else ""
                                        onProgress(65, "Layout done" + pageNote + ". Writing PDF…")
                                    }
                                ) { r ->
                                    inFlight = false
                                    if (cont.isActive) cont.resume(r)
                                }
                            } catch (t: Throwable) {
                                inFlight = false
                                if (cont.isActive) {
                                    cont.resumeWithException(
                                        PdfException("The built-in PDF engine is not available on this device.", true)
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    ticker.cancel()
                }
            }

            if (result == null) {
                signal.cancel()
                var w = 0
                while (inFlight && w < 15_000) {
                    delay(500)
                    w += 500
                }
                throw PdfException(
                    "The PDF engine took too long (over 3 minutes). The page may be very large. " +
                        "Try a page range such as 1-5, or a smaller paper size."
                )
            }
            when (result) {
                is PdfPrinter.Result.Done -> Unit
                is PdfPrinter.Result.Failed -> throw PdfException(result.message)
                is PdfPrinter.Result.Cancelled -> throw PdfException("The conversion was interrupted.")
            }
        } finally {
            try {
                pfd.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun readPageCount(file: File): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            throw PdfException("The generated file could not be read as a PDF.")
        } finally {
            try {
                renderer?.close()
            } catch (ignored: Exception) {
            }
            try {
                pfd?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun buildAttributes(o: PdfOptions): PrintAttributes {
        val base = when (o.paper) {
            PaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4
            PaperSize.A3 -> PrintAttributes.MediaSize.ISO_A3
            PaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
        }
        val media = if (o.orientation == Orientation.LANDSCAPE) base.asLandscape() else base.asPortrait()
        val m = o.margin.mils
        return PrintAttributes.Builder()
            .setMediaSize(media)
            .setResolution(PrintAttributes.Resolution("web2pdf", "PDF", 300, 300))
            .setMinMargins(PrintAttributes.Margins(m, m, m, m))
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
    }

    private suspend fun runJs(webView: WebView, js: String) {
        withTimeoutOrNull(3000) {
            suspendCancellableCoroutine<Unit> { cont ->
                webView.evaluateJavascript(js) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    private fun styleScript(background: Boolean): String {
        val css = if (background) {
            "*{-webkit-print-color-adjust:exact !important;print-color-adjust:exact !important;}"
        } else {
            "@media print{*{background:transparent !important;background-image:none !important;" +
                "box-shadow:none !important;text-shadow:none !important;}}"
        }
        return "(function(){try{var old=document.getElementById('__w2p_style');if(old){old.remove();}" +
            "var s=document.createElement('style');s.id='__w2p_style';s.textContent='" + css + "';" +
            "(document.head||document.documentElement).appendChild(s);" +
            "var imgs=document.querySelectorAll('img[loading=\"lazy\"]');" +
            "for(var i=0;i<imgs.length;i++){imgs[i].loading='eager';}}catch(e){}})();"
    }

    private fun removeScript(): String =
        "(function(){try{var old=document.getElementById('__w2p_style');if(old){old.remove();}}catch(e){}})();"

    companion object {
        private const val PRINT_TIMEOUT_MS = 180_000L
    }
}
