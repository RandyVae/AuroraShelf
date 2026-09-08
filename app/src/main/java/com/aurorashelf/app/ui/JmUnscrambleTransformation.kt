package com.aurorashelf.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation

internal class JmUnscrambleTransformation(
    private val segments: Int,
) : Transformation() {
    override val cacheKey: String = "jm-unscramble-$segments"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (segments <= 1) return input
        val output = createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val remainder = input.height % segments
        val segmentHeight = input.height / segments
        repeat(segments) { index ->
            val sourceTop = input.height - segmentHeight * (index + 1) - remainder
            val height = segmentHeight + if (index == 0) remainder else 0
            val destinationTop = segmentHeight * index + if (index == 0) 0 else remainder
            canvas.drawBitmap(
                input,
                Rect(0, sourceTop, input.width, sourceTop + height),
                Rect(0, destinationTop, input.width, destinationTop + height),
                null,
            )
        }
        return output
    }
}
