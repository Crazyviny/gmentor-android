package com.example.offlinewebview.web

import android.webkit.JavascriptInterface
import com.example.offlinewebview.cache.FileResponseCache

class CharacterSaveBridge(
    private val cache: FileResponseCache
) {
    @JavascriptInterface
    fun saveCharacter(fileName: String?, xml: String?) {
        val normalizedName = fileName
            ?.trim()
            ?.trimStart('/')
            ?.takeIf(String::isNotEmpty)
            ?: return
        val content = xml ?: return
        cache.putXml("char/$normalizedName", content)
    }
}
