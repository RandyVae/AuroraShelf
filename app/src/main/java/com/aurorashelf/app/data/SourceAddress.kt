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
        val sanitizedBase = sanitizeUriInput(base)
        val sanitizedRef = sanitizeUriInput(reference)
        val uri = URI(sanitizedBase).resolve(sanitizedRef)
        uri.toString().takeIf { uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null }
    }.getOrNull()

    private fun sanitizeUriInput(raw: String): String {
        val trimmed = raw.trim()
        val schemeIdx = trimmed.indexOf("://")
        val pathStart = if (schemeIdx >= 0) trimmed.indexOf('/', schemeIdx + 3) else 0
        val effectiveStart = if (pathStart < 0) trimmed.length else pathStart
        val sb = StringBuilder(trimmed.length + 16)
        for (i in trimmed.indices) {
            val ch = trimmed[i]
            if (i < effectiveStart) {
                sb.append(ch)
            } else {
                when (ch) {
                    ' ' -> sb.append("%20")
                    '\u3000' -> sb.append("%E3%80%80")
                    '[' -> sb.append("%5B")
                    ']' -> sb.append("%5D")
                    '{' -> sb.append("%7B")
                    '}' -> sb.append("%7D")
                    '|' -> sb.append("%7C")
                    '^' -> sb.append("%5E")
                    '`' -> sb.append("%60")
                    '"' -> sb.append("%22")
                    '<' -> sb.append("%3C")
                    '>' -> sb.append("%3E")
                    '\\' -> sb.append("%5C")
                    else -> sb.append(ch)
                }
            }
        }
        return sb.toString()
    }
}
