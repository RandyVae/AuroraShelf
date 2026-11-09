package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicChapter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IkmmhComicSourceTest {

    @Test
    fun ikmmhMetadata_exposesExpectedCategories() {
        val source = IkmmhComicSource()
        assertEquals("ikmmh", source.info.id)
        assertEquals("爱看漫", source.info.name)
        val categories = source.info.categories.map { it.id }
        assertTrue(categories.contains("全部"))
        assertTrue(categories.contains("长条"))
    }

    @Test
    fun missingOrEmptyChapterArray_parsesGracefullyWithoutNpe() {
        val chapterJsonMissing = JSONObject("""{"status":200}""")
        val missingArray = chapterJsonMissing.optJSONArray("length")
        val chaptersFromMissing = missingArray?.let { array ->
            List(array.length()) { index ->
                val chapter = array.optJSONObject(index) ?: return@List null
                val chapterUrl = chapter.optString("url").takeIf(String::isNotBlank) ?: return@List null
                ComicChapter(chapterUrl, chapter.optString("name").ifBlank { "章节" })
            }.filterNotNull()
        }.orEmpty()
        assertTrue(chaptersFromMissing.isEmpty())

        val chapterJsonWithNulls = JSONObject("""{
            "length": [
                {"url": "https://example.com/ch1", "name": "第1话"},
                null,
                {"name": "无URL"},
                {"url": "https://example.com/ch2", "name": ""}
            ]
        }""")
        val arrayWithNulls = chapterJsonWithNulls.optJSONArray("length")
        val parsedChapters = arrayWithNulls?.let { array ->
            List(array.length()) { index ->
                val chapter = array.optJSONObject(index) ?: return@List null
                val chapterUrl = chapter.optString("url").takeIf(String::isNotBlank) ?: return@List null
                ComicChapter(chapterUrl, chapter.optString("name").ifBlank { "章节" })
            }.filterNotNull()
        }.orEmpty()

        assertEquals(2, parsedChapters.size)
        assertEquals("https://example.com/ch1", parsedChapters[0].id)
        assertEquals("第1话", parsedChapters[0].title)
        assertEquals("https://example.com/ch2", parsedChapters[1].id)
        assertEquals("章节", parsedChapters[1].title)
    }
}
