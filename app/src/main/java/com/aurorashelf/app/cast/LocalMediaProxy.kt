package com.aurorashelf.app.cast

import android.util.Log
import com.aurorashelf.app.data.VideoRepository
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import okhttp3.OkHttpClient
import okhttp3.Request

internal class CachedStreamSlice(
    val totalLength: Long,
    val contentLength: Long,
    val read: (ByteArray, Int, Int) -> Int,
    val close: () -> Unit,
)

internal fun interface OfflineCacheStreamOpener {
    fun open(
        cacheKey: String,
        sourceUrl: String,
        referer: String?,
        start: Long,
        endInclusive: Long?,
    ): CachedStreamSlice?
}

/**
 * Lightweight local HTTP media streaming proxy.
 * Enables Smart TVs to play video streams that require custom headers (e.g. Referer),
 * HLS (.m3u8) streams with relative/protected segments, or Media3 SimpleCache / local offline videos
 * without hotlink 403 rejections.
 */
internal class LocalMediaProxy(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
    private val allowedDirs: List<File> = emptyList(),
    private val cacheStreamOpener: OfflineCacheStreamOpener? = null,
    internal val sessionToken: String = UUID.randomUUID().toString().replace("-", ""),
) {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var isRunning = false

    val port: Int
        get() = serverSocket?.localPort ?: 0

    fun start(): Boolean {
        if (isRunning && serverSocket?.isClosed == false) return true
        return synchronized(this) {
            if (isRunning && serverSocket?.isClosed == false) return@synchronized true
            try {
                val socket = ServerSocket(0)
                serverSocket = socket
                isRunning = true
                executor.execute { acceptLoop(socket) }
                Log.i(TAG, "LocalMediaProxy started on port ${socket.localPort}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start LocalMediaProxy", e)
                false
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving local IP", e)
        }
        return null
    }

    fun buildProxyUrl(
        videoUrl: String,
        referer: String? = null,
        cacheKey: String? = null,
        localIpOverride: String? = null,
    ): String? {
        val ip = localIpOverride ?: getLocalIpAddress() ?: return null
        if (!start()) return null
        return buildProxyUrlWithBase(
            proxyBaseUrl = "http://$ip:$port",
            token = sessionToken,
            videoUrl = videoUrl,
            referer = referer,
            cacheKey = cacheKey,
        )
    }

    fun buildLocalFileProxyUrl(
        filePath: String,
        referer: String? = null,
        cacheKey: String? = null,
        localIpOverride: String? = null,
    ): String? {
        val trimmed = filePath.trim()
        // Guard against passing an HTTP/HTTPS URL (such as OfflineVideo.mediaUrl) as a local file path.
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return buildProxyUrl(
                videoUrl = trimmed,
                referer = referer,
                cacheKey = cacheKey,
                localIpOverride = localIpOverride,
            )
        }
        val cleanPath = trimmed.removePrefix("file://")
        val ip = localIpOverride ?: getLocalIpAddress() ?: return null
        if (!start()) return null
        val encodedPath = URLEncoder.encode(cleanPath, StandardCharsets.UTF_8.name())
        return "http://$ip:$port/file?token=$sessionToken&path=$encodedPath"
    }

    private fun acceptLoop(server: ServerSocket) {
        while (isRunning && !server.isClosed) {
            try {
                val client = server.accept()
                executor.execute { handleClient(client) }
            } catch (e: Exception) {
                if (!isRunning) break
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            try {
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2 || (parts[0] != "GET" && parts[0] != "HEAD")) return

                val isHead = parts[0] == "HEAD"
                val rawUri = parts[1]
                var rangeHeader: String? = null
                var hostHeader: String? = null
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substring(6).trim()
                    } else if (line.startsWith("Host:", ignoreCase = true)) {
                        hostHeader = line.substring(5).trim()
                    }
                }

                val params = parseQueryParams(rawUri)
                if (params["token"] != sessionToken) {
                    send403(output)
                    return
                }

                val effectiveHost = hostHeader?.takeIf { it.isNotBlank() }
                    ?: "${getLocalIpAddress() ?: "127.0.0.1"}:$port"

                when {
                    rawUri.startsWith("/stream?") ->
                        handleStreamProxy(params, rangeHeader, effectiveHost, isHead, output)
                    rawUri.startsWith("/file?") ->
                        handleFileProxy(params, rangeHeader, isHead, output)
                    else -> send404(output)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Client socket closed: ${e.message}")
            }
        }
    }

    private fun handleStreamProxy(
        params: Map<String, String>,
        rangeHeader: String?,
        hostHeader: String,
        isHead: Boolean,
        output: OutputStream,
    ) {
        val targetUrl = params["url"]?.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        } ?: return send404(output)
        val referer = params["ref"]
        val cacheKey = params["key"]

        // 1. Try Media3 SimpleCache / OfflineCacheStreamOpener first when a cacheKey is present
        val opener = cacheStreamOpener
        if (!cacheKey.isNullOrBlank() && opener != null && !DlnaSoap.isHlsUrl(targetUrl)) {
            val (reqStart, reqEnd) = parseByteRange(rangeHeader)
            val slice = runCatching {
                opener.open(cacheKey, targetUrl, referer, reqStart, reqEnd)
            }.getOrNull()
            if (slice != null) {
                try {
                    val isRange = !rangeHeader.isNullOrBlank() && reqStart >= 0
                    val contentLength = slice.contentLength
                    val totalLength = slice.totalLength
                    val endByte = if (totalLength > 0) (reqStart + contentLength - 1).coerceAtLeast(reqStart) else -1L
                    val headers = StringBuilder()
                    if (isRange && totalLength > 0) {
                        headers.append("HTTP/1.1 206 Partial Content\r\n")
                        headers.append("Content-Range: bytes $reqStart-$endByte/$totalLength\r\n")
                    } else {
                        headers.append("HTTP/1.1 200 OK\r\n")
                    }
                    headers.append("Content-Type: video/mp4\r\n")
                    if (contentLength >= 0) {
                        headers.append("Content-Length: $contentLength\r\n")
                    }
                    headers.append("Accept-Ranges: bytes\r\n")
                    headers.append("Connection: close\r\n\r\n")
                    output.write(headers.toString().toByteArray(StandardCharsets.UTF_8))
                    output.flush()
                    if (!isHead) {
                        val buffer = ByteArray(32 * 1024)
                        var remaining = if (contentLength >= 0) contentLength else Long.MAX_VALUE
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                            val read = slice.read(buffer, 0, toRead)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            if (contentLength >= 0) remaining -= read
                        }
                        output.flush()
                    }
                    return
                } finally {
                    runCatching { slice.close() }
                }
            }
        }

        // 2. Network stream proxy (with HLS M3U8 manifest rewriting support)
        val reqBuilder = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", VideoRepository.USER_AGENT)
            .header("Accept", "*/*")
        if (!referer.isNullOrBlank()) {
            reqBuilder.header("Referer", referer)
        }
        val isHlsRequest = DlnaSoap.isHlsUrl(targetUrl)
        if (!rangeHeader.isNullOrBlank() && !isHlsRequest) {
            reqBuilder.header("Range", rangeHeader)
        }

        val response = okHttpClient.newCall(reqBuilder.build()).execute()
        response.use { resp ->
            val contentType = resp.header("Content-Type").orEmpty()
            val isM3u8 = isHlsRequest ||
                contentType.contains("mpegurl", ignoreCase = true) ||
                contentType.contains("x-mpegURL", ignoreCase = true)

            if (isM3u8 && resp.isSuccessful) {
                val rawManifest = resp.body?.string().orEmpty()
                val rewritten = rewriteHlsManifest(
                    manifest = rawManifest,
                    baseUri = targetUrl,
                    proxyBaseUrl = "http://$hostHeader",
                    token = sessionToken,
                    referer = referer,
                )
                val payload = rewritten.toByteArray(StandardCharsets.UTF_8)
                val headers = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: application/vnd.apple.mpegurl\r\n")
                    append("Content-Length: ${payload.size}\r\n")
                    append("Cache-Control: no-cache\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.write(headers.toByteArray(StandardCharsets.UTF_8))
                if (!isHead) {
                    output.write(payload)
                }
                output.flush()
                return
            }

            val code = resp.code
            val statusText = when (code) {
                206 -> "Partial Content"
                200 -> "OK"
                403 -> "Forbidden"
                404 -> "Not Found"
                else -> resp.message.ifBlank { "OK" }
            }
            val headers = StringBuilder()
            headers.append("HTTP/1.1 $code $statusText\r\n")
            resp.header("Content-Type")?.let { headers.append("Content-Type: $it\r\n") }
                ?: headers.append("Content-Type: video/mp4\r\n")
            resp.header("Content-Length")?.let { headers.append("Content-Length: $it\r\n") }
            resp.header("Content-Range")?.let { headers.append("Content-Range: $it\r\n") }
            headers.append("Accept-Ranges: bytes\r\n")
            headers.append("Connection: close\r\n\r\n")
            output.write(headers.toString().toByteArray(StandardCharsets.UTF_8))
            output.flush()

            if (!isHead) {
                val body = resp.body?.byteStream() ?: return
                copyStream(body, output)
            }
        }
    }

    private fun handleFileProxy(
        params: Map<String, String>,
        rangeHeader: String?,
        isHead: Boolean,
        output: OutputStream,
    ) {
        val path = params["path"] ?: return send404(output)
        val file = File(path)
        if (!isAllowedLocalFile(file, allowedDirs)) {
            send403(output)
            return
        }
        if (!file.exists() || !file.isFile || !file.canRead()) return send404(output)
        val totalLength = file.length()
        if (totalLength <= 0L) return send404(output)

        val (parsedStart, parsedEnd) = parseByteRange(rangeHeader)
        val isRange = !rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")
        val start = parsedStart.coerceIn(0L, totalLength - 1)
        val end = (parsedEnd ?: (totalLength - 1)).coerceIn(start, totalLength - 1)

        val contentLength = end - start + 1
        val headers = StringBuilder()
        if (isRange) {
            headers.append("HTTP/1.1 206 Partial Content\r\n")
            headers.append("Content-Range: bytes $start-$end/$totalLength\r\n")
        } else {
            headers.append("HTTP/1.1 200 OK\r\n")
        }
        headers.append("Content-Type: video/mp4\r\n")
        headers.append("Content-Length: $contentLength\r\n")
        headers.append("Accept-Ranges: bytes\r\n")
        headers.append("Connection: close\r\n\r\n")
        output.write(headers.toString().toByteArray(StandardCharsets.UTF_8))
        output.flush()

        if (!isHead) {
            FileInputStream(file).use { fis ->
                if (start > 0) fis.skipFully(start)
                var remaining = contentLength
                val buffer = ByteArray(32 * 1024)
                while (remaining > 0) {
                    val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                    val read = fis.read(buffer, 0, toRead)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
                output.flush()
            }
        }
    }

    private fun FileInputStream.skipFully(bytesToSkip: Long) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() == -1) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun parseByteRange(rangeHeader: String?): Pair<Long, Long?> {
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=")) return 0L to null
        val rangeVal = rangeHeader.removePrefix("bytes=").trim()
        val rangeParts = rangeVal.split("-")
        val start = rangeParts.getOrNull(0)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val end = rangeParts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()
        return start to end
    }

    private fun send403(output: OutputStream) {
        val msg = "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        output.write(msg.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun send404(output: OutputStream) {
        val msg = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        output.write(msg.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(32 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }

    companion object {
        private const val TAG = "LocalMediaProxy"
        private val HLS_URI_ATTR_REGEX = Regex("""URI="([^"]+)"""")
        private val FORBIDDEN_PATH_SEGMENTS = listOf("shared_prefs", "databases", "code_cache", "no_backup")
        private val FORBIDDEN_EXTENSIONS = setOf("xml", "db", "sqlite", "journal", "properties", "json")

        internal fun parseQueryParams(rawUri: String): Map<String, String> {
            val query = rawUri.substringAfter("?", "")
            if (query.isEmpty()) return emptyMap()
            return query.split("&").mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val key = part.substringBefore("=")
                val rawVal = part.substringAfter("=", "")
                val decoded = runCatching {
                    URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name())
                }.getOrDefault(rawVal)
                key to decoded
            }.toMap()
        }

        internal fun isAllowedLocalFile(file: File, allowedDirs: List<File>): Boolean {
            return try {
                val canonical = file.canonicalPath
                val ext = file.extension.lowercase()
                if (ext in FORBIDDEN_EXTENSIONS) return false
                if (FORBIDDEN_PATH_SEGMENTS.any { canonical.contains("/$it/") }) return false
                if (allowedDirs.isNotEmpty()) {
                    allowedDirs.any { dir ->
                        val dirCanonical = dir.canonicalPath.trimEnd('/') + "/"
                        canonical.startsWith(dirCanonical)
                    }
                } else {
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

        internal fun buildProxyUrlWithBase(
            proxyBaseUrl: String,
            token: String,
            videoUrl: String,
            referer: String? = null,
            cacheKey: String? = null,
        ): String {
            val encodedUrl = URLEncoder.encode(videoUrl, StandardCharsets.UTF_8.name())
            val encodedRef = referer?.takeIf { it.isNotBlank() }
                ?.let { URLEncoder.encode(it, StandardCharsets.UTF_8.name()) }
                .orEmpty()
            val encodedKey = cacheKey?.takeIf { it.isNotBlank() }
                ?.let { URLEncoder.encode(it, StandardCharsets.UTF_8.name()) }
                .orEmpty()
            return buildString {
                append(proxyBaseUrl.trimEnd('/'))
                append("/stream?token=").append(token)
                append("&url=").append(encodedUrl)
                if (encodedRef.isNotEmpty()) {
                    append("&ref=").append(encodedRef)
                }
                if (encodedKey.isNotEmpty()) {
                    append("&key=").append(encodedKey)
                }
            }
        }

        internal fun rewriteHlsManifest(
            manifest: String,
            baseUri: String,
            proxyBaseUrl: String,
            token: String,
            referer: String?,
        ): String {
            val lines = manifest.split("\r\n", "\n")
            return lines.joinToString("\n") { line ->
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> line
                    trimmed.startsWith("#") -> {
                        if (trimmed.contains("URI=\"")) {
                            HLS_URI_ATTR_REGEX.replace(line) { matchResult ->
                                val rawUri = matchResult.groupValues[1]
                                val resolved = resolveRelativeUri(baseUri, rawUri)
                                val proxied = buildProxyUrlWithBase(proxyBaseUrl, token, resolved, referer)
                                """URI="$proxied""""
                            }
                        } else {
                            line
                        }
                    }
                    else -> {
                        val resolved = resolveRelativeUri(baseUri, trimmed)
                        buildProxyUrlWithBase(proxyBaseUrl, token, resolved, referer)
                    }
                }
            }
        }

        internal fun resolveRelativeUri(baseUri: String, reference: String): String {
            if (reference.startsWith("http://", ignoreCase = true) ||
                reference.startsWith("https://", ignoreCase = true)
            ) {
                return reference
            }
            return try {
                URI(baseUri).resolve(reference).toString()
            } catch (_: Exception) {
                val baseFolder = baseUri.substringBefore('?').substringBeforeLast('/', "")
                if (reference.startsWith("/")) {
                    val uri = runCatching { URI(baseUri) }.getOrNull()
                    if (uri != null && uri.host != null) {
                        "${uri.scheme ?: "https"}://${uri.AuthorityOrHost}$reference"
                    } else {
                        "$baseFolder$reference"
                    }
                } else {
                    "$baseFolder/$reference"
                }
            }
        }

        private val URI.AuthorityOrHost: String
            get() = rawAuthority ?: host.orEmpty()
    }
}
