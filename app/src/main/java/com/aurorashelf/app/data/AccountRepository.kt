package com.aurorashelf.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.aurorashelf.app.BuildConfig
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class AccountProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val isVerified: Boolean,
    val subscriptionStatus: String = "free",
)

sealed interface AccountState {
    data object SignedOut : AccountState
    data class SignedIn(val profile: AccountProfile) : AccountState
}

sealed class AccountResult {
    data class Success(val message: String? = null) : AccountResult()
    data class Failure(val message: String) : AccountResult()
}

internal interface AccountTokenCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

internal object AndroidKeyStoreAccountTokenCipher : AccountTokenCipher {
    private const val KEY_ALIAS = "aurora_shelf_account_auth"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @Synchronized
    override fun encrypt(plainText: String): String {
        return try {
            encryptOnce(plainText)
        } catch (_: GeneralSecurityException) {
            deleteKeyQuietly()
            encryptOnce(plainText)
        }
    }

    @Synchronized
    override fun decrypt(cipherText: String): String {
        val payload = ByteBuffer.wrap(Base64.decode(cipherText, Base64.NO_WRAP))
        val ivSize = payload.int
        require(ivSize in 12..32 && payload.remaining() > ivSize) { "Invalid encrypted token" }
        val iv = ByteArray(ivSize).also(payload::get)
        val encrypted = ByteArray(payload.remaining()).also(payload::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(StandardCharsets.UTF_8)
    }

    private fun encryptOnce(value: String): String {
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

    private fun deleteKeyQuietly() {
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        }
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
}

/** Client for the isolated PocketBase account service. Anonymous content access never uses it. */
class AccountRepository internal constructor(
    private val preferences: SharedPreferences,
    private val client: OkHttpClient = defaultAuthClient(),
    private val baseUrl: String = BuildConfig.AUTH_API_BASE_URL,
    private val tokenCipher: AccountTokenCipher = AndroidKeyStoreAccountTokenCipher,
) {
    private constructor(context: Context) : this(
        preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val authMutex = Mutex()
    private val sessionLock = Any()
    private val authGeneration = AtomicLong(0L)
    private val _state = MutableStateFlow(loadState())

    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        require(baseUrl.trim().startsWith("https://", ignoreCase = true)) {
            "AUTH_API_BASE_URL must use HTTPS"
        }
    }

    suspend fun register(email: String, password: String, displayName: String): AccountResult =
        withContext(Dispatchers.IO) {
            val normalizedEmail = email.trim().lowercase()
            if (!EMAIL_PATTERN.matches(normalizedEmail)) return@withContext AccountResult.Failure("请输入有效邮箱")
            if (password.length < 8) return@withContext AccountResult.Failure("密码至少需要 8 位")
            val body = JSONObject()
                .put("email", normalizedEmail)
                .put("password", password)
                .put("passwordConfirm", password)
                .put("name", displayName.trim())
            val created = authMutex.withLock {
                request(
                    path = "/api/collections/users/records",
                    body = body,
                    includeAuth = false,
                    preserveSession = true,
                ) {
                    AccountResult.Success()
                }
            }
            if (created is AccountResult.Failure) return@withContext created
            when (val signedIn = signIn(normalizedEmail, password)) {
                is AccountResult.Success -> AccountResult.Success("注册成功，请检查邮箱完成验证")
                is AccountResult.Failure -> signedIn
            }
        }

    suspend fun signIn(email: String, password: String): AccountResult = withContext(Dispatchers.IO) {
        authMutex.withLock {
            val gen = authGeneration.get()
            val body = JSONObject().put("identity", email.trim()).put("password", password)
            request(
                path = "/api/collections/users/auth-with-password",
                body = body,
                includeAuth = false,
                preserveSession = true,
            ) { response ->
                val profile = parseProfile(response.getJSONObject("record"))
                val token = response.optString("token").takeIf { it.isNotBlank() && it != "null" }
                    ?: throw IOException("认证服务未返回会话")
                if (!persistProfile(profile, token, expectedGeneration = gen)) {
                    return@request AccountResult.Failure("登录状态已变更，请重试")
                }
                AccountResult.Success()
            }
        }
    }

    suspend fun requestVerification(): AccountResult = withContext(Dispatchers.IO) {
        val email = (state.value as? AccountState.SignedIn)?.profile?.email?.takeIf { it.isNotBlank() }
            ?: currentEmail()
        if (email.isBlank()) {
            return@withContext AccountResult.Failure("当前未找到可验证的账号邮箱")
        }
        request(
            path = "/api/collections/users/request-verification",
            body = JSONObject().put("email", email),
            token = loadToken(),
            includeAuth = true,
            preserveSession = true,
        ) {
            AccountResult.Success("验证邮件已发送，请检查收件箱或垃圾箱")
        }
    }

    suspend fun requestPasswordReset(email: String): AccountResult = withContext(Dispatchers.IO) {
        val normalized = email.trim().lowercase()
        if (!EMAIL_PATTERN.matches(normalized)) {
            return@withContext AccountResult.Failure("请输入有效邮箱后再请求重置密码")
        }
        request(
            path = "/api/collections/users/request-password-reset",
            body = JSONObject().put("email", normalized),
            includeAuth = false,
            preserveSession = true,
        ) {
            AccountResult.Success("如果邮箱已注册，重置邮件将很快送达")
        }
    }

    suspend fun refresh(): AccountResult = withContext(Dispatchers.IO) {
        authMutex.withLock {
            val gen = authGeneration.get()
            val token = loadToken()
                ?: return@withContext AccountResult.Failure("未登录")
            request(
                path = "/api/collections/users/auth-refresh",
                body = JSONObject(),
                token = token,
                includeAuth = true,
                preserveSession = false,
            ) { response ->
                val profile = parseProfile(response.getJSONObject("record"))
                val nextToken = response.optString("token").takeIf { it.isNotBlank() && it != "null" } ?: token
                if (!persistProfile(profile, nextToken, expectedGeneration = gen)) {
                    return@request AccountResult.Failure("已退出登录")
                }
                AccountResult.Success()
            }
        }
    }

    fun signOut() {
        synchronized(sessionLock) {
            authGeneration.incrementAndGet()
            preferences.edit {
                remove(KEY_TOKEN)
                remove(KEY_TOKEN_ENC)
                remove(KEY_PROFILE)
                remove(KEY_EMAIL)
            }
            _state.value = AccountState.SignedOut
        }
    }

    private fun currentEmail(): String = preferences.getString(KEY_EMAIL, "").orEmpty()

    private fun loadState(): AccountState {
        val profileJson = preferences.getString(KEY_PROFILE, null) ?: return AccountState.SignedOut
        if (loadToken().isNullOrBlank()) return AccountState.SignedOut
        return runCatching { AccountState.SignedIn(parseProfile(JSONObject(profileJson))) }
            .getOrDefault(AccountState.SignedOut)
    }

    internal fun loadToken(): String? {
        synchronized(sessionLock) {
            val encrypted = preferences.getString(KEY_TOKEN_ENC, null)
            if (!encrypted.isNullOrBlank()) {
                val decrypted = runCatching { tokenCipher.decrypt(encrypted) }.getOrNull()
                if (!decrypted.isNullOrBlank()) return decrypted
            }
            // Migrate any legacy plaintext token to AES-GCM and delete the plaintext copy.
            val legacyPlain = preferences.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
            val migratedCipher = runCatching { tokenCipher.encrypt(legacyPlain) }.getOrNull()
            preferences.edit {
                if (migratedCipher != null) {
                    putString(KEY_TOKEN_ENC, migratedCipher)
                }
                remove(KEY_TOKEN)
            }
            return if (migratedCipher != null) legacyPlain else null
        }
    }

    internal fun persistProfile(
        profile: AccountProfile,
        token: String,
        expectedGeneration: Long = authGeneration.get(),
    ): Boolean {
        if (token.isBlank()) throw IOException("认证服务未返回会话")
        val cipherText = try {
            tokenCipher.encrypt(token)
        } catch (e: Exception) {
            throw IOException("无法加密保存登录凭证", e)
        }
        synchronized(sessionLock) {
            if (authGeneration.get() != expectedGeneration) {
                return false
            }
            preferences.edit {
                putString(KEY_TOKEN_ENC, cipherText)
                remove(KEY_TOKEN)
                putString(KEY_EMAIL, profile.email)
                putString(KEY_PROFILE, profile.toJson().toString())
            }
            _state.value = AccountState.SignedIn(profile)
            return true
        }
    }

    internal fun currentGeneration(): Long = authGeneration.get()

    private fun parseProfile(record: JSONObject): AccountProfile = AccountProfile(
        id = record.optString("id"),
        email = record.optString("email"),
        displayName = record.optString("name").ifBlank { record.optString("email") },
        isVerified = record.optBoolean("verified", false),
        subscriptionStatus = record.optString("subscriptionStatus", "free").ifBlank { "free" },
    )

    private fun request(
        path: String,
        body: JSONObject,
        token: String? = null,
        includeAuth: Boolean = true,
        preserveSession: Boolean = false,
        onSuccess: (JSONObject) -> AccountResult,
    ): AccountResult {
        val sanitizedBase = baseUrl.trim().trimEnd('/')
        if (!sanitizedBase.startsWith("https://", ignoreCase = true)) {
            return AccountResult.Failure("认证服务必须使用 HTTPS 安全连接")
        }
        return try {
            val effectiveToken = if (includeAuth) (token ?: loadToken()) else null
            val request = Request.Builder()
                .url(sanitizedBase + path)
                .post(body.toString().toRequestBody(jsonType))
                .apply {
                    if (includeAuth && !effectiveToken.isNullOrBlank()) {
                        header("Authorization", effectiveToken)
                    }
                }
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
                if (!response.isSuccessful) {
                    if (shouldSignOutOnHttpError(response.code, includeAuth = includeAuth, preserveSession = preserveSession)) {
                        signOut()
                    }
                    AccountResult.Failure(json.optString("message").ifBlank { "账号请求失败（HTTP ${response.code}）" })
                } else {
                    onSuccess(json)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AccountResult.Failure(error.message ?: "账号服务暂时不可用")
        }
    }

    private fun AccountProfile.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("email", email)
        .put("name", displayName)
        .put("verified", isVerified)
        .put("subscriptionStatus", subscriptionStatus)

    companion object {
        internal const val PREFERENCES_NAME = "netgrey_account"
        internal const val KEY_TOKEN = "auth_token"
        internal const val KEY_TOKEN_ENC = "auth_token_enc"
        internal const val KEY_PROFILE = "auth_profile"
        internal const val KEY_EMAIL = "auth_email"
        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        @Volatile private var instance: AccountRepository? = null

        fun get(context: Context): AccountRepository = instance ?: synchronized(this) {
            instance ?: AccountRepository(context).also { instance = it }
        }

        internal fun shouldSignOutOnHttpError(
            code: Int,
            includeAuth: Boolean,
            preserveSession: Boolean,
        ): Boolean = (code == 401 || code == 403) && includeAuth && !preserveSession

        private fun defaultAuthClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
