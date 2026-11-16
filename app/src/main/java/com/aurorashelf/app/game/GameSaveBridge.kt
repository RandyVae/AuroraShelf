package com.aurorashelf.app.game

import android.content.Context
import android.content.SharedPreferences
import android.webkit.JavascriptInterface
import androidx.core.content.edit

/**
 * Durable save bridge so progress survives process death regardless of WebView storage.
 */
class GameSaveBridge(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun resolvePrefKey(rawKey: String?): String {
        val clean = rawKey?.trim().orEmpty()
        if (clean.isEmpty() || clean == PRIMARY_WEB_KEY) return KEY
        return "key_${clean.take(64)}"
    }

    @JavascriptInterface
    fun get(): String {
        return prefs.getString(KEY, "") ?: ""
    }

    @JavascriptInterface
    fun getKey(key: String?): String {
        return prefs.getString(resolvePrefKey(key), "") ?: ""
    }

    @JavascriptInterface
    fun set(value: String?) {
        prefs.edit { putString(KEY, value ?: "") }
    }

    @JavascriptInterface
    fun setKey(key: String?, value: String?) {
        prefs.edit { putString(resolvePrefKey(key), value ?: "") }
    }

    @JavascriptInterface
    fun clear() {
        prefs.edit { remove(KEY) }
    }

    @JavascriptInterface
    fun clearKey(key: String?) {
        prefs.edit { remove(resolvePrefKey(key)) }
    }

    companion object {
        private const val PREFS = "lg_local_save"
        private const val KEY = "state"
        private const val PRIMARY_WEB_KEY = "lg_save_v4"
    }
}
