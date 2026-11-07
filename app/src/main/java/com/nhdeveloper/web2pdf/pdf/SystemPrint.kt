package com.nhdeveloper.web2pdf.pdf

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import com.nhdeveloper.web2pdf.model.Orientation
import com.nhdeveloper.web2pdf.model.PaperSize
import com.nhdeveloper.web2pdf.model.PdfOptions

/** Fallback: opens Android's system print dialog (choose "Save as PDF"). */
object SystemPrint {
    fun print(context: Context, webView: WebView, jobName: String, options: PdfOptions): Boolean {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        val base = when (options.paper) {
            PaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4
            PaperSize.A3 -> PrintAttributes.MediaSize.ISO_A3
            PaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
        }
        val media = if (options.orientation == Orientation.LANDSCAPE) base.asLandscape() else base.asPortrait()
        val attrs = PrintAttributes.Builder().setMediaSize(media).build()
        return try {
            pm.print(jobName, webView.createPrintDocumentAdapter(jobName), attrs)
            true
        } catch (e: Exception) {
            false
        }
    }
}
