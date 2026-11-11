package com.aurorashelf.app.data

import com.aurorashelf.app.model.LiveItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStreamResolverTest {
    private val resolver = LiveStreamResolver()

    @Test
    fun resolvesStripchatFromExistingHls() = runBlocking {
        val item = LiveItem(
            id = "123456",
            username = "test_girl",
            thumbnailUrl = "https://static.example/thumb.jpg",
            viewersCount = 100,
            countryCode = "US",
            isHd = true,
            playerUrl = "https://zh.stripchat.com/test_girl",
            hlsStreamUrl = "https://edge-hls.doppiocdn.media/hls/123456/master/123456_240p.m3u8",
        )
        val result = resolver.resolve(item)
        assertTrue(result is LiveStreamResolveResult.Success)
        val success = result as LiveStreamResolveResult.Success
        assertEquals("https://edge-hls.doppiocdn.media/hls/123456/master/123456_240p.m3u8", success.hlsUrl)
        assertEquals("https://zh.stripchat.com/", success.referer)
    }

    @Test
    fun constructsStripchatFromNumericIdWhenHlsMissing() = runBlocking {
        val item = LiveItem(
            id = "sc-248391444",
            username = "test_girl_2",
            thumbnailUrl = "https://static.example/thumb.jpg",
            viewersCount = 50,
            countryCode = "CN",
            isHd = true,
            playerUrl = "https://zh.stripchat.com/test_girl_2",
            hlsStreamUrl = null,
        )
        val result = resolver.resolve(item)
        assertTrue(result is LiveStreamResolveResult.Success)
        val success = result as LiveStreamResolveResult.Success
        assertEquals("https://edge-hls.doppiocdn.media/hls/248391444/master/248391444.m3u8", success.hlsUrl)
        assertEquals("https://zh.stripchat.com/", success.referer)
    }

    @Test
    fun handlesBlankUsernameGracefully() = runBlocking {
        val item = LiveItem(
            id = "hs-unknown",
            username = "",
            thumbnailUrl = "https://static.example/thumb.jpg",
            viewersCount = 0,
            countryCode = "",
            isHd = false,
            playerUrl = "https://www.hornystudents.com//",
        )
        val result = resolver.resolve(item)
        assertTrue(result is LiveStreamResolveResult.StatusError)
    }
}
