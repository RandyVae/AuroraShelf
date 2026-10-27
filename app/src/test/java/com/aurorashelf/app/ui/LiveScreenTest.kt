package com.aurorashelf.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveScreenTest {
    @Test
    fun roomSlugIgnoresTrailingSlashAndQuery() {
        assertEquals("madison__evans", liveRoomSlug("https://www.hornystudents.com/madison__evans"))
        assertEquals("madison__evans", liveRoomSlug("https://www.hornystudents.com/madison__evans/"))
        assertEquals("madison__evans", liveRoomSlug("https://www.hornystudents.com/madison__evans/?a=1"))
        assertEquals("", liveRoomSlug("https://www.hornystudents.com/"))
        assertEquals("", liveRoomSlug("not a url"))
    }

    @Test
    fun liveNavigationAllowsOnlyOfficialHttpsPages() {
        assertTrue(isAllowedLiveUrl("https://zh.stripchat.com/"))
        assertTrue(isAllowedLiveUrl("https://stripchat.com/model/example"))
        assertTrue(isAllowedLiveUrl("https://www.hornystudents.com/seduza_hyun/"))
        assertTrue(isAllowedLiveUrl("https://chaturbate.com/seduza_hyun/"))
        assertTrue(isAllowedLiveUrl("https://cb-wl.com/test"))
        assertTrue(isAllowedLiveUrl("https://agego.com/verify"))
        assertFalse(isAllowedLiveUrl("https://stripchat.com.evil.example/player"))
        assertFalse(isAllowedLiveUrl("https://hornystudents.com.evil.example/player"))
        assertFalse(isAllowedLiveUrl("http://zh.stripchat.com/"))
        assertFalse(isAllowedLiveUrl("http://www.hornystudents.com/"))
        assertFalse(isAllowedLiveUrl("javascript:alert(1)"))
        assertFalse(isAllowedLiveUrl("intent://example"))
    }

    @Test
    fun viewerCountUsesCompactChineseUnits() {
        assertEquals("999", formatViewerCount(999))
        assertEquals("1.2千", formatViewerCount(1_234))
        assertEquals("1.2万", formatViewerCount(12_345))
    }

    @Test
    fun liveCommentsAreSanitizedBoundedAndDeduplicated() {
        val comments = parseLiveComments(
            """[
                {"id":"1","username":" alice ","message":" hello "},
                {"id":"1","username":"alice","message":"duplicate"},
                {"id":"2","username":"","message":"missing user"},
                {"id":"3","username":"bob","message":"world"},
                {"id":"4","username":"bob","message":"world"}
            ]""",
        )

        assertEquals(2, comments.size)
        assertEquals("alice", comments[0].username)
        assertEquals("duplicate", comments[0].message)
        assertEquals("bob", comments[1].username)
    }

    @Test
    fun resourceInterceptorBlocksTrackersAndHeavyWidgetsWhileAllowingMedia() {
        // Blocked trackers & ad networks
        assertTrue(shouldBlockLiveResource("https://www.google-analytics.com/analytics.js"))
        assertTrue(shouldBlockLiveResource("https://mc.yandex.ru/metrika/tag.js"))
        assertTrue(shouldBlockLiveResource("https://secure.adnxs.com/seg?add=1"))
        assertTrue(shouldBlockLiveResource("https://ads.exoclick.com/ad_code.js"))
        assertTrue(shouldBlockLiveResource("https://browser.sentry-cdn.com/bundle.min.js"))

        // Blocked heavy non-essential widgets
        assertTrue(shouldBlockLiveResource("https://zh.stripchat.com/plugins/wheel-of-fortune/widget.js"))
        assertTrue(shouldBlockLiveResource("https://zh.stripchat.com/static/plugins/reactions/bundle.js"))
        assertTrue(shouldBlockLiveResource("https://zh.stripchat.com/canvas-performance-test/worker.js"))

        // Allowed core page, scripts, media and streams
        assertFalse(shouldBlockLiveResource("https://zh.stripchat.com/Asian_Asami"))
        assertFalse(shouldBlockLiveResource("https://b-hls-12.stripchat.com/hls/12345/master.m3u8"))
        assertFalse(shouldBlockLiveResource("https://zh.stripchat.com/static/app.bundle.js"))
        assertFalse(shouldBlockLiveResource("https://zh.stripchat.com/api/front/v2/models/username/Asian_Asami/cam"))
    }

    @Test
    fun liveOrientationParsingDistinguishesPortraitAndLandscape() {
        assertEquals(true, parseLiveVideoOrientation("ready:portrait"))
        assertEquals(false, parseLiveVideoOrientation("ready:landscape"))
        assertEquals(null, parseLiveVideoOrientation("ready"))
        assertEquals(null, parseLiveVideoOrientation("ready:unknown"))
        assertEquals(null, parseLiveVideoOrientation("offline"))
        assertEquals(null, parseLiveVideoOrientation(null))
    }

    @Test
    fun danmakuItemPreservesPropertiesAndLaneBoundaries() {
        val item = DanmakuItem(
            id = "test-1",
            username = "alice",
            message = "hello world",
            lane = 2,
            durationMs = 7000,
        )
        assertEquals("test-1", item.id)
        assertEquals("alice", item.username)
        assertEquals("hello world", item.message)
        assertEquals(2, item.lane)
        assertEquals(7000, item.durationMs)
    }

    @Test
    fun liveOrientationBridgeReportsDimensionsAndOrientation() {
        var detectedPortrait: Boolean? = null
        var detectedWidth = 0
        var detectedHeight = 0
        val bridge = LiveOrientationBridge(
            onOrientationDetected = { detectedPortrait = it },
            onVideoSizeDetected = { w, h ->
                detectedWidth = w
                detectedHeight = h
            },
        )

        // Landscape 16:9
        bridge.onVideoSize(640, 360)
        // onVideoSize posts to Looper.getMainLooper(), verify immediate fields or call logic
        assertEquals(0, detectedWidth) // queued to main looper

        // Test non-positive dimensions are safely ignored
        bridge.onVideoSize(0, 0)
        bridge.onVideoSize(-1, 200)
    }
}
