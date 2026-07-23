package com.example.offlinewebview.cache

import okhttp3.Response
import java.io.File
import java.security.MessageDigest
import java.util.Properties

class FileResponseCache(
    private val directory: File,
    private val maxBytes: Long
) {
    init {
        directory.mkdirs()
    }

    @Synchronized
    fun get(url: String): CachedResponse? {
        val key = key(url)
        val body = directory.resolve("$key.body")
        val meta = directory.resolve("$key.properties")
        if (!body.isFile || !meta.isFile) return null

        return runCatching {
            val properties = Properties().apply {
                meta.inputStream().buffered().use(::load)
            }
            val headers = properties.stringPropertyNames()
                .filter { it.startsWith(HEADER_PREFIX) }
                .associate { it.removePrefix(HEADER_PREFIX) to properties.getProperty(it) }
            body.setLastModified(System.currentTimeMillis())
            meta.setLastModified(System.currentTimeMillis())
            CachedResponse(
                bodyFile = body,
                mimeType = properties.getProperty("mimeType", "application/octet-stream"),
                charset = properties.getProperty("charset", "UTF-8"),
                statusCode = properties.getProperty("statusCode", "200").toInt(),
                reasonPhrase = properties.getProperty("reasonPhrase", "OK"),
                headers = headers,
                etag = properties.getProperty("etag"),
                lastModified = properties.getProperty("lastModified")
            )
        }.getOrNull()
    }

    @Synchronized
    fun put(url: String, response: Response, bodyBytes: ByteArray): CachedResponse? {
        if (bodyBytes.size > maxBytes) return null
        val key = key(url)
        val body = directory.resolve("$key.body")
        val meta = directory.resolve("$key.properties")
        val temporaryBody = directory.resolve("$key.body.tmp")
        val temporaryMeta = directory.resolve("$key.properties.tmp")
        val mediaType = response.body.contentType()

        return runCatching {
            temporaryBody.outputStream().buffered().use { it.write(bodyBytes) }
            val properties = Properties().apply {
                setProperty("mimeType", mediaType?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream")
                setProperty("charset", mediaType?.charset()?.name() ?: "UTF-8")
                setProperty("statusCode", response.code.toString())
                setProperty("reasonPhrase", response.message.ifBlank { "OK" })
                response.header("ETag")?.let { setProperty("etag", it) }
                response.header("Last-Modified")?.let { setProperty("lastModified", it) }
                response.headers.forEach { (name, value) ->
                    if (name.lowercase() !in HOP_BY_HOP_HEADERS) {
                        setProperty("$HEADER_PREFIX$name", value)
                    }
                }
            }
            temporaryMeta.outputStream().buffered().use {
                properties.store(it, null)
            }
            temporaryBody.copyTo(body, overwrite = true)
            temporaryMeta.copyTo(meta, overwrite = true)
            temporaryBody.delete()
            temporaryMeta.delete()
            trim()
            get(url)
        }.getOrNull()
    }

    @Synchronized
    fun touch(url: String) {
        val timestamp = System.currentTimeMillis()
        val key = key(url)
        directory.resolve("$key.body").setLastModified(timestamp)
        directory.resolve("$key.properties").setLastModified(timestamp)
    }

    private fun trim() {
        val bodies = directory.listFiles { file -> file.extension == "body" }
            ?.sortedBy(File::lastModified)
            ?.toMutableList()
            ?: return
        var total = bodies.sumOf(File::length)
        while (total > maxBytes && bodies.isNotEmpty()) {
            val oldest = bodies.removeFirst()
            total -= oldest.length()
            oldest.delete()
            directory.resolve("${oldest.nameWithoutExtension}.properties").delete()
        }
    }

    private fun key(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val HEADER_PREFIX = "header."
        val HOP_BY_HOP_HEADERS = setOf(
            "connection", "content-length", "content-encoding", "keep-alive",
            "proxy-authenticate", "proxy-authorization", "te", "trailers",
            "transfer-encoding", "upgrade"
        )
    }
}

