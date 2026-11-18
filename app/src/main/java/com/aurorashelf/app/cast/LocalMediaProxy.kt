package com.aurorashelf.app.cast

import android.util.Log
import com.aurorashelf.app.data.VideoRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import okhttp3.Dns
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
        extraHeaders: Map<String, String>,
        start: Long,
        endInclusive: Long?,
    ): CachedStreamSlice?
}

internal data class RegisteredProxyStream(
    val referer: String?,
    val extraHeaders: Map<String, String>,
)

/**
 * Lightweight local HTTP media streaming proxy.
 * Enables Smart TVs to play video streams that require custom headers (e.g. Referer, Origin),
 * HLS (.m3u8) streams with relative/protected segments, or Media3 SimpleCache / local offline videos
 * without hotlink 403 rejections.
 */
internal class LocalMediaProxy(
    okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
    private val allowedDirs: List<File> = emptyList(),
    private val cacheStreamOpener: OfflineCacheStreamOpener? = null,
    sessionToken: String = generateRandomToken(),
) {
    private val upstreamHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .dns(PublicMediaDns)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile
    internal var sessionToken: String = sessionToken
        private set

    private var serverSocket: ServerSocket? = null
    private val acceptExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "local-media-proxy-accept").apply { isDaemon = true }
    }
    private val workerPool = ThreadPoolExecutor(
        2,
        16,
        30L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(64),
        { r -> Thread(r, "local-media-proxy-worker").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    ).apply {
        allowCoreThreadTimeOut(true)
    }

    @Volatile private var isRunning = false
    @Volatile private var lastBoundHostOverride: String? = null

    private val registryLock = Any()
    private val registeredStreams = object : LinkedHashMap<String, RegisteredProxyStream>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RegisteredProxyStream>?): Boolean =
            size > MAX_REGISTERED_STREAMS
    }
    private val registeredStreamPrefixes = object : LinkedHashMap<String, RegisteredProxyStream>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RegisteredProxyStream>?): Boolean =
            size > MAX_REGISTERED_PREFIXES
    }
    private val registeredLocalFiles = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > MAX_REGISTERED_FILES
    }

    val port: Int
        get() = serverSocket?.localPort ?: 0

    fun rotateSession(): String {
        val next = generateRandomToken()
        synchronized(registryLock) {
            sessionToken = next
            registeredStreams.clear()
            registeredStreamPrefixes.clear()
            registeredLocalFiles.clear()
        }
        return next
    }

    fun start(): Boolean {
        if (isRunning && serverSocket?.isClosed == false) return true
        return synchronized(this) {
            if (isRunning && serverSocket?.isClosed == false) return@synchronized true
            try {
                val socket = ServerSocket(0)
                serverSocket = socket
                isRunning = true
                acceptExecutor.execute { acceptLoop(socket) }
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
        rotateSession()
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
        extraHeaders: Map<String, String> = emptyMap(),
    ): String? {
        val ip = localIpOverride ?: getLocalIpAddress() ?: return null
        lastBoundHostOverride = localIpOverride
        if (!start()) return null
        val activeToken = sessionToken
        registerStreamInternal(videoUrl, referer, extraHeaders)
        return buildProxyUrlWithBase(
            proxyBaseUrl = "http://$ip:$port",
            token = activeToken,
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
        extraHeaders: Map<String, String> = emptyMap(),
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
                extraHeaders = extraHeaders,
            )
        }
        val cleanPath = trimmed.removePrefix("file://")
        val targetFile = File(cleanPath)
        if (!isAllowedLocalFile(targetFile, allowedDirs)) {
            Log.w(TAG, "Refused to register disallowed local file path for casting")
            return null
        }
        val canonicalPath = runCatching { targetFile.canonicalPath }.getOrNull() ?: return null
        val ip = localIpOverride ?: getLocalIpAddress() ?: return null
        lastBoundHostOverride = localIpOverride
        if (!start()) return null
        val activeToken = sessionToken
        synchronized(registryLock) {
            registeredLocalFiles[canonicalPath] = true
        }
        val encodedPath = URLEncoder.encode(canonicalPath, StandardCharsets.UTF_8.name())
        return "http://$ip:$port/file?token=$activeToken&path=$encodedPath"
    }

    private fun registerStreamInternal(
        url: String,
        referer: String?,
        extraHeaders: Map<String, String>,
    ) {
        val cleanHeaders = sanitizeForwardedHeaders(extraHeaders)
        val entry = RegisteredProxyStream(referer = referer, extraHeaders = cleanHeaders)
        val prefix = streamDirectoryPrefix(url)
        synchronized(registryLock) {
            registeredStreams[url] = entry
            if (prefix != null) {
                registeredStreamPrefixes[prefix] = entry
            }
        }
    }

    private fun lookupRegisteredStream(targetUrl: String): RegisteredProxyStream? {
        val prefix = streamDirectoryPrefix(targetUrl)
        synchronized(registryLock) {
            registeredStreams[targetUrl]?.let { return it }
            if (prefix != null) {
                registeredStreamPrefixes[prefix]?.let { return it }
                val inherited = registeredStreamPrefixes.entries.firstOrNull { (registeredPrefix, _) ->
                    targetUrl.startsWith("$registeredPrefix/")
                }?.value
                if (inherited != null) return inherited
            }
        }
        return null
    }

    private fun isRegisteredLocalFile(canonicalPath: String): Boolean {
        synchronized(registryLock) {
            return registeredLocalFiles.containsKey(canonicalPath)
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (isRunning && !server.isClosed) {
            try {
                val client = server.accept()
                workerPool.execute { handleClient(client) }
            } catch (_: Exception) {
                if (!isRunning) break
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = CLIENT_SOCKET_TIMEOUT_MS
                val input = socket.getInputStream()
                val output = socket.getOutputStream()
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2 || (parts[0] != "GET" && parts[0] != "HEAD")) return

                val isHead = parts[0] == "HEAD"
                val rawUri = parts[1]
                var rangeHeader: String? = null
                var headerCount = 0
                while (headerCount < 64) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    headerCount++
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substring(6).trim()
                    }
                }

                val params = parseQueryParams(rawUri)
                val providedToken = params["token"].orEmpty()
                if (!constantTimeEquals(providedToken, sessionToken)) {
                    send403(output)
                    return
                }

                // Always derive proxy authority from the server's own bound IP + port (never trust client Host header).
                val boundHost = lastBoundHostOverride ?: getLocalIpAddress() ?: "127.0.0.1"
                val serverAuthority = "$boundHost:$port"

                when {
                    rawUri.startsWith("/stream?") ->
                        handleStreamProxy(params, rangeHeader, serverAuthority, isHead, output)
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
        serverAuthority: String,
        isHead: Boolean,
        output: OutputStream,
    ) {
        val targetUrl = params["url"]?.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        } ?: return send404(output)

        val registered = lookupRegisteredStream(targetUrl) ?: return send403(output)
        val referer = params["ref"]?.takeIf { it.isNotBlank() } ?: registered.referer
        val extraHeaders = registered.extraHeaders
        val cacheKey = params["key"]

        // 1. Try Media3 SimpleCache / OfflineCacheStreamOpener first when a cacheKey is present
        val opener = cacheStreamOpener
        if (!cacheKey.isNullOrBlank() && opener != null && !DlnaSoap.isHlsUrl(targetUrl)) {
            val isSuffixRange = rangeHeader?.trim()?.startsWith("bytes=-") == true
            val (reqStart, reqEnd) = if (isSuffixRange) 0L to null else parseByteRange(rangeHeader)
            val slice = runCatching {
                opener.open(cacheKey, targetUrl, referer, extraHeaders, reqStart, reqEnd)
            }.getOrNull()
            if (slice != null) {
                try {
                    val totalLength = slice.totalLength
                    if (isSuffixRange && totalLength > 0) {
                        slice.close()
                        val suffixLen = rangeHeader.removePrefix("bytes=-").trim().toLongOrNull()?.coerceAtLeast(1L)
                        if (suffixLen != null) {
                            val suffixStart = (totalLength - suffixLen).coerceAtLeast(0L)
                            val suffixSlice = runCatching {
                                opener.open(cacheKey, targetUrl, referer, extraHeaders, suffixStart, totalLength - 1)
                            }.getOrNull()
                            if (suffixSlice != null) {
                                try {
                                    writeCachedSlice(suffixSlice, isRange = true, reqStart = suffixStart, isHead = isHead, output = output)
                                    return
                                } finally {
                                    runCatching { suffixSlice.close() }
                                }
                            }
                        }
                    } else if (!rangeHeader.isNullOrBlank() && totalLength <= 0 && reqStart > 0) {
                        slice.close()
                        val fullSlice = runCatching { opener.open(cacheKey, targetUrl, referer, extraHeaders, 0L, null) }.getOrNull()
                        if (fullSlice != null) {
                            try {
                                writeCachedSlice(fullSlice, isRange = false, reqStart = 0L, isHead = isHead, output = output)
                                return
                            } finally {
                                runCatching { fullSlice.close() }
                            }
                        }
                    } else {
                        val isRange = !rangeHeader.isNullOrBlank() && reqStart >= 0 && totalLength > 0
                        writeCachedSlice(slice, isRange = isRange, reqStart = reqStart, isHead = isHead, output = output)
                        return
                    }
                } finally {
                    runCatching { slice.close() }
                }
            }
        }

        // Block literal loopback/private/link-local hosts before dialing upstream
        val targetHost = runCatching { URI(targetUrl).host }.getOrNull()
        if (targetHost.isNullOrBlank() || isBlockedLiteralHost(targetHost)) {
            send403(output)
            return
        }

        // 2. Network stream proxy (with HLS M3U8 manifest rewriting support)
        val reqBuilder = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", VideoRepository.USER_AGENT)
            .header("Accept", "*/*")
        extraHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
        if (!referer.isNullOrBlank() && extraHeaders.keys.none { it.equals("Referer", ignoreCase = true) }) {
            reqBuilder.header("Referer", referer)
        }
        val isHlsRequest = DlnaSoap.isHlsUrl(targetUrl)
        if (!rangeHeader.isNullOrBlank() && !isHlsRequest) {
            reqBuilder.header("Range", rangeHeader)
        }

        val response = upstreamHttpClient.newCall(reqBuilder.build()).execute()
        response.use { resp ->
            val contentType = resp.header("Content-Type").orEmpty()
            val isM3u8 = isHlsRequest ||
                contentType.contains("mpegurl", ignoreCase = true) ||
                contentType.contains("x-mpegURL", ignoreCase = true)

            if (isM3u8 && resp.isSuccessful) {
                val declaredLen = resp.body?.contentLength() ?: -1L
                if (declaredLen > MAX_MANIFEST_BYTES) {
                    send403(output)
                    return
                }
                val rawManifest = readTextCapped(resp.body?.byteStream(), MAX_MANIFEST_BYTES)
                val activeToken = sessionToken
                val rewritten = rewriteHlsManifest(
                    manifest = rawManifest,
                    baseUri = targetUrl,
                    proxyBaseUrl = "http://$serverAuthority",
                    token = activeToken,
                    referer = referer,
                    onResolvedResource = { resolvedUri ->
                        registerStreamInternal(resolvedUri, referer, extraHeaders)
                    },
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
        val canonicalPath = runCatching { file.canonicalPath }.getOrNull() ?: return send403(output)
        if (!isRegisteredLocalFile(canonicalPath) || !isAllowedLocalFile(file, allowedDirs)) {
            send403(output)
            return
        }
        if (!file.exists() || !file.isFile || !file.canRead()) return send404(output)
        val totalLength = file.length()
        if (totalLength <= 0L) return send404(output)

        val (parsedStart, parsedEnd) = resolveByteRange(rangeHeader, totalLength)
        val isRange = !rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")
        if (isRange && (parsedStart < 0 || parsedStart >= totalLength || (parsedEnd != null && parsedEnd < parsedStart))) {
            send416(totalLength, output)
            return
        }
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

    private fun writeCachedSlice(
        slice: CachedStreamSlice,
        isRange: Boolean,
        reqStart: Long,
        isHead: Boolean,
        output: OutputStream,
    ) {
        val contentLength = slice.contentLength
        val totalLength = slice.totalLength
        val endByte = if (totalLength > 0 && contentLength > 0) (reqStart + contentLength - 1).coerceAtLeast(reqStart) else -1L
        val headers = StringBuilder()
        if (isRange && totalLength > 0 && endByte >= reqStart) {
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
    }

    private fun parseByteRange(rangeHeader: String?): Pair<Long, Long?> {
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=")) return 0L to null
        val rangeVal = rangeHeader.removePrefix("bytes=").trim()
        val rangeParts = rangeVal.split("-", limit = 2)
        val start = rangeParts.getOrNull(0)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val end = rangeParts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()
        return start to end
    }

    private fun resolveByteRange(rangeHeader: String?, totalLength: Long): Pair<Long, Long?> {
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=")) return 0L to null
        val rangeVal = rangeHeader.removePrefix("bytes=").trim()
        if (rangeVal.startsWith("-")) {
            val suffix = rangeVal.removePrefix("-").toLongOrNull()?.coerceAtLeast(1L) ?: return -1L to null
            val start = (totalLength - suffix).coerceAtLeast(0L)
            return start to (totalLength - 1)
        }
        val rangeParts = rangeVal.split("-", limit = 2)
        val start = rangeParts.getOrNull(0)?.toLongOrNull() ?: return -1L to null
        val end = rangeParts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()
        return start to end
    }

    private fun send416(totalLength: Long, output: OutputStream) {
        val msg = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$totalLength\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        output.write(msg.toByteArray(StandardCharsets.UTF_8))
        output.flush()
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
            if (sb.length >= 8192) throw IOException("HTTP header line too long")
            sb.append(b.toChar())
        }
    }

    companion object {
        private const val TAG = "LocalMediaProxy"
        private const val CLIENT_SOCKET_TIMEOUT_MS = 10_000
        internal const val MAX_MANIFEST_BYTES = 8 * 1024 * 1024
        private const val MAX_REGISTERED_STREAMS = 2048
        private const val MAX_REGISTERED_PREFIXES = 256
        private const val MAX_REGISTERED_FILES = 64
        private val HLS_URI_ATTR_REGEX = Regex("""URI="([^"]+)"""")
        private val FORBIDDEN_PATH_SEGMENTS = listOf("shared_prefs", "databases", "code_cache", "no_backup")
        private val FORBIDDEN_EXTENSIONS = setOf("xml", "db", "sqlite", "journal", "properties", "json")
        private val ALLOWED_EXTRA_HEADER_NAMES = setOf(
            "origin",
            "referer",
            "user-agent",
            "accept",
            "accept-language",
            "cookie",
            "authorization",
        )
        private val secureRandom = SecureRandom()

        internal object PublicMediaDns : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                if (isBlockedLiteralHost(hostname)) {
                    throw UnknownHostException("Blocked non-public upstream host: $hostname")
                }
                val resolved = Dns.SYSTEM.lookup(hostname)
                val publicAddresses = resolved.filterNot { isPrivateOrLoopbackAddress(it) }
                if (publicAddresses.isEmpty()) {
                    throw UnknownHostException("Blocked private/loopback upstream target for $hostname")
                }
                return publicAddresses
            }
        }

        internal fun generateRandomToken(): String {
            val bytes = ByteArray(16)
            secureRandom.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        internal fun constantTimeEquals(provided: String, expected: String): Boolean {
            if (provided.isEmpty() || expected.isEmpty()) return false
            return MessageDigest.isEqual(
                provided.toByteArray(StandardCharsets.UTF_8),
                expected.toByteArray(StandardCharsets.UTF_8),
            )
        }

        internal fun isPrivateOrLoopbackAddress(address: InetAddress): Boolean {
            if (address.isLoopbackAddress ||
                address.isAnyLocalAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress
            ) {
                return true
            }
            val bytes = address.address
            // IPv6 Unique Local Addresses (fc00::/7)
            if (bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc) {
                return true
            }
            // IPv4 Carrier-Grade NAT (100.64.0.0/10)
            if (bytes.size == 4 && (bytes[0].toInt() and 0xff) == 100 && ((bytes[1].toInt() and 0xc0) == 64)) {
                return true
            }
            return false
        }

        internal fun isBlockedLiteralHost(host: String): Boolean {
            val clean = host.trim().removeSurrounding("[", "]").lowercase()
            if (clean.isEmpty() || clean == "localhost" || clean.endsWith(".local") || clean.endsWith(".localhost")) {
                return true
            }
            if (clean == "127.0.0.1" || clean == "0.0.0.0" || clean == "::1" || clean == "::") {
                return true
            }
            val ipv4Parts = clean.split('.')
            if (ipv4Parts.size == 4 && ipv4Parts.all { it.toIntOrNull() in 0..255 }) {
                val addr = runCatching { InetAddress.getByName(clean) }.getOrNull() ?: return true
                return isPrivateOrLoopbackAddress(addr)
            }
            if (clean.contains(':')) {
                val addr = runCatching { InetAddress.getByName(clean) }.getOrNull() ?: return true
                return isPrivateOrLoopbackAddress(addr)
            }
            return false
        }

        internal fun streamDirectoryPrefix(url: String): String? {
            val withoutQuery = url.substringBefore('?').substringBefore('#')
            val slashIdx = withoutQuery.lastIndexOf('/')
            if (slashIdx <= "https://".length) return null
            return withoutQuery.substring(0, slashIdx)
        }

        internal fun sanitizeForwardedHeaders(headers: Map<String, String>): Map<String, String> {
            if (headers.isEmpty()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for ((rawKey, rawValue) in headers) {
                val key = rawKey.trim()
                val value = rawValue.replace("\r", "").replace("\n", "").trim()
                if (key.isEmpty() || value.isEmpty()) continue
                if (key.lowercase() in ALLOWED_EXTRA_HEADER_NAMES) {
                    out[key] = value
                }
            }
            return out
        }

        internal fun readTextCapped(stream: InputStream?, maxBytes: Int): String {
            if (stream == null) return ""
            stream.use { input ->
                val out = ByteArrayOutputStream(4096)
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        throw IOException("HLS manifest exceeds ${maxBytes} bytes")
                    }
                    out.write(buf, 0, read)
                }
                return out.toByteArray().toString(StandardCharsets.UTF_8)
            }
        }

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
                    false
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
            onResolvedResource: ((String) -> Unit)? = null,
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
                                onResolvedResource?.invoke(resolved)
                                val proxied = buildProxyUrlWithBase(proxyBaseUrl, token, resolved, referer)
                                """URI="$proxied""""
                            }
                        } else {
                            line
                        }
                    }
                    else -> {
                        val resolved = resolveRelativeUri(baseUri, trimmed)
                        onResolvedResource?.invoke(resolved)
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
