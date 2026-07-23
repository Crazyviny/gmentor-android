package com.example.offlinewebview.web

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewClientCompat

class OfflineWebViewClient(
    private val interceptor: CachingRequestInterceptor
) : WebViewClientCompat() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? = interceptor.intercept(request)

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        if (GmentorHostPolicy.allows(view.url?.let(android.net.Uri::parse)?.host)) {
            view.evaluateJavascript(SAVE_REQUEST_HOOK, null)
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (uri.scheme in setOf("http", "https") && GmentorHostPolicy.allows(uri.host)) return false
        return runCatching {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        }.getOrDefault(true)
    }

    private companion object {
        val SAVE_REQUEST_HOOK = """
            (() => {
              if (window.__gmentorSaveHookInstalled) return;
              window.__gmentorSaveHookInstalled = true;

              const isSaveRequest = (url, method) => {
                if (String(method || "GET").toUpperCase() !== "POST") return false;
                try {
                  return new URL(url, location.href).pathname.endsWith("/save.php");
                } catch (_) {
                  return false;
                }
              };

              const capture = (body) => {
                try {
                  let fileName = null;
                  let xml = null;
                  if (body instanceof FormData) {
                    fileName = body.get("fileName");
                    xml = body.get("xml");
                  } else if (body instanceof URLSearchParams) {
                    fileName = body.get("fileName");
                    xml = body.get("xml");
                  } else if (typeof body === "string") {
                    const params = new URLSearchParams(body);
                    fileName = params.get("fileName");
                    xml = params.get("xml");
                  }
                  if (fileName !== null && xml !== null) {
                    GmentorCache.saveCharacter(String(fileName), String(xml));
                  }
                } catch (_) {}
              };

              const originalOpen = XMLHttpRequest.prototype.open;
              const originalSend = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.open = function(method, url) {
                this.__gmentorMethod = method;
                this.__gmentorUrl = url;
                return originalOpen.apply(this, arguments);
              };
              XMLHttpRequest.prototype.send = function(body) {
                if (isSaveRequest(this.__gmentorUrl, this.__gmentorMethod)) capture(body);
                return originalSend.apply(this, arguments);
              };

              const originalFetch = window.fetch;
              if (originalFetch) {
                window.fetch = function(input, init) {
                  const url = typeof input === "string" ? input : input.url;
                  const method = init?.method || (typeof input === "string" ? "GET" : input.method);
                  if (isSaveRequest(url, method)) {
                    if (init?.body !== undefined) {
                      capture(init.body);
                    } else if (typeof input !== "string") {
                      input.clone().text().then(capture).catch(() => {});
                    }
                  }
                  return originalFetch.apply(this, arguments);
                };
              }
            })();
        """.trimIndent()
    }
}
