package com.aurorashelf.app.data.resilience

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

/**
 * 端侧源站与 CDN 健康度注册中心（持久化 EWMA 延迟、熔断冷却与动态镜像发现）。
 *
 * 设计要点：
 * 1. 所有方法在无 Android Context 的纯 JVM 单元测试环境下也可安全运行；
 * 2. 结合内置镜像池 + 远端零单点清单（RemoteSourceManifest）+ 重定向/门户发现域名；
 * 3. 对慢节点或故障节点按指数退避自动熔断（30s -> 60s -> 120s -> 300s），避免拖垮首屏加载。
 */
internal class SourceHealthRegistryStore(
    private val clock: () -> Long = System::currentTimeMillis,
    private val prefsProvider: () -> SharedPreferences? = { null },
) {
    internal data class HostHealth(
        val host: String,
        val ewmaLatencyMs: Double = DEFAULT_UNPROBED_EWMA_MS,
        val consecutiveFailures: Int = 0,
        val cooldownUntilMs: Long = 0L,
        val totalSuccesses: Int = 0,
        val totalFailures: Int = 0,
        val lastStatusCode: Int = 0,
        val updatedAtMs: Long = 0L,
    )

    private val lock = Any()
    private val healthByHost = ConcurrentHashMap<String, HostHealth>()
    private val learnedMirrorsBySource = ConcurrentHashMap<String, LinkedHashSet<String>>()
    private val manifestMirrorsBySource = ConcurrentHashMap<String, List<String>>()

    @Volatile
    private var loadedFromPrefs = false

    fun loadPersistedIfNeeded() {
        if (loadedFromPrefs) return
        val prefs = prefsProvider() ?: return
        synchronized(lock) {
            if (loadedFromPrefs) return
            loadedFromPrefs = true
            runCatching {
                val healthRaw = prefs.getString(KEY_HEALTH_JSON, null)
                if (!healthRaw.isNullOrBlank()) {
                    val obj = JSONObject(healthRaw)
                    obj.keys().forEach { host ->
                        val item = obj.optJSONObject(host) ?: return@forEach
                        healthByHost[host.lowercase()] = HostHealth(
                            host = host.lowercase(),
                            ewmaLatencyMs = item.optDouble("ewma", DEFAULT_UNPROBED_EWMA_MS),
                            consecutiveFailures = item.optInt("fail", 0),
                            cooldownUntilMs = item.optLong("cooldown", 0L),
                            totalSuccesses = item.optInt("ok", 0),
                            totalFailures = item.optInt("err", 0),
                            lastStatusCode = item.optInt("code", 0),
                            updatedAtMs = item.optLong("ts", 0L),
                        )
                    }
                }
                val mirrorRaw = prefs.getString(KEY_MIRRORS_JSON, null)
                if (!mirrorRaw.isNullOrBlank()) {
                    val obj = JSONObject(mirrorRaw)
                    obj.keys().forEach { key ->
                        val arr = obj.optJSONArray(key) ?: return@forEach
                        val set = LinkedHashSet<String>()
                        for (i in 0 until arr.length()) {
                            arr.optString(i).trim().takeIf { it.startsWith("http") }?.let(set::add)
                        }
                        if (set.isNotEmpty()) {
                            learnedMirrorsBySource[key] = set
                        }
                    }
                }
            }
        }
    }

    private fun persistAsync() {
        val prefs = prefsProvider() ?: return
        val healthJson = JSONObject()
        healthByHost.entries.take(MAX_PERSISTED_HOSTS).forEach { (host, h) ->
            healthJson.put(
                host,
                JSONObject().apply {
                    put("ewma", h.ewmaLatencyMs)
                    put("fail", h.consecutiveFailures)
                    put("cooldown", h.cooldownUntilMs)
                    put("ok", h.totalSuccesses)
                    put("err", h.totalFailures)
                    put("code", h.lastStatusCode)
                    put("ts", h.updatedAtMs)
                },
            )
        }
        val mirrorJson = JSONObject()
        learnedMirrorsBySource.forEach { (key, set) ->
            val arr = JSONArray()
            synchronized(set) { set.take(MAX_MIRRORS_PER_SOURCE).forEach(arr::put) }
            mirrorJson.put(key, arr)
        }
        runCatching {
            prefs.edit {
                putString(KEY_HEALTH_JSON, healthJson.toString())
                putString(KEY_MIRRORS_JSON, mirrorJson.toString())
            }
        }
    }

    fun applyManifestMirrors(mirrorsBySource: Map<String, List<String>>) {
        loadPersistedIfNeeded()
        mirrorsBySource.forEach { (key, origins) ->
            val clean = origins.mapNotNull(::normalizeOrigin).distinct()
            if (clean.isNotEmpty()) {
                manifestMirrorsBySource[key] = clean
            }
        }
    }

    fun registerLearnedMirror(sourceKey: String, originUrl: String) {
        val clean = normalizeOrigin(originUrl) ?: return
        loadPersistedIfNeeded()
        val set = learnedMirrorsBySource.computeIfAbsent(sourceKey) { LinkedHashSet() }
        val changed = synchronized(set) {
            if (set.contains(clean)) {
                false
            } else {
                // 最新自动发现的镜像置于队首（保留有限个数防止膨胀）
                val existing = set.toList()
                set.clear()
                set.add(clean)
                existing.take(MAX_MIRRORS_PER_SOURCE - 1).forEach(set::add)
                true
            }
        }
        if (changed) {
            logSafe("learned mirror [$sourceKey] -> $clean")
            persistAsync()
        }
    }

    /**
     * 汇总某数据源的所有候选基础 Origin：
     * 已学习活跃镜像 + 内置镜像池 + 远端热更新清单 + 默认值（去重）。
     */
    fun resolveOrigins(sourceKey: String, defaults: List<String> = emptyList()): List<String> {
        loadPersistedIfNeeded()
        val merged = LinkedHashSet<String>()
        learnedMirrorsBySource[sourceKey]?.let { set ->
            synchronized(set) { set.forEach(merged::add) }
        }
        defaults.mapNotNull(::normalizeOrigin).forEach(merged::add)
        BUILT_IN_MIRRORS[sourceKey].orEmpty().mapNotNull(::normalizeOrigin).forEach(merged::add)
        manifestMirrorsBySource[sourceKey].orEmpty().mapNotNull(::normalizeOrigin).forEach(merged::add)
        return rankCandidates(sourceKey, merged.toList())
    }

    /**
     * 将给定 URL 的 Host/Origin 自动扩展为完整的镜像候选 URL 列表（按健康度排序）。
     */
    fun expandCandidateUrls(sourceKey: String?, requestUrl: String): List<String> {
        if (sourceKey.isNullOrBlank()) return listOf(requestUrl)
        val parsed = runCatching { URI(requestUrl) }.getOrNull() ?: return listOf(requestUrl)
        val requestOrigin = "${parsed.scheme ?: "https"}://${parsed.authority}".trimEnd('/')
        val origins = resolveOrigins(sourceKey, listOf(requestOrigin))
        if (origins.isEmpty()) return listOf(requestUrl)

        val suffix = buildString {
            append(parsed.rawPath?.takeIf { it.isNotEmpty() } ?: "/")
            if (!parsed.rawQuery.isNullOrBlank()) {
                append('?')
                append(parsed.rawQuery)
            }
            if (!parsed.rawFragment.isNullOrBlank()) {
                append('#')
                append(parsed.rawFragment)
            }
        }
        return origins.map { origin ->
            "${origin.trimEnd('/')}$suffix"
        }.distinct()
    }

    /**
     * 按健康状态与 EWMA 延迟排序候选 URL/Origin。
     * 规则：未在熔断期的节点优先，按得分排序（未探测节点保有适度先验分以免快镜像被饿死）；
     * 若全部节点都在熔断期，则按最早到期时间兜底保留，永不返回空列表。
     */
    fun rankCandidates(sourceKey: String?, candidates: List<String>): List<String> {
        if (candidates.size <= 1) return candidates
        loadPersistedIfNeeded()
        val now = clock()
        val indexed = candidates.distinct().mapIndexed { index, raw ->
            val host = extractHost(raw)
            val health = host?.let { healthByHost[it] }
            val coolingDown = health != null && health.cooldownUntilMs > now && health.consecutiveFailures > 0
            val score = if (health == null || health.totalSuccesses == 0) {
                // 未实测节点保持原有顺序先验（#0 = 320ms, #1 = 345ms ...）
                320.0 + index * 25.0
            } else {
                health.ewmaLatencyMs + health.consecutiveFailures * 350.0
            }
            RankedCandidate(raw = raw, index = index, coolingDown = coolingDown, score = score, cooldownUntilMs = health?.cooldownUntilMs ?: 0L)
        }
        val healthy = indexed.filterNot { it.coolingDown }.sortedWith(compareBy<RankedCandidate> { it.score }.thenBy { it.index })
        val broken = indexed.filter { it.coolingDown }.sortedWith(compareBy<RankedCandidate> { it.cooldownUntilMs }.thenBy { it.score })
        return (healthy + broken).map { it.raw }
    }

    fun recordSuccess(
        sourceKey: String?,
        urlOrHost: String,
        latencyMs: Long,
        finalRedirectUrl: String? = null,
    ) {
        loadPersistedIfNeeded()
        val now = clock()
        val host = extractHost(urlOrHost)
        val clampedLatency = latencyMs.coerceIn(5L, 25_000L).toDouble()
        if (host != null) {
            healthByHost.compute(host) { _, prev ->
                val newEwma = if (prev == null || prev.totalSuccesses == 0) {
                    clampedLatency
                } else {
                    EWMA_ALPHA * clampedLatency + (1.0 - EWMA_ALPHA) * prev.ewmaLatencyMs
                }
                HostHealth(
                    host = host,
                    ewmaLatencyMs = newEwma,
                    consecutiveFailures = 0,
                    cooldownUntilMs = 0L,
                    totalSuccesses = (prev?.totalSuccesses ?: 0) + 1,
                    totalFailures = prev?.totalFailures ?: 0,
                    lastStatusCode = 200,
                    updatedAtMs = now,
                )
            }
        }
        if (!sourceKey.isNullOrBlank() && !finalRedirectUrl.isNullOrBlank()) {
            val origHost = host
            val finalHost = extractHost(finalRedirectUrl)
            val finalOrigin = normalizeOrigin(finalRedirectUrl)
            if (origHost != null && finalHost != null && origHost != finalHost && finalOrigin != null &&
                isPlausibleSourceMirror(sourceKey, origHost, finalHost)
            ) {
                registerLearnedMirror(sourceKey, finalOrigin)
                // 同时记录最终重定向宿主健康分
                healthByHost.compute(finalHost) { _, prev ->
                    HostHealth(
                        host = finalHost,
                        ewmaLatencyMs = if (prev == null || prev.totalSuccesses == 0) clampedLatency else EWMA_ALPHA * clampedLatency + (1.0 - EWMA_ALPHA) * prev.ewmaLatencyMs,
                        consecutiveFailures = 0,
                        cooldownUntilMs = 0L,
                        totalSuccesses = (prev?.totalSuccesses ?: 0) + 1,
                        totalFailures = prev?.totalFailures ?: 0,
                        lastStatusCode = 200,
                        updatedAtMs = now,
                    )
                }
            }
        }
        persistAsync()
    }

    fun recordFailure(
        sourceKey: String?,
        urlOrHost: String,
        statusCode: Int = 0,
        isHardBlock: Boolean = false,
    ) {
        loadPersistedIfNeeded()
        val now = clock()
        val host = extractHost(urlOrHost) ?: return
        var openedCooldownMs = 0L
        healthByHost.compute(host) { _, prev ->
            val failures = (prev?.consecutiveFailures ?: 0) + 1
            val shouldOpen = failures >= CIRCUIT_FAILURE_THRESHOLD || isHardBlock || statusCode in listOf(403, 502, 503, 504)
            val cooldownMs = if (shouldOpen) {
                val shift = (failures - 1).coerceIn(0, 3)
                min(MAX_COOLDOWN_MS, BASE_COOLDOWN_MS * (1L shl shift))
            } else {
                prev?.cooldownUntilMs?.let { (it - now).coerceAtLeast(0L) } ?: 0L
            }
            openedCooldownMs = cooldownMs
            HostHealth(
                host = host,
                ewmaLatencyMs = (prev?.ewmaLatencyMs ?: DEFAULT_UNPROBED_EWMA_MS) + 400.0,
                consecutiveFailures = failures,
                cooldownUntilMs = if (cooldownMs > 0L) now + cooldownMs else 0L,
                totalSuccesses = prev?.totalSuccesses ?: 0,
                totalFailures = (prev?.totalFailures ?: 0) + 1,
                lastStatusCode = statusCode,
                updatedAtMs = now,
            )
        }
        if (openedCooldownMs > 0L) {
            logSafe("circuit open [$sourceKey] host=$host status=$statusCode cooldown=${openedCooldownMs / 1000}s")
        }
        persistAsync()
    }

    fun isCircuitOpen(urlOrHost: String): Boolean {
        val host = extractHost(urlOrHost) ?: return false
        val health = healthByHost[host] ?: return false
        return health.cooldownUntilMs > clock() && health.consecutiveFailures > 0
    }

    fun snapshotHealth(urlOrHost: String): HostHealth? {
        val host = extractHost(urlOrHost) ?: return null
        return healthByHost[host]
    }

    fun telemetrySummary(): String {
        val now = clock()
        val tracked = healthByHost.values.sortedByDescending { it.updatedAtMs }.take(12)
        val openCircuits = healthByHost.values.count { it.cooldownUntilMs > now && it.consecutiveFailures > 0 }
        val hostsText = tracked.joinToString("; ") { h ->
            val state = if (h.cooldownUntilMs > now) "OPEN" else "OK"
            "${h.host}:${h.ewmaLatencyMs.toInt()}ms($state,ok=${h.totalSuccesses},err=${h.totalFailures})"
        }
        return "tracked=${healthByHost.size} openCircuits=$openCircuits [$hostsText]"
    }

    fun clearAllForTesting() {
        healthByHost.clear()
        learnedMirrorsBySource.clear()
        manifestMirrorsBySource.clear()
        prefsProvider()?.edit(commit = true) { clear() }
    }

    private data class RankedCandidate(
        val raw: String,
        val index: Int,
        val coolingDown: Boolean,
        val score: Double,
        val cooldownUntilMs: Long,
    )

    companion object {
        private const val TAG = "AuroraHealth"
        private const val EWMA_ALPHA = 0.30
        private const val DEFAULT_UNPROBED_EWMA_MS = 450.0
        private const val CIRCUIT_FAILURE_THRESHOLD = 2
        private const val BASE_COOLDOWN_MS = 30_000L
        private const val MAX_COOLDOWN_MS = 300_000L
        private const val MAX_PERSISTED_HOSTS = 64
        private const val MAX_MIRRORS_PER_SOURCE = 8
        private const val KEY_HEALTH_JSON = "host_health_v1"
        private const val KEY_MIRRORS_JSON = "learned_mirrors_v1"

        internal val BUILT_IN_MIRRORS: Map<String, List<String>> = mapOf(
            "copy" to listOf("https://api.mangacopy.com", "https://api.2024manga.com"),
            "91porn" to listOf("https://h1014.sol148.com", "https://91porn.com"),
            "pornhub" to listOf("https://cn.pornhub.com", "https://jp.pornhub.com", "https://www.pornhub.com"),
            "missav" to listOf("https://missav.media", "https://missav123.com", "https://missav.ws", "https://missav.ai"),
            "jm" to listOf("https://www.cdnhjk.net", "https://www.cdngwc.net", "https://www.cdngwc.club", "https://www.cdngwc.cc"),
            "t906" to listOf("https://t924.sol140.com", "https://t925.sol140.com", "https://t906.abc2507.cc"),
            "baozi" to listOf("https://cn.webmota.com", "https://cn.twbzmg.com", "https://www.baozimh.com"),
            "baozi-cdn" to listOf("https://s1.baozicdn.com", "https://s2.baozicdn.com", "https://static-tw.baozimh.com", "https://s1.bzcdn.net", "https://s2.bzcdn.net"),
            "jm-cdn" to listOf("https://cdn-msp.jmapiproxy2.cc", "https://cdn-msp2.jmapiproxy2.cc", "https://cdn-msp3.jmapiproxy2.cc", "https://cdn-msp.jmapinodeudzn.net", "https://cdn-msp.jmapiproxy1.cc"),
        )

        internal fun normalizeOrigin(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
            val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
            val scheme = (uri.scheme?.lowercase() ?: "https").takeIf { it == "http" || it == "https" } ?: return null
            val portPart = if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            return "$scheme://$host$portPart"
        }

        internal fun extractHost(urlOrHost: String): String? {
            val trimmed = urlOrHost.trim()
            if (trimmed.isEmpty()) return null
            if (!trimmed.contains("://") && !trimmed.contains('/')) {
                return trimmed.substringBefore(':').lowercase().takeIf { it.isNotBlank() }
            }
            return runCatching { URI(trimmed).host?.lowercase() }.getOrNull()?.takeIf { it.isNotBlank() }
        }

        private fun isPlausibleSourceMirror(sourceKey: String, origHost: String, finalHost: String): Boolean {
            if (finalHost.contains("cloudflare") || finalHost.contains("challenge")) return false
            val keyword = when (sourceKey) {
                "missav" -> "missav"
                "pornhub" -> "pornhub"
                "91porn" -> "91"
                "t906" -> "sol"
                "copy" -> "manga"
                "baozi" -> "webmota"
                else -> origHost.substringBefore('.').takeIf { it.length >= 4 } ?: return false
            }
            return finalHost.contains(keyword) || finalHost.endsWith(origHost.substringAfter('.', origHost))
        }

        private fun logSafe(msg: String) {
            runCatching { Log.i(TAG, msg) }
        }
    }
}

internal object SourceHealthRegistry {
    @Volatile
    private var appContext: Context? = null

    private val defaultStore: SourceHealthRegistryStore by lazy {
        SourceHealthRegistryStore(
            prefsProvider = {
                appContext?.getSharedPreferences("aurora_source_health", Context.MODE_PRIVATE)
            },
        )
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        defaultStore.loadPersistedIfNeeded()
    }

    fun applyManifestMirrors(mirrorsBySource: Map<String, List<String>>) =
        defaultStore.applyManifestMirrors(mirrorsBySource)

    fun registerLearnedMirror(sourceKey: String, originUrl: String) =
        defaultStore.registerLearnedMirror(sourceKey, originUrl)

    fun resolveOrigins(sourceKey: String, defaults: List<String> = emptyList()): List<String> =
        defaultStore.resolveOrigins(sourceKey, defaults)

    fun expandCandidateUrls(sourceKey: String?, requestUrl: String): List<String> =
        defaultStore.expandCandidateUrls(sourceKey, requestUrl)

    fun rankCandidates(sourceKey: String?, candidates: List<String>): List<String> =
        defaultStore.rankCandidates(sourceKey, candidates)

    fun recordSuccess(sourceKey: String?, urlOrHost: String, latencyMs: Long, finalRedirectUrl: String? = null) =
        defaultStore.recordSuccess(sourceKey, urlOrHost, latencyMs, finalRedirectUrl)

    fun recordFailure(sourceKey: String?, urlOrHost: String, statusCode: Int = 0, isHardBlock: Boolean = false) =
        defaultStore.recordFailure(sourceKey, urlOrHost, statusCode, isHardBlock)

    fun isCircuitOpen(urlOrHost: String): Boolean = defaultStore.isCircuitOpen(urlOrHost)

    fun telemetrySummary(): String = defaultStore.telemetrySummary()

    fun clearAllForTesting() = defaultStore.clearAllForTesting()

    internal fun sourceKeyForUrl(url: String): String? {
        val host = SourceHealthRegistryStore.extractHost(url) ?: return null
        return when {
            host == "h1014.sol148.com" || host == "91porn.com" || host.endsWith(".sol148.com") -> "91porn"
            host.endsWith("pornhub.com") -> "pornhub"
            host.contains("missav") -> "missav"
            host.contains("2024manga") || host.contains("mangacopy") -> "copy"
            host.contains("cdnhjk") || host.contains("cdngwc") -> "jm"
            host.contains("sol140.com") || host.contains("abc2507") -> "t906"
            host.contains("webmota") || host.contains("twbzmg") || host.contains("baozimh") -> "baozi"
            else -> null
        }
    }
}
