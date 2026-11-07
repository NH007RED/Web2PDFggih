package com.nhdeveloper.web2pdf.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** One piece of text found on a page (usually one speech bubble), in bitmap pixel coordinates. */
data class TextBubble(val text: String, val box: Rect)

/**
 * Offline OCR (Hindi/Devanagari + English) used by tap-to-speak.
 * Comic PDFs are pictures, so the text has to be read from the rendered page image.
 */
class TextBubbleOcr {
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

    suspend fun recognize(bitmap: Bitmap): List<TextBubble> {
        val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val raw = ArrayList<TextBubble>()
        for (block in result.textBlocks) {
            val box = block.boundingBox ?: continue
            val text = block.text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
            if (text.isNotEmpty()) raw.add(TextBubble(text, Rect(box)))
        }
        return merge(raw)
    }

    /** Joins blocks that sit very close together (one bubble split into pieces by the OCR). */
    private fun merge(items: List<TextBubble>): List<TextBubble> {
        if (items.size < 2) return items
        val parent = IntArray(items.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) {
                val n = parent[c]
                parent[c] = r
                c = n
            }
            return r
        }
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                val a = items[i].box
                val b = items[j].box
                val gap = (minOf(a.height(), b.height()) * 0.6f).toInt().coerceAtLeast(4)
                val ar = Rect(a.left - gap, a.top - gap, a.right + gap, a.bottom + gap)
                if (Rect.intersects(ar, b)) {
                    val ri = find(i)
                    val rj = find(j)
                    if (ri != rj) parent[rj] = ri
                }
            }
        }
        val groups = LinkedHashMap<Int, MutableList<TextBubble>>()
        for (i in items.indices) groups.getOrPut(find(i)) { ArrayList() }.add(items[i])
        return groups.values.map { g ->
            val sorted = g.sortedWith(compareBy({ it.box.top / 20 }, { it.box.left }))
            val box = Rect(sorted[0].box)
            for (t in sorted) box.union(t.box)
            TextBubble(sorted.joinToString(" ") { it.text }, box)
        }
    }

    /** Returns the bubble that contains (or is closest to) the tapped point, or null if nothing is near. */
    fun hit(bubbles: List<TextBubble>, x: Float, y: Float, slack: Int): TextBubble? {
        var best: TextBubble? = null
        var bestDist = Float.MAX_VALUE
        for (b in bubbles) {
            val r = b.box
            val dx = maxOf(r.left - x, 0f, x - r.right)
            val dy = maxOf(r.top - y, 0f, y - r.bottom)
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            if (d <= slack && d < bestDist) {
                best = b
                bestDist = d
            }
        }
        return best
    }

    fun close() {
        try {
            recognizer.close()
        } catch (ignored: Exception) {
        }
    }
}
