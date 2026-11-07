package android.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import com.nhdeveloper.web2pdf.utils.PageRanges

/**
 * Drives a PrintDocumentAdapter (from WebView.createPrintDocumentAdapter) directly into a file,
 * with no system print dialog. This class lives in the android.print package because the
 * LayoutResultCallback / WriteResultCallback constructors are package-private.
 */
object PdfPrinter {

    sealed interface Result {
        data object Done : Result
        data class Failed(val message: String) : Result
        data object Cancelled : Result
    }

    fun print(
        adapter: PrintDocumentAdapter,
        attributes: PrintAttributes,
        output: ParcelFileDescriptor,
        pageSpec: String,
        signal: CancellationSignal,
        onLayoutDone: (Int) -> Unit = {},
        onResult: (Result) -> Unit
    ) {
        var finished = false

        fun finish(result: Result) {
            if (finished) return
            finished = true
            try {
                adapter.onFinish()
            } catch (ignored: Throwable) {
            }
            onResult(result)
        }

        val writeCallback = object : PrintDocumentAdapter.WriteResultCallback() {
            override fun onWriteFinished(pages: Array<out PageRange>?) {
                finish(Result.Done)
            }

            override fun onWriteFailed(error: CharSequence?) {
                finish(Result.Failed(error?.toString() ?: "The PDF could not be written."))
            }

            override fun onWriteCancelled() {
                finish(Result.Cancelled)
            }
        }

        val layoutCallback = object : PrintDocumentAdapter.LayoutResultCallback() {
            override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                val pageCount = info?.pageCount ?: PrintDocumentInfo.PAGE_COUNT_UNKNOWN
                val ranges: Array<PageRange> = try {
                    resolve(pageSpec, pageCount)
                } catch (e: IllegalArgumentException) {
                    finish(Result.Failed(e.message ?: "Invalid page range."))
                    return
                }
                onLayoutDone(pageCount)
                try {
                    adapter.onWrite(ranges, output, signal, writeCallback)
                } catch (t: Throwable) {
                    finish(Result.Failed(t.message ?: "The PDF could not be written."))
                }
            }

            override fun onLayoutFailed(error: CharSequence?) {
                finish(Result.Failed(error?.toString() ?: "The page could not be laid out for printing."))
            }

            override fun onLayoutCancelled() {
                finish(Result.Cancelled)
            }
        }

        val previous = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("previous", "previous", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        adapter.onStart()
        adapter.onLayout(previous, attributes, signal, layoutCallback, Bundle())
    }

    private fun resolve(spec: String, pageCount: Int): Array<PageRange> {
        if (spec.isBlank()) return arrayOf(PageRange.ALL_PAGES)
        val ranges = PageRanges.parse(spec)
        if (ranges.isEmpty()) return arrayOf(PageRange.ALL_PAGES)
        val out = mutableListOf<PageRange>()
        for (r in ranges) {
            if (pageCount > 0 && r.first > pageCount) {
                throw IllegalArgumentException(
                    "The page range starts after the last page (this page has $pageCount printed pages)."
                )
            }
            val end = if (pageCount > 0) minOf(r.last, pageCount) else r.last
            out += PageRange(r.first - 1, end - 1)
        }
        return out.toTypedArray()
    }
}
