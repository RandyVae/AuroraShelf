package com.aurorashelf.app.data.resilience

import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * TUN-Safe 抗污染 DNS 解析器（System DNS 极速优先 + Fake-IP 原样放行 + DoH 竞速自愈）。
 *
 * 关键边界：
 * 1. 若系统 DNS 返回 RFC 2544 测试网段 `198.18.0.0/15`（Clash/Surge/Shadowrocket TUN Fake-IP），
 *    必须原样放行且不以真实公网 IP 覆盖，否则将破坏代理 Zero-RTT 转发；
 * 2. 若系统 DNS 返回 `0.0.0.0` / `127.0.0.0/8` / `::1` 等污染结果、抛出 `UnknownHostException`、
 *    或耗时超过 600ms，则自动通过直连 IP 的 DoH（1.1.1.1 & 223.5.5.5）并配合静态兜底表完成自愈。
 */
internal object ResilientDns : Dns {
    private const val TAG = "ResilientDns"
    private const val POSITIVE_CACHE_TTL_MS = 10 * 60_000L
    private const val STALE_CACHE_MAX_MS = 60 * 60_000L
    private const val SYSTEM_DNS_RACE_MS = 600L

    private data class DnsCacheEntry(
        val addresses: List<InetAddress>,
        val savedAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, DnsCacheEntry>()
    private val lookupExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "aurora-dns-worker").apply { isDaemon = true }
    }

    // Bootstrap HTTP client targeting literal DoH IPs (no recursive DNS lookup needed).
    private val dohHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .build()
    }

    private val preferredStaticAddresses: Map<String, List<String>> = mapOf(
        "ehgt.org" to listOf("81.171.14.118", "109.236.85.28"),
        "ul.ehgt.org" to listOf("94.100.24.82"),
        "1.1.1.1" to listOf("1.1.1.1"),
        "223.5.5.5" to listOf("223.5.5.5"),
    )

    override fun lookup(hostname: String): List<InetAddress> =
        resolveHost(
            hostname = hostname,
            systemLookup = { Dns.SYSTEM.lookup(it) },
            dohLookup = ::queryDohProviders,
        )

    internal fun resolveHost(
        hostname: String,
        systemLookup: (String) -> List<InetAddress>,
        dohLookup: (String) -> List<InetAddress>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<InetAddress> {
        val cleanHost = hostname.trim().lowercase()
        if (cleanHost.isEmpty()) throw UnknownHostException(hostname)

        // IP 字面量直接解析，零外部耗时
        if (isIpLiteral(cleanHost)) {
            return listOf(InetAddress.getByName(cleanHost))
        }

        // 先尝试系统 DNS 极速竞速（限时 600ms，兼容 Fake-IP）
        val systemFuture = lookupExecutor.submit(
            Callable { runCatching { systemLookup(cleanHost) }.getOrNull() },
        )
        val fastSystemResult = runCatching {
            systemFuture.get(SYSTEM_DNS_RACE_MS, TimeUnit.MILLISECONDS)
        }.getOrNull()

        if (!fastSystemResult.isNullOrEmpty()) {
            // 若命中 TUN 代理 Fake-IP (198.18.0.0/15)，立即原样返回，禁止写入持久缓存或被 DoH 覆盖
            if (fastSystemResult.any(::isTunFakeIp)) {
                return fastSystemResult
            }
            val clean = filterValidRoutableAddresses(fastSystemResult)
            if (clean.isNotEmpty()) {
                val merged = mergePreferred(cleanHost, clean)
                cache[cleanHost] = DnsCacheEntry(merged, nowMs)
                return merged
            }
        }

        // 检查新鲜正缓存（仅在系统 DNS 未立刻返回 Fake-IP 且非健康路由时使用）
        cache[cleanHost]?.let { entry ->
            if (nowMs - entry.savedAtMs in 0..POSITIVE_CACHE_TTL_MS && entry.addresses.isNotEmpty()) {
                return entry.addresses
            }
        }

        // 系统 DNS 超时（>600ms）、污染（bogon）或 NXDOMAIN -> 触发 DoH + 延迟等待系统 DNS + 静态表
        val dohFuture = lookupExecutor.submit(
            Callable { runCatching { dohLookup(cleanHost) }.getOrDefault(emptyList()) },
        )
        val dohAddresses = filterValidRoutableAddresses(runCatching { dohFuture.get(2800, TimeUnit.MILLISECONDS) }.getOrDefault(emptyList()))
        if (dohAddresses.isNotEmpty()) {
            val merged = mergePreferred(cleanHost, dohAddresses)
            cache[cleanHost] = DnsCacheEntry(merged, nowMs)
            logSafe("DoH healed [$cleanHost] -> ${merged.joinToString { it.hostAddress ?: "" }}")
            return merged
        }

        // 若 DoH 受网络隔离，等待系统 DNS 最终完成
        val slowSystemResult = runCatching {
            if (!systemFuture.isDone) systemFuture.get(1800, TimeUnit.MILLISECONDS) else fastSystemResult
        }.getOrNull().orEmpty()
        if (slowSystemResult.any(::isTunFakeIp)) {
            return slowSystemResult
        }
        val cleanSlowSystem = filterValidRoutableAddresses(slowSystemResult)
        if (cleanSlowSystem.isNotEmpty()) {
            val merged = mergePreferred(cleanHost, cleanSlowSystem)
            cache[cleanHost] = DnsCacheEntry(merged, nowMs)
            return merged
        }

        // 静态兜底 IP 表（E-Hentai 等关键域名）
        val staticFallback = preferredAddressesForHost(cleanHost)
        if (staticFallback.isNotEmpty()) {
            cache[cleanHost] = DnsCacheEntry(staticFallback, nowMs)
            return staticFallback
        }

        // 陈旧缓存（1 小时内）兜底
        cache[cleanHost]?.takeIf { nowMs - it.savedAtMs <= STALE_CACHE_MAX_MS && it.addresses.isNotEmpty() }?.let {
            return it.addresses
        }

        throw UnknownHostException(hostname)
    }

    internal fun isTunFakeIp(address: InetAddress): Boolean {
        if (address !is Inet4Address) return false
        val bytes = address.address
        if (bytes.size != 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        return b0 == 198 && (b1 == 18 || b1 == 19)
    }

    internal fun isPoisonedOrBogon(address: InetAddress): Boolean {
        if (isTunFakeIp(address)) return false
        return address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress
    }

    internal fun filterValidRoutableAddresses(addresses: List<InetAddress>): List<InetAddress> =
        addresses.filterNot(::isPoisonedOrBogon).distinctBy { it.hostAddress }

    private fun preferredAddressesForHost(cleanHost: String): List<InetAddress> =
        preferredStaticAddresses[cleanHost].orEmpty().mapNotNull { ip ->
            runCatching { InetAddress.getByName(ip) }.getOrNull()
        }

    private fun mergePreferred(cleanHost: String, resolved: List<InetAddress>): List<InetAddress> {
        val preferred = preferredAddressesForHost(cleanHost)
        return (preferred + resolved).distinctBy { it.hostAddress }
    }

    private fun queryDohProviders(hostname: String): List<InetAddress> {
        val endpoints = listOf(
            "https://1.1.1.1/dns-query?name=$hostname&type=A",
            "https://223.5.5.5/resolve?name=$hostname&type=A",
        )
        for (url in endpoints) {
            val resolved = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/dns-json")
                    .build()
                dohHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<InetAddress>()
                    val raw = response.body?.string().orEmpty()
                    parseDohJsonIps(raw)
                }
            }.getOrDefault(emptyList())
            if (resolved.isNotEmpty()) return resolved
        }
        return emptyList()
    }

    internal fun parseDohJsonIps(jsonText: String): List<InetAddress> {
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return emptyList()
        val answers = root.optJSONArray("Answer") ?: return emptyList()
        val ips = mutableListOf<InetAddress>()
        for (i in 0 until answers.length()) {
            val ans = answers.optJSONObject(i) ?: continue
            // DNS Record Type 1 = A (IPv4), 28 = AAAA
            val type = ans.optInt("type", 0)
            if (type != 1 && type != 28) continue
            val data = ans.optString("data").trim()
            if (isIpLiteral(data)) {
                runCatching { InetAddress.getByName(data) }.getOrNull()?.let { addr ->
                    if (!isPoisonedOrBogon(addr)) ips += addr
                }
            }
        }
        return ips.distinctBy { it.hostAddress }
    }

    private fun isIpLiteral(value: String): Boolean {
        val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        return ipv4.matches(value) || value.contains(':')
    }

    private fun logSafe(msg: String) {
        runCatching { Log.i(TAG, msg) }
    }
}
