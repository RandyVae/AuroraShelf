package com.aurorashelf.app.data.comic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.aurorashelf.app.ui.JmUnscrambleTransformation
import coil3.size.Size
import com.aurorashelf.app.model.ComicChapter
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSummary
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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

    private val _chapters = MutableStateFlow<List<DownloadedComicChapter>>(emptyList())
    val chapters: StateFlow<List<DownloadedComicChapter>> = _chapters.asStateFlow()

    init {
        scope.launch { loadMetadata() }
    }

    companion object {
        private const val TAG = "ComicDownloadManager"

        @Volatile
        private var instance: ComicDownloadManager? = null

        fun getInstance(context: Context): ComicDownloadManager =
            instance ?: synchronized(this) {
                instance ?: ComicDownloadManager(context.applicationContext).also { instance = it }
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

    fun startDownload(
        comic: ComicSummary,
        chapter: ComicChapter,
        repository: ComicRepository,
    ) {
        val itemId = generateId(comic.id, chapter.id)
        val existing = _chapters.value.find { it.id == itemId }
        if (existing?.status == ComicDownloadStatus.COMPLETED) return

        val newRecord = existing?.copy(status = ComicDownloadStatus.QUEUED) ?: DownloadedComicChapter(
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

        updateChapterRecord(newRecord)

        activeJobs[itemId]?.cancel()
        activeJobs[itemId] = scope.launch {
            executeDownload(itemId, comic, chapter, repository)
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
        val record = findChapter(id) ?: return
        if (record.status == ComicDownloadStatus.COMPLETED ||
            record.status == ComicDownloadStatus.DOWNLOADING ||
            record.status == ComicDownloadStatus.RESOLVING ||
            record.status == ComicDownloadStatus.QUEUED
        ) {
            return
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

    fun removeDownload(id: String) {
        activeJobs.remove(id)?.cancel()
        val item = _chapters.value.find { it.id == id }
        if (item != null) {
            resolveChapterDir(item.comicId, item.chapterId).deleteRecursively()
        }
        _chapters.update { list -> list.filterNot { it.id == id } }
        saveMetadata()
    }

    fun removeAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        baseDir.deleteRecursively()
        baseDir.mkdirs()
        _chapters.value = emptyList()
        saveMetadata()
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
                    ) ?: return@withPermit,
                    persist = true,
                )

                var totalBytes = 0L

                for ((idx, rawPage) in pages.withIndex()) {
                    if (!isActive) throw CancellationException("Download cancelled")
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
        val url = URL(page.imageUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
            if (page.referer.isNotBlank()) setRequestProperty("Referer", page.referer)
            instanceFollowRedirects = true
        }
        val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("图片下载失败 HTTP $code")
            }
            conn.inputStream.use { input ->
                val rawBytes = input.readBytes()
                val plainBytes = com.aurorashelf.app.ui.CoverImageLoader.decryptPayloadIfNeeded(
                    rawBytes,
                    page.imageUrl,
                    page.referer,
                )
                if (page.verticalSegments > 1) {
                    val rawBitmap = BitmapFactory.decodeByteArray(plainBytes, 0, plainBytes.size)
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
            }
            if (targetFile.exists()) targetFile.delete()
            if (!tmpFile.renameTo(targetFile)) {
                tmpFile.copyTo(targetFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        } finally {
            conn.disconnect()
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
