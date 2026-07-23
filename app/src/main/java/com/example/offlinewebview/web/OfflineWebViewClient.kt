package com.example.offlinewebview.web

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewClientCompat
import java.util.Locale

class OfflineWebViewClient(
    startUrl: String,
    private val interceptor: CachingRequestInterceptor
) : WebViewClientCompat() {
    private val allowedHost = Uri.parse(startUrl).host.orEmpty().lowercase(Locale.US)

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? = interceptor.intercept(request)

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (uri.scheme == "https" && uri.host?.lowercase(Locale.US) == allowedHost) return false
        return runCatching {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        }.getOrDefault(true)
    }
}

