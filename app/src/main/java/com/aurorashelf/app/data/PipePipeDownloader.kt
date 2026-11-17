package com.aurorashelf.app.data

import com.aurorashelf.app.model.ContentSourceCatalog
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import android.util.Log
import com.aurorashelf.app.data.resilience.AuroraHttpClient
import com.aurorashelf.app.data.resilience.SourceHealthRegistry
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** Minimal cookie-free network implementation for the vendored PipePipe extractor. */
internal class PipePipeDownloader : Downloader() {
    // 共享全局 HTTP/2 连接池 + TUN-Safe DoH DNS 解析，并提供 22 秒读超时适配国际大站点提取器
    private val client: OkHttpClient by lazy {
        AuroraHttpClient.standardClient.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(22, TimeUnit.SECONDS)
            .build()
    }

    override fun execute(request: Request): Response {
        val okhttpRequest = request.toOkHttpRequest()
        return executeWithRetry(okhttpRequest).use { response ->
            Log.i(TAG, "GET ${request.url()} code=${response.code} bytes=${response.body?.contentLength()}")
            response.toExtractorResponse(request.url())
        }
    }

    private fun executeWithRetry(request: okhttp3.Request): okhttp3.Response {
        val urlStr = request.url.toString()
        val sourceKey = SourceHealthRegistry.sourceKeyForUrl(urlStr)
        val started = System.currentTimeMillis()
        return try {
            val resp = client.newCall(request).execute()
            if (resp.isSuccessful && sourceKey != null) {
                SourceHealthRegistry.recordSuccess(sourceKey, urlStr, System.currentTimeMillis() - started)
            }
            resp
        } catch (failure: IOException) {
            val isRetryableTimeout = failure is java.net.SocketTimeoutException
            if (sourceKey != null) {
                SourceHealthRegistry.recordFailure(sourceKey, urlStr, isHardBlock = true)
            }
            if (request.method !in IDEMPOTENT_METHODS || (!failure.isTransientTransportFailure() && !isRetryableTimeout)) {
                throw failure
            }
            // 若具备其他健康镜像（如 Pornhub cn/jp/www），先切换到健康镜像重试，否则降级 HTTP/1.1 重试
            val alternateUrl = sourceKey?.let { key ->
                SourceHealthRegistry.expandCandidateUrls(key, urlStr).firstOrNull { it != urlStr }
            }
            val retryRequest = if (alternateUrl != null) {
                request.newBuilder().url(alternateUrl).build()
            } else {
                request
            }
            AuroraHttpClient.http1Client.newBuilder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(22, TimeUnit.SECONDS)
                .build()
                .newCall(retryRequest)
                .execute()
        }
    }

    override fun executeAsync(request: Request, callback: AsyncCallback): CancellableCall {
        val call = client.newCall(request.toOkHttpRequest())
        val cancellable = CancellableCall(call)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cancellable.setFinished()
                callback.onError(e)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                try {
                    response.use { callback.onSuccess(it.toExtractorResponse(request.url())) }
                } catch (error: Exception) {
                    callback.onError(error)
                } finally {
                    cancellable.setFinished()
                }
            }
        })
        return cancellable
    }

    private fun Request.toOkHttpRequest(): okhttp3.Request {
        val method = httpMethod().uppercase()
        val requestData = dataToSend()
        val body = when {
            requestData != null -> requestData.toRequestBody()
            method in METHODS_REQUIRING_BODY -> ByteArray(0).toRequestBody()
            else -> null
        }
        return okhttp3.Request.Builder()
            .url(url())
            .method(method, body)
            .header("User-Agent", VideoRepository.USER_AGENT)
            .apply {
                headers().forEach { (name, values) ->
                    removeHeader(name)
                    values.forEach { addHeader(name, it) }
                }
                if (url().contains("pornhub.com")) {
                    val existingCookie = headers()["Cookie"]?.joinToString("; ").orEmpty()
                    val ageCookie = "age_verified=1; has_access=1; accessAgeDisclaimerPH=1; platform=mobile; cookieConsent=3; bs=000000000000000022522ffa8ac94dcd;"
                    val combined = if (existingCookie.isBlank()) ageCookie else "$existingCookie; $ageCookie"
                    removeHeader("Cookie")
                    addHeader("Cookie", combined)
                }
            }
            .build()
    }

    private fun okhttp3.Response.toExtractorResponse(originalUrl: String): Response {
        if (code == 429) throw ReCaptchaException("站点要求进行人机验证", originalUrl)
        val bytes = body?.bytes() ?: ByteArray(0)
        return Response(
            code,
            message,
            headers.toMultimap(),
            String(bytes, StandardCharsets.UTF_8),
            bytes,
            request.url.toString(),
        )
    }

    companion object {
        private val IDEMPOTENT_METHODS = setOf("GET", "HEAD")
        private val METHODS_REQUIRING_BODY = setOf("POST", "PUT", "PATCH")
        private const val TAG = "PipePipeDownloader"
    }
}
