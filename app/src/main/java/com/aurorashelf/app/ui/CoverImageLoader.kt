package com.aurorashelf.app.ui

import com.aurorashelf.app.data.VideoRepository
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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

    fun load(address: String, referer: String): ByteArray? = runCatching {
        val huangguoCover = isHuangguoCover(address, referer)
        val cryptoConfig = if (huangguoCover) currentConfig(referer) else null
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            connection.setRequestProperty("User-Agent", VideoRepository.USER_AGENT)
            requestOrigin(referer)?.let { (page, origin) ->
                connection.setRequestProperty("Referer", page)
                connection.setRequestProperty("Origin", origin)
            }
            if (huangguoCover) {
                connection.setRequestProperty("Referer", "https://huangguoai.com/")
                connection.setRequestProperty("Origin", "https://huangguoai.com")
            }
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { it.readBytes() }
            if (huangguoCover) decryptIfNeeded(bytes, cryptoConfig ?: FALLBACK_CONFIG) else bytes
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

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
        ).toURL()
        val connection = endpoint.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("User-Agent", VideoRepository.USER_AGENT)
            connection.setRequestProperty("Referer", "${refererUri.scheme ?: "https"}://${refererUri.host}/")
            if (connection.responseCode !in 200..299) return null
            val script = connection.inputStream.use { it.bufferedReader(Charsets.UTF_8).readText() }
            parseCryptoConfig(script)
        } finally {
            connection.disconnect()
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
}
