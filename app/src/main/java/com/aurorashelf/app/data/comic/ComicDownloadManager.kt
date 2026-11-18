package com.aurorashelf.app.data.comic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import coil3.size.Size
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.data.resilience.AuroraHttpClient
import com.aurorashelf.app.model.ComicChapter
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSummary
import com.aurorashelf.app.ui.CoverImageLoader
import com.aurorashelf.app.ui.JmUnscrambleTransformation
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

enum class ComicDownloadStatus {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    COMPLETED,
    PAUSED,
    FAILED,
}

data class DownloadedComicChapter(
    val id: String,
    val comicId: String,
    val sourceId: String,
    val comicTitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val coverUrl: String,
    val totalPages: Int,
    val downloadedPages: Int,
    val totalBytes: Long,
    val status: ComicDownloadStatus,
    val progress: Float = if (totalPages > 0) downloadedPages.toFloat() / totalPages else 0f,
    val errorMessage: String? = null,
)

class ComicDownloadManager private constructor(appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val baseDir = File(appContext.filesDir, "OfflineComics").apply { if (!exists()) mkdirs() }
    private val metaFile = File(baseDir, "comic_metadata.json")
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val downloadSemaphore = Semaphore(2)
    private val metadataLock = Any()
    private val httpClient = AuroraHttpClient.standardClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _chapters = MutableStateFlow<List<DownloadedComicChapter>>(emptyList())
    val chapters: StateFlow<List<DownloadedComicChapter>> = _chapters.asStateFlow()

    /** Ensures cold-start operations await initial metadata load before mutating or persisting `comic_metadata.json`. */
    private val initDeferred: Deferred<Unit> = scope.async { loadMetadata() }

    companion object {
        private const val TAG = "ComicDownloadManager"
        internal const val MIN_FREE_SPACE_BYTES = 200L * 1024 * 1024 // 200 MB
        private const val MAX_PAGE_BYTES = 35 * 1024 * 1024 // 35 MB cap per image
        private const val MAX_DECODE_WIDTH = 1440
        private const val MAX_DECODE_HEIGHT = 8192

        @Volatile
        private var instance: ComicDownloadManager? = null

        fun getInstance(context: Context): ComicDownloadManager =
            instance ?: synchronized(this) {
                instance ?: ComicDownloadManager(context.applicationContext).also { instance = it }
            }

        internal fun calculateSampleSize(
            rawWidth: Int,
            rawHeight: Int,
            maxWidth: Int = MAX_DECODE_WIDTH,
            maxHeight: Int = MAX_DECODE_HEIGHT,
        ): Int {
            var sampleSize = 1
            if (rawWidth <= 0 || rawHeight <= 0) return sampleSize
            while ((rawWidth / sampleSize) > maxWidth || (rawHeight / sampleSize) > maxHeight) {
                sampleSize *= 2
            }
            return sampleSize
        }
    }

    fun isDownloaded(comicId: String, chapterId: String): Boolean {
        val id = generateId(comicId, chapterId)
        return _chapters.value.find { it.id == id }?.status == ComicDownloadStatus.COMPLETED
    }

    fun getStatus(comicId: String, chapterId: String): DownloadedComicChapter? {
        val id = generateId(comicId, chapterId)
        return _chapters.value.find { it.id == id }
    }

    private fun resolveChapterDir(comicId: String, chapterId: String): File {
        val legacyDir = File(File(baseDir, sanitizeLegacy(comicId)), sanitizeLegacy(chapterId))
        if (legacyDir.exists() && legacyDir.name.toByteArray(Charsets.UTF_8).size <= 200) {
            return legacyDir
        }
        return File(File(baseDir, safeSegment(comicId)), safeSegment(chapterId))
    }

    fun getLocalPageFile(comicId: String, chapterId: String, pageIndex: Int): File? {
        val chapterDir = resolveChapterDir(comicId, chapterId)
        val file = File(chapterDir, "page_$pageIndex.jpg")
        return if (file.exists() && file.length() > 0) file else null
    }

    fun getLocalPages(comic: ComicSummary, chapter: ComicChapter): List<ComicPage> {
        val record = getStatus(comic.id, chapter.id) ?: return emptyList()
        val total = if (record.downloadedPages > 0) record.downloadedPages else record.totalPages
        if (total <= 0) return emptyList()

        return (0 until total).mapNotNull { idx ->
            val file = getLocalPageFile(comic.id, chapter.id, idx)
            if (file != null) {
                ComicPage(
                    imageUrl = file.toURI().toString(),
                    referer = "",
                    resolutionUrl = null,
                    verticalSegments = 0,
                )
            } else null
        }
    }

    fun formattedTotalSpace(): String {
        val total = _chapters.value.sumOf { it.totalBytes }
        if (total <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = total.toDouble()
        var unitIdx = 0
        while (size >= 1024 && unitIdx < units.size - 1) {
            size /= 1024
            unitIdx++
        }
        return "${DecimalFormat("#0.0").format(size)} ${units[unitIdx]}"
    }

    private fun hasSufficientDiskSpace(): Boolean =
        runCatching { baseDir.usableSpace }.getOrDefault(0L) >= MIN_FREE_SPACE_BYTES

    fun startDownload(
        comic: ComicSummary,
        chapter: ComicChapter,
        repository: ComicRepository,
    ) {
        val itemId = generateId(comic.id, chapter.id)
        scope.launch {
            initDeferred.await()
            val existing = _chapters.value.find { it.id == itemId }
            if (existing?.status == ComicDownloadStatus.COMPLETED) return@launch

            if (!hasSufficientDiskSpace()) {
                val failedRecord = (existing ?: DownloadedComicChapter(
                    id = itemId,
                    comicId = comic.id,
                    sourceId = comic.sourceId,
                    comicTitle = comic.title,
                    chapterId = chapter.id,
                    chapterTitle = chapter.title,
                    coverUrl = comic.coverUrl.orEmpty(),
                    totalPages = 0,
                    downloadedPages = 0,
                    totalBytes = 0,
                    status = ComicDownloadStatus.FAILED,
                )).copy(
                    status = ComicDownloadStatus.FAILED,
                    errorMessage = "设备可用存储空间不足 200 MB",
                )
                updateChapterRecord(failedRecord, persist = true)
                return@launch
            }

            val newRecord = existing?.copy(status = ComicDownloadStatus.QUEUED, errorMessage = null)
                ?: DownloadedComicChapter(
                    id = itemId,
                    comicId = comic.id,
                    sourceId = comic.sourceId,
                    comicTitle = comic.title,
                    chapterId = chapter.id,
                    chapterTitle = chapter.title,
                    coverUrl = comic.coverUrl.orEmpty(),
                    totalPages = 0,
                    downloadedPages = 0,
                    totalBytes = 0,
                    status = ComicDownloadStatus.QUEUED,
                )

            updateChapterRecord(newRecord, persist = true)

            val prevJob = activeJobs.remove(itemId)
            prevJob?.cancelAndJoin()
            val job = scope.launch {
                executeDownload(itemId, comic, chapter, repository)
            }
            activeJobs[itemId] = job
        }
    }

    fun startDownloadAll(
        details: ComicDetails,
        repository: ComicRepository,
    ) {
        details.chapters.forEach { ch ->
            startDownload(details.comic, ch, repository)
        }
    }

    fun retryDownload(
        id: String,
        repository: ComicRepository,
    ) {
        scope.launch {
            initDeferred.await()
            val record = findChapter(id) ?: return@launch
            if (record.status == ComicDownloadStatus.COMPLETED ||
                record.status == ComicDownloadStatus.DOWNLOADING ||
                record.status == ComicDownloadStatus.RESOLVING ||
                record.status == ComicDownloadStatus.QUEUED
            ) {
                return@launch
            }
            val comic = ComicSummary(
                sourceId = record.sourceId,
                id = record.comicId,
                title = record.comicTitle,
                coverUrl = record.coverUrl,
            )
            val chapter = ComicChapter(
                id = record.chapterId,
                title = record.chapterTitle,
            )
            startDownload(comic, chapter, repository)
        }
    }

    fun removeDownload(id: String) {
        if (initDeferred.isCompleted) {
            _chapters.update { list -> list.filterNot { it.id == id } }
        }
        scope.launch {
            initDeferred.await()
            val item = findChapter(id)
            val job = activeJobs.remove(id)
            job?.cancelAndJoin()
            if (item != null) {
                deleteChapterFilesAndCleanParent(item.comicId, item.chapterId)
            }
            _chapters.update { list -> list.filterNot { it.id == id } }
            saveMetadata()
        }
    }

    fun removeAll() {
        if (initDeferred.isCompleted) {
            _chapters.value = emptyList()
        }
        scope.launch {
            initDeferred.await()
            val runningJobs = activeJobs.values.toList()
            activeJobs.clear()
            runningJobs.forEach { it.cancel() }
            runningJobs.forEach { runCatching { it.join() } }
            baseDir.deleteRecursively()
            baseDir.mkdirs()
            _chapters.value = emptyList()
            saveMetadata()
        }
    }

    private fun deleteChapterFilesAndCleanParent(comicId: String, chapterId: String) {
        val chapterDir = resolveChapterDir(comicId, chapterId)
        val comicDir = chapterDir.parentFile
        chapterDir.deleteRecursively()
        if (comicDir != null && comicDir != baseDir && comicDir.exists() && comicDir.listFiles().isNullOrEmpty()) {
            comicDir.delete()
        }
    }

    private suspend fun executeDownload(
        itemId: String,
        comic: ComicSummary,
        chapter: ComicChapter,
        repository: ComicRepository,
    ) = withContext(Dispatchers.IO) {
        val currentJob = coroutineContext[Job]
        try {
            downloadSemaphore.withPermit {
                if (!isActive) return@withPermit
                if (!hasSufficientDiskSpace()) {
                    throw IOException("设备可用存储空间不足 200 MB")
                }
                updateChapterStatus(itemId, ComicDownloadStatus.RESOLVING)

                val pages = repository.chapter(comic, chapter.id)
                if (pages.isEmpty()) throw IllegalStateException("章节没有图片")

                val chapterDir = resolveChapterDir(comic.id, chapter.id).apply {
                    if (!exists()) mkdirs()
                }

                updateChapterRecord(
                    findChapter(itemId)?.copy(
                        totalPages = pages.size,
                        status = ComicDownloadStatus.DOWNLOADING,
                        errorMessage = null,
                    ) ?: return@withPermit,
                    persist = true,
                )

                var totalBytes = 0L

                for ((idx, rawPage) in pages.withIndex()) {
                    if (!isActive) throw CancellationException("Download cancelled")
                    if (idx % 5 == 0 && !hasSufficientDiskSpace()) {
                        throw IOException("存储空间不足，已暂停下载")
                    }
                    val pageFile = File(chapterDir, "page_$idx.jpg")

                    if (!pageFile.exists() || pageFile.length() == 0L) {
                        val resolved = ComicPageResolver.resolve(rawPage)
                        downloadAndSavePage(resolved, pageFile)
                    }

                    totalBytes += pageFile.length()
                    val downloaded = idx + 1
                    val progress = downloaded.toFloat() / pages.size

                    updateChapterRecord(
                        findChapter(itemId)?.copy(
                            downloadedPages = downloaded,
                            totalBytes = totalBytes,
                            progress = progress,
                            status = ComicDownloadStatus.DOWNLOADING,
                        ) ?: return@withPermit,
                        persist = (downloaded % 5 == 0 || downloaded == pages.size),
                    )
                }

                updateChapterRecord(
                    findChapter(itemId)?.copy(
                        downloadedPages = pages.size,
                        totalBytes = totalBytes,
                        progress = 1.0f,
                        status = ComicDownloadStatus.COMPLETED,
                        errorMessage = null,
                    ) ?: return@withPermit,
                    persist = true,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $itemId", e)
            updateChapterRecord(
                findChapter(itemId)?.copy(
                    status = ComicDownloadStatus.FAILED,
                    errorMessage = e.message ?: "下载失败",
                ) ?: return@withContext,
                persist = true,
            )
        } finally {
            if (currentJob != null) {
                activeJobs.remove(itemId, currentJob)
            } else {
                activeJobs.remove(itemId)
            }
        }
    }

    private suspend fun downloadAndSavePage(page: ComicPage, targetFile: File) {
        val request = Request.Builder()
            .url(page.imageUrl)
            .header("User-Agent", VideoRepository.USER_AGENT)
            .apply {
                if (page.referer.isNotBlank()) header("Referer", page.referer)
            }
            .build()
        val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            val rawBytes = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("图片下载失败 HTTP ${resp.code}")
                }
                val declaredLen = resp.body?.contentLength() ?: -1L
                if (declaredLen > MAX_PAGE_BYTES) {
                    throw IOException("单页图片超过 35 MB 上限")
                }
                val stream = resp.body?.byteStream() ?: throw IOException("图片响应为空")
                stream.use { input ->
                    val out = ByteArrayOutputStream(minOf(declaredLen.coerceAtLeast(8192L), 262_144L).toInt())
                    val buf = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_PAGE_BYTES) {
                            throw IOException("单页图片超过 35 MB 上限")
                        }
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            }
            if (rawBytes.isEmpty()) throw IOException("下载的图片内容为空")

            val plainBytes = CoverImageLoader.decryptPayloadIfNeeded(
                rawBytes,
                page.imageUrl,
                page.referer,
            )
            if (page.verticalSegments > 1) {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size, boundsOpts)
                val sampleSize = calculateSampleSize(boundsOpts.outWidth, boundsOpts.outHeight)
                val pixelCount = (boundsOpts.outWidth / sampleSize).toLong() * (boundsOpts.outHeight / sampleSize).toLong()
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    // JPEG has no alpha channel; RGB_565 halves peak bitmap memory on ultra-tall Webtoon strips.
                    inPreferredConfig = if (pixelCount > 3_500_000L) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                }
                val rawBitmap = BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size, decodeOpts)
                    ?: throw IllegalStateException("解码混淆图片失败")
                var unscrambled: Bitmap? = null
                try {
                    unscrambled = unscrambleBitmap(rawBitmap, page.verticalSegments)
                    FileOutputStream(tmpFile).use { out ->
                        unscrambled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                    }
                } finally {
                    rawBitmap.recycle()
                    if (unscrambled != null && unscrambled !== rawBitmap) {
                        unscrambled.recycle()
                    }
                }
            } else {
                FileOutputStream(tmpFile).use { out ->
                    out.write(plainBytes)
                }
            }
            if (targetFile.exists()) targetFile.delete()
            if (!tmpFile.renameTo(targetFile)) {
                tmpFile.copyTo(targetFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }
    }

    private suspend fun unscrambleBitmap(source: Bitmap, segments: Int): Bitmap =
        JmUnscrambleTransformation(segments).transform(source, Size.ORIGINAL)

    private fun findChapter(id: String) = _chapters.value.find { it.id == id }

    private fun updateChapterStatus(id: String, status: ComicDownloadStatus) {
        _chapters.update { list ->
            list.map { if (it.id == id) it.copy(status = status) else it }
        }
    }

    private fun updateChapterRecord(record: DownloadedComicChapter, persist: Boolean = true) {
        _chapters.update { list ->
            val idx = list.indexOfFirst { it.id == record.id }
            if (idx >= 0) {
                list.toMutableList().apply { set(idx, record) }
            } else {
                listOf(record) + list
            }
        }
        if (persist) {
            saveMetadata()
        }
    }

    private fun generateId(comicId: String, chapterId: String) = "${comicId}_$chapterId"

    private fun sanitizeLegacy(str: String): String =
        str.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun safeSegment(str: String): String {
        val clean = sanitizeLegacy(str)
        if (clean.toByteArray(Charsets.UTF_8).size <= 80) return clean
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(str.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return "${clean.take(40)}_$hash"
    }

    private fun loadMetadata() {
        synchronized(metadataLock) {
            if (!metaFile.exists()) return
            try {
                val jsonStr = metaFile.readText()
                val array = JSONArray(jsonStr)
                val list = mutableListOf<DownloadedComicChapter>()
                var needsResetPersist = false
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawStatus = ComicDownloadStatus.valueOf(obj.getString("status"))
                    val normalizedStatus = if (
                        rawStatus == ComicDownloadStatus.QUEUED ||
                        rawStatus == ComicDownloadStatus.RESOLVING ||
                        rawStatus == ComicDownloadStatus.DOWNLOADING
                    ) {
                        needsResetPersist = true
                        ComicDownloadStatus.FAILED
                    } else {
                        rawStatus
                    }
                    val existingErr = if (obj.has("errorMessage") && !obj.isNull("errorMessage")) obj.getString("errorMessage") else null
                    list.add(
                        DownloadedComicChapter(
                            id = obj.getString("id"),
                            comicId = obj.getString("comicId"),
                            sourceId = obj.getString("sourceId"),
                            comicTitle = obj.getString("comicTitle"),
                            chapterId = obj.getString("chapterId"),
                            chapterTitle = obj.getString("chapterTitle"),
                            coverUrl = obj.getString("coverUrl"),
                            totalPages = obj.getInt("totalPages"),
                            downloadedPages = obj.getInt("downloadedPages"),
                            totalBytes = obj.getLong("totalBytes"),
                            status = normalizedStatus,
                            errorMessage = if (normalizedStatus == ComicDownloadStatus.FAILED && existingErr.isNullOrBlank()) {
                                "下载已中断，点击重试"
                            } else {
                                existingErr
                            },
                        ),
                    )
                }
                _chapters.value = list
                if (needsResetPersist) {
                    saveMetadataLocked()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load metadata", e)
            }
        }
    }

    private fun saveMetadata() {
        synchronized(metadataLock) {
            saveMetadataLocked()
        }
    }

    private fun saveMetadataLocked() {
        try {
            val array = JSONArray()
            _chapters.value.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("comicId", item.comicId)
                    put("sourceId", item.sourceId)
                    put("comicTitle", item.comicTitle)
                    put("chapterId", item.chapterId)
                    put("chapterTitle", item.chapterTitle)
                    put("coverUrl", item.coverUrl)
                    put("totalPages", item.totalPages)
                    put("downloadedPages", item.downloadedPages)
                    put("totalBytes", item.totalBytes)
                    put("status", item.status.name)
                    put("errorMessage", item.errorMessage)
                }
                array.put(obj)
            }
            val tmp = File(metaFile.parentFile, "${metaFile.name}.tmp")
            tmp.writeText(array.toString())
            if (!tmp.renameTo(metaFile)) {
                tmp.copyTo(metaFile, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save metadata", e)
        }
    }
}
