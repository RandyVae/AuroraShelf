package com.aurorashelf.app.ui

import android.content.Context
import com.aurorashelf.app.data.VideoRepository
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request

/** Loads normal covers and decrypts Huangguo's public AES-wrapped cover bytes. */
internal object CoverImageLoader {
    private val HUANGGUO_CDN_HOSTS = setOf("pic.cuinhri.cn", "pic.zdmhyg.cn", "pic.fisawck.cn")
    private val KNOWN_ENCRYPTED_HOSTS = setOf("pic.cuinhri.cn", "pic.zdmhyg.cn", "pic.fisawck.cn", "pic.drleao.cn")
    private val KNOWN_ENCRYPTED_SITE_HOSTS = setOf("huangguoai.com", "91dongman.net", "91dongman.com")
    private val HUAVOD_REFERER_HOSTS = listOf(
        "huavod.com",
        "huarenlivewebsite.top",
        "hitv.com",
        "okokserver.com",
        "weserv.nl",
        "hhmage.com",
        "qpic.cn",
        "iqiyipic.com",
        "alicdn.com",
    )
    private val DEAD_COVER_HOSTS = listOf("dbzy.tv", "dbzy5.com")
    private val PLACEHOLDER_COVER_TOKENS = listOf(
        "tv_default_small",
        "tv_default_large",
        "movie_default_small",
        "movie_default_large",
        "/cuphead/movie-static/pics/",
    )
    private val DOUBAN_SMALL_TOKENS = listOf(
        "/view/photo/s_ratio_poster/",
        "/view/photo/m_ratio_poster/",
        "/view/photo/sqxs/",
        "/view/photo/ipst/",
        "/view/photo/thumb/",
        "/view/photo/small/",
    )
    private val DOUBAN_STATIC_COVERS = mapOf(
        "眩晕" to "https://img9.doubanio.com/view/photo/l_ratio_poster/public/p2932533736.jpg",
        "给阿嬷的情书" to "https://img3.doubanio.com/view/photo/l_ratio_poster/public/p2931851430.jpg",
        "地球超新鲜第二季" to "https://img3.doubanio.com/view/photo/l_ratio_poster/public/p2933553320.jpg",
        "地球超新鲜 第二季" to "https://img3.doubanio.com/view/photo/l_ratio_poster/public/p2933553320.jpg",
        "寻梦环游记" to "https://img1.doubanio.com/view/photo/l_ratio_poster/public/p2503997609.jpg",
        "疯狂动物城2" to "https://img9.doubanio.com/view/photo/l_ratio_poster/public/p2928373804.jpg",
        "霜花店" to "https://img9.doubanio.com/view/photo/l_ratio_poster/public/p453856276.jpg",
    )
    private const val DOUBAN_NEGATIVE_SENTINEL = "__NO_DOUBAN_POSTER__"
    private val doubanUrlCache = ConcurrentHashMap<String, String>()
    private val doubanClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    private const val HUANGGUO_HOST = "huangguoai.com"
    private const val CRYPTO_WORKER_PATH = "/static/web/js/plugins/crypto-worker.js"
    private const val CONFIG_TTL_MS = 15 * 60 * 1000L
    private val FALLBACK_CONFIG = CryptoConfig(
        mode = "CBC",
        key = "f5d965df75336270".toByteArray(Charsets.US_ASCII),
        iv = "97b60394abc2fbe1".toByteArray(Charsets.US_ASCII),
    )
    private val CONFIG_LOCK = Any()
    @Volatile private var cachedConfig: CryptoConfig? = null
    @Volatile private var cachedConfigAt = 0L

    /** Process-wide loader installed once by [initialize]; null until the Application starts. */
    @Volatile private var sharedLoader: CoverLoader? = null
    @Volatile private var initialized = false
    private val loaderLock = Any()

    /** Shared OkHttp client: connection pool + HTTP/2 reuse replace a connection per cover. */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()
    }

    /**
     * Installs the process-wide two-tier cache. Called once from Application.onCreate; cheap
     * (creates only File/Executor objects, the disk index is built lazily on a background thread).
     */
    fun initialize(context: Context) {
        synchronized(loaderLock) {
            if (initialized) return
            val cacheDirectory = File(context.applicationContext.cacheDir, CoverCache.CACHE_DIRECTORY_NAME)
            sharedLoader = CoverLoader(
                cache = CoverCache(cacheDirectory),
                fetcher = HttpCoverFetcher(),
                imageValidator = { looksLikeImage(it) },
            )
            initialized = true
        }
    }

    internal fun normalizeThumbnailUrl(url: String): String {
        var clean = url.trim()
        if (clean.contains("THUMBNUM")) {
            clean = clean.replace("THUMBNUM", "1")
        }
        return clean
    }

    internal fun upgradeCoverUrl(url: String): String? {
        val clean = normalizeThumbnailUrl(url)
        if (clean.isEmpty()) return null
        val lower = clean.lowercase(Locale.US)
        if (PLACEHOLDER_COVER_TOKENS.any { lower.contains(it) }) return null
        if (!lower.contains("doubanio.com") && !lower.contains("douban.com")) return clean
        var upgraded = clean
        for (token in DOUBAN_SMALL_TOKENS) {
            if (upgraded.contains(token, ignoreCase = true)) {
                upgraded = upgraded.replace(token, "/view/photo/l_ratio_poster/", ignoreCase = true)
            }
        }
        return upgraded
    }

    internal fun isDeadCoverHost(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return DEAD_COVER_HOSTS.any { lower.contains(it) }
    }

    internal fun quickDoubanLookup(title: String?): String? {
        val clean = normalizeSearchTitle(title.orEmpty())
        if (clean.isEmpty()) return null
        DOUBAN_STATIC_COVERS[title.orEmpty().trim()]?.let { return it }
        DOUBAN_STATIC_COVERS[clean]?.let { return it }
        val cached = doubanUrlCache[clean]
        return cached?.takeUnless { it == DOUBAN_NEGATIVE_SENTINEL }
    }

    private fun normalizeSearchTitle(raw: String): String =
        raw.trim()
            .replace(Regex("""\[[^\]]*\]|\([^)]*\)|【[^】]*】"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    internal fun resolveDoubanPosterUrl(title: String): String? {
        quickDoubanLookup(title)?.let { return it }
        val clean = normalizeSearchTitle(title)
        if (clean.length < 2) return null
        val cached = doubanUrlCache[clean]
        if (cached == DOUBAN_NEGATIVE_SENTINEL) return null
        if (cached != null) return cached

        val encoded = URLEncoder.encode(clean, "UTF-8")
        val request = Request.Builder()
            .url("https://movie.douban.com/j/subject_suggest?q=$encoded")
            .header("User-Agent", VideoRepository.USER_AGENT)
            .header("Referer", "https://movie.douban.com/")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        val resolved = runCatching {
            doubanClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                val itemRegex = Regex("""\{[^{}]*"img"\s*:\s*"([^"]+)"[^{}]*\}""")
                val typeRegex = Regex(""""type"\s*:\s*"([^"]+)"""")
                var fallbackUrl: String? = null
                for (match in itemRegex.findAll(body)) {
                    val rawImg = match.groupValues[1].replace("\\/", "/")
                    val candidate = upgradeCoverUrl(rawImg) ?: continue
                    val type = typeRegex.find(match.value)?.groupValues?.getOrNull(1).orEmpty()
                    if (type == "movie" || type == "tv") {
                        return@use candidate
                    }
                    if (fallbackUrl == null) fallbackUrl = candidate
                }
                fallbackUrl
            }
        }.getOrNull()

        doubanUrlCache[clean] = resolved ?: DOUBAN_NEGATIVE_SENTINEL
        return resolved
    }

    @JvmOverloads
    fun load(address: String, referer: String, title: String? = null): ByteArray? {
        val normalized = normalizeThumbnailUrl(address)
        val upgraded = upgradeCoverUrl(normalized)
        val isDeadOrPlaceholder = upgraded == null || isDeadCoverHost(normalized)

        if (isDeadOrPlaceholder && !title.isNullOrBlank()) {
            resolveDoubanPosterUrl(title)?.let { doubanUrl ->
                loader().load(doubanUrl, "https://movie.douban.com/")?.let { return it }
            }
        }

        val primaryUrl = upgraded ?: normalized
        if (primaryUrl.isNotBlank()) {
            loader().load(primaryUrl, referer)?.let { return it }
        }

        if (!title.isNullOrBlank() &&
            (referer.contains("huavod.com", ignoreCase = true) || isDeadOrPlaceholder)
        ) {
            resolveDoubanPosterUrl(title)?.let { doubanUrl ->
                if (doubanUrl != primaryUrl) {
                    loader().load(doubanUrl, "https://movie.douban.com/")?.let { return it }
                }
            }
        }
        return null
    }

    private fun loader(): CoverLoader = sharedLoader ?: synchronized(loaderLock) {
        sharedLoader ?: CoverLoader(
            cache = CoverCache(diskDirectory = null),
            fetcher = HttpCoverFetcher(),
            imageValidator = { looksLikeImage(it) },
        ).also { sharedLoader = it }
    }

    internal fun isHuangguoCover(address: String, referer: String? = null): Boolean = runCatching {
        val host = URI(address).host?.lowercase(Locale.US).orEmpty()
        val refererHost = URI(referer.orEmpty()).host?.lowercase(Locale.US).orEmpty()
        host in HUANGGUO_CDN_HOSTS || (refererHost == HUANGGUO_HOST && host != HUANGGUO_HOST)
    }.getOrDefault(false)

    internal fun isEncryptedCover(address: String, referer: String? = null): Boolean = runCatching {
        val host = URI(address).host?.lowercase(Locale.US).orEmpty()
        val refererHost = URI(referer.orEmpty()).host?.lowercase(Locale.US).orEmpty()
        host in KNOWN_ENCRYPTED_HOSTS ||
            host in KNOWN_ENCRYPTED_SITE_HOSTS ||
            refererHost in KNOWN_ENCRYPTED_SITE_HOSTS ||
            isHuangguoCover(address, referer)
    }.getOrDefault(false)

    internal fun requestOrigin(referer: String): Pair<String, String>? = runCatching {
        val uri = URI(referer)
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        val origin = "${uri.scheme}://${uri.host}${if (uri.port >= 0) ":${uri.port}" else ""}"
        referer to origin
    }.getOrNull()

    internal fun decryptPayloadIfNeeded(
        payload: ByteArray,
        address: String,
        referer: String? = null,
    ): ByteArray {
        if (looksLikeImage(payload)) return payload
        if (!isEncryptedCover(address, referer) && payload.size < AES_BLOCK_SIZE) return payload
        val huangguoCover = isHuangguoCover(address, referer)
        val cryptoConfig = if (huangguoCover) currentConfig(referer.orEmpty()) else FALLBACK_CONFIG
        return decryptIfNeeded(payload, cryptoConfig)
            ?: decryptIfNeeded(payload, FALLBACK_CONFIG)
            ?: payload
    }

    private fun decryptIfNeeded(payload: ByteArray, config: CryptoConfig): ByteArray? {
        // The CDN can return an ordinary image for cached/legacy covers. Only run AES
        // when the payload is not already a decodable image and has a valid block size.
        if (looksLikeImage(payload)) return payload
        if (payload.isEmpty() || payload.size % AES_BLOCK_SIZE != 0) return null
        val cipher = Cipher.getInstance("AES/${config.mode}/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(config.key, "AES"),
            IvParameterSpec(config.iv),
        )
        return cipher.doFinal(payload).takeIf(::looksLikeImage)
    }

    private fun currentConfig(referer: String): CryptoConfig {
        val now = System.currentTimeMillis()
        cachedConfig?.takeIf { now - cachedConfigAt < CONFIG_TTL_MS }?.let { return it }
        synchronized(CONFIG_LOCK) {
            val secondCheck = System.currentTimeMillis()
            cachedConfig?.takeIf { secondCheck - cachedConfigAt < CONFIG_TTL_MS }?.let { return it }
            val config = fetchConfig(referer) ?: FALLBACK_CONFIG
            cachedConfig = config
            cachedConfigAt = secondCheck
            return config
        }
    }

    private fun fetchConfig(referer: String): CryptoConfig? = runCatching {
        val refererUri = URI(referer)
        if (refererUri.host?.equals(HUANGGUO_HOST, ignoreCase = true) != true) return null
        val endpoint = URI(
            refererUri.scheme ?: "https",
            refererUri.rawUserInfo,
            refererUri.host,
            refererUri.port,
            CRYPTO_WORKER_PATH,
            null,
            null,
        ).toURL().toString()
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept-Encoding", "identity")
            .header("User-Agent", VideoRepository.USER_AGENT)
            .header("Referer", "${refererUri.scheme ?: "https"}://${refererUri.host}/")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            parseCryptoConfig(response.body?.string().orEmpty())
        }
    }.getOrNull()

    internal fun parseCryptoConfig(script: String): CryptoConfig? = runCatching {
        val mode = SCRIPT_VALUE.find(script, "mode") ?: return null
        val key = decodeScriptValue(SCRIPT_VALUE.find(script, "media_key") ?: return null)
        val iv = decodeScriptValue(SCRIPT_VALUE.find(script, "media_iv") ?: return null)
        if (!mode.equals("CBC", ignoreCase = true) || key.size !in setOf(16, 24, 32) || iv.size != AES_BLOCK_SIZE) {
            return null
        }
        CryptoConfig(mode.uppercase(Locale.US), key, iv)
    }.getOrNull()

    /** Downloads one cover, keeping the previous UA/Referer/Origin/Accept and AES semantics. */
    private fun download(address: String, referer: String): ByteArray? = runCatching {
        val normalizedAddress = normalizeThumbnailUrl(address)
        fetchCoverBytes(normalizedAddress, referer)?.let { return@runCatching it }
        missAvFallbackUrl(normalizedAddress)?.let { fallbackAddress ->
            fetchCoverBytes(fallbackAddress, "https://missav.media/")?.let { return@runCatching it }
        }
        null
    }.getOrNull()

    private fun fetchCoverBytes(normalizedAddress: String, referer: String): ByteArray? {
        val huangguoCover = isHuangguoCover(normalizedAddress, referer)
        val encryptedCover = isEncryptedCover(normalizedAddress, referer)
        val cryptoConfig = if (huangguoCover) currentConfig(referer) else FALLBACK_CONFIG
        val isMissAvCover = isMissAvCoverRequest(normalizedAddress, referer)
        val lowerAddr = normalizedAddress.lowercase(Locale.US)
        val lowerRef = referer.lowercase(Locale.US)
        val isDoubanCover = lowerAddr.contains("doubanio.com") || lowerAddr.contains("douban.com")
        val isHuavodCover = !isDoubanCover && (
            HUAVOD_REFERER_HOSTS.any { lowerAddr.contains(it) } ||
                lowerRef.contains("huavod.com")
            )

        val request = Request.Builder()
            .url(normalizedAddress)
            .header("Accept", ACCEPT_HEADER)
            .header("User-Agent", VideoRepository.USER_AGENT)
            .apply {
                requestOrigin(referer)?.let { (page, origin) ->
                    header("Referer", page)
                    header("Origin", origin)
                }
                if (huangguoCover) {
                    header("Referer", "https://huangguoai.com/")
                    header("Origin", "https://huangguoai.com")
                } else if (isDoubanCover) {
                    header("Referer", "https://movie.douban.com/")
                    removeHeader("Origin")
                } else if (isHuavodCover) {
                    header("Referer", "https://huavod.com/")
                    header("Origin", "https://huavod.com")
                } else if (referer.contains("91dongman") || normalizedAddress.contains("drleao.cn")) {
                    header("Referer", "https://91dongman.net/")
                    header("Origin", "https://91dongman.net")
                } else if (referer.contains("xvideos") || normalizedAddress.contains("xvideos")) {
                    header("Referer", "https://www.xvideos.com/")
                    header("Origin", "https://www.xvideos.com")
                } else if (isMissAvCover) {
                    header("Referer", "https://missav.media/")
                    header("Origin", "https://missav.media")
                }
            }
            .build()
        val primaryBytes = executeCoverRequest(request, cryptoConfig, encryptedCover)
        if (primaryBytes != null) return primaryBytes

        if (isHuavodCover) {
            val bareRequest = Request.Builder()
                .url(normalizedAddress)
                .header("Accept", ACCEPT_HEADER)
                .header("User-Agent", VideoRepository.USER_AGENT)
                .build()
            return executeCoverRequest(bareRequest, cryptoConfig, encryptedCover)
        }
        return null
    }

    private fun executeCoverRequest(
        request: Request,
        cryptoConfig: CryptoConfig,
        encryptedCover: Boolean,
    ): ByteArray? = httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@use null
        val bytes = response.body?.bytes() ?: return@use null
        if (looksLikeImage(bytes)) return@use bytes
        if (encryptedCover || bytes.size >= AES_BLOCK_SIZE) {
            decryptIfNeeded(bytes, cryptoConfig)
                ?: decryptIfNeeded(bytes, FALLBACK_CONFIG)
        } else {
            null
        }
    }

    internal fun isMissAvCoverRequest(address: String, referer: String): Boolean {
        val lowerAddr = address.lowercase(Locale.US)
        val lowerRef = referer.lowercase(Locale.US)
        return lowerAddr.contains("fourhoi") ||
            lowerAddr.contains("mrstcdn.store") ||
            lowerAddr.contains("missav") ||
            lowerRef.contains("missav")
    }

    internal fun missAvFallbackUrl(address: String): String? = runCatching {
        val uri = URI(address)
        val host = uri.host?.lowercase(Locale.US) ?: return@runCatching null
        val path = uri.rawPath.orEmpty()
        when {
            host == "fourhoi.com" || host.endsWith(".fourhoi.com") ->
                URI(uri.scheme, uri.userInfo, "fourhoi.mrstcdn.store", uri.port, uri.rawPath, uri.rawQuery, uri.rawFragment).toString()
            path.endsWith("/cover-n.jpg", ignoreCase = true) ->
                URI(uri.scheme, uri.userInfo, host, uri.port, path.dropLast("cover-n.jpg".length) + "cover-t.jpg", uri.rawQuery, uri.rawFragment).toString()
            path.endsWith("/cover-t.jpg", ignoreCase = true) ->
                URI(uri.scheme, uri.userInfo, host, uri.port, path.dropLast("cover-t.jpg".length) + "cover-n.jpg", uri.rawQuery, uri.rawFragment).toString()
            else -> null
        }
    }.getOrNull()

    private fun decodeScriptValue(value: String): ByteArray = value.split('_')
        .map { it.toInt().also { code -> require(code in 0..127) }.toChar() }
        .joinToString("")
        .toByteArray(Charsets.UTF_8)

    internal data class CryptoConfig(val mode: String, val key: ByteArray, val iv: ByteArray)

    private object SCRIPT_VALUE {
        private val pattern = Regex("\\b%s\\s*:\\s*[\\\"']([^\\\"']+)[\\\"']")

        fun find(script: String, name: String): String? = pattern.pattern.format(Regex.escape(name)).toRegex().find(script)?.groupValues?.getOrNull(1)
    }

    internal fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) return true
        if (bytes.size >= 4 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()) return true
        if (bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
            bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) return true
        return isIsoBmffImage(bytes)
    }

    /** AVIF/HEIC/HEIF are ISO-BMFF containers whose first box type is "ftyp" at offset 4. */
    private fun isIsoBmffImage(bytes: ByteArray): Boolean {
        if (bytes.size < ISO_BMFF_HEADER_SIZE) return false
        if (!bytes.copyOfRange(FTYP_BOX_OFFSET, FTYP_BOX_OFFSET + FTYP_BOX_SIZE).contentEquals(FTYP_BOX_TYPE)) return false
        val majorBrand = String(
            bytes,
            MAJOR_BRAND_OFFSET,
            MAJOR_BRAND_SIZE,
            Charsets.US_ASCII,
        ).trimEnd('\u0000')
        return majorBrand in ISO_BMFF_IMAGE_BRANDS
    }

    private const val AES_BLOCK_SIZE = 16
    private const val ISO_BMFF_HEADER_SIZE = 12
    private const val FTYP_BOX_OFFSET = 4
    private const val FTYP_BOX_SIZE = 4
    private const val MAJOR_BRAND_OFFSET = 8
    private const val MAJOR_BRAND_SIZE = 4
    private val FTYP_BOX_TYPE = "ftyp".toByteArray(Charsets.US_ASCII)
    private val ISO_BMFF_IMAGE_BRANDS = setOf("avif", "avis", "miaf", "heic", "heix", "hevc", "hevx", "mif1", "msf1")

    private class HttpCoverFetcher : CoverFetcher {
        override fun fetch(address: String, referer: String): ByteArray? = download(address, referer)
    }
}

/** Fetches cover bytes; injectable so the cache/single-flight logic is testable on the JVM. */
internal fun interface CoverFetcher {
    fun fetch(address: String, referer: String): ByteArray?
}

/**
 * Memory -> disk -> single-flight network loader with a 60 s in-memory negative cache for failures.
 *
 * Bytes are only cached when [imageValidator] accepts them: a truncated/foreign disk file is
 * dropped, and an HTTP-200 error page from a misbehaving CDN is passed to Coil as before but not
 * stored, so the UI retry path can still download a fresh copy. Failures are cached for
 * [negativeTtlMs] only, never forever; successful images have no artificial expiry.
 */
internal class CoverLoader(
    private val cache: CoverCache,
    private val fetcher: CoverFetcher,
    private val imageValidator: (ByteArray) -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val negativeTtlMs: Long = NEGATIVE_CACHE_TTL_MS,
) {
    private val flights = ConcurrentHashMap<String, Flight>()
    private val negativeUntil = ConcurrentHashMap<String, Long>()

    fun load(address: String, referer: String): ByteArray? {
        cache.memoryGet(address)?.let { return it }

        val now = clock()
        negativeUntil[address]?.let { blockedUntil ->
            if (blockedUntil > now) return null
            negativeUntil.remove(address, blockedUntil)
        }

        cache.diskGet(address)?.let { bytes ->
            if (imageValidator(bytes)) {
                cache.memoryPut(address, bytes)
                return bytes
            }
            // Corrupted/foreign file on disk: drop it and fall through to the network.
            cache.invalidate(address)
        }

        val flight = Flight()
        val inFlight = flights.putIfAbsent(address, flight)
        if (inFlight != null) {
            inFlight.await()
            return inFlight.result
        }
        var result: ByteArray? = null
        try {
            val fetched = runCatching { fetcher.fetch(address, referer) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
            result = fetched
            if (fetched != null) {
                negativeUntil.remove(address)
                // Never persist HTTP error bodies or unknown formats: keeps the retry path honest.
                if (imageValidator(fetched)) runCatching { cache.put(address, fetched) }
            } else {
                rememberNegative(address)
            }
        } finally {
            flight.result = result
            flights.remove(address, flight)
            flight.complete()
        }
        return result
    }

    private fun rememberNegative(address: String) {
        negativeUntil[address] = clock() + negativeTtlMs
        if (negativeUntil.size <= NEGATIVE_CACHE_MAX_ENTRIES) return
        val now = clock()
        for ((key, blockedUntil) in negativeUntil) {
            if (blockedUntil <= now) negativeUntil.remove(key, blockedUntil)
        }
        // Burst of failures inside one TTL window must not grow the map without bound.
        if (negativeUntil.size > NEGATIVE_CACHE_MAX_ENTRIES * 2) negativeUntil.clear()
    }

    private class Flight {
        private val latch = CountDownLatch(1)
        @Volatile var result: ByteArray? = null
        fun await() = latch.await()
        fun complete() = latch.countDown()
    }
}

private const val NEGATIVE_CACHE_TTL_MS = 60_000L
private const val NEGATIVE_CACHE_MAX_ENTRIES = 512
private const val ACCEPT_HEADER = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
