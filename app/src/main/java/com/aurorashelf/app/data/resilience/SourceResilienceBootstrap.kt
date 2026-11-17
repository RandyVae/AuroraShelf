package com.aurorashelf.app.data.resilience

import android.content.Context
import android.util.Log
import com.aurorashelf.app.model.ContentSourceCatalog

/**
 * 启动期统一初始化与预热入口。
 */
internal object SourceResilienceBootstrap {
    private const val TAG = "AuroraResilience"

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        SourceHealthRegistry.initialize(appContext)
        SourceSnapshotStore.initialize(appContext)
        RemoteSourceManifest.initialize(appContext)

        // 按当前健康度排序并预热核心国内直连及高频源的 TCP/TLS 连接池（仅发极轻 HEAD 请求，不下载正文）
        val warmOrigins = buildList {
            add(ContentSourceCatalog.HUAVOD_BASE_URL)
            add(ContentSourceCatalog.HUANGGUO_BASE_URL)
            add(ContentSourceCatalog.T91DONGMAN_BASE_URL)
            addAll(SourceHealthRegistry.resolveOrigins("91porn").take(2))
            addAll(SourceHealthRegistry.resolveOrigins("copy").take(2))
            addAll(SourceHealthRegistry.resolveOrigins("baozi").take(1))
            addAll(SourceHealthRegistry.resolveOrigins("t906").take(1))
        }
        AuroraHttpClient.prewarmOriginsAsync(warmOrigins)
        runCatching { Log.i(TAG, "resilience bootstrap ready origins=${warmOrigins.size}") }
    }
}
