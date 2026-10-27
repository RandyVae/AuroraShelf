package com.aurorashelf.app.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.VideoRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * High-level manager coordinating DLNA device discovery, media casting, and remote transport control.
 */
@OptIn(UnstableApi::class)
class CastManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val ssdpDiscovery = SsdpDiscovery(appContext, okHttpClient)

    internal val localMediaProxy = LocalMediaProxy(
        okHttpClient = okHttpClient,
        allowedDirs = listOfNotNull(appContext.cacheDir, appContext.filesDir, appContext.externalCacheDir),
        cacheStreamOpener = { cacheKey, sourceUrl, referer, start, endInclusive ->
            openOfflineCachedStream(cacheKey, sourceUrl, referer, start, endInclusive)
        },
    )

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _castState = MutableStateFlow<CastState>(CastState.Idle)
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    /** Pre-selected target TV when chosen from a top bar before opening a video. */
    private val _selectedDevice = MutableStateFlow<CastDevice?>(null)
    val selectedDevice: StateFlow<CastDevice?> = _selectedDevice.asStateFlow()

    val currentDevice: CastDevice?
        get() = when (val state = _castState.value) {
            is CastState.Connected -> state.device
            is CastState.Connecting -> state.device
            else -> _selectedDevice.value
        }

    private var discoveryJob: Job? = null
    private var syncJob: Job? = null

    fun selectDevice(device: CastDevice?) {
        _selectedDevice.value = device
    }

    fun startDiscovery() {
        if (_isSearching.value) return
        _isSearching.value = true
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            try {
                val found = ssdpDiscovery.discover(
                    timeoutMs = 3500,
                    onDeviceFound = { partial -> _devices.value = partial },
                )
                _devices.value = found
            } catch (e: Exception) {
                Log.w(TAG, "Discovery error: ${e.message}")
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _isSearching.value = false
    }

    fun cast(
        device: CastDevice,
        videoUrl: String,
        title: String,
        referer: String? = null,
        localFilePath: String? = null,
        cacheKey: String? = null,
        initialPositionMs: Long = 0L,
    ) {
        syncJob?.cancel()
        _selectedDevice.value = device
        _castState.value = CastState.Connecting(device)

        scope.launch(Dispatchers.IO) {
            try {
                // Determine the stream URL for the TV:
                val streamUrl = when {
                    !cacheKey.isNullOrBlank() -> {
                        localMediaProxy.buildProxyUrl(
                            videoUrl = videoUrl,
                            referer = referer,
                            cacheKey = cacheKey,
                        ) ?: videoUrl
                    }
                    !localFilePath.isNullOrBlank() -> {
                        localMediaProxy.buildLocalFileProxyUrl(
                            filePath = localFilePath,
                            referer = referer,
                        ) ?: videoUrl
                    }
                    !referer.isNullOrBlank() || DlnaSoap.isHlsUrl(videoUrl) -> {
                        localMediaProxy.buildProxyUrl(
                            videoUrl = videoUrl,
                            referer = referer,
                        ) ?: videoUrl
                    }
                    else -> videoUrl
                }

                Log.i(TAG, "Casting to ${device.name}: streamUrl=${sanitizeStreamUrl(streamUrl)}")

                // 1. SetAVTransportURI
                val mimeType = if (DlnaSoap.isHlsUrl(videoUrl)) "application/vnd.apple.mpegurl" else "video/mp4"
                val setUriXml = DlnaSoap.buildSetAvTransportUriXml(streamUrl, title, mimeTypeOverride = mimeType)
                executeSoapAction(device.avTransportUrl, "SetAVTransportURI", setUriXml)

                // 2. Play first (many DLNA TVs require Play before accepting REL_TIME Seek)
                val playXml = DlnaSoap.buildPlayXml()
                executeSoapAction(device.avTransportUrl, "Play", playXml)

                // 3. Initial seek if specified
                if (initialPositionMs > 1000L) {
                    delay(400L)
                    val seekXml = DlnaSoap.buildSeekXml(initialPositionMs)
                    runCatching { executeSoapAction(device.avTransportUrl, "Seek", seekXml) }
                }

                withContext(Dispatchers.Main) {
                    _castState.value = CastState.Connected(
                        device = device,
                        mediaUrl = streamUrl,
                        title = title,
                        isPlaying = true,
                        positionMs = initialPositionMs.coerceAtLeast(0L),
                    )
                }

                startPositionSync(device)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cast to ${device.name}", e)
                withContext(Dispatchers.Main) {
                    _castState.value = CastState.Error(e.message ?: "投屏連接失敗")
                }
            }
        }
    }

    private fun openOfflineCachedStream(
        cacheKey: String,
        sourceUrl: String,
        referer: String?,
        start: Long,
        endInclusive: Long?,
    ): CachedStreamSlice? {
        return try {
            val cacheManager = OfflineCacheManager.get(appContext)
            val totalCachedLength = androidx.media3.datasource.cache.ContentMetadata.getContentLength(
                cacheManager.cache.getContentMetadata(cacheKey),
            )
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(VideoRepository.USER_AGENT)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .apply {
                    if (!referer.isNullOrBlank()) {
                        setDefaultRequestProperties(mapOf("Referer" to referer, "Accept" to "*/*"))
                    }
                }
            val dataSource = cacheManager.playbackDataSourceFactory(httpFactory).createDataSource()
            val requestedLength = when {
                endInclusive != null && endInclusive >= start -> endInclusive - start + 1
                totalCachedLength > start -> totalCachedLength - start
                else -> C.LENGTH_UNSET.toLong()
            }
            val spec = DataSpec.Builder()
                .setUri(Uri.parse(sourceUrl))
                .setKey(cacheKey)
                .setPosition(start)
                .setLength(requestedLength)
                .build()
            val openedLength = dataSource.open(spec)
            val effectiveContentLength = when {
                openedLength != C.LENGTH_UNSET.toLong() -> openedLength
                requestedLength != C.LENGTH_UNSET.toLong() -> requestedLength
                else -> -1L
            }
            val effectiveTotalLength = when {
                totalCachedLength != C.LENGTH_UNSET.toLong() -> totalCachedLength
                effectiveContentLength > 0 && start == 0L -> effectiveContentLength
                else -> -1L
            }
            CachedStreamSlice(
                totalLength = effectiveTotalLength,
                contentLength = effectiveContentLength,
                read = { buffer, offset, len -> dataSource.read(buffer, offset, len) },
                close = { runCatching { dataSource.close() } },
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open Media3 cached stream for $cacheKey: ${e.message}")
            null
        }
    }

    fun play() {
        val connected = _castState.value as? CastState.Connected ?: return
        scope.launch(Dispatchers.IO) {
            try {
                executeSoapAction(connected.device.avTransportUrl, "Play", DlnaSoap.buildPlayXml())
                withContext(Dispatchers.Main) {
                    _castState.value = connected.copy(isPlaying = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Play error: ${e.message}")
            }
        }
    }

    fun pause() {
        val connected = _castState.value as? CastState.Connected ?: return
        scope.launch(Dispatchers.IO) {
            try {
                executeSoapAction(connected.device.avTransportUrl, "Pause", DlnaSoap.buildPauseXml())
                withContext(Dispatchers.Main) {
                    _castState.value = connected.copy(isPlaying = false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pause error: ${e.message}")
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val connected = _castState.value as? CastState.Connected ?: return
        scope.launch(Dispatchers.IO) {
            try {
                executeSoapAction(connected.device.avTransportUrl, "Seek", DlnaSoap.buildSeekXml(positionMs))
                withContext(Dispatchers.Main) {
                    _castState.value = connected.copy(positionMs = positionMs)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Seek error: ${e.message}")
            }
        }
    }

    fun stop() {
        val connected = _castState.value as? CastState.Connected
        syncJob?.cancel()
        syncJob = null
        _castState.value = CastState.Idle
        localMediaProxy.stop()

        if (connected != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    executeSoapAction(connected.device.avTransportUrl, "Stop", DlnaSoap.buildStopXml())
                } catch (e: Exception) {
                    Log.d(TAG, "Stop response: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        _selectedDevice.value = null
        stop()
    }

    private fun startPositionSync(device: CastDevice) {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(2000L)
                try {
                    val xml = executeSoapAction(device.avTransportUrl, "GetPositionInfo", DlnaSoap.buildGetPositionInfoXml())
                    val relTime = DlnaSoap.parseTagValue(xml, "RelTime")
                    val trackDuration = DlnaSoap.parseTagValue(xml, "TrackDuration")

                    val currentMs = relTime?.let { DlnaSoap.parseRelTime(it) } ?: 0L
                    val durationMs = trackDuration?.let { DlnaSoap.parseRelTime(it) } ?: 0L

                    withContext(Dispatchers.Main) {
                        val current = _castState.value as? CastState.Connected
                        if (current != null && current.device.id == device.id) {
                            _castState.value = current.copy(
                                positionMs = if (currentMs > 0) currentMs else current.positionMs,
                                durationMs = if (durationMs > 0) durationMs else current.durationMs,
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun executeSoapAction(url: String, action: String, xmlPayload: String): String {
        val mediaType = "text/xml; charset=utf-8".toMediaType()
        val requestBody = xmlPayload.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("SOAPAction", DlnaSoap.getActionHeader(action))
            .addHeader("Content-Type", "text/xml; charset=utf-8")
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful && !body.contains("errorCode")) {
                throw java.io.IOException("SOAP $action failed with HTTP ${response.code}")
            }
            return body
        }
    }

    companion object {
        private const val TAG = "CastManager"

        @Volatile
        private var instance: CastManager? = null

        fun getInstance(context: Context): CastManager =
            instance ?: synchronized(this) {
                instance ?: CastManager(context).also { instance = it }
            }

        internal fun sanitizeStreamUrl(url: String): String {
            return url.replace(Regex("([?&]token=)[^&]+"), "$1***")
        }
    }
}
