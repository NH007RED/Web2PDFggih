package com.nhdeveloper.web2pdf.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToInt

/** Thread-safe wrapper around PdfRenderer for the in-app viewer. */
class PdfDocumentReader(file: File) {
    private val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer
    private val lock = ReentrantLock()
    private var closed = false

    val pageCount: Int

    init {
        renderer = try {
            PdfRenderer(pfd)
        } catch (e: Exception) {
            pfd.close()
            throw e
        }
        pageCount = renderer.pageCount
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed || index < 0 || index >= pageCount) return@withLock null
            try {
                val page = renderer.openPage(index)
                try {
                    val w = widthPx.coerceAtLeast(1)
                    val h = (w.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            try {
                renderer.close()
            } catch (ignored: Exception) {
            }
            try {
                pfd.close()
            } catch (ignored: Exception) {
            }
        }
    }
}
