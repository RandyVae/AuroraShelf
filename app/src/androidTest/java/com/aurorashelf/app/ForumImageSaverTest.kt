package com.aurorashelf.app

import androidx.test.platform.app.InstrumentationRegistry
import com.aurorashelf.app.data.forum.ForumImageRequest
import com.aurorashelf.app.data.forum.ForumImageSaver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumImageSaverTest {
    @Test fun embeddedImageIsPublishedToGalleryWithoutBitmapConversion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val saver = ForumImageSaver(context)
        val result = saver.save(
            ForumImageRequest(
                imageUrl = ONE_PIXEL_PNG,
                pageUrl = "https://example.invalid/post",
                userAgent = "AuroraShelfTest",
                cookies = null,
            ),
        )
        assertTrue(result.isSuccess)
        val uri = result.getOrThrow()
        try {
            assertEquals("image/png", context.contentResolver.getType(uri))
            val signature = context.contentResolver.openInputStream(uri)?.use { input ->
                ByteArray(PNG_SIGNATURE.size).also { bytes -> input.read(bytes) }
            }
            assertArrayEquals(PNG_SIGNATURE, signature)
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        const val ONE_PIXEL_PNG =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
