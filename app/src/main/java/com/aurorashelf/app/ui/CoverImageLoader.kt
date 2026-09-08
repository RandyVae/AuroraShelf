package com.aurorashelf.app.ui

import com.aurorashelf.app.data.VideoRepository
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Loads normal covers and decrypts Huangguo's public AES-wrapped cover bytes. */
internal object CoverImageLoader {
    private val HUANGGUO_CDN_HOSTS = setOf("pic.cuinhri.cn", "pic.zdmhyg.cn")
    private val MEDIA_KEY = "f5d965df75336270".toByteArray(Charsets.US_ASCII)
    private val MEDIA_IV = "97b60394abc2fbe1".toByteArray(Charsets.US_ASCII)

    fun load(address: String, referer: String): ByteArray? = runCatching {
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
            if (isHuangguoCover(address)) {
                connection.setRequestProperty("Referer", "https://huangguoai.com/")
                connection.setRequestProperty("Origin", "https://huangguoai.com")
            }
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { it.readBytes() }
            if (isHuangguoCover(address)) decryptIfNeeded(bytes) else bytes
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    internal fun isHuangguoCover(address: String): Boolean = runCatching {
        URI(address).host?.lowercase() in HUANGGUO_CDN_HOSTS
    }.getOrDefault(false)

    internal fun requestOrigin(referer: String): Pair<String, String>? = runCatching {
        val uri = URI(referer)
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        val origin = "${uri.scheme}://${uri.host}${if (uri.port >= 0) ":${uri.port}" else ""}"
        referer to origin
    }.getOrNull()

    private fun decryptIfNeeded(payload: ByteArray): ByteArray? {
        // The CDN can return an ordinary image for cached/legacy covers. Only run AES
        // when the payload is not already a decodable image and has a valid block size.
        if (looksLikeImage(payload)) return payload
        if (payload.isEmpty() || payload.size % AES_BLOCK_SIZE != 0) return null
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(MEDIA_KEY, "AES"),
            IvParameterSpec(MEDIA_IV),
        )
        return cipher.doFinal(payload).takeIf(::looksLikeImage)
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) return true
        if (bytes.size >= 6 && bytes.copyOfRange(0, 6).contentEquals("GIF8".toByteArray())) return true
        return bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
            bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
    }

    private const val AES_BLOCK_SIZE = 16
}
