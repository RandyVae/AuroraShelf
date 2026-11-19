package com.aurorashelf.app.data.resilience

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 零单点故障（Zero-SPOF）远端自愈配置清单（https://bananarey.us/source-manifest.json）。
 *
 * 设计原则：
 * - 启动时在后台非阻塞、3 秒短超时同步，绝不阻塞首屏渲染；
 * - 即使 OTA 配置伺服器完全宕机或无法访问，本地内置镜像池 + 门户自探测依然 100% 独立运作；
 * - 成功拉取后缓存在本地 SharedPreferences，随时热更新最新镜像域名或备用 CSS 选择器提示。
 */
internal object RemoteSourceManifest {
    private const val TAG = "RemoteSourceManifest"
    private const val MANIFEST_URL = "https://bananarey.us/source-manifest.json"
    private const val PREFS_NAME = "aurora_remote_manifest"
    private const val KEY_JSON = "manifest_json_v1"

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var customCardSelectors: Map<String, List<String>> = emptyMap()

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedJson = prefs.getString(KEY_JSON, null)
        if (!cachedJson.isNullOrBlank()) {
            applyManifestJson(cachedJson)
        }
        syncAsync(context.applicationContext)
    }

    fun syncAsync(context: Context) {
        syncScope.launch {
            runCatching {
                val res = AuroraHttpClient.fetchText(
                    url = MANIFEST_URL,
                    sourceKey = null,
                    timeoutMs = 3_000L,
                    learnRedirectMirror = false,
                )
                if (applyManifestJson(res.body)) {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                        putString(KEY_JSON, res.body)
                    }
                    Log.i(TAG, "synced remote source manifest latency=${res.latencyMs}ms")
                }
            }
        }
    }

    fun cardSelectorsFor(sourceKey: String): List<String> =
        customCardSelectors[sourceKey].orEmpty()

    internal fun applyManifestJson(rawJson: String): Boolean {
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return false
        val mirrorsObj = root.optJSONObject("mirrors")
        if (mirrorsObj != null) {
            val map = linkedMapOf<String, List<String>>()
            mirrorsObj.keys().forEach { key ->
                val arr = mirrorsObj.optJSONArray(key) ?: return@forEach
                val urls = buildList {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().takeIf { it.startsWith("http") }?.let(::add)
                    }
                }
                if (urls.isNotEmpty()) map[key] = urls
            }
            if (map.isNotEmpty()) {
                SourceHealthRegistry.applyManifestMirrors(map)
            }
        }
        val selectorsObj = root.optJSONObject("cardSelectors")
        if (selectorsObj != null) {
            val map = linkedMapOf<String, List<String>>()
            selectorsObj.keys().forEach { key ->
                val arr = selectorsObj.optJSONArray(key) ?: return@forEach
                val selectors = buildList {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let(::add)
                    }
                }
                if (selectors.isNotEmpty()) map[key] = selectors
            }
            customCardSelectors = map
        }
        return true
    }
}
