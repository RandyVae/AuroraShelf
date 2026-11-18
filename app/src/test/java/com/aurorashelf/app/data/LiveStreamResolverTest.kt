package com.aurorashelf.app.data

import com.aurorashelf.app.model.LiveItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStreamResolverTest {
    private val resolver = LiveStreamResolver()

    @Test
    fun resolvesStripchatFromExistingHlsAndUpgradesToAutoWithPkey() = runBlocking {
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
        val expectedKey = LiveStreamResolver.currentStripchatPkey()
        assertEquals(
            "https://edge-hls.doppiocdn.media/hls/123456/master/123456_auto.m3u8?pkey=$expectedKey",
            success.hlsUrl,
        )
        assertEquals("https://zh.stripchat.com/", success.referer)
    }

    @Test
    fun constructsStripchatAutoMasterFromNumericIdWhenHlsMissing() = runBlocking {
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
        val expectedKey = LiveStreamResolver.currentStripchatPkey()
        assertEquals(
            "https://edge-hls.doppiocdn.media/hls/248391444/master/248391444_auto.m3u8?pkey=$expectedKey",
            success.hlsUrl,
        )
        assertEquals("https://zh.stripchat.com/", success.referer)
    }

    @Test
    fun refreshesPkeyWhenMouflonAdvertDetectedAndRecoversNativeStream() = runBlocking {
        val probes = mutableListOf<String>()
        val verifyingResolver = LiveStreamResolver(
            verifyConstructedStream = true,
            streamProbe = { url, _ ->
                probes.add(url)
                if (url.contains("pkey=fresh_key_999")) {
                    StreamProbeOutcome.LIVE_MANIFEST
                } else {
                    StreamProbeOutcome.MOUFLON_LOCKED
                }
            },
            pkeyRefresher = { "fresh_key_999" },
        )
        val item = LiveItem(
            id = "83306615",
            username = "live_room",
            thumbnailUrl = "https://static.example/thumb.jpg",
            viewersCount = 200,
            countryCode = "JP",
            isHd = true,
            playerUrl = "https://zh.stripchat.com/live_room",
            hlsStreamUrl = "https://edge-hls.doppiocdn.media/hls/83306615/master/83306615_240p.m3u8",
        )
        val result = verifyingResolver.resolve(item)
        assertTrue(result is LiveStreamResolveResult.Success)
        assertEquals(2, probes.size)
        assertTrue((result as LiveStreamResolveResult.Success).hlsUrl.endsWith("83306615_auto.m3u8?pkey=fresh_key_999"))
    }

    @Test
    fun fallsBackToWebWhenMouflonRemainsLockedAfterKeyRefresh() = runBlocking {
        val lockedResolver = LiveStreamResolver(
            verifyConstructedStream = true,
            streamProbe = { _, _ -> StreamProbeOutcome.MOUFLON_LOCKED },
            pkeyRefresher = { null },
        )
        val item = LiveItem(
            id = "83306615",
            username = "locked_room",
            thumbnailUrl = "https://static.example/thumb.jpg",
            viewersCount = 80,
            countryCode = "US",
            isHd = true,
            playerUrl = "https://zh.stripchat.com/locked_room",
            hlsStreamUrl = "https://edge-hls.doppiocdn.media/hls/83306615/master/83306615_auto.m3u8",
        )
        val result = lockedResolver.resolve(item)
        assertTrue(result is LiveStreamResolveResult.Failure)
    }

    @Test
    fun extractsMmpBundleAndPkeyFromStripchatPayloads() {
        val html = """{"MMPExternalUnitedSourceOrigin":"https:\u002F\u002Fmmp.doppiocdn.com\u002Fplayer\u002Fmmp","playerModuleExternalLoading":{"mmpVersion":"v2.16.1"}}"""
        assertEquals(
            "https://mmp.doppiocdn.com/player/mmp/v2.16.1/main.js",
            LiveStreamResolver.extractMmpBundleUrlFromHtml(html),
        )
        val js = """function Sr(e){const t=new URL(e);return t.searchParams.set("pkey","B0p93vi8Uj6AYyZb"),t.toString()}"""
        assertEquals("B0p93vi8Uj6AYyZb", LiveStreamResolver.extractPkeyFromMmpJs(js))
        assertTrue(LiveStreamResolver.isMouflonLockedPlaylist("#EXTM3U\n#EXT-X-MOUFLON-ADVERT\n"))
        assertTrue(LiveStreamResolver.isMouflonLockedPlaylist("#EXTM3U\n#EXT-X-MOUFLON:FILE:abc\n"))
        assertFalse(LiveStreamResolver.isMouflonLockedPlaylist("#EXTM3U\n#EXTINF:2.000\nhttps://media-hls.doppiocdn.media/seg.mp4"))
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

    @Test
    fun unescapesUnicodeSequencesCorrectly() {
        val raw = """\u0022hls_source\u0022: \u0022https:\/\/edge4\u002Dsin.live.mmcdn.com\/v1\/llhls.m3u8?token\u003Dabc123\u0026session\u003Dxyz\u0022"""
        val unescaped = resolver.unescapeUnicode(raw)
        assertEquals(
            """"hls_source": "https://edge4-sin.live.mmcdn.com/v1/llhls.m3u8?token=abc123&session=xyz"""",
            unescaped,
        )
    }
}
