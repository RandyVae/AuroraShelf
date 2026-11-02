package com.aurorashelf.app.game

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class GameModuleTest {

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)

        override fun getString(key: String?, defValue: String?): String? =
            data[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            data[key] as? MutableSet<String> ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            (data[key] as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            (data[key] as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (data[key] as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (data[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearPending = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = this
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearPending = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearPending) data.clear()
                for ((k, v) in pending) {
                    if (v === this) data.remove(k) else data[k] = v
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private fun createFakeContext(sharedPreferences: SharedPreferences): Context {
        return object : android.content.ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return sharedPreferences
            }
        }
    }

    @Test
    fun gameSaveBridge_roundTripsState() {
        val prefs = InMemorySharedPreferences()
        val context = createFakeContext(prefs)
        val bridge = GameSaveBridge(context)

        assertEquals("", bridge.get())

        bridge.set("""{"gold":999,"heroes":["valkyrie"]}""")
        assertEquals("""{"gold":999,"heroes":["valkyrie"]}""", bridge.get())

        bridge.clear()
        assertEquals("", bridge.get())
    }

    @Test
    fun gameSaveBridge_handlesNullGracefully() {
        val prefs = InMemorySharedPreferences()
        val context = createFakeContext(prefs)
        val bridge = GameSaveBridge(context)

        bridge.set(null)
        assertEquals("", bridge.get())
    }

    @Test
    fun gameAssetServer_mimeTypeCoverage() {
        val companion = GameAssetServer::class.java.getDeclaredField("MIME")
        companion.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val mimeMap = companion.get(null) as Map<String, String>

        assertEquals("text/html; charset=utf-8", mimeMap["html"])
        assertEquals("application/javascript; charset=utf-8", mimeMap["js"])
        assertEquals("video/mp4", mimeMap["mp4"])
        assertEquals("audio/mp4", mimeMap["m4a"])
        assertEquals("image/webp", mimeMap["webp"])
        assertEquals("application/octet-stream", mimeMap["skel"])
        assertEquals("text/plain; charset=utf-8", mimeMap["atlas"])
    }

    @Test
    fun gameAssetServer_rangePatternMatchesExpectedHeaders() {
        val companion = GameAssetServer::class.java.getDeclaredField("RANGE_PATTERN")
        companion.isAccessible = true
        val pattern = companion.get(null) as java.util.regex.Pattern

        val matcher1 = pattern.matcher("bytes=0-1024")
        assertTrue(matcher1.find())
        assertEquals("0", matcher1.group(1))
        assertEquals("1024", matcher1.group(2))

        val matcher2 = pattern.matcher("bytes=2048-")
        assertTrue(matcher2.find())
        assertEquals("2048", matcher2.group(1))
        assertEquals("", matcher2.group(2))
    }
}
