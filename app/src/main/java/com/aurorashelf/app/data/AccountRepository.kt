package com.aurorashelf.app.data

import android.content.Context
import com.aurorashelf.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

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

/** Client for the isolated PocketBase account service. Anonymous content access never uses it. */
class AccountRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val _state = MutableStateFlow(loadState())

    val state: StateFlow<AccountState> = _state.asStateFlow()

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
            val created = request("/api/collections/users/records", body) {
                AccountResult.Success()
            }
            if (created is AccountResult.Failure) return@withContext created
            when (val signedIn = signIn(normalizedEmail, password)) {
                is AccountResult.Success -> AccountResult.Success("注册成功，请检查邮箱完成验证")
                is AccountResult.Failure -> signedIn
            }
        }

    suspend fun signIn(email: String, password: String): AccountResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("identity", email.trim()).put("password", password)
        request("/api/collections/users/auth-with-password", body) { response ->
            val profile = parseProfile(response.getJSONObject("record"))
            persistProfile(profile, response.optString("token"))
            AccountResult.Success()
        }
    }

    suspend fun requestVerification(): AccountResult = withContext(Dispatchers.IO) {
        request("/api/collections/users/request-verification", JSONObject().put("email", currentEmail())) {
            AccountResult.Success("验证邮件已发送")
        }
    }

    suspend fun requestPasswordReset(email: String): AccountResult = withContext(Dispatchers.IO) {
        request("/api/collections/users/request-password-reset", JSONObject().put("email", email.trim())) {
            AccountResult.Success("如果邮箱已注册，重置邮件将很快送达")
        }
    }

    suspend fun refresh(): AccountResult = withContext(Dispatchers.IO) {
        val token = preferences.getString(KEY_TOKEN, null)
            ?: return@withContext AccountResult.Failure("未登录")
        request("/api/collections/users/auth-refresh", JSONObject(), token) { response ->
            val profile = parseProfile(response.getJSONObject("record"))
            persistProfile(profile, response.optString("token", token))
            AccountResult.Success()
        }
    }

    fun signOut() {
        preferences.edit().remove(KEY_TOKEN).remove(KEY_PROFILE).apply()
        _state.value = AccountState.SignedOut
    }

    private fun currentEmail(): String = preferences.getString(KEY_EMAIL, "").orEmpty()

    private fun loadState(): AccountState {
        val profileJson = preferences.getString(KEY_PROFILE, null) ?: return AccountState.SignedOut
        return runCatching { AccountState.SignedIn(parseProfile(JSONObject(profileJson))) }
            .getOrDefault(AccountState.SignedOut)
    }

    private fun persistProfile(profile: AccountProfile, token: String) {
        if (token.isBlank()) throw IOException("认证服务未返回会话")
        preferences.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, profile.email)
            .putString(KEY_PROFILE, profile.toJson().toString())
            .apply()
        _state.value = AccountState.SignedIn(profile)
    }

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
        token: String? = preferences.getString(KEY_TOKEN, null),
        onSuccess: (JSONObject) -> AccountResult,
    ): AccountResult {
        return try {
            val request = Request.Builder()
                .url(BuildConfig.AUTH_API_BASE_URL.trimEnd('/') + path)
                .post(body.toString().toRequestBody(jsonType))
                .apply { if (!token.isNullOrBlank()) header("Authorization", token) }
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
                if (!response.isSuccessful) {
                    AccountResult.Failure(json.optString("message").ifBlank { "账号请求失败（HTTP ${response.code}）" })
                } else {
                    onSuccess(json)
                }
            }
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
        private const val PREFERENCES_NAME = "netgrey_account"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_PROFILE = "auth_profile"
        private const val KEY_EMAIL = "auth_email"
        private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        @Volatile private var instance: AccountRepository? = null

        fun get(context: Context): AccountRepository = instance ?: synchronized(this) {
            instance ?: AccountRepository(context).also { instance = it }
        }
    }
}
