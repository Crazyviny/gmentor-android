package com.example.offlinewebview.web

import java.util.Locale

internal object GmentorHostPolicy {
    private const val ROOT_HOST = "gmentor.ru"

    fun allows(host: String?): Boolean {
        val normalizedHost = host?.trimEnd('.')?.lowercase(Locale.US) ?: return false
        return normalizedHost == ROOT_HOST || normalizedHost.endsWith(".$ROOT_HOST")
    }
}
