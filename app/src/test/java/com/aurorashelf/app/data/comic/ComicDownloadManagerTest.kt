package com.aurorashelf.app.data.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicDownloadManagerTest {
    @Test
    fun chapterRecordProgressCalculatesCorrectly() {
        val chapter = DownloadedComicChapter(
            id = "c1_ch1",
            comicId = "c1",
            sourceId = "baozi",
            comicTitle = "测试漫画",
            chapterId = "ch1",
            chapterTitle = "第一话",
            coverUrl = "https://example.com/cover.jpg",
            totalPages = 20,
            downloadedPages = 10,
            totalBytes = 1024 * 1024,
            status = ComicDownloadStatus.DOWNLOADING,
        )

        assertEquals(0.5f, chapter.progress, 0.001f)
        assertFalse(chapter.status == ComicDownloadStatus.COMPLETED)
    }

    @Test
    fun completedChapterRecordStatus() {
        val chapter = DownloadedComicChapter(
            id = "c1_ch2",
            comicId = "c1",
            sourceId = "baozi",
            comicTitle = "测试漫画",
            chapterId = "ch2",
            chapterTitle = "第二话",
            coverUrl = "https://example.com/cover.jpg",
            totalPages = 15,
            downloadedPages = 15,
            totalBytes = 2 * 1024 * 1024,
            status = ComicDownloadStatus.COMPLETED,
        )

        assertEquals(1.0f, chapter.progress, 0.001f)
        assertTrue(chapter.status == ComicDownloadStatus.COMPLETED)
    }
}
