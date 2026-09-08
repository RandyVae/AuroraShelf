package com.aurorashelf.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.size.Size
import com.aurorashelf.app.ui.JmUnscrambleTransformation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JmUnscrambleTransformationTest {
    @Test
    fun reversesUnevenVerticalSegmentsWithoutGaps() = runBlocking {
        val expectedRows = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA)
        val input = Bitmap.createBitmap(2, 5, Bitmap.Config.ARGB_8888).apply {
            val scrambledRows = intArrayOf(expectedRows[3], expectedRows[4], expectedRows[0], expectedRows[1], expectedRows[2])
            scrambledRows.forEachIndexed { y, color ->
                setPixel(0, y, color)
                setPixel(1, y, color)
            }
        }

        val output = JmUnscrambleTransformation(segments = 2).transform(input, Size.ORIGINAL)

        expectedRows.forEachIndexed { y, color ->
            assertEquals("row $y", color, output.getPixel(0, y))
            assertEquals("row $y", color, output.getPixel(1, y))
        }
    }
}
