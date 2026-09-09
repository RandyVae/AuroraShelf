package com.aurorashelf.app.data.comic

import android.annotation.SuppressLint
import android.util.Base64
import com.aurorashelf.app.BuildConfig
import com.aurorashelf.app.model.ComicCategory
import com.aurorashelf.app.model.ComicChapter
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ComicSummary
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class JmComicSource : ComicSource {
    override val info = ComicSourceInfo(
        id = "jm",
        name = "禁漫天堂",
        description = "禁漫天堂 · 匿名浏览",
        categories = listOf(
            ComicCategory("0", "全部"),
            ComicCategory("doujin", "同人"),
            ComicCategory("single", "单本"),
            ComicCategory("short", "短篇"),
            ComicCategory("hanman", "韩漫"),
            ComicCategory("meiman", "美漫"),
            ComicCategory("3D", "3D"),
        ),
    )

    override suspend fun browse(categoryId: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val path = "categories/filter?o=mr&c=${ComicHttp.encode(categoryId)}&page=$page"
        parseComics(request(path))
    }

    override suspend fun search(query: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        parseComics(request("search?search_query=${ComicHttp.encode(query)}&o=mr&page=$page"))
    }

    override suspend fun details(comic: ComicSummary): ComicDetails = withContext(Dispatchers.IO) {
        val item = request("album?id=${ComicHttp.encode(comic.id)}")
        val series = item.optJSONArray("series") ?: JSONArray()
        val chapters = if (series.length() == 0) {
            listOf(ComicChapter(comic.id, "完整内容"))
        } else {
            List(series.length()) { index ->
                val chapter = series.optJSONObject(index) ?: JSONObject()
                val order = chapter.optString("sort").ifBlank { (index + 1).toString() }
                ComicChapter(
                    id = chapter.optString("id").ifBlank { comic.id },
                    title = chapter.optString("name").ifBlank { "第 $order 话" },
                )
            }
        }
        ComicDetails(
            comic = comic.copy(
                title = item.optString("name").ifBlank { comic.title },
                subtitle = jsonStrings(item.optJSONArray("author")).joinToString(" / ").ifBlank { comic.subtitle },
                coverUrl = coverUrl(comic.id),
                tags = jsonStrings(item.optJSONArray("tags")).ifEmpty { comic.tags },
            ),
            description = item.optString("description"),
            chapters = chapters,
        )
    }

    override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> = withContext(Dispatchers.IO) {
        val item = request("chapter?id=${ComicHttp.encode(chapterId)}")
        val photoId = item.optString("id").ifBlank { chapterId }
        val images = item.optJSONArray("images") ?: JSONArray()
        val scrambleId = requestScrambleId(photoId)
        List(images.length()) { index ->
            val filename = images.optString(index)
            val url = "https://${IMAGE_DOMAINS[index % IMAGE_DOMAINS.size]}/media/photos/$photoId/$filename"
            ComicPage(
                imageUrl = url,
                referer = "https://${API_DOMAINS.first()}/",
                verticalSegments = JmImageScrambler.segmentCount(scrambleId, photoId, filename),
            )
        }.filter { it.imageUrl.substringAfterLast('/').isNotBlank() }
    }

    private fun parseComics(data: JSONObject): List<ComicSummary> {
        val content = data.optJSONArray("content") ?: return emptyList()
        return List(content.length()) { index ->
            val item = content.optJSONObject(index) ?: return@List null
            val id = item.optString("id")
            val title = item.optString("name")
            if (id.isBlank() || title.isBlank()) return@List null
            val author = when (val value = item.opt("author")) {
                is JSONArray -> jsonStrings(value).joinToString(" / ")
                else -> value?.toString().orEmpty()
            }
            val tags = buildList {
                addAll(jsonStrings(item.optJSONArray("tags")))
                item.optJSONObject("category")?.optString("title")?.takeIf(String::isNotBlank)?.let(::add)
                item.optJSONObject("category_sub")?.optString("title")?.takeIf(String::isNotBlank)?.let(::add)
            }
            ComicSummary(info.id, id, title, author, coverUrl(id), tags.distinct())
        }.filterNotNull()
    }

    private fun request(path: String): JSONObject {
        ensureProtocolConfigured()
        var lastFailure: Throwable? = null
        API_DOMAINS.forEach { domain ->
            runCatching {
                val timestamp = (System.currentTimeMillis() / 1_000L).toString()
                val response = ComicHttp.getResponse("https://$domain/$path", headers(timestamp, BuildConfig.JM_TOKEN_SECRET))
                check(response.status == 200) { "HTTP ${response.status}" }
                val outer = JSONObject(response.body)
                check(outer.optInt("code") == 200) { outer.optString("errorMsg").ifBlank { "code ${outer.optInt("code")}" } }
                val encrypted = outer.optString("data")
                check(encrypted.isNotBlank()) { "响应内容为空" }
                return JSONObject(decrypt(encrypted, timestamp))
            }.onFailure { lastFailure = it }
        }
        error("禁漫接口连接失败：${lastFailure?.message.orEmpty()}")
    }

    private fun requestScrambleId(photoId: String): Int {
        val path = "chapter_view_template?id=${ComicHttp.encode(photoId)}&mode=vertical&page=0&app_img_shunt=1&express=off"
        var lastFailure: Throwable? = null
        API_DOMAINS.forEach { domain ->
            runCatching {
                val timestamp = (System.currentTimeMillis() / 1_000L).toString()
                val response = ComicHttp.getResponse("https://$domain/$path", headers(timestamp, BuildConfig.JM_TOKEN_SECRET_2))
                check(response.status == 200) { "HTTP ${response.status}" }
                return SCRAMBLE_PATTERN.find(response.body)?.groupValues?.get(1)?.toInt()
                    ?: DEFAULT_SCRAMBLE_ID
            }.onFailure { lastFailure = it }
        }
        error("禁漫图片参数获取失败：${lastFailure?.message.orEmpty()}")
    }

    private fun headers(timestamp: String, secret: String) = mapOf(
        "token" to md5Hex(timestamp + secret),
        "tokenparam" to "$timestamp,$APP_VERSION",
        "Cookie" to "AVS=1",
        "X-Requested-With" to "com.JMComic3.app",
    )

    // The upstream wire format is fixed to AES-ECB; this is protocol decoding,
    // not storage encryption. User credentials and tokens never use this cipher.
    @SuppressLint("GetInstance")
    private fun decrypt(encoded: String, timestamp: String): String {
        val key = md5Hex(timestamp + BuildConfig.JM_DATA_SECRET).toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(Base64.decode(encoded, Base64.DEFAULT)).toString(StandardCharsets.UTF_8)
    }

    private fun coverUrl(id: String) = "https://${IMAGE_DOMAINS.first()}/media/albums/${id}_3x4.jpg"

    private fun jsonStrings(array: JSONArray?): List<String> = array?.let {
        List(it.length()) { index -> it.optString(index) }.filter(String::isNotBlank)
    }.orEmpty()

    private fun ensureProtocolConfigured() {
        check(
            BuildConfig.JM_TOKEN_SECRET.isNotBlank() &&
                BuildConfig.JM_TOKEN_SECRET_2.isNotBlank() &&
                BuildConfig.JM_DATA_SECRET.isNotBlank(),
        ) { "此构建未配置禁漫协议参数" }
    }

    private fun md5Hex(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val APP_VERSION = "2.1.2"
        const val DEFAULT_SCRAMBLE_ID = 220980
        val SCRAMBLE_PATTERN = Regex("var\\s+scramble_id\\s*=\\s*(\\d+)")
        val API_DOMAINS = listOf("www.cdnhjk.net", "www.cdngwc.cc", "www.cdngwc.net", "www.cdngwc.club")
        val IMAGE_DOMAINS = listOf(
            "cdn-msp.jmapiproxy1.cc",
            "cdn-msp.jmapiproxy2.cc",
            "cdn-msp2.jmapiproxy2.cc",
            "cdn-msp3.jmapiproxy2.cc",
            "cdn-msp.jmapinodeudzn.net",
        )
    }
}

internal object JmImageScrambler {
    fun segmentCount(scrambleId: Int, photoId: String, filename: String): Int {
        val id = photoId.toIntOrNull() ?: return 0
        if (id < scrambleId) return 0
        if (id < SCRAMBLE_ALGORITHM_V2) return 10
        val modulus = if (id < SCRAMBLE_ALGORITHM_V3) 10 else 8
        // The JM protocol hashes the basename only. Including .jpg/.webp changes
        // the segment count and leaves the decoded page as offset horizontal bands.
        val imageName = filename.substringAfterLast('/').substringBefore('?').substringBeforeLast('.')
        val digest = MessageDigest.getInstance("MD5")
            .digest("$id$imageName".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return (digest.last().code % modulus) * 2 + 2
    }

    private const val SCRAMBLE_ALGORITHM_V2 = 268850
    private const val SCRAMBLE_ALGORITHM_V3 = 421926
}
