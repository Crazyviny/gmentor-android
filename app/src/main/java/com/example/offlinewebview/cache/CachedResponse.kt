package com.example.offlinewebview.cache

import java.io.File

data class CachedResponse(
    val bodyFile: File,
    val mimeType: String,
    val charset: String,
    val statusCode: Int,
    val reasonPhrase: String,
    val headers: Map<String, String>,
    val etag: String?,
    val lastModified: String?
)

