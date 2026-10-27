package com.aurorashelf.app.cast

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaSoapTest {

    @Test
    fun formatRelTime_formatsZeroAndPositiveDurations() {
        assertEquals("00:00:00", DlnaSoap.formatRelTime(0L))
        assertEquals("00:00:00", DlnaSoap.formatRelTime(-500L))
        assertEquals("00:01:05", DlnaSoap.formatRelTime(65_000L))
        assertEquals("01:01:01", DlnaSoap.formatRelTime(3_661_000L))
        assertEquals("12:34:56", DlnaSoap.formatRelTime(((12 * 3600) + (34 * 60) + 56) * 1000L))
    }

    @Test
    fun parseRelTime_parsesValidTimeStrings() {
        assertEquals(0L, DlnaSoap.parseRelTime("00:00:00"))
        assertEquals(65_000L, DlnaSoap.parseRelTime("00:01:05"))
        assertEquals(3_661_000L, DlnaSoap.parseRelTime("01:01:01"))
        assertEquals(3_661_000L, DlnaSoap.parseRelTime("01:01:01.450"))
        assertEquals(0L, DlnaSoap.parseRelTime("invalid"))
        assertEquals(0L, DlnaSoap.parseRelTime(""))
    }

    @Test
    fun escapeXml_escapesSpecialCharacters() {
        val input = """<test title="Tom & Jerry's \"Show\"">"""
        val expected = """&lt;test title=&quot;Tom &amp; Jerry&apos;s \&quot;Show\&quot;&quot;&gt;"""
        assertEquals(expected, DlnaSoap.escapeXml(input))
    }

    @Test
    fun parseTagValue_extractsXmlValues() {
        val xml = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <TrackDuration>00:45:30</TrackDuration>
                  <RelTime>00:12:34</RelTime>
                </u:GetPositionInfoResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        assertEquals("00:45:30", DlnaSoap.parseTagValue(xml, "TrackDuration"))
        assertEquals("00:12:34", DlnaSoap.parseTagValue(xml, "RelTime"))
        assertNull(DlnaSoap.parseTagValue(xml, "NonExistentTag"))
    }

    @Test
    fun buildSetAvTransportUriXml_containsRequiredEnvelopeAndMetadata() {
        val mediaUrl = "http://192.168.1.50:8080/stream?url=https%3A%2F%2Fcdn.example.com%2Fvideo.mp4"
        val title = "Sample Video <Episode 1>"
        val xml = DlnaSoap.buildSetAvTransportUriXml(mediaUrl, title)

        assertTrue(xml.contains("SetAVTransportURI"))
        assertTrue(xml.contains("http://192.168.1.50:8080/stream?url=https%3A%2F%2Fcdn.example.com%2Fvideo.mp4"))
        assertTrue(xml.contains("&lt;dc:title&gt;Sample Video &amp;lt;Episode 1&amp;gt;&lt;/dc:title&gt;"))
        assertTrue(xml.contains("video/mp4"))
    }

    @Test
    fun buildSetAvTransportUriXml_hlsMimeType_detectsRawAndEncodedM3u8() {
        val hlsUrl = "http://192.168.1.50:8080/playlist.m3u8"
        val xml = DlnaSoap.buildSetAvTransportUriXml(hlsUrl, "Live Stream")
        assertTrue(xml.contains("application/vnd.apple.mpegurl"))

        val encodedProxyHlsUrl =
            "http://192.168.1.50:8080/stream?token=abc&url=https%3A%2F%2Fcdn.example.com%2Fmaster%2Em3u8&ref=https%3A%2F%2Fexample.com"
        val encodedXml = DlnaSoap.buildSetAvTransportUriXml(encodedProxyHlsUrl, "Proxied HLS")
        assertTrue(encodedXml.contains("application/vnd.apple.mpegurl"))
    }

    @Test
    fun buildSeekXml_containsTargetTime() {
        val xml = DlnaSoap.buildSeekXml(125_000L) // 2 min 5 sec
        assertTrue(xml.contains("<Target>00:02:05</Target>"))
        assertTrue(xml.contains("<Unit>REL_TIME</Unit>"))
    }

    @Test
    fun buildControlXml_playPauseStop() {
        val playXml = DlnaSoap.buildPlayXml()
        assertTrue(playXml.contains("<u:Play"))
        assertTrue(playXml.contains("<Speed>1</Speed>"))

        val pauseXml = DlnaSoap.buildPauseXml()
        assertTrue(pauseXml.contains("<u:Pause"))

        val stopXml = DlnaSoap.buildStopXml()
        assertTrue(stopXml.contains("<u:Stop"))
    }

    @Test
    fun localMediaProxy_rewritesHlsManifestSegmentsAndKeyUris() {
        val manifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-KEY:METHOD=AES-128,URI="keys/enc.key"
            #EXTINF:10.0,
            seg_001.ts
            #EXTINF:10.0,
            https://cdn.example.com/hls/seg_002.ts
        """.trimIndent()

        val rewritten = LocalMediaProxy.rewriteHlsManifest(
            manifest = manifest,
            baseUri = "https://cdn.example.com/hls/index.m3u8?sig=1",
            proxyBaseUrl = "http://192.168.1.10:9000",
            token = "tok123",
            referer = "https://site.example.com/",
        )

        assertTrue(rewritten.contains("""URI="http://192.168.1.10:9000/stream?token=tok123&url=https%3A%2F%2Fcdn.example.com%2Fhls%2Fkeys%2Fenc.key&ref=https%3A%2F%2Fsite.example.com%2F""""))
        assertTrue(rewritten.contains("http://192.168.1.10:9000/stream?token=tok123&url=https%3A%2F%2Fcdn.example.com%2Fhls%2Fseg_001.ts&ref=https%3A%2F%2Fsite.example.com%2F"))
        assertTrue(rewritten.contains("http://192.168.1.10:9000/stream?token=tok123&url=https%3A%2F%2Fcdn.example.com%2Fhls%2Fseg_002.ts&ref=https%3A%2F%2Fsite.example.com%2F"))
    }

    @Test
    fun localMediaProxy_fileSandboxingAndRemoteFallback() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "aurora_proxy_test_${System.nanoTime()}")
        val allowedMediaDir = File(tempDir, "cache/offline_media").apply { mkdirs() }
        val prefsDir = File(tempDir, "shared_prefs").apply { mkdirs() }
        val validMp4 = File(allowedMediaDir, "video.mp4").apply { writeBytes(ByteArray(16) { it.toByte() }) }
        val secretXml = File(prefsDir, "aurora_shelf.xml").apply { writeText("<map><string name=\"secret\">1</string></map>") }

        try {
            assertTrue(LocalMediaProxy.isAllowedLocalFile(validMp4, listOf(allowedMediaDir)))
            assertFalse(LocalMediaProxy.isAllowedLocalFile(secretXml, listOf(allowedMediaDir)))
            assertFalse(LocalMediaProxy.isAllowedLocalFile(secretXml, emptyList()))

            // Remote URL passed to buildLocalFileProxyUrl must transparently route to /stream instead of /file
            val proxy = LocalMediaProxy(allowedDirs = listOf(allowedMediaDir), sessionToken = "secToken")
            val remoteRouted = proxy.buildLocalFileProxyUrl(
                filePath = "https://cdn.example.com/cached_remote.mp4",
                referer = "https://example.com/",
                cacheKey = "offline-123",
                localIpOverride = "127.0.0.1",
            )
            assertNotNull(remoteRouted)
            assertTrue(remoteRouted!!.contains("/stream?token=secToken"))
            assertTrue(remoteRouted.contains("key=offline-123"))
            proxy.stop()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun localMediaProxy_servesOfflineCacheSliceAndRejectsInvalidToken() {
        val fakeData = "0123456789ABCDEF".toByteArray()
        val proxy = LocalMediaProxy(
            sessionToken = "validToken",
            cacheStreamOpener = { cacheKey, _, _, start, endInclusive ->
                if (cacheKey == "offline-demo") {
                    val end = (endInclusive ?: (fakeData.size - 1L)).toInt().coerceAtMost(fakeData.size - 1)
                    val sliceBytes = fakeData.copyOfRange(start.toInt(), end + 1)
                    var pos = 0
                    CachedStreamSlice(
                        totalLength = fakeData.size.toLong(),
                        contentLength = sliceBytes.size.toLong(),
                        read = { buf, off, len ->
                            if (pos >= sliceBytes.size) -1
                            else {
                                val count = minOf(len, sliceBytes.size - pos)
                                System.arraycopy(sliceBytes, pos, buf, off, count)
                                pos += count
                                count
                            }
                        },
                        close = {},
                    )
                } else null
            },
        )
        try {
            val streamUrl = proxy.buildProxyUrl(
                videoUrl = "https://cdn.example.com/movie.mp4",
                referer = "https://example.com",
                cacheKey = "offline-demo",
                localIpOverride = "127.0.0.1",
            )!!

            // 1. Valid request with Range header returns HTTP 206 Partial Content from cache slice
            val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Range", "bytes=4-9")
            }
            assertEquals(206, conn.responseCode)
            assertEquals("bytes 4-9/16", conn.getHeaderField("Content-Range"))
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            assertEquals("456789", body)

            // 2. Request with invalid token returns HTTP 403 Forbidden
            val tamperedUrl = streamUrl.replace("token=validToken", "token=wrongToken")
            val badConn = URL(tamperedUrl).openConnection() as HttpURLConnection
            assertEquals(403, badConn.responseCode)
        } finally {
            proxy.stop()
        }
    }
}
