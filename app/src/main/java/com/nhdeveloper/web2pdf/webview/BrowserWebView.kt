package com.nhdeveloper.web2pdf.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

private fun friendlyError(code: Int): String = when (code) {
    WebViewClient.ERROR_HOST_LOOKUP ->
        "The website could not be found. Check the address and your internet connection."
    WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_IO ->
        "Could not connect to the website. Check your internet connection and try again."
    WebViewClient.ERROR_TIMEOUT -> "The website took too long to respond. Try again later."
    WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ->
        "A secure connection to the website could not be established."
    WebViewClient.ERROR_BAD_URL, WebViewClient.ERROR_UNSUPPORTED_SCHEME ->
        "This address cannot be opened. Only http and https pages are supported."
    WebViewClient.ERROR_REDIRECT_LOOP -> "The website redirected too many times."
    WebViewClient.ERROR_AUTHENTICATION, WebViewClient.ERROR_PROXY_AUTHENTICATION ->
        "This page requires you to sign in, which is not supported."
    WebViewClient.ERROR_TOO_MANY_REQUESTS -> "The website is receiving too many requests. Try again later."
    else -> "The page could not be loaded. It may be unsupported or temporarily unavailable."
}

@SuppressLint("SetJavaScriptEnabled")
fun createBrowserWebView(context: Context, state: BrowserState): WebView {
    val webView = WebView(context)
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        mediaPlaybackRequiresUserGesture = true
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }

    fun sync(view: WebView) {
        state.canGoBack = view.canGoBack()
        state.canGoForward = view.canGoForward()
    }

    webView.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            state.loading = true
            state.error = null
            state.warning = null
            state.userStopped = false
            if (url != null) state.currentUrl = url
            sync(view)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            state.loading = false
            state.progress = 100
            if (url != null) state.currentUrl = url
            state.title = view.title
            sync(view)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            if (url != null) state.currentUrl = url
            sync(view)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme?.lowercase()
            return scheme != "http" && scheme != "https"
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                state.error = friendlyError(error.errorCode)
                state.loading = false
            }
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (!request.isForMainFrame) return
            val code = errorResponse.statusCode
            state.warning = when {
                code == 401 || code == 403 ->
                    "This page requires a login or blocks automated access (HTTP $code). The PDF may only contain an error page."
                code == 404 -> "The page was not found (HTTP 404). The PDF may only contain an error page."
                code == 429 -> "The website is limiting requests (HTTP 429). Try again later."
                code >= 500 -> "The website reported a server problem (HTTP $code). The PDF may be incomplete."
                code >= 400 -> "The website returned an error (HTTP $code). The PDF may only contain an error page."
                else -> null
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // Never bypass certificate validation.
            handler.cancel()
            state.error = "The website's security certificate could not be verified, so the page was not loaded."
            state.loading = false
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            state.crashed = true
            state.loading = false
            state.error = "The page ran out of memory or crashed. Go back and try again."
            return true
        }
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            state.progress = newProgress
            if (newProgress < 100) state.loading = true
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            state.title = title
        }
    }

    webView.setDownloadListener { _, _, _, _, _ ->
        state.warning = "This link is a file download, not a web page, so it cannot be converted."
    }
    return webView
}
