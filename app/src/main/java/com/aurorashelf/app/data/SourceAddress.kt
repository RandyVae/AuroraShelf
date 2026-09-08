package com.aurorashelf.app.data

import java.net.URI

/** Validates user-entered addresses before persistence and network use. */
object SourceAddress {
    fun error(value: String): String? {
        if (value.isBlank()) return "站点地址不能为空，可恢复默认站点"
        val uri = runCatching { URI(value.trim()) }.getOrNull()
            ?: return "请输入有效网址，例如 https://example.com"
        return when {
            uri.scheme !in setOf("https", "http") -> "网址必须以 https:// 或 http:// 开头"
            uri.host.isNullOrBlank() -> "网址缺少有效域名"
            uri.rawUserInfo != null -> "请勿在网址中填写用户名或密码"
            uri.rawQuery != null || uri.rawFragment != null -> "请填写站点根地址，不包含查询参数或锚点"
            else -> null
        }
    }

    fun resolve(base: String, reference: String): String? = runCatching {
        if (reference.isBlank()) return null
        val uri = URI(base).resolve(reference.trim())
        uri.toString().takeIf { uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null }
    }.getOrNull()
}
