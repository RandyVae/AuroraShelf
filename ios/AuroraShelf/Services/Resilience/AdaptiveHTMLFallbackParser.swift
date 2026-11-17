import Foundation

/// 结构化启发式 HTML 视频卡片与媒体流兜底解析器（iOS/iPadOS）。
/// 当某视频站改版更换 CSS 类名导致主解析器在 200 OK 页面上返回 0 条结果时自动接管恢复，
/// 只要原站视频详情链接与封面图结构尚在，即可自动修复提取可用片源列表。
public enum AdaptiveHTMLFallbackParser {
    private static let anchorRegex = try? NSRegularExpression(pattern: #"(?is)<a\b([^>]*)>([\s\S]*?)</a>"#)
    private static let attrRegex = try? NSRegularExpression(pattern: #"(?i)\b([a-z_:][-a-z0-9_:.]*)\s*=\s*(['"])(.*?)\2"#)
    private static let imgRegex = try? NSRegularExpression(pattern: #"(?is)<img\b([^>]*)>"#)
    private static let durationRegex = try? NSRegularExpression(pattern: #"\b\d{1,2}:\d{2}(?::\d{2})?\b"#)
    private static let streamURLRegex = try? NSRegularExpression(
        pattern: #"(?i)(https?://[^"'\\\s<>]+\.(?:m3u8|mp4)(?:\?[^"'\\\s<>]*)?)"#
    )

    private static let videoRouteMarkers: [String] = [
        "view_video.php", "viewkey=", "/voddetail/", "/vodplay/", "/video/", "/videos/",
        "/watch", "/drama/", "/anime/", "/detail/", "/v/"
    ]

    public static func extractFallbackVideoItems(
        html: String,
        baseURL: String,
        sourceName: String,
        category: String = "全部"
    ) -> [VideoItem] {
        guard html.count > 256,
              let resolvedBase = URL(string: baseURL),
              let anchorRegex,
              let attrRegex,
              let imgRegex else {
            return []
        }

        let nsHTML = html as NSString
        let fullRange = NSRange(location: 0, length: nsHTML.length)
        let matches = anchorRegex.matches(in: html, range: fullRange)

        var results: [VideoItem] = []
        var seenURLs = Set<String>()

        for match in matches {
            let attrRaw = nsHTML.substring(with: match.range(at: 1))
            let bodyRaw = nsHTML.substring(with: match.range(at: 2))
            let attrs = parseAttributes(attrRaw, regex: attrRegex)
            guard let rawHref = attrs["href"]?.trimmingCharacters(in: .whitespacesAndNewlines),
                  looksLikeVideoHref(rawHref, baseURL: baseURL),
                  let itemURL = URL(string: decodeHTMLEntities(rawHref), relativeTo: resolvedBase)?.absoluteURL,
                  seenURLs.insert(itemURL.absoluteString).inserted else {
                continue
            }

            let cardWindow = nearbyContext(in: nsHTML, around: match.range)
            let scanHTML = bodyRaw.contains("<img") ? bodyRaw : (cardWindow.isEmpty ? bodyRaw : cardWindow)
            let scanNS = scanHTML as NSString
            let imgs = imgRegex.matches(in: scanHTML, range: NSRange(location: 0, length: scanNS.length))

            var coverURL = ""
            var imgAlt = ""
            for img in imgs {
                let imgAttrs = parseAttributes(scanNS.substring(with: img.range(at: 1)), regex: attrRegex)
                if imgAlt.isEmpty, let alt = imgAttrs["alt"]?.trimmingCharacters(in: .whitespacesAndNewlines), !alt.isEmpty {
                    imgAlt = alt
                }
                let candidateKeys = ["data-src", "data-original", "data-lazy-src", "data-poster", "data-thumb", "src"]
                for key in candidateKeys {
                    if let val = imgAttrs[key]?.trimmingCharacters(in: .whitespacesAndNewlines),
                       !val.isEmpty,
                       !val.lowercased().hasPrefix("data:image"),
                       !val.lowercased().contains("placeholder"),
                       let absCover = URL(string: decodeHTMLEntities(val), relativeTo: resolvedBase)?.absoluteURL.absoluteString {
                        coverURL = absCover
                        break
                    }
                }
                if !coverURL.isEmpty { break }
            }

            if coverURL.isEmpty && !rawHref.lowercased().contains("view_video") {
                continue
            }

            let rawTitle = (attrs["title"]?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty)
                ?? imgAlt.nilIfEmpty
                ?? cleanVisibleTitle(from: bodyRaw).nilIfEmpty
                ?? cleanVisibleTitle(from: cardWindow).nilIfEmpty
            guard let title = rawTitle, title.count >= 2 else { continue }

            let duration = extractDuration(from: scanHTML) ?? ""
            let safeId = "heal-" + String(absHash(itemURL.absoluteString))

            results.append(
                VideoItem(
                    id: safeId,
                    title: title,
                    subtitle: "\(sourceName) · 自动修复加载",
                    coverUrl: coverURL,
                    playbackUrl: itemURL.absoluteString,
                    duration: duration,
                    rating: "",
                    tags: [sourceName, category],
                    category: category,
                    isTop10: false,
                    rankNumber: nil,
                    sourceName: sourceName
                )
            )
        }

        if !results.isEmpty {
            print("[AuroraDomSelfHeal] structural fallback recovered \(results.count) items for \(sourceName)")
        }
        return results
    }

    public static func extractFallbackStreamURL(from html: String) -> URL? {
        guard let streamURLRegex else { return nil }
        let nsHTML = html as NSString
        let matches = streamURLRegex.matches(in: html, range: NSRange(location: 0, length: nsHTML.length))
        for m in matches {
            let raw = nsHTML.substring(with: m.range(at: 1))
                .replacingOccurrences(of: "\\/", with: "/")
                .replacingOccurrences(of: "&amp;", with: "&")
            let lower = raw.lowercased()
            if lower.contains("preview.mp4") || lower.contains("91p52.com") { continue }
            if let url = URL(string: raw) { return url }
        }
        return nil
    }

    private static func looksLikeVideoHref(_ href: String, baseURL: String) -> Bool {
        guard !href.isEmpty, href != "#" else { return false }
        let lower = href.lowercased()
        if lower.hasPrefix("javascript:") || lower.contains("/login") || lower.contains("/register") || lower.contains("/advert") {
            return false
        }
        if videoRouteMarkers.contains(where: { lower.contains($0) }) { return true }
        if baseURL.lowercased().contains("missav") && (lower.contains("/cn/") || lower.contains("/ja/")) {
            return true
        }
        return false
    }

    private static func nearbyContext(in nsHTML: NSString, around range: NSRange) -> String {
        let start = max(0, range.location - 400)
        let end = min(nsHTML.length, range.location + range.length + 1200)
        guard end > start else { return "" }
        return nsHTML.substring(with: NSRange(location: start, length: end - start))
    }

    private static func parseAttributes(_ fragment: String, regex: NSRegularExpression) -> [String: String] {
        let ns = fragment as NSString
        var out: [String: String] = [:]
        for match in regex.matches(in: fragment, range: NSRange(location: 0, length: ns.length)) where match.numberOfRanges >= 4 {
            let key = ns.substring(with: match.range(at: 1)).lowercased()
            out[key] = decodeHTMLEntities(ns.substring(with: match.range(at: 3)))
        }
        return out
    }

    private static func cleanVisibleTitle(from htmlFragment: String) -> String {
        var stripped = htmlFragment
            .replacingOccurrences(of: #"(?is)<span[^>]*class=['"][^'"]*\bduration\b[^'"]*['"][^>]*>[\s\S]*?</span>"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"(?is)<(?:div|span)[^>]*class=['"][^'"]*\b(?:hd-text-icon|original-text-icon|badge|public-list-prb)\b[^'"]*['"][^>]*>[\s\S]*?</(?:div|span)>"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"(?is)<(script|style)\b[^>]*>[\s\S]*?</\1>"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"(?is)<[^>]+>"#, with: " ", options: .regularExpression)
        stripped = decodeHTMLEntities(stripped)
        return stripped.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func extractDuration(from text: String) -> String? {
        guard let durationRegex else { return nil }
        let ns = text as NSString
        guard let match = durationRegex.firstMatch(in: text, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return ns.substring(with: match.range)
    }

    private static func decodeHTMLEntities(_ value: String) -> String {
        value
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#039;", with: "'")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&apos;", with: "'")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&nbsp;", with: " ")
    }

    private static func absHash(_ value: String) -> UInt64 {
        var hash: UInt64 = 14695981039346656037
        for byte in value.utf8 {
            hash ^= UInt64(byte)
            hash &*= 1099511628211
        }
        return hash
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
