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
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * High-performance loopback HTTP server that serves APK assets with HTTP 206 Partial Content (Range requests)
 * and direct streaming buffers for smooth 1080p video playback and zero-OOM memory safety.
 */
class GameAssetServer(context: Context) : Runnable {
    private val assets: AssetManager = context.assets

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
        // Try fixed port 8765 once so IndexedDB/localStorage origin stays stable across launches,
        // and immediately fall back to an ephemeral port if another process holds 8765 (#18).
        serverSocket = runCatching { ServerSocket(8765, 60, loopback) }.getOrNull()
            ?: ServerSocket(0, 60, loopback)
        val socket = serverSocket ?: error("Unable to start local game asset server")
        port = socket.localPort
        clientPool = Executors.newCachedThreadPool { r ->
            Thread(r, "lg-asset-worker").apply { isDaemon = true }
        }
        acceptThread = Thread(this, "lg-asset-server").apply {
            isDaemon = true
            start()
        }
        return port
    }

    fun getPort(): Int = port

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (ignored: IOException) {}
        acceptThread?.interrupt()
        try {
            clientPool?.shutdownNow()
        } catch (ignored: Throwable) {}
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
                        } catch (ignored: Throwable) {}
                    }
                } else {
                    try { socket.close() } catch (ignored: Throwable) {}
                }
            } catch (e: Exception) {
                if (!running) break
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 15000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                var rawPath = parts[1]

                var rangeHeader: String? = null
                var headerLine: String? = reader.readLine()
                while (!headerLine.isNullOrEmpty()) {
                    val line = headerLine
                    if (line.regionMatches(0, "Range:", 0, 6, ignoreCase = true)) {
                        rangeHeader = line.substring(6).trim()
                    }
                    headerLine = reader.readLine()
                }

                val q = rawPath.indexOf('?')
                if (q >= 0) rawPath = rawPath.substring(0, q)
                var path = URLDecoder.decode(rawPath, "UTF-8")
                if (path == "/" || path.isEmpty()) path = "/index.html"
                if (path.startsWith("/")) path = path.substring(1)
                if (path.contains("..")) {
                    write(s, 403, "text/plain", "forbidden".toByteArray(StandardCharsets.UTF_8))
                    return
                }

                val assetPath = "www/$path"
                var totalLength: Long = -1
                var afd: AssetFileDescriptor? = null
                try {
                    afd = assets.openFd(assetPath)
                    totalLength = afd.length
                } catch (e: Exception) {
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
                    } catch (missing: IOException) {
                        write(s, 404, "text/plain; charset=utf-8", ("not found: $path").toByteArray(StandardCharsets.UTF_8))
                        return
                    }
                } finally {
                    afd?.let {
                        try { it.close() } catch (ignored: Exception) {}
                    }
                }

                val lower = path.lowercase(Locale.ROOT)
                val dot = lower.lastIndexOf('.')
                val ext = if (dot >= 0) lower.substring(dot + 1) else ""
                val mime = MIME[ext] ?: "application/octet-stream"

                val os = BufferedOutputStream(s.getOutputStream())

                if (rangeHeader != null && rangeHeader.startsWith("bytes=") && totalLength > 0) {
                    val m = RANGE_PATTERN.matcher(rangeHeader)
                    if (m.find()) {
                        val start = m.group(1)?.toLongOrNull() ?: 0L
                        val endStr = m.group(2)
                        val end = if (!endStr.isNullOrEmpty()) endStr.toLong() else totalLength - 1
                        if (start >= totalLength) {
                            val h416 = "HTTP/1.1 416 Range Not Satisfiable\r\n" +
                                "Content-Range: bytes */$totalLength\r\n" +
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
                            "Access-Control-Allow-Origin: *\r\n" +
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
                                    val toRead = Math.min(buf.size.toLong(), bytesRemaining).toInt()
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
                    "Access-Control-Allow-Origin: *\r\n" +
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
        } catch (ignored: Throwable) {
            // Client socket reset or connection closed
        }
    }

    private fun write(s: Socket, code: Int, type: String, body: ByteArray) {
        try {
            val os = s.getOutputStream()
            val reason = when (code) {
                404 -> "Not Found"
                403 -> "Forbidden"
                else -> "Error"
            }
            val headers = "HTTP/1.1 $code $reason\r\n" +
                "Content-Type: $type\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            os.write(headers.toByteArray(StandardCharsets.ISO_8859_1))
            os.write(body)
            os.flush()
        } catch (ignored: Throwable) {}
    }

    companion object {
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
    }
}
