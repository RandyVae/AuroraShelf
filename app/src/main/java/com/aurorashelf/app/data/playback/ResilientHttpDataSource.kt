package com.aurorashelf.app.data.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Shared OkHttp-backed [HttpDataSource.Factory] for ExoPlayer HLS/MP4 playback and offline caching.
 * Reuses HTTP/2 connections to avoid BunnyCDN `428 CDN-Limit` connection bursts, retries transient
 * `428`/`429`/`503` segment responses inline, and falls back once to permissive media TLS if a
 * third-party CDN port/cert (such as `:65`) rejects the system trust store.
 */
@OptIn(UnstableApi::class)
internal class ResilientHttpDataSourceFactory(
    private val userAgent: String,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
) : HttpDataSource.Factory {
    private val defaultProperties = HttpDataSource.RequestProperties()
    private var transferListener: TransferListener? = null

    fun setTransferListener(listener: TransferListener?): ResilientHttpDataSourceFactory {
        transferListener = listener
        return this
    }

    override fun setDefaultRequestProperties(defaultRequestProperties: MutableMap<String, String>): HttpDataSource.Factory {
        defaultProperties.clearAndSet(defaultRequestProperties)
        return this
    }

    override fun createDataSource(): HttpDataSource {
        return ResilientOkHttpDataSource(
            userAgent = userAgent,
            defaultRequestProperties = defaultProperties,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
        ).apply {
            transferListener?.let { addTransferListener(it) }
        }
    }
}

@OptIn(UnstableApi::class)
internal class ResilientOkHttpDataSource(
    private val userAgent: String,
    private val defaultRequestProperties: HttpDataSource.RequestProperties,
    private val connectTimeoutMs: Int,
    private val readTimeoutMs: Int,
) : BaseDataSource(/* isNetwork = */ true), HttpDataSource {
    private val requestProperties = HttpDataSource.RequestProperties()
    private var currentDataSpec: DataSpec? = null
    private var response: Response? = null
    private var responseByteStream: InputStream? = null
    private var opened = false
    private var bytesToRead: Long = C.LENGTH_UNSET.toLong()
    private var bytesRead: Long = 0L

    override fun setRequestProperty(name: String, value: String) {
        requestProperties.set(name, value)
    }

    override fun clearRequestProperty(name: String) {
        requestProperties.remove(name)
    }

    override fun clearAllRequestProperties() {
        requestProperties.clear()
    }

    override fun getResponseCode(): Int = response?.code ?: -1

    override fun getResponseHeaders(): Map<String, List<String>> =
        response?.headers?.toMultimap() ?: emptyMap()

    override fun getUri(): Uri? =
        response?.request?.url?.toString()?.let(Uri::parse) ?: currentDataSpec?.uri

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        bytesRead = 0L
        bytesToRead = C.LENGTH_UNSET.toLong()
        transferInitializing(dataSpec)

        val executed = executeWithRetries(dataSpec)
        response = executed
        val body = executed.body
        val code = executed.code
        val headers = executed.headers.toMultimap()

        if (!executed.isSuccessful) {
            val errorBytes = runCatching { body?.bytes() ?: ByteArray(0) }.getOrDefault(ByteArray(0))
            closeConnectionQuietly()
            throw HttpDataSource.InvalidResponseCodeException(
                code,
                executed.message,
                null,
                headers,
                dataSpec,
                errorBytes,
            )
        }

        val isAutoDecompressed =
            executed.networkResponse?.header("Content-Encoding")?.equals("gzip", ignoreCase = true) == true
        val contentLength = if (isAutoDecompressed) -1L else (body?.contentLength() ?: -1L)
        bytesToRead = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            contentLength >= 0L -> contentLength
            else -> C.LENGTH_UNSET.toLong()
        }
        responseByteStream = body?.byteStream() ?: java.io.ByteArrayInputStream(ByteArray(0))
        opened = true
        transferStarted(dataSpec)
        return bytesToRead
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val stream = responseByteStream ?: return C.RESULT_END_OF_INPUT
        val remaining = if (bytesToRead == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            val left = bytesToRead - bytesRead
            if (left <= 0L) return C.RESULT_END_OF_INPUT
            minOf(length.toLong(), left).toInt()
        }
        return try {
            val count = stream.read(buffer, offset, remaining)
            if (count == -1) {
                C.RESULT_END_OF_INPUT
            } else {
                bytesRead += count
                bytesTransferred(count)
                count
            }
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e,
                checkNotNull(currentDataSpec),
                HttpDataSource.HttpDataSourceException.TYPE_READ,
            )
        }
    }

    override fun close() {
        try {
            closeConnectionQuietly()
        } finally {
            if (opened) {
                opened = false
                transferEnded()
            }
            currentDataSpec = null
        }
    }

    private fun closeConnectionQuietly() {
        runCatching { responseByteStream?.close() }
        responseByteStream = null
        runCatching { response?.close() }
        response = null
    }

    private fun executeWithRetries(dataSpec: DataSpec): Response {
        var usePermissiveTls = false
        var lastException: IOException? = null
        for (attempt in 0 until MAX_HTTP_ATTEMPTS) {
            val request = buildOkHttpRequest(dataSpec)
            val client = clientFor(usePermissiveTls)
            try {
                val resp = client.newCall(request).execute()
                if (resp.isSuccessful || !isInlineRetryableHttpStatus(resp.code) || attempt == MAX_HTTP_ATTEMPTS - 1) {
                    return resp
                }
                val retryAfterMs = parseRetryAfterMs(resp.header("Retry-After"))
                resp.close()
                sleepBackoff(retryAfterMs ?: inlineBackoffMs(attempt))
            } catch (e: InterruptedIOException) {
                throw HttpDataSource.HttpDataSourceException.createForIOException(
                    e,
                    dataSpec,
                    HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                )
            } catch (e: SSLException) {
                lastException = e
                if (!usePermissiveTls) {
                    usePermissiveTls = true
                    continue
                }
                if (attempt == MAX_HTTP_ATTEMPTS - 1) {
                    throw HttpDataSource.HttpDataSourceException.createForIOException(
                        e,
                        dataSpec,
                        HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                    )
                }
                sleepBackoff(inlineBackoffMs(attempt))
            } catch (e: IOException) {
                lastException = e
                if (attempt == MAX_HTTP_ATTEMPTS - 1) {
                    throw HttpDataSource.HttpDataSourceException.createForIOException(
                        e,
                        dataSpec,
                        HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                    )
                }
                sleepBackoff(inlineBackoffMs(attempt))
            }
        }
        throw HttpDataSource.HttpDataSourceException.createForIOException(
            lastException ?: IOException("Unable to open ${dataSpec.uri}"),
            dataSpec,
            HttpDataSource.HttpDataSourceException.TYPE_OPEN,
        )
    }

    private fun clientFor(permissiveTls: Boolean): OkHttpClient {
        val base = if (permissiveTls) permissiveClient else strictClient
        if (connectTimeoutMs == DEFAULT_CONNECT_TIMEOUT_MS && readTimeoutMs == DEFAULT_READ_TIMEOUT_MS) {
            return base
        }
        return base.newBuilder()
            .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    private fun buildOkHttpRequest(dataSpec: DataSpec): Request {
        val builder = Request.Builder().url(dataSpec.uri.toString())
        val headers = LinkedHashMap<String, String>()
        headers.putAll(defaultRequestProperties.snapshot)
        headers.putAll(requestProperties.snapshot)
        headers.putAll(dataSpec.httpRequestHeaders)

        val requestHost = dataSpec.uri.host?.lowercase()
        val originHeader = headers["Origin"]
        if (originHeader != null && requestHost != null) {
            val originHost = runCatching { Uri.parse(originHeader).host?.lowercase() }.getOrNull()
            // Avoid sending cross-origin Origin headers on third-party GET CDN segments where it can trip strict CDN rules.
            if (originHost != null && originHost != requestHost && dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET) {
                headers.remove("Origin")
            }
        }
        if (!headers.containsKey("User-Agent")) {
            headers["User-Agent"] = userAgent
        }
        if (!dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP)) {
            headers.putIfAbsent("Accept-Encoding", "identity")
        }
        headers.forEach { (k, v) -> builder.header(k, v) }

        val rangeHeader = buildRangeHeader(dataSpec.position, dataSpec.length)
        if (rangeHeader != null) {
            builder.header("Range", rangeHeader)
        }
        if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_HEAD) {
            builder.head()
        } else {
            builder.get()
        }
        return builder.build()
    }

    private fun sleepBackoff(delayMs: Long) {
        try {
            Thread.sleep(delayMs)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrupted during HTTP backoff").apply { initCause(interrupted) }
        }
    }

    internal companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000
        private const val DEFAULT_READ_TIMEOUT_MS = 20_000
        private const val MAX_HTTP_ATTEMPTS = 3

        private val sharedPool = ConnectionPool(8, 2, TimeUnit.MINUTES)

        private val strictClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectionPool(sharedPool)
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(DEFAULT_READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }

        private val permissiveClient: OkHttpClient by lazy {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
            }
            strictClient.newBuilder()
                .sslSocketFactory(sslContext.socketFactory, trustAll)
                .hostnameVerifier { _, _ -> true }
                .build()
        }

        internal fun isInlineRetryableHttpStatus(code: Int): Boolean =
            code == 408 || code == 428 || code == 429 || code == 503

        internal fun inlineBackoffMs(attempt: Int): Long = when (attempt) {
            0 -> 700L
            1 -> 1_500L
            else -> 2_500L
        }

        internal fun parseRetryAfterMs(headerValue: String?): Long? {
            val seconds = headerValue?.trim()?.toLongOrNull() ?: return null
            if (seconds <= 0L) return null
            return (seconds * 1000L).coerceAtMost(5_000L)
        }

        internal fun buildRangeHeader(position: Long, length: Long): String? {
            if (position == 0L && length == C.LENGTH_UNSET.toLong()) return null
            return if (length == C.LENGTH_UNSET.toLong()) {
                "bytes=$position-"
            } else {
                "bytes=$position-${position + length - 1}"
            }
        }
    }
}

/**
 * Media3 [LoadErrorHandlingPolicy] that retries HTTP `428`/`429`/`408`/`5xx` and transient TLS/socket
 * errors on HLS segments and manifests instead of aborting immediately with [PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS] (2004).
 */
@OptIn(UnstableApi::class)
internal class ResilientLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 4

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val ex = loadErrorInfo.exception
        val retryable = when (ex) {
            is HttpDataSource.InvalidResponseCodeException -> isPolicyRetryableHttpStatus(ex.responseCode)
            is SSLException, is SocketTimeoutException, is SocketException -> true
            else -> ex.cause is SSLException || ex.cause is SocketTimeoutException
        }
        if (!retryable) {
            return super.getRetryDelayMsFor(loadErrorInfo)
        }
        val shift = (loadErrorInfo.errorCount - 1).coerceIn(0, 3)
        return (1_000L shl shift).coerceAtMost(5_000L)
    }

    internal companion object {
        internal fun isPolicyRetryableHttpStatus(code: Int): Boolean =
            code == 408 || code == 428 || code == 429 || code in 500..599

        internal fun isRecoverablePlayerErrorCode(errorCode: Int): Boolean = errorCode in setOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        )
    }
}
