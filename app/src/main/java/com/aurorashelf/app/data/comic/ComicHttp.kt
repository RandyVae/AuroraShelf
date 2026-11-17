package com.aurorashelf.app.data.comic

import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.data.resilience.AuroraHttpClient
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal data class ComicHttpResponse(
    val status: Int,
    val body: String,
    val finalUrl: String,
)

internal object ComicHttp {
    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        request(url = url, method = "GET", headers = headers)

    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String =
        request(
            url = url,
            method = "POST",
            headers = headers + ("Content-Type" to "application/json"),
            body = body,
        )

    fun getResponse(url: String, headers: Map<String, String> = emptyMap()): ComicHttpResponse =
        response(url = url, method = "GET", headers = headers)

    fun postJsonResponse(url: String, body: String, headers: Map<String, String> = emptyMap()): ComicHttpResponse =
        response(
            url = url,
            method = "POST",
            headers = headers + ("Content-Type" to "application/json; charset=UTF-8"),
            body = body,
        )

    fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    fun resolve(baseUrl: String, address: String?): String? {
        if (address.isNullOrBlank()) return null
        return runCatching { URI(baseUrl).resolve(address).toString() }.getOrNull()
    }

    private fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null,
    ): String {
        val resp = response(url, method, headers, body, acceptErrors = false)
        return resp.body
    }

    private fun response(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null,
        acceptErrors: Boolean = true,
    ): ComicHttpResponse = openWithPooledClient(url, method, headers, body, acceptErrors)

    private fun openWithPooledClient(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        acceptErrors: Boolean = false,
    ): ComicHttpResponse {
        var currentUrl = url
        var currentMethod = method
        var currentBody = body
        var currentHeaders = headers
        var redirectCount = 0
        val maxRedirects = 5
        // 统一关闭自动跟随重定向，显式实现跨域名剥离认证 Header 逻辑并共享 AuroraHttpClient 连接池与 DoH/TLS 自愈能力
        val noRedirectClient = AuroraHttpClient.standardClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        while (true) {
            val requestBuilder = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", VideoRepository.USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
            currentHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }
            if (currentMethod == "POST" || currentMethod == "PUT") {
                val contentType = currentHeaders.entries
                    .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                    ?.value ?: "application/json; charset=UTF-8"
                val requestBody = (currentBody ?: "").toRequestBody(contentType.toMediaType())
                if (currentMethod == "PUT") requestBuilder.put(requestBody) else requestBuilder.post(requestBody)
            } else if (currentMethod == "HEAD") {
                requestBuilder.head()
            } else {
                requestBuilder.get()
            }

            val BuiltRequest = requestBuilder.build()
            val okResponse: Response = try {
                noRedirectClient.newCall(BuiltRequest).execute()
            } catch (err: IOException) {
                val isSsl = generateSequence(err as Throwable?) { it.cause }.any { it is javax.net.ssl.SSLException }
                val fallback = if (isSsl) AuroraHttpClient.permissiveTlsClient else AuroraHttpClient.http1Client
                fallback.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    .newCall(BuiltRequest)
                    .execute()
            }

            okResponse.use { resp ->
                val status = resp.code
                if (status in listOf(301, 302, 303, 307, 308) && redirectCount < maxRedirects) {
                    val location = resp.header("Location")
                    if (!location.isNullOrBlank()) {
                        val nextUrl = resolve(currentUrl, location) ?: location
                        val oldHost = runCatching { URI(currentUrl).host?.lowercase() }.getOrNull()
                        val nextHost = runCatching { URI(nextUrl).host?.lowercase() }.getOrNull()
                        if (oldHost != null && nextHost != null && oldHost != nextHost) {
                            currentHeaders = currentHeaders.filterKeys { key ->
                                !key.equals("authorization", ignoreCase = true) &&
                                    !key.equals("cookie", ignoreCase = true) &&
                                    !key.equals("token", ignoreCase = true) &&
                                    !key.equals("signature", ignoreCase = true)
                            }
                        }
                        if (status in listOf(301, 302, 303) && currentMethod != "GET" && currentMethod != "HEAD") {
                            currentMethod = "GET"
                            currentBody = null
                        }
                        currentUrl = nextUrl
                        redirectCount++
                        continue
                    }
                }
                if (!acceptErrors && status !in 200..299) {
                    throw IOException("漫画源返回 HTTP $status")
                }
                val text = resp.body?.string().orEmpty()
                return ComicHttpResponse(
                    status = status,
                    body = text,
                    finalUrl = resp.request.url.toString(),
                )
            }
        }
    }

}
