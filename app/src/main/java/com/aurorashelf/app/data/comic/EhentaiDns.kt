package com.aurorashelf.app.data.comic

import com.aurorashelf.app.data.resilience.ResilientDns
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

/** Keeps E-Hentai image delivery usable when a device DNS result is slow or unreachable. */
internal object EhentaiDns : Dns by ResilientDns

/** Shared connection pool for E-Hentai metadata and image-page resolution. */
internal object EhentaiHttp {
    private val client = OkHttpClient.Builder()
        .dns(ResilientDns)
        // Some E-Hentai edge nodes intermittently abort Android's HTTP/2 TLS session.
        // HTTP/1.1 keeps full certificate validation and avoids that transport failure.
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun get(url: String, headers: Map<String, String>): Pair<String, String> {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("漫画源返回 HTTP ${response.code}")
            val body = response.body ?: throw java.io.IOException("漫画源返回空响应")
            body.string() to response.request.url.toString()
        }
    }
}
