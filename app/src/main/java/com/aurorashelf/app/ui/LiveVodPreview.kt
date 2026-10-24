package com.aurorashelf.app.ui

import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.model.LiveItem
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import java.net.URI

/**
 * VOD cover preview for live rooms.
 *
 * The public "hlsPlaylist" of a room is a short (about 20 s) snapshot, not a live feed, so it
 * cannot replace the live player. It is still useful as a cover: pre-warm it muted while the live
 * grid is visible, then paint it instantly when the user taps the card while the real live page
 * keeps loading behind. When the live WebView reports ready the preview is removed and the live
 * stream takes over seamlessly.
 */
@OptIn(UnstableApi::class)
internal object LiveVodPreview {
    private var player: ExoPlayer? = null
    private var url: String? = null
    private var taken = false

    fun ensure(context: android.content.Context, item: LiveItem) {
        val playlist = item.hlsUrl ?: return
        if (player != null && url == playlist && !taken) return
        release()
        val referer = "${com.aurorashelf.app.data.StripchatLiveRepository.SITE_BASE}/${item.username}"
        val origin = runCatching {
            val uri = URI(referer)
            "${uri.scheme}://${uri.host}"
        }.getOrDefault("https://zh.stripchat.com")
        val dataSource = DefaultHttpDataSource.Factory()
            .setUserAgent(VideoRepository.USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to referer, "Origin" to origin))
        runCatching {
            val exo = ExoPlayer.Builder(context.applicationContext).build()
            exo.setVolume(0f)
            exo.repeatMode = Player.REPEAT_MODE_ONE
            exo.setMediaSource(
                HlsMediaSource.Factory(dataSource)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(MediaItem.fromUri(playlist)),
            )
            exo.prepare()
            exo.playWhenReady = true
            player = exo
            url = playlist
            taken = false
            Log.i(TAG, "prewarming cover host=${runCatching { URI(playlist).host }.getOrNull()}")
        }.onFailure {
            Log.w(TAG, "cover preview unavailable: ${it.message}")
            release()
        }
    }

    /** Hands the warm player to the player screen (muted); returns null if another room is warm. */
    fun take(playlist: String?): ExoPlayer? {
        if (playlist == null || url != playlist) return null
        val exo = player ?: return null
        taken = true
        player = null
        url = null
        exo.setVolume(0f)
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
        exo.playWhenReady = true
        return exo
    }

    fun release() {
        player?.release()
        player = null
        url = null
        taken = false
    }

    private const val TAG = "LiveVodPreview"
}

/** Renders the warm cover player full-bleed; controls belong to the live player that replaces it. */
@OptIn(UnstableApi::class)
@Composable
internal fun VodCoverPlayer(player: ExoPlayer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier = modifier.background(AuroraImmersiveBackground)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
}
