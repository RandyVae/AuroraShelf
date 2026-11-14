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

    @JavascriptInterface
    fun get(): String {
        return prefs.getString(KEY, "") ?: ""
    }

    @JavascriptInterface
    fun set(value: String?) {
        prefs.edit { putString(KEY, value ?: "") }
    }

    @JavascriptInterface
    fun clear() {
        prefs.edit { remove(KEY) }
    }

    companion object {
        private const val PREFS = "lg_local_save"
        private const val KEY = "state"
    }
}
