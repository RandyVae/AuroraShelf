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
    private val writeLock = Any()

    private fun resolvePrefKey(rawKey: String?): String {
        val clean = rawKey?.trim().orEmpty()
        if (clean.isEmpty() || clean == PRIMARY_WEB_KEY || clean == LEGACY_WEB_KEY) return KEY
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
        val safeValue = value ?: ""
        if (safeValue.length > MAX_VALUE_CHARS) return
        synchronized(writeLock) {
            prefs.edit { putString(KEY, safeValue) }
        }
    }

    @JavascriptInterface
    fun setKey(key: String?, value: String?) {
        val safeValue = value ?: ""
        if (safeValue.length > MAX_VALUE_CHARS) return
        val resolved = resolvePrefKey(key)
        synchronized(writeLock) {
            if (!prefs.contains(resolved) && prefs.all.size >= MAX_KEYS) {
                return
            }
            prefs.edit { putString(resolved, safeValue) }
        }
    }

    @JavascriptInterface
    fun clear() {
        synchronized(writeLock) {
            prefs.edit { remove(KEY) }
        }
    }

    @JavascriptInterface
    fun clearKey(key: String?) {
        val resolved = resolvePrefKey(key)
        synchronized(writeLock) {
            prefs.edit { remove(resolved) }
        }
    }

    @JavascriptInterface
    fun flush() {
        flushSync()
    }

    fun flushSync() {
        synchronized(writeLock) {
            prefs.edit().commit()
        }
    }

    companion object {
        private const val PREFS = "lg_local_save"
        private const val KEY = "state"
        internal const val PRIMARY_WEB_KEY = "lg-local-edition-v2"
        private const val LEGACY_WEB_KEY = "lg_save_v4"
        internal const val MAX_VALUE_CHARS = 2 * 1024 * 1024
        internal const val MAX_KEYS = 32
    }
}
