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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Discovers DLNA / UPnP MediaRenderer devices on the local network via SSDP (M-SEARCH).
 */
internal class SsdpDiscovery(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
) {
    private var multicastLock: WifiManager.MulticastLock? = null

    suspend fun discover(
        timeoutMs: Int = 3500,
        onDeviceFound: ((List<CastDevice>) -> Unit)? = null,
    ): List<CastDevice> = withContext(Dispatchers.IO) {
        val devices = ConcurrentHashMap<String, CastDevice>()
        val parseJobs = mutableListOf<Deferred<CastDevice?>>()
        var socket: DatagramSocket? = null
        acquireMulticastLock()

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
                        socket?.send(packet)
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
                        socket?.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length, StandardCharsets.UTF_8)
                        val location = extractHeader(response, "LOCATION")
                        if (!location.isNullOrBlank() && seenLocations.add(location)) {
                            val senderIp = receivePacket.address.hostAddress ?: ""
                            // Parse each discovered XML asynchronously so slow routers/IoT nodes
                            // never stall the UDP receive loop from capturing fast TV responses.
                            parseJobs += async(Dispatchers.IO) {
                                parseDeviceFromLocation(location, senderIp)?.also { device ->
                                    devices[device.id] = device
                                    onDeviceFound?.invoke(devices.values.toList())
                                    Log.i(TAG, "Discovered DLNA device: ${device.name} at ${device.location}")
                                }
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Continue listening until timeoutMs expires
                    } catch (e: Exception) {
                        Log.w(TAG, "Socket receive error: ${e.message}")
                        break
                    }
                }

                if (parseJobs.isNotEmpty()) {
                    withTimeoutOrNull(2500L) {
                        parseJobs.awaitAll()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSDP discovery error", e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
            releaseMulticastLock()
        }

        devices.values.toList()
    }

    internal fun parseDeviceFromLocation(location: String, senderIp: String): CastDevice? {
        return try {
            val request = Request.Builder().url(location).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val xml = response.body?.string() ?: return null

                val friendlyName = DlnaSoap.parseTagValue(xml, "friendlyName") ?: return null
                val manufacturer = DlnaSoap.parseTagValue(xml, "manufacturer").orEmpty()
                val modelName = DlnaSoap.parseTagValue(xml, "modelName").orEmpty()
                val udn = DlnaSoap.parseTagValue(xml, "UDN") ?: location

                // Find AVTransport control URL
                val avTransportControlUrl = extractControlUrl(xml, "urn:schemas-upnp-org:service:AVTransport:1") ?: return null
                val resolvedAvUrl = resolveUrl(location, avTransportControlUrl)

                // Find RenderingControl (for volume control)
                val renderingControlUrl = extractControlUrl(xml, "urn:schemas-upnp-org:service:RenderingControl:1")
                val resolvedRenderingUrl = renderingControlUrl?.let { resolveUrl(location, it) }

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

    internal fun extractControlUrl(xml: String, serviceType: String): String? {
        var startIdx = 0
        while (true) {
            val serviceIdx = xml.indexOf(serviceType, startIdx)
            if (serviceIdx == -1) return null

            // Find enclosing <service>...</service> block
            val serviceStart = xml.lastIndexOf("<service>", serviceIdx)
            val serviceEnd = xml.indexOf("</service>", serviceIdx)
            if (serviceStart != -1 && serviceEnd != -1 && serviceStart < serviceEnd) {
                val serviceBlock = xml.substring(serviceStart, serviceEnd)
                val controlUrl = DlnaSoap.parseTagValue(serviceBlock, "controlURL")
                if (!controlUrl.isNullOrBlank()) return controlUrl
            }
            startIdx = serviceIdx + serviceType.length
        }
    }

    private fun resolveUrl(baseUrl: String, controlUrl: String): String {
        return if (controlUrl.startsWith("http://", ignoreCase = true) || controlUrl.startsWith("https://", ignoreCase = true)) {
            controlUrl
        } else {
            try {
                URI(baseUrl).resolve(controlUrl).toString()
            } catch (_: Exception) {
                val base = baseUrl.substringBefore("/", "").ifBlank { baseUrl }
                if (controlUrl.startsWith("/")) "$base$controlUrl" else "$base/$controlUrl"
            }
        }
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

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("NetgreyCastMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot acquire MulticastLock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
    }

    companion object {
        private const val TAG = "SsdpDiscovery"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
    }
}
