package com.nhdeveloper.web2pdf.utils

import android.net.Uri
import android.util.Patterns

sealed interface UrlResult {
    data class Valid(val url: String) : UrlResult
    data class Invalid(val reason: String) : UrlResult
}

object UrlUtils {
    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    fun check(input: String): UrlResult {
        val text = input.trim()
        if (text.isEmpty()) return UrlResult.Invalid("Enter a website address.")
        if (text.any { it.isWhitespace() }) return UrlResult.Invalid("Web addresses cannot contain spaces.")
        val full = if (schemeRegex.containsMatchIn(text)) text else "https://$text"
        val uri = try {
            Uri.parse(full)
        } catch (e: Exception) {
            return UrlResult.Invalid("This does not look like a valid web address.")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return UrlResult.Invalid("Only http and https addresses can be converted.")
        }
        val host = uri.host
        if (host.isNullOrBlank()) return UrlResult.Invalid("This does not look like a valid web address.")
        val hostOk = host == "localhost" ||
            Patterns.IP_ADDRESS.matcher(host).matches() ||
            (host.contains('.') && Patterns.DOMAIN_NAME.matcher(host).matches())
        if (!hostOk) return UrlResult.Invalid("This does not look like a valid web address (e.g. example.com).")
        return UrlResult.Valid(full)
    }

    fun hostOf(url: String): String =
        try {
            Uri.parse(url).host ?: url
        } catch (e: Exception) {
            url
        }
}
