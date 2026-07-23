package com.example.offlinewebview.web

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.example.offlinewebview.cache.CachedResponse
import com.example.offlinewebview.cache.FileResponseCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.Locale

class CachingRequestInterceptor(
    startUrl: String,
    private val client: OkHttpClient,
    private val cache: FileResponseCache
) {
    private val allowedHost = Uri.parse(startUrl).host.orEmpty().lowercase(Locale.US)

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (!isEligible(request)) return null
        val cached = cache.get(url)

        val networkRequest = Request.Builder()
            .url(url)
            .get()
            .apply {
                request.requestHeaders.forEach { (name, value) ->
                    if (name.lowercase() !in UNSAFE_REQUEST_HEADERS) header(name, value)
                }
                CookieManager.getInstance().getCookie(url)?.let { header("Cookie", it) }
                cached?.etag?.let { header("If-None-Match", it) }
                if (cached?.etag == null) cached?.lastModified?.let {
                    header("If-Modified-Since", it)
                }
            }
            .build()

        return try {
            client.newCall(networkRequest).execute().use { response ->
                response.headers("Set-Cookie").forEach {
                    CookieManager.getInstance().setCookie(url, it)
                }
                if (response.code == 304 && cached != null) {
                    cache.touch(url)
                    return cached.toWebResourceResponse()
                }
                val bodyBytes = response.body.bytes()
                val shouldStore = response.isSuccessful &&
                    !"no-store".let { directive ->
                        response.header("Cache-Control").orEmpty().lowercase().contains(directive)
                    }
                val stored = if (shouldStore) cache.put(url, response, bodyBytes) else null
                stored?.toWebResourceResponse() ?: WebResourceResponse(
                    response.body.contentType()?.let { "${it.type}/${it.subtype}" },
                    response.body.contentType()?.charset()?.name() ?: "UTF-8",
                    response.code,
                    response.message.ifBlank { "OK" },
                    response.headers.toMap().filterKeys {
                        it.lowercase() !in UNSAFE_RESPONSE_HEADERS
                    },
                    ByteArrayInputStream(bodyBytes)
                )
            }
        } catch (_: Exception) {
            cached?.toWebResourceResponse()
        }
    }

    private fun isEligible(request: WebResourceRequest): Boolean =
        request.method.equals("GET", ignoreCase = true) &&
            request.url.scheme?.lowercase() in setOf("http", "https") &&
            request.url.host?.lowercase(Locale.US) == allowedHost

    private fun CachedResponse.toWebResourceResponse() = WebResourceResponse(
        mimeType,
        charset,
        statusCode,
        reasonPhrase,
        headers,
        bodyFile.inputStream().buffered()
    )

    private companion object {
        val UNSAFE_REQUEST_HEADERS = setOf(
            "connection", "content-length", "host", "transfer-encoding"
        )
        val UNSAFE_RESPONSE_HEADERS = setOf(
            "connection", "content-length", "content-encoding", "transfer-encoding"
        )
    }
}

