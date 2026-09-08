package com.aurorashelf.app.data.comic

import com.aurorashelf.app.BuildConfig
import com.aurorashelf.app.model.ComicCategory
import com.aurorashelf.app.model.ComicChapter
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ComicSummary
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class PicacgComicSource(
    private val authStore: ComicAuthStore,
) : AuthenticatedComicSource {
    override val info = ComicSourceInfo(
        id = "picacg",
        name = "哔咔",
        description = "哔咔漫画 · 需要账号授权",
        categories = listOf(
            ComicCategory("dd", "最新"),
            ComicCategory("ld", "最多喜欢"),
            ComicCategory("vd", "最多浏览"),
        ),
        requiresAuthentication = true,
    )

    override val isAuthenticated: Boolean
        get() = authStore.picacgToken.isNotBlank()

    override suspend fun authenticate(account: String, password: String) = withContext(Dispatchers.IO) {
        require(account.isNotBlank() && password.isNotBlank()) { "请输入哔咔账号和密码" }
        ensureProtocolConfigured()
        val json = request(
            method = "POST",
            path = "auth/sign-in",
            token = "",
            body = JSONObject().put("email", account.trim()).put("password", password).toString(),
        )
        val token = json.optJSONObject("data")?.optString("token").orEmpty()
        check(token.isNotBlank()) { json.optString("message").ifBlank { "哔咔授权失败" } }
        authStore.picacgToken = token
    }

    override fun signOut() {
        authStore.picacgToken = ""
    }

    override suspend fun browse(categoryId: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val sort = categoryId.takeIf { it in setOf("dd", "ld", "vd") } ?: "dd"
        parseComics(request("GET", "comics?page=$page&s=$sort").path("data", "comics").optJSONArray("docs"))
    }

    override suspend fun search(query: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("keyword", query).put("sort", "dd").toString()
        parseComics(request("POST", "comics/advanced-search?page=$page", body = body).path("data", "comics").optJSONArray("docs"))
    }

    override suspend fun details(comic: ComicSummary): ComicDetails = withContext(Dispatchers.IO) {
        val item = request("GET", "comics/${comic.id}").path("data", "comic")
        val chapters = mutableListOf<ComicChapter>()
        var page = 1
        do {
            val eps = request("GET", "comics/${comic.id}/eps?page=$page").path("data", "eps")
            val docs = eps.optJSONArray("docs") ?: JSONArray()
            repeat(docs.length()) { index ->
                val chapter = docs.optJSONObject(index) ?: return@repeat
                val order = chapter.optInt("order", chapters.size + 1)
                chapters += ComicChapter(order.toString(), chapter.optString("title").ifBlank { "第 $order 话" })
            }
            page++
        } while (page <= eps.optInt("pages", 1))
        ComicDetails(
            comic = parseComic(item) ?: comic,
            description = item.optString("description"),
            chapters = chapters.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE },
        )
    }

    override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<ComicPage>()
        var page = 1
        do {
            val result = request("GET", "comics/${comic.id}/order/$chapterId/pages?page=$page").path("data", "pages")
            val docs = result.optJSONArray("docs") ?: JSONArray()
            repeat(docs.length()) { index ->
                mediaUrl(docs.optJSONObject(index)?.optJSONObject("media"))?.let { url ->
                    pages += ComicPage(url, API_ROOT)
                }
            }
            page++
        } while (page <= result.optInt("pages", 1))
        pages
    }

    private fun request(method: String, path: String, token: String = authStore.picacgToken, body: String? = null): JSONObject {
        ensureProtocolConfigured()
        if (path != "auth/sign-in") check(token.isNotBlank()) { "请先授权哔咔账号" }
        val headers = signedHeaders(method, path, token)
        val response = if (body == null) {
            ComicHttp.getResponse("$API_ROOT/$path", headers)
        } else {
            ComicHttp.postJsonResponse("$API_ROOT/$path", body, headers)
        }
        val json = runCatching { JSONObject(response.body) }.getOrElse {
            error("哔咔返回了无法识别的数据（HTTP ${response.status}）")
        }
        if (response.status == 401) {
            authStore.picacgToken = ""
            error("哔咔授权已失效，请重新登录")
        }
        if (response.status !in 200..299 || json.optString("message") !in setOf("", "success")) {
            error(json.optString("message").ifBlank { "哔咔返回 HTTP ${response.status}" })
        }
        return json
    }

    private fun signedHeaders(method: String, path: String, token: String): Map<String, String> {
        val time = (System.currentTimeMillis() / 1_000L).toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val payload = (path + time + nonce + method.uppercase() + BuildConfig.PICACG_API_KEY).lowercase()
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(BuildConfig.PICACG_SIGNATURE_SECRET.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        }
        val signature = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return mapOf(
            "api-key" to BuildConfig.PICACG_API_KEY,
            "accept" to "application/vnd.picacomic.com.v1+json",
            "app-channel" to "3",
            "authorization" to token,
            "time" to time,
            "nonce" to nonce,
            "app-version" to "2.2.1.3.3.4",
            "app-uuid" to "defaultUuid",
            "image-quality" to "original",
            "app-platform" to "android",
            "app-build-version" to "45",
            "version" to "v1.4.1",
            "signature" to signature,
        )
    }

    private fun parseComics(items: JSONArray?): List<ComicSummary> = items?.let { array ->
        List(array.length()) { parseComic(array.optJSONObject(it)) }.filterNotNull()
    }.orEmpty()

    private fun parseComic(item: JSONObject?): ComicSummary? {
        item ?: return null
        val id = item.optString("_id").ifBlank { item.optString("id") }
        val title = item.optString("title")
        if (id.isBlank() || title.isBlank()) return null
        return ComicSummary(
            sourceId = info.id,
            id = id,
            title = title,
            subtitle = item.optString("author"),
            coverUrl = mediaUrl(item.optJSONObject("thumb")),
            tags = buildList {
                addStrings(item.optJSONArray("categories"))
                addStrings(item.optJSONArray("tags"))
            }.distinct(),
        )
    }

    private fun MutableList<String>.addStrings(array: JSONArray?) {
        if (array == null) return
        repeat(array.length()) { index -> array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
    }

    private fun mediaUrl(media: JSONObject?): String? {
        media ?: return null
        val server = media.optString("fileServer").trimEnd('/')
        val path = media.optString("path").trimStart('/')
        return if (server.isBlank() || path.isBlank()) null else "$server/static/$path"
    }

    private fun JSONObject.path(vararg names: String): JSONObject {
        var current = this
        names.forEach { current = current.optJSONObject(it) ?: error("哔咔响应缺少 ${names.joinToString(".")}") }
        return current
    }

    private fun ensureProtocolConfigured() {
        check(BuildConfig.PICACG_API_KEY.isNotBlank() && BuildConfig.PICACG_SIGNATURE_SECRET.isNotBlank()) {
            "此构建未配置哔咔协议参数"
        }
    }

    private companion object {
        const val API_ROOT = "https://picaapi.picacomic.com"
    }
}
