package com.aurorashelf.app.ui

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CoverImageLoaderTest {
    @Test
    fun recognizesCurrentAndLegacyHuangguoCoverHosts() {
        assertTrue(CoverImageLoader.isHuangguoCover("https://pic.zdmhyg.cn/upload/cover.jpg?auth_key=1"))
        assertTrue(CoverImageLoader.isHuangguoCover("https://pic.cuinhri.cn/upload/cover.jpg"))
        assertTrue(CoverImageLoader.isHuangguoCover("https://pic.fisawck.cn/upload_01/upload/cover.jpg?auth_key=1"))
        assertFalse(CoverImageLoader.isHuangguoCover("https://example.com/cover.jpg"))
    }

    @Test
    fun recognizesRotatedPicCdnOnlyForHuangguoReferer() {
        val currentCdn = "https://pic.rotated-example.cn/upload/cover.jpg"
        assertTrue(CoverImageLoader.isHuangguoCover(currentCdn, "https://huangguoai.com/detail/117/"))
        assertFalse(CoverImageLoader.isHuangguoCover(currentCdn, "https://example.com/video/1"))
    }

    @Test
    fun derivesRefererAndOriginForCoverRequests() {
        assertEquals(
            "https://example.com/video/1" to "https://example.com",
            CoverImageLoader.requestOrigin("https://example.com/video/1"),
        )
        assertEquals(
            "https://example.com:8443/video/1" to "https://example.com:8443",
            CoverImageLoader.requestOrigin("https://example.com:8443/video/1"),
        )
        assertNull(CoverImageLoader.requestOrigin("javascript:alert(1)"))
    }

    @Test
    fun parsesThePublicCryptoWorkerConfiguration() {
        val config = CoverImageLoader.parseCryptoConfig(
            """
            const i0 = {
                mode: "CBC",
                media_key: "102_53_100_57_54_53_100_102_55_53_51_51_54_50_55_48",
                media_iv: "57_55_98_54_48_51_57_52_97_98_99_50_102_98_101_49"
            };
            """.trimIndent(),
        )

        requireNotNull(config)
        assertEquals("CBC", config.mode)
        assertArrayEquals("f5d965df75336270".toByteArray(), config.key)
        assertArrayEquals("97b60394abc2fbe1".toByteArray(), config.iv)
    }

    @Test
    fun rejectsUnsupportedOrMalformedCryptoWorkerConfiguration() {
        assertNull(
            CoverImageLoader.parseCryptoConfig(
                "mode: 'CTR', media_key: '1_2_3', media_iv: '1_2_3'",
            ),
        )
        assertNull(CoverImageLoader.parseCryptoConfig("mode: 'CBC', media_key: 'not_numbers', media_iv: '1_2'"))
    }

    @Test
    fun recognizesAvifAndHeicIsoBmffImages() {
        for (brand in listOf("avif", "avis", "heic", "heix", "hevc", "hevx", "mif1", "msf1")) {
            assertTrue("major brand $brand should be treated as an image", CoverImageLoader.looksLikeImage(isoBmffImage(brand)))
        }
    }

    @Test
    fun rejectsCiphertextAndNonImageIsoBmff() {
        // Fixed seed keeps the sample deterministic while still being opaque AES-like bytes.
        val ciphertext = ByteArray(16).also { Random(20260910L).nextBytes(it) }
        assertFalse("AES ciphertext must not be treated as an image", CoverImageLoader.looksLikeImage(ciphertext))
        assertFalse("MP4 isom brand is not an image brand", CoverImageLoader.looksLikeImage(isoBmffImage("isom")))
        assertFalse("truncated ftyp box is not a complete image", CoverImageLoader.looksLikeImage(isoBmffImage("avif").copyOf(8)))
    }

    private fun isoBmffImage(majorBrand: String): ByteArray =
        byteArrayOf(0, 0, 0, 0x20) + "ftyp".toByteArray() + majorBrand.toByteArray() + ByteArray(20)
}
