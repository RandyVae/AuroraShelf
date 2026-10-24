package com.aurorashelf.app.ui

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Executor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverCacheTest {
    private val directExecutor = Executor { command -> command.run() }

    @Test
    fun putStoresInMemoryAndOnDisk() {
        val directory = Files.createTempDirectory("cover-cache").toFile()
        try {
            val cache = newCache(directory)
            val bytes = jpegBytes()
            cache.put(ADDRESS, bytes)

            assertArrayEquals(bytes, requireNotNull(cache.get(ADDRESS)))
            val file = requireNotNull(cache.diskFileFor(ADDRESS))
            assertTrue("disk entry should exist", file.isFile)

            // A cold cache instance must serve the same payload from the disk tier.
            val reopened = newCache(directory)
            assertArrayEquals(bytes, requireNotNull(reopened.get(ADDRESS)))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun evictsMemoryEntriesOverTheByteLimit() {
        val cache = newCache(directory = null, memoryLimitBytes = 10L)
        cache.put(ADDRESS, ByteArray(6) { 1 })
        cache.put(OTHER, ByteArray(6) { 2 })

        assertNull("least recently used entry must be evicted", cache.memoryGet(ADDRESS))
        assertTrue(cache.memoryBytesForTest() <= 10L)
        assertArrayEquals(ByteArray(6) { 2 }, requireNotNull(cache.memoryGet(OTHER)))
    }

    @Test
    fun memoryEvictionUsesLeastRecentlyUsedOrder() {
        val cache = newCache(directory = null, memoryLimitBytes = 12L)
        cache.put(ADDRESS, ByteArray(4) { 1 })
        cache.put(OTHER, ByteArray(4) { 2 })
        requireNotNull(cache.memoryGet(ADDRESS)) // touch A so B becomes least recently used
        cache.put(THIRD, ByteArray(4) { 3 })
        assertEquals(12L, cache.memoryBytesForTest())
        cache.put(FOURTH, ByteArray(4) { 4 })

        assertNull(cache.memoryGet(OTHER))
        assertTrue(cache.memoryBytesForTest() <= 12L)
        assertTrue(requireNotNull(cache.memoryGet(ADDRESS)).isNotEmpty())
        assertTrue(requireNotNull(cache.memoryGet(FOURTH)).isNotEmpty())
    }

    @Test
    fun evictsOldestDiskEntriesOverTheByteLimit() {
        val directory = Files.createTempDirectory("cover-cache").toFile()
        var ticks = 0L
        // One-second steps keep the LRU order deterministic even on coarse-mtime filesystems.
        val clock = { 1_600_000_000_000L + (++ticks) * 1_000L }
        try {
            val cache = newCache(directory, diskLimitBytes = 10L, clock = clock)
            cache.put(ADDRESS, ByteArray(6) { 1 })
            cache.put(OTHER, ByteArray(6) { 2 })

            assertFalse("oldest disk entry must be evicted", requireNotNull(cache.diskFileFor(ADDRESS)).exists())
            assertTrue(requireNotNull(cache.diskFileFor(OTHER)).exists())

            val reopened = newCache(directory)
            assertNull(reopened.diskGet(ADDRESS))
            assertArrayEquals(ByteArray(6) { 2 }, requireNotNull(reopened.diskGet(OTHER)))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun unreadableDiskEntriesDegradeToMiss() {
        val directory = Files.createTempDirectory("cover-cache").toFile()
        try {
            val cache = newCache(directory)
            val file = requireNotNull(cache.diskFileFor(ADDRESS))
            val parent = requireNotNull(file.parentFile)
            assertTrue(parent.mkdirs() || parent.isDirectory)
            // A directory where a cache file is expected must behave as a cache miss.
            assertTrue(file.mkdirs())
            assertNull(cache.diskGet(ADDRESS))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptedEmptyDiskFileIsRemovedAndMissed() {
        val directory = Files.createTempDirectory("cover-cache").toFile()
        try {
            val cache = newCache(directory)
            val file = requireNotNull(cache.diskFileFor(ADDRESS))
            requireNotNull(file.parentFile).mkdirs()
            file.writeBytes(ByteArray(0))

            assertNull(cache.diskGet(ADDRESS))
            assertFalse("empty cache file should be deleted", file.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun emptyPayloadsAreNotCached() {
        val cache = newCache(directory = null)
        cache.put(ADDRESS, ByteArray(0))

        assertNull(cache.memoryGet(ADDRESS))
        assertNull(cache.get(ADDRESS))
    }

    @Test
    fun fileNameIsStableSha256OfTheAddress() {
        val cache = newCache(directory = null)
        val first = cache.fileNameFor(ADDRESS)
        val second = newCache(directory = null).fileNameFor(ADDRESS)
        val different = cache.fileNameFor(OTHER)

        assertEquals(first, second)
        assertNotEquals(first, different)
        assertTrue("file name must be 64 lowercase hex chars", Regex("[0-9a-f]{64}").matches(first))
        assertFalse(first.contains('/'))
        assertFalse(first.contains('\\'))
        assertEquals(sha256Hex(ADDRESS), first)
    }

    private fun newCache(
        directory: File?,
        memoryLimitBytes: Long = CoverCache.DEFAULT_MEMORY_LIMIT_BYTES,
        diskLimitBytes: Long = CoverCache.DEFAULT_DISK_LIMIT_BYTES,
        clock: () -> Long = System::currentTimeMillis,
    ): CoverCache = CoverCache(
        diskDirectory = directory,
        memoryLimitBytes = memoryLimitBytes,
        diskLimitBytes = diskLimitBytes,
        ioExecutor = directExecutor,
        clock = clock,
    )

    private fun jpegBytes(): ByteArray = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x20, 0x40)

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val ADDRESS = "https://pic.example.com/upload/cover-1.jpg?auth_key=1"
        const val OTHER = "https://pic.example.com/upload/cover-2.jpg"
        const val THIRD = "https://pic.example.com/upload/cover-3.jpg"
        const val FOURTH = "https://pic.example.com/upload/cover-4.jpg"
    }
}
