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
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap
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

    private val _chapters = MutableStateFlow<List<DownloadedComicChapter>>(emptyList())
    val chapters: StateFlow<List<DownloadedComicChapter>> = _chapters.asStateFlow()

    init {
        loadMetadata()
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

    fun getLocalPageFile(comicId: String, chapterId: String, pageIndex: Int): File? {
        val chapterDir = File(File(baseDir, sanitize(comicId)), sanitize(chapterId))
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

    fun removeDownload(id: String) {
        activeJobs.remove(id)?.cancel()
        val item = _chapters.value.find { it.id == id }
        if (item != null) {
            val chapterDir = File(File(baseDir, sanitize(item.comicId)), sanitize(item.chapterId))
            chapterDir.deleteRecursively()
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
        updateChapterStatus(itemId, ComicDownloadStatus.RESOLVING)

        try {
            val pages = repository.chapter(comic, chapter.id)
            if (pages.isEmpty()) throw IllegalStateException("章节没有图片")

            val chapterDir = File(File(baseDir, sanitize(comic.id)), sanitize(chapter.id)).apply {
                if (!exists()) mkdirs()
            }

            updateChapterRecord(
                findChapter(itemId)?.copy(
                    totalPages = pages.size,
                    status = ComicDownloadStatus.DOWNLOADING,
                ) ?: return@withContext,
            )

            var totalBytes = 0L

            for ((idx, rawPage) in pages.withIndex()) {
                if (!isActive) return@withContext
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
                    ) ?: return@withContext,
                )
            }

            updateChapterRecord(
                findChapter(itemId)?.copy(
                    downloadedPages = pages.size,
                    totalBytes = totalBytes,
                    progress = 1.0f,
                    status = ComicDownloadStatus.COMPLETED,
                ) ?: return@withContext,
            )
            saveMetadata()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $itemId", e)
            updateChapterRecord(
                findChapter(itemId)?.copy(
                    status = ComicDownloadStatus.FAILED,
                    errorMessage = e.message ?: "下载失败",
                ) ?: return@withContext,
            )
            saveMetadata()
        } finally {
            activeJobs.remove(itemId)
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

        conn.inputStream.use { input ->
            if (page.verticalSegments > 1) {
                // 还原 JM 解密混淆切片
                val rawBytes = input.readBytes()
                val rawBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    ?: throw IllegalStateException("解码混淆图片失败")
                val unscrambled = unscrambleBitmap(rawBitmap, page.verticalSegments)
                FileOutputStream(targetFile).use { out ->
                    unscrambled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                rawBitmap.recycle()
                unscrambled.recycle()
            } else {
                FileOutputStream(targetFile).use { out ->
                    input.copyTo(out)
                }
            }
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

    private fun updateChapterRecord(record: DownloadedComicChapter) {
        _chapters.update { list ->
            val idx = list.indexOfFirst { it.id == record.id }
            if (idx >= 0) {
                list.toMutableList().apply { set(idx, record) }
            } else {
                listOf(record) + list
            }
        }
        saveMetadata()
    }

    private fun generateId(comicId: String, chapterId: String) = "${comicId}_$chapterId"

    private fun sanitize(str: String): String =
        str.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun loadMetadata() {
        if (!metaFile.exists()) return
        try {
            val jsonStr = metaFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DownloadedComicChapter>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
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
                        status = ComicDownloadStatus.valueOf(obj.getString("status")),
                        errorMessage = if (obj.has("errorMessage") && !obj.isNull("errorMessage")) obj.getString("errorMessage") else null,
                    ),
                )
            }
            _chapters.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load metadata", e)
        }
    }

    private fun saveMetadata() {
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
            metaFile.writeText(array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save metadata", e)
        }
    }
}
