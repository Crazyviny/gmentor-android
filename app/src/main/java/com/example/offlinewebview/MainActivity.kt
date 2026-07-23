package com.example.offlinewebview

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Button
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import com.example.offlinewebview.cache.FileResponseCache
import com.example.offlinewebview.web.CachingRequestInterceptor
import com.example.offlinewebview.web.CharacterSaveBridge
import com.example.offlinewebview.web.OfflineWebViewClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.web_view)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        val startUrl = BuildConfig.START_URL
        val cache = FileResponseCache(filesDir.resolve("web-response-cache"), 256L * 1024 * 1024)
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val interceptor = CachingRequestInterceptor(httpClient, cache)
        webView.addJavascriptInterface(CharacterSaveBridge(cache), "GmentorCache")
        webView.webViewClient = OfflineWebViewClient(interceptor)
        webView.loadUrl(startUrl)
        findViewById<Button>(R.id.show_cache_button).setOnClickListener {
            showCacheContents(cache)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun showCacheContents(cache: FileResponseCache) {
        Thread {
            val entries = cache.entries()
            val totalBytes = entries.sumOf { it.sizeBytes }
            val items = entries.map {
                "${it.path}\n${Formatter.formatFileSize(this, it.sizeBytes)}"
            }.toTypedArray()

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                AlertDialog.Builder(this)
                    .setTitle(
                        getString(
                            R.string.cache_dialog_title,
                            entries.size,
                            Formatter.formatFileSize(this, totalBytes)
                        )
                    )
                    .apply {
                        if (items.isEmpty()) {
                            setMessage(R.string.cache_is_empty)
                        } else {
                            setItems(items, null)
                        }
                    }
                    .setPositiveButton(R.string.close, null)
                    .show()
            }
        }.start()
    }

    override fun onDestroy() {
        webView.apply {
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
