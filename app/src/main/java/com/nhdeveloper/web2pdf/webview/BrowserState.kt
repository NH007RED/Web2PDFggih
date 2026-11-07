package com.nhdeveloper.web2pdf.webview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BrowserState {
    var progress by mutableIntStateOf(0)
    var title by mutableStateOf<String?>(null)
    var currentUrl by mutableStateOf("")
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var warning by mutableStateOf<String?>(null)
    var crashed by mutableStateOf(false)
    var userStopped by mutableStateOf(false)

    val isSecure: Boolean get() = currentUrl.startsWith("https://")
}
