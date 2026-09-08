package com.aurorashelf.app.data.comic

import com.aurorashelf.app.data.VideoRepository
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

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
    ): String = open(url, method, headers, body) { connection ->
        connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun response(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null,
    ): ComicHttpResponse = open(url, method, headers, body, acceptErrors = true) { connection ->
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        ComicHttpResponse(
            status = status,
            body = stream?.bufferedReader()?.use { it.readText() }.orEmpty(),
            finalUrl = connection.url.toString(),
        )
    }

    private fun <T> open(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        acceptErrors: Boolean = false,
        read: (HttpURLConnection) -> T,
    ): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", VideoRepository.USER_AGENT)
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter().use { it.write(body) }
            }
            val status = connection.responseCode
            if (!acceptErrors && status !in 200..299) {
                throw IOException("漫画源返回 HTTP $status")
            }
            return read(connection)
        } finally {
            connection.disconnect()
        }
    }

}
