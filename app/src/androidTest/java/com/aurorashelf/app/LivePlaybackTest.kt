package com.aurorashelf.app

import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.data.PlaybackResolver
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.model.FeedCategory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Optional end-to-end network check. Remote titles and media addresses are never logged. */
@androidx.annotation.OptIn(UnstableApi::class)
class LivePlaybackTest {
    @Test fun huangguoDetailExposesEpisodeSelection() {
        if (InstrumentationRegistry.getArguments().getString("live") != "true") return
        runBlocking {
            val episodes = PlaybackResolver().resolveEpisodes("https://huangguoai.com/detail/117/")
            assertTrue("Expected Huangguo detail page to expose multiple episodes", episodes.size > 1)
            assertTrue("Expected episode links to point at playback pages", episodes.all { it.pageUrl.contains("/video/117/") })
            assertTrue("Expected episode numbering to be ordered", episodes.zipWithNext().all { (a, b) -> a.number < b.number })
        }
    }

    @Test fun huangguoPlaybackReachesReady() {
        if (InstrumentationRegistry.getArguments().getString("live") != "true") return
        runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val source = PlaybackResolver().resolve("https://huangguoai.com/video/117/ep-2/")
        val ready = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException?>()
        val position = AtomicLong(0L)
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(VideoRepository.USER_AGENT)
                .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
                .setDefaultRequestProperties(mapOf("Referer" to source.referer, "Origin" to "https://huangguoai.com"))
            player = ExoPlayer.Builder(context).build().apply {
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready.countDown() }
                    override fun onPlayerError(error: PlaybackException) { failure.set(error); ready.countDown() }
                })
                setMediaSource(androidx.media3.exoplayer.hls.HlsMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(source.url)))
                playWhenReady = true
                prepare()
            }
        }
        try {
            assertTrue("Expected Huangguo HLS to become ready", ready.await(40, TimeUnit.SECONDS))
            assertNull("Huangguo playback failed with code ${failure.get()?.errorCode}", failure.get())
            val hasSelectedVideoTrack = AtomicReference(false)
            instrumentation.runOnMainSync {
                hasSelectedVideoTrack.set(
                    player.currentTracks.groups.any { group ->
                        group.type == C.TRACK_TYPE_VIDEO && group.isSelected
                    },
                )
            }
            assertTrue("Expected Huangguo episode 2 to select a video track", hasSelectedVideoTrack.get())
            repeat(20) {
                instrumentation.runOnMainSync { position.set(player.currentPosition) }
                if (position.get() > 0L) return@repeat
                Thread.sleep(250)
            }
            assertTrue("Expected Huangguo playback position to advance", position.get() > 0L)
        } finally { instrumentation.runOnMainSync { player.release() } }
        }
    }

    @Test fun firstLiveRecordReachesPlayback() {
        if (InstrumentationRegistry.getArguments().getString("live") != "true") return
        runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val record = VideoRepository()
            .loadFeed(AppPreferences.DEFAULT_SOURCE, FeedCategory.MONTHLY_HOT, page = 1)
            .first()
        val source = PlaybackResolver().resolve(record.pageUrl)
        val ready = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException?>()
        val position = AtomicLong(0L)
        lateinit var player: ExoPlayer

        instrumentation.runOnMainSync {
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(VideoRepository.USER_AGENT)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setDefaultRequestProperties(mapOf("Referer" to source.referer))
            player = ExoPlayer.Builder(context).build().apply {
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) ready.countDown()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        failure.set(error)
                        ready.countDown()
                    }
                })
                setMediaSource(DefaultMediaSourceFactory(http).createMediaSource(MediaItem.fromUri(source.url)))
                playWhenReady = true
                prepare()
            }
        }

        try {
            assertTrue("Expected the live media to become ready", ready.await(40, TimeUnit.SECONDS))
            assertNull("Playback failed with code ${failure.get()?.errorCode}", failure.get())
            repeat(20) {
                instrumentation.runOnMainSync { position.set(player.currentPosition) }
                if (position.get() > 0L) return@repeat
                Thread.sleep(250)
            }
            assertTrue("Expected playback position to advance", position.get() > 0L)
            android.util.Log.i("LivePlaybackTest", "Verified ready playback with advancing position; content omitted")
        } finally {
            instrumentation.runOnMainSync { player.release() }
        }
        }
    }
}
