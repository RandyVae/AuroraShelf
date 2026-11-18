package com.aurorashelf.app.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Discovers DLNA / UPnP MediaRenderer devices on the local network via SSDP (M-SEARCH).
 */
internal class SsdpDiscovery(
    private val context: Context,
    okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
) {
    private val noRedirectClient: OkHttpClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun discover(
        timeoutMs: Int = 3500,
        onDeviceFound: ((List<CastDevice>) -> Unit)? = null,
    ): List<CastDevice> = withContext(Dispatchers.IO) {
        val devices = ConcurrentHashMap<String, CastDevice>()
        val parseJobs = mutableListOf<Deferred<CastDevice?>>()
        val fetchGate = Semaphore(MAX_CONCURRENT_XML_FETCHES)
        var socket: DatagramSocket? = null
        val localLock = acquireMulticastLock()

        try {
            coroutineScope {
                socket = DatagramSocket(0).apply {
                    soTimeout = 600
                    broadcast = true
                }

                val searchTargets = listOf(
                    "urn:schemas-upnp-org:service:AVTransport:1",
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "ssdp:all",
                )

                val multicastGroup = InetAddress.getByName(SSDP_ADDRESS)
                for (st in searchTargets) {
                    val query = buildSearchMessage(st)
                    val sendData = query.toByteArray(StandardCharsets.UTF_8)
                    val packet = DatagramPacket(sendData, sendData.size, multicastGroup, SSDP_PORT)
                    try {
                        socket.send(packet)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to send SSDP M-SEARCH for $st: ${e.message}")
                    }
                }

                val startTime = System.currentTimeMillis()
                val receiveBuffer = ByteArray(4096)
                val seenLocations = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
                        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length, StandardCharsets.UTF_8)
                        val location = extractHeader(response, "LOCATION")
                        if (!location.isNullOrBlank() &&
                            seenLocations.size < MAX_SSDP_LOCATIONS &&
                            seenLocations.add(location)
                        ) {
                            val senderIp = receivePacket.address.hostAddress ?: ""
                            parseJobs += async(Dispatchers.IO) {
                                fetchGate.withPermit {
                                    parseDeviceFromLocation(location, senderIp)?.also { device ->
                                        devices[device.id] = device
                                        onDeviceFound?.invoke(devices.values.toList())
                                        Log.i(TAG, "Discovered DLNA device: ${device.name} at ${device.location}")
                                    }
                                }
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Continue listening until timeoutMs expires
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        Log.w(TAG, "Socket receive error: ${e.message}")
                        break
                    }
                }

                if (parseJobs.isNotEmpty()) {
                    val completed = withTimeoutOrNull(2500L) {
                        parseJobs.awaitAll()
                    }
                    if (completed == null) {
                        parseJobs.forEach { it.cancel() }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "SSDP discovery error", e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
            releaseMulticastLock(localLock)
        }

        devices.values.toList()
    }

    internal fun parseDeviceFromLocation(location: String, senderIp: String): CastDevice? {
        return try {
            val uri = runCatching { URI(location) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            val locationHost = uri.host.orEmpty()
            if (senderIp.isNotBlank() && !locationHost.equals(senderIp, ignoreCase = true)) {
                Log.w(TAG, "Rejected SSDP LOCATION host mismatch: $locationHost != sender $senderIp")
                return null
            }
            if (!isPrivateOrLocalHost(locationHost)) {
                Log.w(TAG, "Rejected non-local SSDP LOCATION host: $locationHost")
                return null
            }
            val request = Request.Builder().url(location).build()
            noRedirectClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val declaredLen = response.body?.contentLength() ?: -1L
                if (declaredLen > MAX_DEVICE_XML_BYTES) return null
                val xml = LocalMediaProxy.readTextCapped(response.body?.byteStream(), MAX_DEVICE_XML_BYTES)
                if (xml.isBlank()) return null

                val friendlyName = DlnaSoap.parseTagValue(xml, "friendlyName") ?: return null
                val manufacturer = DlnaSoap.parseTagValue(xml, "manufacturer").orEmpty()
                val modelName = DlnaSoap.parseTagValue(xml, "modelName").orEmpty()
                val udn = DlnaSoap.parseTagValue(xml, "UDN") ?: location

                // Find AVTransport control URL
                val avTransportControlUrl = extractControlUrl(xml, "urn:schemas-upnp-org:service:AVTransport:1") ?: return null
                val resolvedAvUrl = resolveUrl(location, avTransportControlUrl)
                if (resolvedAvUrl.isBlank()) return null

                // Find RenderingControl (for volume control)
                val renderingControlUrl = extractControlUrl(xml, "urn:schemas-upnp-org:service:RenderingControl:1")
                val resolvedRenderingUrl = renderingControlUrl?.let { resolveUrl(location, it).takeIf(String::isNotBlank) }

                CastDevice(
                    id = udn,
                    name = friendlyName,
                    manufacturer = manufacturer,
                    modelName = modelName,
                    location = location,
                    avTransportUrl = resolvedAvUrl,
                    renderingControlUrl = resolvedRenderingUrl,
                    ipAddress = senderIp,
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse device XML from $location: ${e.message}")
            null
        }
    }

    internal fun extractControlUrl(xml: String, serviceType: String): String? =
        DlnaSoap.extractServiceControlUrl(xml, serviceType)

    private fun isPrivateOrLocalHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true) || host == "127.0.0.1" || host == "::1") return true
        return runCatching {
            val address = InetAddress.getByName(host)
            address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress
        }.getOrDefault(false)
    }

    private fun resolveUrl(baseUrl: String, controlUrl: String): String {
        val resolved = if (controlUrl.startsWith("http://", ignoreCase = true) || controlUrl.startsWith("https://", ignoreCase = true)) {
            controlUrl
        } else {
            try {
                URI(baseUrl).resolve(controlUrl).toString()
            } catch (_: Exception) {
                val base = baseUrl.substringBefore("/", "").ifBlank { baseUrl }
                if (controlUrl.startsWith("/")) "$base$controlUrl" else "$base/$controlUrl"
            }
        }
        val baseHost = runCatching { URI(baseUrl).host.orEmpty() }.getOrDefault("")
        val resolvedHost = runCatching { URI(resolved).host.orEmpty() }.getOrDefault("")
        if (baseHost.isNotBlank() && resolvedHost.isNotBlank() && !baseHost.equals(resolvedHost, ignoreCase = true)) {
            Log.w(TAG, "Rejected cross-host controlUrl: $resolved for base $baseUrl")
            return ""
        }
        return resolved
    }

    private fun extractHeader(response: String, headerName: String): String? {
        val lines = response.split("\r\n", "\n")
        val prefix = "$headerName:"
        for (line in lines) {
            if (line.startsWith(prefix, ignoreCase = true)) {
                return line.substring(prefix.length).trim()
            }
        }
        return null
    }

    private fun buildSearchMessage(target: String): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 3\r\n" +
            "ST: $target\r\n\r\n"

    private fun acquireMulticastLock(): WifiManager.MulticastLock? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.createMulticastLock("NetgreyCastMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot acquire MulticastLock: ${e.message}")
            null
        }
    }

    private fun releaseMulticastLock(lock: WifiManager.MulticastLock?) {
        try {
            if (lock?.isHeld == true) {
                lock.release()
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "SsdpDiscovery"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        internal const val MAX_CONCURRENT_XML_FETCHES = 4
        internal const val MAX_SSDP_LOCATIONS = 32
        internal const val MAX_DEVICE_XML_BYTES = 256 * 1024
    }
}
