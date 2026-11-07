package com.nhdeveloper.web2pdf.utils

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileNameUtils {
    private val illegal = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val spaces = Regex("\\s+")

    fun sanitize(name: String): String =
        name.replace(illegal, " ").replace(spaces, " ").trim().trim('.').take(80).trim()

    fun ensurePdf(name: String): String {
        val base = name.trim().removeSuffix(".pdf").removeSuffix(".PDF")
        val clean = sanitize(base).ifBlank { "document" }
        return "$clean.pdf"
    }

    fun defaultName(title: String?, url: String): String {
        val base = title?.takeIf { it.isNotBlank() } ?: UrlUtils.hostOf(url)
        val clean = sanitize(base).ifBlank { "webpage" }.take(50).trim()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "${clean}_$stamp"
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 0 -> "Unknown size"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

    fun formatDate(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
}
