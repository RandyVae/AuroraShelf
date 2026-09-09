package com.aurorashelf.app.data.comic

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

/** Keeps E-Hentai image delivery usable when a device DNS result is slow or unreachable. */
internal object EhentaiDns : Dns {
    private val preferredAddresses = mapOf(
        "ehgt.org" to listOf("81.171.14.118", "109.236.85.28"),
        "ul.ehgt.org" to listOf("94.100.24.82"),
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val preferred = preferredAddresses[hostname].orEmpty().mapNotNull { address ->
            runCatching { InetAddress.getByName(address) }.getOrNull()
        }
        val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
        return (preferred + system).distinctBy(InetAddress::getHostAddress).ifEmpty {
            throw UnknownHostException(hostname)
        }
    }
}

/** Shared connection pool for E-Hentai metadata and image-page resolution. */
internal object EhentaiHttp {
    private val client = OkHttpClient.Builder()
        .dns(EhentaiDns)
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
