package com.nhdeveloper.web2pdf.utils

/** Parses page range text such as "1-3, 5" into merged 1-based inclusive ranges. */
object PageRanges {
    fun parse(spec: String): List<IntRange> {
        val parts = spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        for (part in parts) {
            val bounds = part.split('-').map { it.trim() }
            val range: IntRange? = when (bounds.size) {
                1 -> bounds[0].toIntOrNull()?.let { it..it }
                2 -> {
                    val a = bounds[0].toIntOrNull()
                    val b = bounds[1].toIntOrNull()
                    if (a != null && b != null) a..b else null
                }
                else -> null
            }
            if (range == null || range.first < 1 || range.last < range.first) {
                throw IllegalArgumentException("Invalid page range \"$part\". Use formats like 1-3, 5.")
            }
            out += range
        }
        val sorted = out.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        for (r in sorted) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) {
                merged[merged.size - 1] = last.first..maxOf(last.last, r.last)
            } else {
                merged += r
            }
        }
        return merged
    }

    fun isValid(spec: String): Boolean =
        try {
            parse(spec)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
}
