package com.aurorashelf.app.cast

import java.io.StringReader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

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
        val rawMimeType = mimeTypeOverride ?: if (isHlsUrl(mediaUrl)) {
            "application/vnd.apple.mpegurl"
        } else {
            "video/mp4"
        }
        val escapedMimeType = escapeXml(rawMimeType)

        val didlMetadata = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="http://schemas.upnp.org/metadata-1-0/upnp/">
              <item id="0" parentID="-1" restricted="1">
                <dc:title>$escapedTitle</dc:title>
                <upnp:class>object.item.videoItem</upnp:class>
                <res protocolInfo="http-get:*:$escapedMimeType:*">$escapedUrl</res>
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
        if (xml.isBlank() || tag.isBlank()) return null
        val parsed = parseSafeXmlDocument(xml)
        if (parsed != null) {
            findFirstElementText(parsed.documentElement, tag)?.let { return it }
        }
        // Fallback for non-well-formed device XML snippets, ensuring XML entities are properly unescaped
        val regex = Regex("""<(?:[A-Za-z0-9_-]+:)?${Regex.escape(tag)}(?:\s[^>]*)?>(.*?)</(?:[A-Za-z0-9_-]+:)?${Regex.escape(tag)}>""", RegexOption.DOT_MATCHES_ALL)
        val raw = regex.find(xml)?.groupValues?.get(1)?.trim() ?: return null
        return unescapeXml(raw)
    }

    fun extractServiceControlUrl(xml: String, serviceType: String): String? {
        if (xml.isBlank() || serviceType.isBlank()) return null
        val doc = parseSafeXmlDocument(xml)
        if (doc != null) {
            val services = doc.getElementsByTagNameNS("*", "service")
            for (i in 0 until services.length) {
                val serviceElem = services.item(i) as? Element ?: continue
                val typeVal = findDirectChildText(serviceElem, "serviceType")
                if (typeVal == serviceType) {
                    val controlUrl = findDirectChildText(serviceElem, "controlURL")
                    if (!controlUrl.isNullOrBlank()) return controlUrl
                }
            }
        }
        // Fallback for partial/quirky XML blocks
        val serviceRegex = Regex("""<(?:[A-Za-z0-9_-]+:)?service(?:\s[^>]*)?>(.*?)</(?:[A-Za-z0-9_-]+:)?service>""", RegexOption.DOT_MATCHES_ALL)
        for (match in serviceRegex.findAll(xml)) {
            val block = match.groupValues[1]
            if (parseTagValue(block, "serviceType") == serviceType) {
                val controlUrl = parseTagValue(block, "controlURL")
                if (!controlUrl.isNullOrBlank()) return controlUrl
            }
        }
        return null
    }

    private fun parseSafeXmlDocument(xml: String): Document? {
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            }
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml.trim())))
        }.getOrNull()
    }

    private fun matchesTagName(node: Node, targetTag: String): Boolean {
        val local = node.localName
        if (local != null && local == targetTag) return true
        val name = node.nodeName ?: return false
        return name == targetTag || name.endsWith(":$targetTag")
    }

    private fun findFirstElementText(node: Node?, targetTag: String): String? {
        if (node == null) return null
        if (node.nodeType == Node.ELEMENT_NODE && matchesTagName(node, targetTag)) {
            return node.textContent?.trim()
        }
        val children = node.childNodes ?: return null
        for (i in 0 until children.length) {
            findFirstElementText(children.item(i), targetTag)?.let { return it }
        }
        return null
    }

    private fun findDirectChildText(parent: Element, targetTag: String): String? {
        val children = parent.childNodes ?: return null
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && matchesTagName(child, targetTag)) {
                return child.textContent?.trim()
            }
        }
        return null
    }

    fun unescapeXml(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&#34;", "\"")
        .replace("&amp;", "&")

    fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
