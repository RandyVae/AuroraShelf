package com.aurorashelf.app.data.resilience

import android.util.Log
import com.aurorashelf.app.data.isTransientTransportFailure
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal data class HttpTextResult(
    val body: String,
    val finalUrl: String,
    val statusCode: Int,
    val latencyMs: Long,
)

/**
 * 全应用共享的自愈 HTTP/2 传输引擎（替换零散的冷连接 Jsoup.connect 与 HttpURLConnection）。
 *
 * 自愈能力清单：
 * 1. 全局连接池复用 HTTP/2 与 TLS Session（ConnectionPool(16, 3min)），消除重复握手延迟；
 * 2. 注入 [ResilientDns] 解决 DNS 污染与超慢解析，并兼容 VPN TUN Fake-IP；
 * 3. HTTP/2 流重置（StreamResetException / PROTOCOL_ERROR / EOF）自动降级到 HTTP/1.1 重试；
 * 4. TLS 证书链校验异常（SSLException / CertPath）自动降级至宽松 TLS 会话重试；
 * 5. 阶梯对冲竞速（Staggered Hedged Racing, default 1,100ms）：
 *    - 若主镜像快速响应（<1.1s），完全零冗余流量，避免触发 CDN 428/429；
 *    - 若主镜像黑洞/超时/卡顿超过 1.1s 或瞬间报错，立即并发拉起次优镜像竞速，获胜者瞬间取消慢者！
 */
internal object AuroraHttpClient {
    private const val TAG = "AuroraHttp"
    private const val DEFAULT_HEDGE_DELAY_MS = 1_100L
    private val warmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sharedConnectionPool = ConnectionPool(16, 3, TimeUnit.MINUTES)

    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 12
    }

    internal val standardClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .dispatcher(sharedDispatcher)
            .dns(ResilientDns)
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    internal val http1Client: OkHttpClient by lazy {
        standardClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    internal val permissiveTlsClient: OkHttpClient by lazy {
        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }
        standardClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .build()
    }

    fun executeRaw(
        request: Request,
        timeoutMs: Long = 12_000L,
    ): Response {
        val clientForTimeout = if (timeoutMs != 12_000L) {
            standardClient.newBuilder()
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(timeoutMs.coerceAtMost(8_000L), TimeUnit.MILLISECONDS)
                .build()
        } else {
            standardClient
        }
        return try {
            clientForTimeout.newCall(request).execute()
        } catch (err: IOException) {
            when {
                isSslError(err) -> permissiveTlsClient.newCall(request).execute()
                isHttp2StreamError(err) || err.isTransientTransportFailure() -> http1Client.newCall(request).execute()
                else -> throw err
            }
        }
    }

    fun fetchText(
        url: String,
        sourceKey: String? = SourceHealthRegistry.sourceKeyForUrl(url),
        method: String = "GET",
        bodyText: String? = null,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 12_000L,
        acceptErrorStatus: Boolean = false,
        learnRedirectMirror: Boolean = true,
    ): HttpTextResult {
        val startedAt = System.currentTimeMillis()
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        if (method.equals("POST", ignoreCase = true)) {
            val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
                ?: "application/json; charset=UTF-8"
            requestBuilder.post((bodyText ?: "").toRequestBody(contentType.toMediaType()))
        } else {
            requestBuilder.get()
        }

        val response = try {
            executeRaw(requestBuilder.build(), timeoutMs = timeoutMs)
        } catch (err: IOException) {
            SourceHealthRegistry.recordFailure(sourceKey, url, statusCode = 0, isHardBlock = true)
            throw err
        }

        response.use { resp ->
            val latencyMs = System.currentTimeMillis() - startedAt
            val status = resp.code
            val finalUrl = resp.request.url.toString()
            val rawBytes = resp.body?.bytes() ?: ByteArray(0)
            val charset = resolveHtmlOrHttpCharset(resp, rawBytes)
            val text = String(rawBytes, charset)

            if (!acceptErrorStatus && status !in 200..299) {
                val hardBlock = status in listOf(403, 502, 503, 504)
                SourceHealthRegistry.recordFailure(sourceKey, url, statusCode = status, isHardBlock = hardBlock)
                throw HttpStatusException("HTTP error fetching URL", status, finalUrl)
            }

            if (status in 200..299) {
                SourceHealthRegistry.recordSuccess(
                    sourceKey = sourceKey,
                    urlOrHost = url,
                    latencyMs = latencyMs,
                    finalRedirectUrl = if (learnRedirectMirror) finalUrl else null,
                )
            }
            return HttpTextResult(
                body = text,
                finalUrl = finalUrl,
                statusCode = status,
                latencyMs = latencyMs,
            )
        }
    }

    fun fetchDocument(
        url: String,
        sourceKey: String? = SourceHealthRegistry.sourceKeyForUrl(url),
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 12_000L,
    ): Document {
        val candidates = SourceHealthRegistry.expandCandidateUrls(sourceKey, url)
        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                val result = fetchText(
                    url = candidate,
                    sourceKey = sourceKey,
                    headers = headers,
                    timeoutMs = timeoutMs,
                )
                return Jsoup.parse(result.body, result.finalUrl)
            } catch (err: HttpStatusException) {
                // 404/410 belong to application routing (e.g. pagination end or Huangguo deprecated path); do not swallow
                if (err.statusCode == 404 || err.statusCode == 410) throw err
                lastError = err
            } catch (err: IOException) {
                lastError = err
            }
        }
        throw (lastError ?: IOException("所有候选页面地址均不可达"))
    }

    /**
     * 阶梯对冲竞速请求：
     * - 首先请求健康分最高的 #1 候选地址；
     * - 若 #1 在 [hedgeDelayMs]（默认 1,100ms）内未完成，或更早抛异常，立即拉起 #2 并发竞速；
     * - 任意候选返回合法结果即刻取消其余慢任务；
     * - 所有逻辑通过注入 [executor] 完全支持纯 JVM 单元测试与故障注入论证。
     */
    suspend fun fetchHedgedText(
        sourceKey: String?,
        candidateUrls: List<String>,
        headersBuilder: (String) -> Map<String, String> = { emptyMap() },
        timeoutMs: Long = 12_000L,
        hedgeDelayMs: Long = DEFAULT_HEDGE_DELAY_MS,
        validator: ((HttpTextResult) -> Boolean)? = null,
        executor: suspend (String, Map<String, String>) -> HttpTextResult = { targetUrl, hdrs ->
            fetchText(url = targetUrl, sourceKey = sourceKey, headers = hdrs, timeoutMs = timeoutMs)
        },
    ): HttpTextResult {
        val ranked = SourceHealthRegistry.rankCandidates(sourceKey, candidateUrls.distinct())
        require(ranked.isNotEmpty()) { "候选请求地址为空" }

        if (ranked.size == 1) {
            val target = ranked.first()
            val res = executor(target, headersBuilder(target))
            if (validator != null && !validator(res)) {
                throw IOException("源站响应校验未通过 ($target)")
            }
            return res
        }

        return coroutineScope {
            val outcomes = Channel<Pair<Int, Result<HttpTextResult>>>(Channel.UNLIMITED)
            val jobs = mutableListOf<Deferred<Unit>>()

            fun launchCandidate(index: Int, url: String) {
                val job = async(Dispatchers.IO) {
                    val result = runCatching {
                        val res = executor(url, headersBuilder(url))
                        if (validator != null && !validator(res)) {
                            throw IOException("源站响应校验未通过 ($url)")
                        }
                        res
                    }
                    outcomes.send(index to result)
                }
                jobs += job
            }

            // t = 0: 只发送榜首健康节点，零冗余并发请求
            launchCandidate(0, ranked[0])

            var nextToLaunch = 1
            var remainingActive = 1
            var lastFailure: Throwable? = null

            // t = hedgeDelayMs: 若 #0 未在窗口期内返回，并发发送 #1 进行对冲竞速
            val firstOutcome = withTimeoutOrNull(hedgeDelayMs) {
                outcomes.receive()
            }
            if (firstOutcome != null) {
                remainingActive--
                val res = firstOutcome.second
                if (res.isSuccess) {
                    jobs.forEach { it.cancel() }
                    return@coroutineScope res.getOrThrow()
                }
                lastFailure = res.exceptionOrNull()
            }

            while (nextToLaunch < ranked.size || remainingActive > 0) {
                if (nextToLaunch < ranked.size) {
                    val idx = nextToLaunch++
                    remainingActive++
                    launchCandidate(idx, ranked[idx])
                }
                val nextOutcome = if (nextToLaunch < ranked.size) {
                    withTimeoutOrNull(hedgeDelayMs) { outcomes.receive() }
                } else {
                    outcomes.receive()
                }
                if (nextOutcome != null) {
                    remainingActive--
                    val res = nextOutcome.second
                    if (res.isSuccess) {
                        jobs.forEach { it.cancel() }
                        return@coroutineScope res.getOrThrow()
                    }
                    lastFailure = res.exceptionOrNull()
                }
            }

            jobs.forEach { it.cancel() }
            throw lastFailure ?: IOException("所有候选镜像站均无响应")
        }
    }

    suspend fun fetchHedgedDocument(
        sourceKey: String?,
        requestUrl: String,
        headersBuilder: (String) -> Map<String, String> = { emptyMap() },
        timeoutMs: Long = 12_000L,
        hedgeDelayMs: Long = DEFAULT_HEDGE_DELAY_MS,
    ): Document {
        val candidates = SourceHealthRegistry.expandCandidateUrls(sourceKey, requestUrl)
        val result = fetchHedgedText(
            sourceKey = sourceKey,
            candidateUrls = candidates,
            headersBuilder = headersBuilder,
            timeoutMs = timeoutMs,
            hedgeDelayMs = hedgeDelayMs,
        )
        return Jsoup.parse(result.body, result.finalUrl)
    }

    fun prewarmOriginsAsync(urls: List<String>) {
        if (urls.isEmpty()) return
        warmupScope.launch {
            urls.distinct().forEach { originUrl ->
                launch {
                    val started = System.currentTimeMillis()
                    runCatching {
                        val req = Request.Builder()
                            .url(originUrl)
                            .head()
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
                            .build()
                        executeRaw(req, timeoutMs = 4_000L).use { resp ->
                            val ms = System.currentTimeMillis() - started
                            SourceHealthRegistry.recordSuccess(
                                sourceKey = SourceHealthRegistry.sourceKeyForUrl(originUrl),
                                urlOrHost = originUrl,
                                latencyMs = ms,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun resolveHtmlOrHttpCharset(response: Response, bytes: ByteArray): Charset {
        response.body?.contentType()?.charset()?.let { return it }
        val headSnippet = String(bytes, 0, bytes.size.coerceAtMost(1024), StandardCharsets.ISO_8859_1)
        val metaMatch = CHARSET_META_REGEX.find(headSnippet)?.groupValues?.getOrNull(1)?.trim()
        if (!metaMatch.isNullOrBlank()) {
            val resolved = runCatching { Charset.forName(metaMatch) }.getOrNull()
            if (resolved != null) return resolved
        }
        return StandardCharsets.UTF_8
    }

    private fun isHttp2StreamError(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { cause ->
            val name = cause.javaClass.simpleName
            val msg = cause.message.orEmpty()
            name.contains("StreamReset", ignoreCase = true) ||
                msg.contains("PROTOCOL_ERROR", ignoreCase = true) ||
                msg.contains("stream was reset", ignoreCase = true)
        }

    private fun isSslError(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { it is SSLException }

    private val CHARSET_META_REGEX = Regex("""<meta[^>]+charset\s*=\s*["']?([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)

    private fun logSafe(msg: String) {
        runCatching { Log.i(TAG, msg) }
    }
}
