package com.aurorashelf.app.ui

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val COVER_MEMORY_LIMIT_BYTES = 24L * 1024L * 1024L
private const val COVER_DISK_LIMIT_BYTES = 96L * 1024L * 1024L

private fun newCoverCacheExecutor(): Executor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "cover-cache-io").apply { isDaemon = true }
}

/**
 * Byte-bounded two-tier LRU cache for cover images.
 *
 * Deliberately free of Android APIs: the disk tier only needs a plain [File] directory, so it can
 * be unit-tested against a temporary directory. Disk writes/evictions run on [ioExecutor] so a
 * cache miss never waits for file IO; every disk read is best-effort and silently degrades to a
 * miss (the caller then goes to the network as if it were not cached).
 */
internal class CoverCache(
    private val diskDirectory: File?,
    private val memoryLimitBytes: Long = COVER_MEMORY_LIMIT_BYTES,
    private val diskLimitBytes: Long = COVER_DISK_LIMIT_BYTES,
    private val ioExecutor: Executor = newCoverCacheExecutor(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val memory = LinkedHashMap<String, ByteArray>(16, 0.75f, true)
    private var memoryBytes = 0L

    private val diskStateLock = Any()
    private val diskSizes = HashMap<String, Long>()
    private var diskBytes = 0L
    private var diskIndexLoaded = false

    fun memoryGet(key: String): ByteArray? = synchronized(memory) { memory[key] }

    fun memoryPut(key: String, bytes: ByteArray) {
        if (bytes.isEmpty() || memoryLimitBytes <= 0 || bytes.size > memoryLimitBytes) return
        synchronized(memory) {
            memory[key]?.let { memoryBytes -= it.size }
            memory[key] = bytes
            memoryBytes += bytes.size
            val iterator = memory.entries.iterator()
            while (memoryBytes > memoryLimitBytes && iterator.hasNext()) {
                memoryBytes -= iterator.next().value.size
                iterator.remove()
            }
        }
    }

    /** Memory-first lookup; a disk hit is promoted into memory (still no image validation here). */
    fun get(key: String): ByteArray? = memoryGet(key) ?: diskGet(key)?.also { memoryPut(key, it) }

    fun put(key: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        memoryPut(key, bytes)
        val directory = diskDirectory ?: return
        if (bytes.size > diskLimitBytes) return
        val name = fileNameFor(key)
        ioExecutor.execute { writeToDisk(directory, name, bytes) }
    }

    fun invalidate(key: String) {
        synchronized(memory) {
            memory.remove(key)?.let { memoryBytes -= it.size }
        }
        if (diskDirectory == null) return
        val name = fileNameFor(key)
        ioExecutor.execute { deleteFromDisk(name) }
    }

    /** Disk-only lookup. Unreadable or empty files are deleted and reported as a miss. */
    internal fun diskGet(key: String): ByteArray? {
        val directory = diskDirectory ?: return null
        val file = File(directory, fileNameFor(key))
        ensureDiskIndexLoaded()
        if (!file.isFile) return null
        val bytes = runCatching { file.readBytes() }.getOrNull()
        if (bytes == null || bytes.isEmpty()) {
            runCatching { file.delete() }
            synchronized(diskStateLock) {
                diskSizes.remove(file.name)?.let { diskBytes -= it }
            }
            return null
        }
        synchronized(diskStateLock) {
            val previous = diskSizes.put(file.name, bytes.size.toLong())
            diskBytes += bytes.size.toLong() - (previous ?: 0L)
        }
        runCatching { file.setLastModified(clock()) }
        return bytes
    }

    internal fun diskFileFor(key: String): File? = diskDirectory?.let { File(it, fileNameFor(key)) }

    internal fun diskBytesForTest(): Long = synchronized(diskStateLock) { diskBytes }

    internal fun memoryBytesForTest(): Long = synchronized(memory) { memoryBytes }

    internal fun clearMemory() = synchronized(memory) {
        memory.clear()
        memoryBytes = 0
    }

    internal fun fileNameFor(key: String): String = sha256Hex(key)

    private fun ensureDiskIndexLoaded() {
        if (diskIndexLoaded) return
        val directory = diskDirectory ?: run {
            diskIndexLoaded = true
            return
        }
        synchronized(diskStateLock) {
            if (diskIndexLoaded) return
            directory.listFiles().orEmpty().forEach { file ->
                if (file.name.endsWith(TEMP_SUFFIX)) {
                    runCatching { file.delete() }
                } else if (file.isFile) {
                    val size = file.length()
                    diskSizes[file.name] = size
                    diskBytes += size
                }
            }
            diskIndexLoaded = true
        }
    }

    private fun writeToDisk(directory: File, name: String, bytes: ByteArray) {
        ensureDiskIndexLoaded()
        if (!directory.isDirectory && !directory.mkdirs()) return
        val target = File(directory, name)
        val temporary = File(directory, name + TEMP_SUFFIX)
        val written = runCatching {
            temporary.writeBytes(bytes)
            temporary.renameTo(target)
        }.getOrDefault(false)
        if (!written) {
            runCatching { temporary.delete() }
            return
        }
        runCatching { target.setLastModified(clock()) }
        synchronized(diskStateLock) {
            val previous = diskSizes.put(name, bytes.size.toLong())
            diskBytes += bytes.size.toLong() - (previous ?: 0L)
            enforceDiskLimitLocked(directory)
        }
    }

    private fun deleteFromDisk(name: String) {
        val directory = diskDirectory ?: return
        synchronized(diskStateLock) {
            if (runCatching { File(directory, name).delete() }.getOrDefault(false)) {
                diskSizes.remove(name)?.let { diskBytes -= it }
            }
        }
    }

    private fun enforceDiskLimitLocked(directory: File) {
        if (diskBytes <= diskLimitBytes) return
        val candidates = directory.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(TEMP_SUFFIX) }
            ?.sortedWith(compareBy<File>({ it.lastModified() }, { it.name }))
            .orEmpty()
        for (file in candidates) {
            if (diskBytes <= diskLimitBytes) break
            val size = diskSizes[file.name] ?: file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) {
                diskSizes.remove(file.name)
                diskBytes -= size
            }
        }
    }

    companion object {
        internal const val DEFAULT_MEMORY_LIMIT_BYTES = COVER_MEMORY_LIMIT_BYTES
        internal const val DEFAULT_DISK_LIMIT_BYTES = COVER_DISK_LIMIT_BYTES
        internal const val CACHE_DIRECTORY_NAME = "cover_cache"
        private const val TEMP_SUFFIX = ".tmp"

        internal fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
