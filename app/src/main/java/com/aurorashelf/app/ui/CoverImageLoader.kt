package com.aurorashelf.app.ui

import android.content.Context
import com.aurorashelf.app.data.VideoRepository
import java.io.File
import java.net.URI
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

    fun load(address: String, referer: String): ByteArray? = loader().load(address, referer)

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

    internal fun requestOrigin(referer: String): Pair<String, String>? = runCatching {
        val uri = URI(referer)
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        val origin = "${uri.scheme}://${uri.host}${if (uri.port >= 0) ":${uri.port}" else ""}"
        referer to origin
    }.getOrNull()

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
        val huangguoCover = isHuangguoCover(address, referer)
        val cryptoConfig = if (huangguoCover) currentConfig(referer) else null
        val request = Request.Builder()
            .url(address)
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
                }
            }
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val bytes = response.body?.bytes() ?: return@use null
            if (huangguoCover) decryptIfNeeded(bytes, cryptoConfig ?: FALLBACK_CONFIG) else bytes
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
        if (bytes.size >= 6 && bytes.copyOfRange(0, 6).contentEquals("GIF8".toByteArray())) return true
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
    private val ISO_BMFF_IMAGE_BRANDS = setOf("avif", "avis", "heic", "heix", "hevc", "hevx", "mif1", "msf1")

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
