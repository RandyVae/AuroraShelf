package com.aurorashelf.app.data.forum

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cookie forwarding policy for forum images: a forum cookie may only be sent to the exact same
 * HTTPS host as the page that produced it. These are plain JVM tests; [shouldForwardCookies] must
 * never depend on Android APIs.
 */
class ForumImagePolicyTest {
    @Test
    fun sameHostWithDifferentPathForwardsCookies() {
        assertTrue(
            shouldForwardCookies(
                "https://forum.example.com/thread/1.html",
                "https://forum.example.com/attachments/cover.jpg",
            ),
        )
    }

    @Test
    fun hostComparisonIsCaseInsensitive() {
        assertTrue(
            shouldForwardCookies(
                "https://Forum.Example.COM/thread/1.html",
                "https://forum.example.com/attachments/cover.jpg",
            ),
        )
        assertTrue(
            shouldForwardCookies(
                "https://forum.example.com/thread/1.html",
                "https://FORUM.EXAMPLE.COM/attachments/cover.jpg",
            ),
        )
    }

    @Test
    fun differentHostIsRejected() {
        assertFalse(
            shouldForwardCookies(
                "https://forum.example.com/thread/1.html",
                "https://cdn.third-party.example/attachments/cover.jpg",
            ),
        )
    }

    @Test
    fun subdomainIsRejectedInBothDirections() {
        assertFalse(
            shouldForwardCookies(
                "https://forum.example.com/thread/1.html",
                "https://img.forum.example.com/attachments/cover.jpg",
            ),
        )
        assertFalse(
            shouldForwardCookies(
                "https://img.forum.example.com/thread/1.html",
                "https://forum.example.com/attachments/cover.jpg",
            ),
        )
    }

    @Test
    fun embeddedDataImageIsRejected() {
        assertFalse(
            shouldForwardCookies(
                "https://forum.example.com/thread/1.html",
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
            ),
        )
    }

    @Test
    fun plainHttpImageIsRejected() {
        assertFalse(
            shouldForwardCookies(
                "https://forum.example.com/thread/1.html",
                "http://forum.example.com/attachments/cover.jpg",
            ),
        )
    }

    @Test
    fun malformedOrRelativeUrlsAreRejected() {
        val pageUrl = "https://forum.example.com/thread/1.html"
        assertFalse(shouldForwardCookies(pageUrl, ""))
        assertFalse(shouldForwardCookies(pageUrl, "cover.jpg"))
        assertFalse(shouldForwardCookies(pageUrl, "/attachments/cover.jpg"))
        assertFalse(shouldForwardCookies(pageUrl, "//forum.example.com/attachments/cover.jpg"))
        assertFalse(shouldForwardCookies(pageUrl, "https://"))
        assertFalse(shouldForwardCookies(pageUrl, "https://forum.example.com/a b.jpg"))
        assertFalse(shouldForwardCookies("not a url", "https://forum.example.com/cover.jpg"))
    }

    @Test
    fun sameHostOnDifferentPortForwardsCookies() {
        // Deliberate policy: HTTP cookies are host-scoped, not port-scoped, so the port is ignored.
        assertTrue(
            shouldForwardCookies(
                "https://forum.example.com:443/thread/1.html",
                "https://forum.example.com:8443/attachments/cover.jpg",
            ),
        )
        assertTrue(
            shouldForwardCookies(
                "https://forum.example.com/thread/1.html",
                "https://forum.example.com:8443/attachments/cover.jpg",
            ),
        )
    }

    @Test
    fun plainHttpPageIsRejected() {
        assertFalse(
            shouldForwardCookies(
                "http://forum.example.com/thread/1.html",
                "https://forum.example.com/attachments/cover.jpg",
            ),
        )
    }

    @Test
    fun nonHttpsSchemesAreRejected() {
        val pageUrl = "https://forum.example.com/thread/1.html"
        assertFalse(shouldForwardCookies(pageUrl, "ftp://forum.example.com/cover.jpg"))
        assertFalse(shouldForwardCookies(pageUrl, "javascript:alert(1)"))
    }

    @Test
    fun userInfoCannotSpoofTheImageHost() {
        assertFalse(
            shouldForwardCookies(
                "https://forum.example.com/thread/1.html",
                "https://forum.example.com@evil.example/attachments/cover.jpg",
            ),
        )
    }
}
