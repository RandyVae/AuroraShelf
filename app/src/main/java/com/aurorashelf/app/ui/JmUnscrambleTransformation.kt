package com.aurorashelf.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation

internal class JmUnscrambleTransformation(
    private val segments: Int,
) : Transformation() {
    override val cacheKey: String = "jm-unscramble-$segments-v2"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (segments <= 1) return input
        val srcW = input.width
        val srcH = input.height
        if (srcW <= 0 || srcH <= 0) return input

        // Cap output dimensions within Android hardware-accelerated Canvas texture limits (4096 px)
        // and screen width (1440 px) so ultra-tall Webtoon strips never trigger GPU texture crash or OOM.
        val scale = minOf(
            1f,
            MAX_TEXTURE_WIDTH.toFloat() / srcW.toFloat(),
            MAX_TEXTURE_HEIGHT.toFloat() / srcH.toFloat(),
        )
        val outW = (srcW * scale).toInt().coerceAtLeast(1)
        val outH = (srcH * scale).toInt().coerceAtLeast(1)
        val config = if (outW.toLong() * outH.toLong() > 3_500_000L && !input.hasAlpha()) {
            Bitmap.Config.RGB_565
        } else {
            input.config ?: Bitmap.Config.ARGB_8888
        }

        val output = createBitmap(outW, outH, config)
        val canvas = Canvas(output)
        val paint = if (scale < 1f) Paint(Paint.FILTER_BITMAP_FLAG) else null
        val remainder = srcH % segments
        val segmentHeight = srcH / segments
        repeat(segments) { index ->
            val sourceTop = srcH - segmentHeight * (index + 1) - remainder
            val height = segmentHeight + if (index == 0) remainder else 0
            val destinationTop = segmentHeight * index + if (index == 0) 0 else remainder
            if (scale == 1f) {
                canvas.drawBitmap(
                    input,
                    Rect(0, sourceTop, srcW, sourceTop + height),
                    Rect(0, destinationTop, outW, destinationTop + height),
                    null,
                )
            } else {
                canvas.drawBitmap(
                    input,
                    Rect(0, sourceTop, srcW, sourceTop + height),
                    RectF(
                        0f,
                        destinationTop * scale,
                        outW.toFloat(),
                        (destinationTop + height) * scale,
                    ),
                    paint,
                )
            }
        }
        return output
    }

    internal companion object {
        internal const val MAX_TEXTURE_WIDTH = 1440
        internal const val MAX_TEXTURE_HEIGHT = 4096
    }
}
