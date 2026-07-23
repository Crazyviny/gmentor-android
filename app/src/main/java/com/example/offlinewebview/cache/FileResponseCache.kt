package com.example.offlinewebview.cache

import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        val (body, meta) = findFiles(cachePath(url)) ?: return null

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
        val path = cachePath(url)
        val key = key(path)
        val body = directory.resolve("$key.body")
        val meta = directory.resolve("$key.properties")
        val temporaryBody = directory.resolve("$key.body.tmp")
        val temporaryMeta = directory.resolve("$key.properties.tmp")
        val mediaType = response.body.contentType()

        return runCatching {
            temporaryBody.outputStream().buffered().use { it.write(bodyBytes) }
            val properties = Properties().apply {
                setProperty("cachePath", path)
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
    fun putXml(path: String, xml: String): CachedResponse? {
        val normalizedPath = cachePath(path)
        val key = key(normalizedPath)
        val body = directory.resolve("$key.body")
        val meta = directory.resolve("$key.properties")
        val temporaryBody = directory.resolve("$key.body.tmp")
        val temporaryMeta = directory.resolve("$key.properties.tmp")

        return runCatching {
            temporaryBody.outputStream().buffered().use {
                it.write(xml.toByteArray(Charsets.UTF_8))
            }
            val properties = Properties().apply {
                setProperty("cachePath", normalizedPath)
                setProperty("mimeType", "application/xml")
                setProperty("charset", "UTF-8")
                setProperty("statusCode", "200")
                setProperty("reasonPhrase", "OK")
                setProperty("${HEADER_PREFIX}Content-Type", "application/xml; charset=UTF-8")
            }
            temporaryMeta.outputStream().buffered().use {
                properties.store(it, null)
            }
            temporaryBody.copyTo(body, overwrite = true)
            temporaryMeta.copyTo(meta, overwrite = true)
            temporaryBody.delete()
            temporaryMeta.delete()
            trim()
            get(normalizedPath)
        }.getOrNull()
    }

    @Synchronized
    fun entries(): List<CacheEntry> =
        directory.listFiles { file -> file.extension == "body" }
            .orEmpty()
            .map { body ->
                val meta = directory.resolve("${body.nameWithoutExtension}.properties")
                val path = runCatching {
                    Properties().apply {
                        meta.inputStream().buffered().use(::load)
                    }.let {
                        it.getProperty("cachePath")
                            ?: it.getProperty("url")?.let(::cachePath)
                    }
                }.getOrNull()
                CacheEntry(
                    path = path ?: "[старый кэш] ${body.nameWithoutExtension}",
                    sizeBytes = body.length()
                )
            }
            .sortedBy(CacheEntry::path)

    @Synchronized
    fun touch(url: String) {
        val timestamp = System.currentTimeMillis()
        val (body, meta) = findFiles(cachePath(url)) ?: return
        body.setLastModified(timestamp)
        meta.setLastModified(timestamp)
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

    private fun cachePath(url: String): String =
        url.toHttpUrlOrNull()
            ?.encodedPath
            ?.trimStart('/')
            ?.ifEmpty { "/" }
            ?: url.substringBefore('?').trimStart('/').ifEmpty { "/" }

    private fun findFiles(path: String): Pair<File, File>? {
        val canonicalKey = key(path)
        val canonicalBody = directory.resolve("$canonicalKey.body")
        val canonicalMeta = directory.resolve("$canonicalKey.properties")
        if (canonicalBody.isFile && canonicalMeta.isFile) {
            return canonicalBody to canonicalMeta
        }

        return directory.listFiles { file -> file.extension == "properties" }
            .orEmpty()
            .firstNotNullOfOrNull { meta ->
                val storedPath = runCatching {
                    Properties().apply {
                        meta.inputStream().buffered().use(::load)
                    }.let {
                        it.getProperty("cachePath")
                            ?: it.getProperty("url")?.let(::cachePath)
                    }
                }.getOrNull()
                val body = directory.resolve("${meta.nameWithoutExtension}.body")
                if (storedPath == path && body.isFile) body to meta else null
            }
    }

    private fun key(cachePath: String): String = MessageDigest.getInstance("SHA-256")
        .digest(cachePath.toByteArray())
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
