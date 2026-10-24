package com.aurorashelf.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveScreenTest {
    @Test
    fun liveNavigationAllowsOnlyOfficialHttpsPages() {
        assertTrue(isAllowedLiveUrl("https://zh.stripchat.com/"))
        assertTrue(isAllowedLiveUrl("https://stripchat.com/model/example"))
        assertFalse(isAllowedLiveUrl("https://stripchat.com.evil.example/player"))
        assertFalse(isAllowedLiveUrl("http://zh.stripchat.com/"))
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
}
