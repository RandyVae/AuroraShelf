package com.aurorashelf.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryManagerTest {

    @Test
    fun trackerMeasuresSectionDwellWithoutDoubleCountingAcrossHeartbeats() {
        var mono = 100.0
        var wall = 1795790000.0
        var seq = 0
        val tracker = TelemetrySessionTracker(
            monoClockSec = { mono },
            wallClockSec = { wall },
            idFactory = { "id-${++seq}" },
        )

        assertTrue(tracker.onForeground())
        assertFalse(tracker.onForeground())

        mono += 30.0
        wall += 30.0
        assertTrue(tracker.heartbeat())

        mono += 12.0
        wall += 12.0
        tracker.setSection("comics", "comic_read")

        mono += 18.0
        wall += 18.0
        tracker.onBackground()

        val batch = tracker.peekBatch(32)
        assertEquals(5, batch.size)
        assertEquals("session_start", batch[0].type)
        assertEquals("heartbeat", batch[1].type)
        assertEquals(30, batch[1].durationSec)
        assertEquals("section_dwell", batch[2].type)
        assertEquals("video", batch[2].section)
        assertEquals(12, batch[2].durationSec)
        assertEquals("section_dwell", batch[3].type)
        assertEquals("comics", batch[3].section)
        assertEquals("comic_read", batch[3].subSection)
        assertEquals(18, batch[3].durationSec)
        assertEquals("session_end", batch[4].type)

        val videoSum = batch.filter { it.section == "video" }.sumOf { it.durationSec }
        val comicsSum = batch.filter { it.section == "comics" }.sumOf { it.durationSec }
        assertEquals(42, videoSum)
        assertEquals(18, comicsSum)
    }

    @Test
    fun trackerCapsQueueAtMaxSizeAndRejectsUnknownSections() {
        var mono = 0.0
        var wall = 1795790000.0
        var seq = 0
        val tracker = TelemetrySessionTracker(
            monoClockSec = { mono },
            wallClockSec = { wall },
            idFactory = { "q-${++seq}" },
            maxQueueSize = 5,
        )

        tracker.onForeground()
        tracker.setSection("unknown_tab", "bad")
        for (i in 1..6) {
            mono += 5.0
            wall += 5.0
            tracker.heartbeat()
        }

        val queue = tracker.snapshotQueue()
        assertEquals(5, queue.size)
        assertTrue(queue.all { it.section == "video" })
    }

    @Test
    fun payloadSerializationAndQueueRoundTripAreR8Safe() {
        val ev = TelemetryEvent(
            eventId = "ev-test-1",
            type = "section_dwell",
            sessionId = "sess-test-1",
            section = "game",
            subSection = "game_play",
            durationSec = 45,
            ts = 1795790000.0,
        )
        val payload = TelemetryManager.buildBatchPayload(
            installId = "and-install-1",
            deviceForm = "phone",
            installChannel = "apk",
            account = AccountProfile(
                id = "u1",
                email = "randy@example.com",
                displayName = "Randy",
                isVerified = true,
            ),
            events = listOf(ev),
        )
        assertEquals("and-install-1", payload.getString("install_id"))
        assertEquals("android", payload.getString("platform"))
        assertEquals("randy@example.com", payload.getString("account_email"))

        val decoded = TelemetryManager.decodeQueue(JSONArray().put(ev.toJson()).toString())
        assertEquals(1, decoded.size)
        assertEquals(ev, decoded.first())
    }
}
