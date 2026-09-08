package com.aurorashelf.app.data.comic

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface ComicAuthStore {
    var picacgToken: String
}

internal object EmptyComicAuthStore : ComicAuthStore {
    override var picacgToken: String
        get() = ""
        set(_) = Unit
}

internal class EncryptedComicAuthStore(context: Context) : ComicAuthStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override var picacgToken: String
        get() {
            val encoded = preferences.getString(KEY_PICACG_TOKEN, "").orEmpty()
            if (encoded.isBlank()) return ""
            return runCatching { decrypt(encoded) }.getOrElse { error ->
                Log.w(TAG, "Stored comic token could not be decrypted", error)
                preferences.edit { remove(KEY_PICACG_TOKEN) }
                ""
            }
        }
        set(value) {
            preferences.edit {
                if (value.isBlank()) remove(KEY_PICACG_TOKEN) else putString(KEY_PICACG_TOKEN, encrypt(value))
            }
        }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteBuffer.allocate(Int.SIZE_BYTES + cipher.iv.size + encrypted.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
        val ivSize = payload.int
        require(ivSize in 12..32 && payload.remaining() > ivSize) { "Invalid encrypted token" }
        val iv = ByteArray(ivSize).also(payload::get)
        val encrypted = ByteArray(payload.remaining()).also(payload::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(StandardCharsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val TAG = "ComicAuthStore"
        const val PREFERENCES_NAME = "comic_auth"
        const val KEY_PICACG_TOKEN = "picacg_token"
        const val KEY_ALIAS = "aurora_shelf_comic_auth"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
