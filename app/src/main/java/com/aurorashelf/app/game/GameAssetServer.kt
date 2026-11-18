package com.aurorashelf.app.game

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * High-performance loopback HTTP server that serves APK assets with HTTP 206 Partial Content (Range requests)
 * and direct streaming buffers for smooth 1080p video playback and zero-OOM memory safety.
 */
class GameAssetServer(context: Context) : Runnable {
    private val assets: AssetManager = context.assets

    val launchToken: String = generateLaunchToken()

    @Volatile
    private var running = true
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var clientPool: ExecutorService? = null
    private var port = -1

    @Synchronized
    fun start(): Int {
        if (serverSocket != null) return port
        val loopback = InetAddress.getByName("127.0.0.1")
        // Enable SO_REUSEADDR before binding 127.0.0.1:8765 so IndexedDB/localStorage origin stays stable
        // across rapid re-launches without TIME_WAIT collisions, falling back to ephemeral port if occupied.
        serverSocket = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(loopback, 8765), 60)
            }
        }.getOrElse {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(loopback, 0), 60)
            }
        }
        val socket = serverSocket ?: error("Unable to start local game asset server")
        port = socket.localPort
        clientPool = ThreadPoolExecutor(
            2,
            12,
            30L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(64),
            { r -> Thread(r, "lg-asset-worker").apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardOldestPolicy(),
        ).apply {
            allowCoreThreadTimeOut(true)
        }
        acceptThread = Thread(this, "lg-asset-server").apply {
            isDaemon = true
            start()
        }
        return port
    }

    fun getPort(): Int = port

    fun buildEntryUrl(): String = "http://127.0.0.1:$port/__g_$launchToken/index.html"

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: IOException) {}
        acceptThread?.interrupt()
        try {
            clientPool?.shutdownNow()
        } catch (_: Throwable) {}
        clientPool = null
    }

    override fun run() {
        while (running) {
            try {
                val socket = serverSocket?.accept() ?: break
                val pool = clientPool
                if (pool != null && !pool.isShutdown) {
                    pool.execute {
                        try {
                            handle(socket)
                        } catch (_: Throwable) {}
                    }
                } else {
                    try { socket.close() } catch (_: Throwable) {}
                }
            } catch (_: Exception) {
                if (!running) break
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 10_000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                if (method != "GET" && method != "HEAD") {
                    write(s, 405, "text/plain; charset=utf-8", "method not allowed".toByteArray(StandardCharsets.UTF_8))
                    return
                }
                var rawPath = parts[1]

                var rangeHeader: String? = null
                var originHeader: String? = null
                var refererHeader: String? = null
                var secFetchSite: String? = null
                var authTokenHeader: String? = null
                var headerCount = 0
                var headerLine: String? = reader.readLine()
                while (!headerLine.isNullOrEmpty() && headerCount < 64) {
                    val line = headerLine
                    headerCount++
                    when {
                        line.regionMatches(0, "Range:", 0, 6, ignoreCase = true) ->
                            rangeHeader = line.substring(6).trim()
                        line.regionMatches(0, "Origin:", 0, 7, ignoreCase = true) ->
                            originHeader = line.substring(7).trim()
                        line.regionMatches(0, "Referer:", 0, 8, ignoreCase = true) ->
                            refererHeader = line.substring(8).trim()
                        line.regionMatches(0, "Sec-Fetch-Site:", 0, 15, ignoreCase = true) ->
                            secFetchSite = line.substring(15).trim()
                        line.regionMatches(0, "X-Aurora-Game-Token:", 0, 20, ignoreCase = true) ->
                            authTokenHeader = line.substring(20).trim()
                    }
                    headerLine = reader.readLine()
                }

                val q = rawPath.indexOf('?')
                if (q >= 0) rawPath = rawPath.substring(0, q)
                var decodedPath = runCatching { URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)

                val tokenPrefix = "/__g_$launchToken/"
                val expectedOrigin = "http://127.0.0.1:$port"
                val hasPathToken = decodedPath.startsWith(tokenPrefix) || decodedPath == "/__g_$launchToken"
                val hasHeaderToken = !authTokenHeader.isNullOrEmpty() && constantTimeEquals(authTokenHeader, launchToken)
                val hasRefererToken = refererHeader?.startsWith("$expectedOrigin$tokenPrefix") == true

                // Reject cross-site / foreign-origin requests from external browsers on the same device
                if (!originHeader.isNullOrEmpty() && originHeader != expectedOrigin && originHeader != "null") {
                    write(s, 403, "text/plain; charset=utf-8", "forbidden".toByteArray(StandardCharsets.UTF_8))
                    return
                }
                if (!secFetchSite.isNullOrEmpty() && secFetchSite.equals("cross-site", ignoreCase = true)) {
                    write(s, 403, "text/plain; charset=utf-8", "forbidden".toByteArray(StandardCharsets.UTF_8))
                    return
                }
                if (!hasPathToken && !hasHeaderToken && !hasRefererToken) {
                    write(s, 403, "text/plain; charset=utf-8", "forbidden".toByteArray(StandardCharsets.UTF_8))
                    return
                }

                if (decodedPath == "/__g_$launchToken") {
                    decodedPath = "/index.html"
                } else if (decodedPath.startsWith(tokenPrefix)) {
                    decodedPath = "/" + decodedPath.substring(tokenPrefix.length)
                }

                val normalizedPath = normalizeAssetSubPath(decodedPath)
                if (normalizedPath == null) {
                    write(s, 403, "text/plain; charset=utf-8", "forbidden".toByteArray(StandardCharsets.UTF_8))
                    return
                }

                val assetPath = "www/$normalizedPath"
                var totalLength: Long = -1
                var afd: AssetFileDescriptor? = null
                try {
                    afd = assets.openFd(assetPath)
                    totalLength = afd.length
                } catch (_: Exception) {
                    try {
                        assets.open(assetPath).use { testIn ->
                            totalLength = testIn.available().toLong()
                            if (totalLength <= 0) {
                                val tmp = ByteArray(8192)
                                var count = 0L
                                var n: Int
                                while (testIn.read(tmp).also { n = it } > 0) count += n
                                totalLength = count
                            }
                        }
                    } catch (_: IOException) {
                        write(s, 404, "text/plain; charset=utf-8", "not found".toByteArray(StandardCharsets.UTF_8))
                        return
                    }
                } finally {
                    afd?.let {
                        try { it.close() } catch (_: Exception) {}
                    }
                }

                val lower = normalizedPath.lowercase(Locale.ROOT)
                val dot = lower.lastIndexOf('.')
                val ext = if (dot >= 0) lower.substring(dot + 1) else ""
                val mime = MIME[ext] ?: "application/octet-stream"

                val os = BufferedOutputStream(s.getOutputStream())

                if (rangeHeader != null && rangeHeader.startsWith("bytes=") && totalLength > 0) {
                    val m = RANGE_PATTERN.matcher(rangeHeader)
                    if (m.find()) {
                        val start = m.group(1)?.toLongOrNull() ?: -1L
                        val endStr = m.group(2)
                        val end = if (!endStr.isNullOrEmpty()) (endStr.toLongOrNull() ?: -1L) else totalLength - 1
                        if (start < 0L || start >= totalLength || end < start) {
                            val h416 = "HTTP/1.1 416 Range Not Satisfiable\r\n" +
                                "Content-Range: bytes */$totalLength\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n\r\n"
                            os.write(h416.toByteArray(StandardCharsets.ISO_8859_1))
                            os.flush()
                            return
                        }
                        val validEnd = if (end >= totalLength) totalLength - 1 else end
                        val contentLength = validEnd - start + 1

                        val headers = "HTTP/1.1 206 Partial Content\r\n" +
                            "Content-Type: $mime\r\n" +
                            "Accept-Ranges: bytes\r\n" +
                            "Content-Range: bytes $start-$validEnd/$totalLength\r\n" +
                            "Content-Length: $contentLength\r\n" +
                            "Connection: close\r\n\r\n"
                        os.write(headers.toByteArray(StandardCharsets.ISO_8859_1))

                        if (method != "HEAD") {
                            BufferedInputStream(assets.open(assetPath)).use { `in` ->
                                var skipped = 0L
                                while (skipped < start) {
                                    val sCount = `in`.skip(start - skipped)
                                    if (sCount <= 0) {
                                        if (`in`.read() < 0) break
                                        skipped++
                                    } else {
                                        skipped += sCount
                                    }
                                }
                                val buf = ByteArray(32768)
                                var bytesRemaining = contentLength
                                while (bytesRemaining > 0) {
                                    val toRead = minOf(buf.size.toLong(), bytesRemaining).toInt()
                                    val read = `in`.read(buf, 0, toRead)
                                    if (read < 0) break
                                    os.write(buf, 0, read)
                                    bytesRemaining -= read
                                }
                            }
                        }
                        os.flush()
                        return
                    }
                }

                val headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: $mime\r\n" +
                    "Content-Length: $totalLength\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Connection: close\r\n\r\n"
                os.write(headers.toByteArray(StandardCharsets.ISO_8859_1))

                if (method != "HEAD") {
                    BufferedInputStream(assets.open(assetPath)).use { `in` ->
                        val buf = ByteArray(32768)
                        var n: Int
                        while (`in`.read(buf).also { n = it } > 0) {
                            os.write(buf, 0, n)
                        }
                    }
                }
                os.flush()
            }
        } catch (_: Throwable) {
            // Client socket reset or connection closed
        }
    }

    private fun write(s: Socket, code: Int, type: String, body: ByteArray) {
        try {
            val os = s.getOutputStream()
            val reason = when (code) {
                404 -> "Not Found"
                403 -> "Forbidden"
                405 -> "Method Not Allowed"
                416 -> "Range Not Satisfiable"
                else -> "Error"
            }
            val headers = "HTTP/1.1 $code $reason\r\n" +
                "Content-Type: $type\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            os.write(headers.toByteArray(StandardCharsets.ISO_8859_1))
            os.write(body)
            os.flush()
        } catch (_: Throwable) {}
    }

    companion object {
        private val secureRandom = SecureRandom()
        private val RANGE_PATTERN = Pattern.compile("bytes=(\\d+)-(\\d*)")
        private val MIME = mapOf(
            "html" to "text/html; charset=utf-8",
            "css" to "text/css; charset=utf-8",
            "js" to "application/javascript; charset=utf-8",
            "json" to "application/json; charset=utf-8",
            "png" to "image/png",
            "webp" to "image/webp",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "svg" to "image/svg+xml",
            "mp4" to "video/mp4",
            "m4a" to "audio/mp4",
            "mp3" to "audio/mpeg",
            "ogg" to "audio/ogg",
            "atlas" to "text/plain; charset=utf-8",
            "skel" to "application/octet-stream",
            "woff2" to "font/woff2",
        )

        internal fun generateLaunchToken(): String {
            val bytes = ByteArray(16)
            secureRandom.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        internal fun constantTimeEquals(a: String, b: String): Boolean {
            if (a.isEmpty() || b.isEmpty()) return false
            return MessageDigest.isEqual(
                a.toByteArray(StandardCharsets.UTF_8),
                b.toByteArray(StandardCharsets.UTF_8),
            )
        }

        internal fun normalizeAssetSubPath(rawPath: String): String? {
            if (rawPath.contains('\u0000') || rawPath.contains('\\')) return null
            val trimmed = if (rawPath == "/" || rawPath.isEmpty()) "index.html" else rawPath.trimStart('/')
            if (trimmed.isEmpty()) return "index.html"
            val segments = mutableListOf<String>()
            for (seg in trimmed.split('/')) {
                when (seg) {
                    "", "." -> continue
                    ".." -> return null
                    else -> segments.add(seg)
                }
            }
            return if (segments.isEmpty()) "index.html" else segments.joinToString("/")
        }
    }
}
