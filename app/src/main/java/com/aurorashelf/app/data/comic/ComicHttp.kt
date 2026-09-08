package com.aurorashelf.app.data.comic

import com.aurorashelf.app.data.VideoRepository
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

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

    private fun <T> open(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
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
            if (status !in 200..299) {
                throw IOException("漫画源返回 HTTP $status")
            }
            return read(connection)
        } finally {
            connection.disconnect()
        }
    }

}
