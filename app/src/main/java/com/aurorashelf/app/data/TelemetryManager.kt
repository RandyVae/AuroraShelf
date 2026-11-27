package com.aurorashelf.app.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.edit
import com.aurorashelf.app.BuildConfig
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal data class TelemetryEvent(
    val eventId: String,
    val type: String,
    val sessionId: String,
    val section: String,
    val subSection: String,
    val durationSec: Int,
    val ts: Double,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("event_id", eventId)
        .put("type", type)
        .put("session_id", sessionId)
        .put("section", section)
        .put("sub_section", subSection)
        .put("duration_sec", durationSec)
        .put("ts", ts)

    companion object {
        fun fromJson(obj: JSONObject): TelemetryEvent? {
            val eventId = obj.optString("event_id").trim()
            val type = obj.optString("type").trim()
            val sessionId = obj.optString("session_id").trim()
            val section = obj.optString("section").trim()
            if (eventId.isEmpty() || type.isEmpty() || sessionId.isEmpty() || section.isEmpty()) {
                return null
            }
            return TelemetryEvent(
                eventId = eventId,
                type = type,
                sessionId = sessionId,
                section = section,
                subSection = obj.optString("sub_section", section).ifBlank { section },
                durationSec = obj.optInt("duration_sec", 0).coerceIn(0, 3600),
                ts = obj.optDouble("ts", 0.0),
            )
        }
    }
}

/**
 * Pure, deterministic session and section dwell tracker.
 * Heartbeats emit incremental delta seconds without duplicating time when switching sections or ending a session.
 */
internal class TelemetrySessionTracker(
    private val monoClockSec: () -> Double = { SystemClock.elapsedRealtime() / 1000.0 },
    private val wallClockSec: () -> Double = { System.currentTimeMillis() / 1000.0 },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val maxQueueSize: Int = MAX_QUEUE_SIZE,
) {
    private val lock = Any()
    private val pendingQueue = ArrayDeque<TelemetryEvent>()

    private var activeSessionId: String? = null
    private var currentSection: String = "video"
    private var currentSubSection: String = "video_browse"
    private var lastTickMono: Double = 0.0

    fun loadPersistedEvents(events: List<TelemetryEvent>) {
        synchronized(lock) {
            for (ev in events) {
                enqueueLocked(ev)
            }
        }
    }

    fun onForeground(): Boolean {
        synchronized(lock) {
            if (activeSessionId != null) return false
            val nowMono = monoClockSec()
            val nowWall = wallClockSec()
            val sid = "sess-${idFactory()}"
            activeSessionId = sid
            lastTickMono = nowMono
            enqueueLocked(
                TelemetryEvent(
                    eventId = "ev-${idFactory()}",
                    type = "session_start",
                    sessionId = sid,
                    section = currentSection,
                    subSection = currentSubSection,
                    durationSec = 0,
                    ts = nowWall,
                ),
            )
            return true
        }
    }

    fun setSection(section: String, subSection: String = section) {
        val cleanSection = section.trim().lowercase()
        val cleanSub = subSection.trim().lowercase().ifBlank { cleanSection }
        if (cleanSection !in ALLOWED_SECTIONS) return

        synchronized(lock) {
            if (cleanSection == currentSection && cleanSub == currentSubSection) return
            val sid = activeSessionId
            if (sid != null) {
                val nowMono = monoClockSec()
                val nowWall = wallClockSec()
                val elapsed = ((nowMono - lastTickMono).toInt()).coerceIn(0, MAX_EVENT_DWELL_SEC)
                lastTickMono = nowMono
                enqueueLocked(
                    TelemetryEvent(
                        eventId = "ev-${idFactory()}",
                        type = "section_dwell",
                        sessionId = sid,
                        section = currentSection,
                        subSection = currentSubSection,
                        durationSec = elapsed,
                        ts = nowWall,
                    ),
                )
            }
            currentSection = cleanSection
            currentSubSection = cleanSub
        }
    }

    fun heartbeat(): Boolean {
        synchronized(lock) {
            val sid = activeSessionId ?: return false
            val nowMono = monoClockSec()
            val nowWall = wallClockSec()
            val delta = ((nowMono - lastTickMono).toInt()).coerceIn(0, MAX_EVENT_DWELL_SEC)
            if (delta < 1) return false
            lastTickMono += delta
            enqueueLocked(
                TelemetryEvent(
                    eventId = "ev-${idFactory()}",
                    type = "heartbeat",
                    sessionId = sid,
                    section = currentSection,
                    subSection = currentSubSection,
                    durationSec = delta,
                    ts = nowWall,
                ),
            )
            return true
        }
    }

    fun onBackground() {
        synchronized(lock) {
            val sid = activeSessionId ?: return
            val nowMono = monoClockSec()
            val nowWall = wallClockSec()
            val remaining = ((nowMono - lastTickMono).toInt()).coerceIn(0, MAX_EVENT_DWELL_SEC)
            enqueueLocked(
                TelemetryEvent(
                    eventId = "ev-${idFactory()}",
                    type = "section_dwell",
                    sessionId = sid,
                    section = currentSection,
                    subSection = currentSubSection,
                    durationSec = remaining,
                    ts = nowWall,
                ),
            )
            enqueueLocked(
                TelemetryEvent(
                    eventId = "ev-${idFactory()}",
                    type = "session_end",
                    sessionId = sid,
                    section = currentSection,
                    subSection = currentSubSection,
                    durationSec = 0,
                    ts = nowWall,
                ),
            )
            activeSessionId = null
            lastTickMono = nowMono
        }
    }

    fun peekBatch(limit: Int = MAX_BATCH_SIZE): List<TelemetryEvent> = synchronized(lock) {
        pendingQueue.take(limit.coerceAtLeast(1))
    }

    fun markSent(count: Int) {
        synchronized(lock) {
            repeat(count.coerceAtMost(pendingQueue.size)) {
                pendingQueue.removeFirst()
            }
        }
    }

    fun snapshotQueue(): List<TelemetryEvent> = synchronized(lock) {
        pendingQueue.toList()
    }

    private fun enqueueLocked(event: TelemetryEvent) {
        while (pendingQueue.size >= maxQueueSize) {
            pendingQueue.removeFirst()
        }
        pendingQueue.addLast(event)
    }

    companion object {
        const val MAX_QUEUE_SIZE = 200
        const val MAX_BATCH_SIZE = 32
        const val MAX_EVENT_DWELL_SEC = 3600
        val ALLOWED_SECTIONS = setOf("video", "comics", "live", "forum", "game", "search", "library")
    }
}

object TelemetryManager {
    private const val PREFS_NAME = "aurora_telemetry"
    private const val KEY_INSTALL_ID = "install_id_v1"
    private const val KEY_QUEUE_JSON = "pending_events_v1"
    private const val ENDPOINT_URL = "https://bananarey.us/api/telemetry/batch"
    private const val HEARTBEAT_INTERVAL_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    @Volatile
    private var initialized = false
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var installId: String = ""
    @Volatile
    private var deviceForm: String = "phone"
    @Volatile
    private var installChannel: String = "apk"
    @Volatile
    private var accountProfile: AccountProfile? = null

    private var lastAppSection: String = "video"
    private var lastAppSubSection: String = "video_browse"
    private var startedActivityCount: Int = 0
    private var heartbeatJob: Job? = null

    private val tracker = TelemetrySessionTracker()

    fun initialize(application: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val ctx = application.applicationContext
            appContext = ctx
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            installId = loadOrCreateInstallId(prefs)
            deviceForm = if (ctx.resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "phone"
            installChannel = detectInstallChannel()

            val cached = decodeQueue(prefs.getString(KEY_QUEUE_JSON, null))
            if (cached.isNotEmpty()) {
                tracker.loadPersistedEvents(cached)
            }

            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {
                    val becameForeground = synchronized(this@TelemetryManager) {
                        val wasZero = startedActivityCount == 0
                        startedActivityCount += 1
                        wasZero
                    }
                    if (becameForeground) {
                        tracker.onForeground()
                        startHeartbeat()
                        flushAsync()
                    }
                }
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {
                    val wentBackground = synchronized(this@TelemetryManager) {
                        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                        startedActivityCount == 0
                    }
                    if (wentBackground) {
                        stopHeartbeat()
                        tracker.onBackground()
                        persistQueue()
                        flushAsync()
                    }
                }
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })

            scope.launch {
                runCatching {
                    AccountRepository.get(ctx).state.collectLatest { st ->
                        accountProfile = (st as? AccountState.SignedIn)?.profile
                    }
                }
            }
            initialized = true
        }
    }

    fun setAppSection(section: String, subSection: String = section) {
        lastAppSection = section
        lastAppSubSection = subSection
        tracker.setSection(section, subSection)
    }

    fun enterGameSection() {
        tracker.setSection("game", "game_play")
    }

    fun leaveGameSection() {
        tracker.setSection(lastAppSection, lastAppSubSection)
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (tracker.heartbeat()) {
                    persistQueue()
                    flushBlocking()
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun flushAsync() {
        scope.launch {
            flushBlocking()
        }
    }

    private fun flushBlocking() {
        if (installId.isBlank()) return
        runCatching {
            while (true) {
                val batch = tracker.peekBatch(TelemetrySessionTracker.MAX_BATCH_SIZE)
                if (batch.isEmpty()) break
                val payload = buildBatchPayload(
                    installId = installId,
                    deviceForm = deviceForm,
                    installChannel = installChannel,
                    account = accountProfile,
                    events = batch,
                )
                val req = Request.Builder()
                    .url(ENDPOINT_URL)
                    .post(payload.toString().toRequestBody(jsonType))
                    .build()
                val ok = httpClient.newCall(req).execute().use { resp ->
                    resp.isSuccessful || resp.code == 400
                }
                if (ok) {
                    tracker.markSent(batch.size)
                    persistQueue()
                } else {
                    persistQueue()
                    break
                }
            }
        }
    }

    private fun persistQueue() {
        val ctx = appContext ?: return
        val arr = JSONArray()
        tracker.snapshotQueue().forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_QUEUE_JSON, arr.toString())
        }
    }

    private fun loadOrCreateInstallId(prefs: SharedPreferences): String {
        val existing = prefs.getString(KEY_INSTALL_ID, null)?.trim().orEmpty()
        if (existing.isNotBlank()) return existing
        val created = "and-${UUID.randomUUID()}"
        prefs.edit { putString(KEY_INSTALL_ID, created) }
        return created
    }

    private fun detectInstallChannel(): String {
        val fp = Build.FINGERPRINT.lowercase(Locale.ROOT)
        val model = Build.MODEL.lowercase(Locale.ROOT)
        val isEmu = fp.contains("generic") ||
            fp.contains("emulator") ||
            model.contains("emulator") ||
            model.contains("sdk_gphone") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("goldfish")
        if (isEmu) return "simulator"
        if (BuildConfig.DEBUG) return "debug"
        return "apk"
    }

    internal fun buildBatchPayload(
        installId: String,
        deviceForm: String,
        installChannel: String,
        account: AccountProfile?,
        events: List<TelemetryEvent>,
    ): JSONObject {
        val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
            .map { it.orEmpty().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .ifBlank { "Android Device" }
        val arr = JSONArray()
        events.forEach { arr.put(it.toJson()) }
        return JSONObject()
            .put("install_id", installId)
            .put("platform", "android")
            .put("device_form", deviceForm)
            .put("device_model", deviceName)
            .put("os_version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("app_build", BuildConfig.VERSION_CODE)
            .put("install_channel", installChannel)
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("timezone", TimeZone.getDefault().id)
            .put("account_id", account?.id.orEmpty())
            .put("account_email", account?.email.orEmpty())
            .put("account_name", account?.displayName.orEmpty())
            .put("events", arr)
    }

    internal fun decodeQueue(raw: String?): List<TelemetryEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    TelemetryEvent.fromJson(obj)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }
}
