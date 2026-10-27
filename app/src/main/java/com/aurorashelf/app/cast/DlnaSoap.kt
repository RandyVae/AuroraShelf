package com.aurorashelf.app.cast

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal object DlnaSoap {
    private const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val RENDERING_CONTROL_SERVICE = "urn:schemas-upnp-org:service:RenderingControl:1"

    fun isHlsUrl(url: String): Boolean {
        if (url.contains(".m3u8", ignoreCase = true) || url.contains("%2Em3u8", ignoreCase = true)) {
            return true
        }
        return runCatching {
            URLDecoder.decode(url, StandardCharsets.UTF_8.name()).contains(".m3u8", ignoreCase = true)
        }.getOrDefault(false)
    }

    fun buildSetAvTransportUriXml(
        mediaUrl: String,
        title: String,
        mimeTypeOverride: String? = null,
    ): String {
        val escapedUrl = escapeXml(mediaUrl)
        val escapedTitle = escapeXml(title)
        val mimeType = mimeTypeOverride ?: if (isHlsUrl(mediaUrl)) {
            "application/vnd.apple.mpegurl"
        } else {
            "video/mp4"
        }

        val didlMetadata = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="http://schemas.upnp.org/metadata-1-0/upnp/">
              <item id="0" parentID="-1" restricted="1">
                <dc:title>$escapedTitle</dc:title>
                <upnp:class>object.item.videoItem</upnp:class>
                <res protocolInfo="http-get:*:$mimeType:*">$escapedUrl</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()
        val escapedMetadata = escapeXml(didlMetadata)

        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:SetAVTransportURI xmlns:u="$AV_TRANSPORT_SERVICE">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>$escapedUrl</CurrentURI>
                  <CurrentURIMetaData>$escapedMetadata</CurrentURIMetaData>
                </u:SetAVTransportURI>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    fun buildPlayXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:Play xmlns:u="$AV_TRANSPORT_SERVICE">
              <InstanceID>0</InstanceID>
              <Speed>1</Speed>
            </u:Play>
          </s:Body>
        </s:Envelope>
    """.trimIndent()

    fun buildPauseXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:Pause xmlns:u="$AV_TRANSPORT_SERVICE">
              <InstanceID>0</InstanceID>
            </u:Pause>
          </s:Body>
        </s:Envelope>
    """.trimIndent()

    fun buildStopXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:Stop xmlns:u="$AV_TRANSPORT_SERVICE">
              <InstanceID>0</InstanceID>
            </u:Stop>
          </s:Body>
        </s:Envelope>
    """.trimIndent()

    fun buildSeekXml(positionMs: Long): String {
        val relTime = formatRelTime(positionMs)
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:Seek xmlns:u="$AV_TRANSPORT_SERVICE">
                  <InstanceID>0</InstanceID>
                  <Unit>REL_TIME</Unit>
                  <Target>$relTime</Target>
                </u:Seek>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    fun buildGetPositionInfoXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:GetPositionInfo xmlns:u="$AV_TRANSPORT_SERVICE">
              <InstanceID>0</InstanceID>
            </u:GetPositionInfo>
          </s:Body>
        </s:Envelope>
    """.trimIndent()

    fun buildGetTransportInfoXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
          <s:Body>
            <u:GetTransportInfo xmlns:u="$AV_TRANSPORT_SERVICE">
              <InstanceID>0</InstanceID>
            </u:GetTransportInfo>
          </s:Body>
        </s:Envelope>
    """.trimIndent()

    fun getActionHeader(action: String): String = "\"$AV_TRANSPORT_SERVICE#$action\""

    fun formatRelTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun parseRelTime(timeStr: String): Long {
        val parts = timeStr.trim().split(":")
        if (parts.size != 3) return 0L
        val hours = parts[0].toLongOrNull() ?: 0L
        val minutes = parts[1].toLongOrNull() ?: 0L
        val secondsPart = parts[2].split(".")[0]
        val seconds = secondsPart.toLongOrNull() ?: 0L
        return ((hours * 3600) + (minutes * 60) + seconds) * 1000L
    }

    fun parseTagValue(xml: String, tag: String): String? {
        val openTag = "<$tag"
        val closeTag = "</$tag>"
        val startIdx = xml.indexOf(openTag)
        if (startIdx == -1) return null
        val contentStart = xml.indexOf(">", startIdx)
        if (contentStart == -1) return null
        val endIdx = xml.indexOf(closeTag, contentStart)
        if (endIdx == -1) return null
        return xml.substring(contentStart + 1, endIdx).trim()
    }

    fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
