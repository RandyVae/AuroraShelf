import Foundation

/// Native HTTP adapter for sites that expose their catalogue and playback metadata as HTML.
/// The URL patterns mirror the source-specific routes used by the Android extractors while
/// keeping parsing and playback resolution in the iOS networking stack.
enum HTMLVideoSourceAdapter {
    private static let feedAnchorRegex = try? NSRegularExpression(pattern: #"(?is)<a\b([^>]*)>(.*?)</a>"#)
    private static let feedAttributeRegex = try? NSRegularExpression(pattern: #"(?i)\b([a-z_:][-a-z0-9_:.]*)\s*=\s*(['\"])(.*?)\2"#)
    private static let feedImageRegex = try? NSRegularExpression(pattern: #"(?is)<img\b([^>]*)>"#)

    static func feedURL(for source: VideoSourceDefinition, category: String, page: Int = 1) -> URL? {
        guard VideoSourceCatalog.htmlAdapterSourceIDs.contains(source.id) else { return nil }
        let page = max(page, 1)
        let route: String
        switch source.id {
        case "missav":
            let isChinese = category.contains("中字") || category.contains("字幕") || category.contains("中文")
            route = category.contains("热门") || category.contains("精选") ? "/cn/release" :
                category.contains("推荐") ? "/cn" :
                isChinese ? "/cn/chinese-subtitle" : "/cn/new"
        case "85po":
            let base = category.contains("热") || category.contains("精选") ? "/ja/most-popular" : category.contains("推荐") ? "/ja/top-rated" : "/ja/latest-updates"
            route = page == 1 ? "\(base)/" : "\(base)/\(page)/"
        case "pornhub":
            route = category.contains("热") ? "/video?o=mv&t=m" :
                category.contains("推荐") ? "/video?o=tr&t=m" :
                category.contains("4K") ? "/video?c=298" : "/video?o=cm"
        case "javnoni":
            let isChinese = category.contains("热") || category.contains("中字") || category.contains("字幕") || category.contains("中文")
            let base = isChinese ? "/archives/category/uncensored" : category.contains("推荐") ? "/archives/tag/japan" : ""
            route = page == 1 ? (base.isEmpty ? "/" : "\(base)/") : (base.isEmpty ? "/page/\(page)/" : "\(base)/page/\(page)/")
        case "javsb":
            if category.contains("排行") || category.contains("热") {
                route = page == 1 ? "/ja/label/rank/by/hits_week.html" : "/ja/label/rank/by/hits_week/page/\(page).html"
            } else if category.contains("精选") || category.contains("推荐") {
                route = page == 1 ? "/ja/label/rank/by/score.html" : "/ja/label/rank/by/score/page/\(page).html"
            } else if category.contains("最新") {
                route = page == 1 ? "/ja/label/rank/by/time.html" : "/ja/label/rank/by/time/page/\(page).html"
            } else {
                route = page == 1 ? "/ja/" : "/ja/label/rank/by/hits_week/page/\(page).html"
            }
        case "tokyomotion":
            route = category.contains("评分") || category.contains("收藏") || category.contains("推荐") ? "/videos?sort=rating" : category.contains("热") ? "/videos?sort=popular" : "/videos"
        case "kissjav":
            let sortBy = category.contains("热") || category.contains("热门") ? "?sort_by=video_viewed" : category.contains("新") ? "" : "?sort_by=rating"
            let pathPrefix = category.contains("新") ? "/latest-updates" : "/most-popular"
            route = page == 1 ? "\(pathPrefix)/\(sortBy)" : "\(pathPrefix)/\(page)/\(sortBy)"
        case "spankbang":
            let base = category.contains("热门") ? "/hot" : category.contains("4K") ? "/4k" : ""
            route = page == 1 ? (base.isEmpty ? "/" : "\(base)/") : (base.isEmpty ? "/new_videos/\(page)/" : "\(base)/\(page)/")
        case "xhamster":
            let base = category.contains("新") ? "/newest" : (category.contains("本地") ? "" : "/best")
            route = page == 1 ? (base.isEmpty ? "/" : "\(base)/") : (base.isEmpty ? "/best/\(page)" : "\(base)/\(page)")
        case "eporner":
            route = "/api/v2/video/search/"
        case "mrdouga":
            let isPopular = category.contains("热")
            let pathPrefix = isPopular ? "/most-popular" : "/latest-updates"
            let query = isPopular ? "?sort_by=video_viewed" : ""
            route = page == 1 ? "\(pathPrefix)/\(query)" : "\(pathPrefix)/\(page)/\(query)"
        case "ohentai":
            route = category.contains("人气") ? "/popular/" : category.contains("新") ? "/latest/" : "/"
        case "xnxx":
            // XNXX's current kiosk is /best/; /new/ returns 404 on the live site.
            // When page > 1, path-index as /best/\(page) without appending query parameters.
            route = page == 1 ? "/best/" : "/best/\(page)"
        default:
            route = "/"
        }

        guard var components = URLComponents(string: baseURL(for: source) + route) else { return nil }
        if source.id == "eporner" {
            components.queryItems = [
                URLQueryItem(name: "query", value: category == "全部" ? "featured" : category),
                URLQueryItem(name: "per_page", value: "24"),
                URLQueryItem(name: "page", value: String(page))
            ]
        } else if page > 1 && queryPaginatedSourceIDs.contains(source.id) {
            var queryItems = components.queryItems ?? []
            queryItems.append(URLQueryItem(name: "page", value: String(page)))
            components.queryItems = queryItems
        }
        return components.url
    }

    private static let queryPaginatedSourceIDs: Set<String> = [
        "missav", "pornhub", "tokyomotion", "ohentai"
    ]

    static func searchURL(for source: VideoSourceDefinition, query: String, page: Int = 1) -> URL? {
        guard VideoSourceCatalog.supportedSourceIDs.contains(source.id) else { return nil }
        var components: URLComponents?
        switch source.id {
        case "huavod":
            components = URLComponents(string: baseURL(for: source) + "/vodsearch/-------------.html")
            components?.queryItems = [URLQueryItem(name: "wd", value: query)]
        case "huangguo":
            components = URLComponents(string: baseURL(for: source) + "/search")
            components?.queryItems = [URLQueryItem(name: "keyword", value: query)]
        case "91porn":
            components = URLComponents(string: baseURL(for: source) + "/search_result.php")
            components?.queryItems = [URLQueryItem(name: "search_id", value: query)]
        case "91dongman":
            components = URLComponents(string: baseURL(for: source) + "/search-result/")
            components?.queryItems = [URLQueryItem(name: "q", value: query)]
        case "xvideos":
            components = URLComponents(string: baseURL(for: source) + "/")
            components?.queryItems = [URLQueryItem(name: "k", value: query)]
        case "missav":
            guard let encoded = query.addingPercentEncoding(withAllowedCharacters: Self.pathQueryAllowed) else { return nil }
            components = URLComponents(string: baseURL(for: source) + "/ja/search/\(encoded)")
        case "85po":
            guard let encoded = query.addingPercentEncoding(withAllowedCharacters: Self.pathQueryAllowed) else { return nil }
            components = URLComponents(string: baseURL(for: source) + "/ja/search/\(encoded)/")
        case "pornhub":
            components = URLComponents(string: baseURL(for: source) + "/video/search")
            components?.queryItems = [URLQueryItem(name: "search", value: query), URLQueryItem(name: "page", value: String(page))]
        case "javnoni":
            components = URLComponents(string: baseURL(for: source) + "/")
            components?.queryItems = [URLQueryItem(name: "s", value: query), URLQueryItem(name: "paged", value: String(page))]
        case "javsb":
            components = URLComponents(string: baseURL(for: source) + "/ja/vod/search.html")
            components?.queryItems = [URLQueryItem(name: "wd", value: query), URLQueryItem(name: "page", value: String(page))]
        case "tokyomotion":
            components = URLComponents(string: baseURL(for: source) + "/search")
            components?.queryItems = [URLQueryItem(name: "search_query", value: query), URLQueryItem(name: "search_type", value: "videos"), URLQueryItem(name: "page", value: String(page))]
        case "kissjav", "mrdouga":
            guard let encoded = query.addingPercentEncoding(withAllowedCharacters: Self.pathQueryAllowed) else { return nil }
            components = URLComponents(string: baseURL(for: source) + "/search/\(encoded)/")
            components?.queryItems = [URLQueryItem(name: "page", value: String(page))]
        case "spankbang":
            guard let encoded = query.addingPercentEncoding(withAllowedCharacters: Self.pathQueryAllowed) else { return nil }
            components = URLComponents(string: baseURL(for: source) + "/s/\(encoded)/")
            components?.queryItems = [URLQueryItem(name: "page", value: String(page))]
        case "xhamster":
            guard let encoded = query.addingPercentEncoding(withAllowedCharacters: Self.pathQueryAllowed) else { return nil }
            components = URLComponents(string: baseURL(for: source) + "/search/\(encoded)/")
            components?.queryItems = [URLQueryItem(name: "page", value: String(page))]
        case "eporner":
            components = URLComponents(string: baseURL(for: source) + "/api/v2/video/search/")
            components?.queryItems = [URLQueryItem(name: "query", value: query), URLQueryItem(name: "per_page", value: "24"), URLQueryItem(name: "page", value: String(page))]
        case "134x":
            components = URLComponents(string: baseURL(for: source) + "/search")
            components?.queryItems = [URLQueryItem(name: "q", value: query), URLQueryItem(name: "page", value: String(page))]
        case "ohentai":
            components = URLComponents(string: baseURL(for: source) + "/search.php")
            components?.queryItems = [URLQueryItem(name: "k", value: query)]
        case "xnxx":
            guard let encoded = query.addingPercentEncoding(withAllowedCharacters: Self.pathQueryAllowed) else { return nil }
            components = URLComponents(string: baseURL(for: source) + "/search/\(encoded)\(page > 1 ? "/\(page)" : "")")
        default: return nil
        }
        return components?.url
    }

    private static let pathQueryAllowed: CharacterSet = {
        var allowed = CharacterSet.urlPathAllowed
        allowed.remove(charactersIn: "/?#%")
        return allowed
    }()

    static func isSearchSupported(_ source: VideoSourceDefinition) -> Bool {
        VideoSourceCatalog.searchableSourceIDs.contains(source.id)
    }

    static func baseURL(for source: VideoSourceDefinition) -> String {
        switch source.id {
        case "missav": return "https://missav.media"
        case "85po": return "https://www.85po.com"
        case "pornhub": return "https://jp.pornhub.com"
        case "javnoni": return "https://javnoni.com"
        case "javsb": return "https://jav.sb"
        case "tokyomotion": return "https://www.tokyomotion.net"
        case "kissjav": return "https://kissjav.li"
        case "spankbang": return "https://www.spankbang.com"
        case "xhamster": return "https://xhamster.com"
        case "eporner": return "https://www.eporner.com"
        case "134x": return "https://134x.com"
        case "mrdouga": return "https://mrdouga.com"
        case "ohentai": return "https://ohentai.org"
        case "xnxx": return "https://www.xnxx.com"
        default: return source.baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        }
    }

    static func parseHTMLFeed(
        _ html: String,
        source: VideoSourceDefinition,
        category: String,
        baseURL: String? = nil
    ) -> [VideoItem] {
        let pageBase = baseURL ?? Self.baseURL(for: source)
        guard
            let anchorRegex = feedAnchorRegex,
            let attributeRegex = feedAttributeRegex,
            let imageRegex = feedImageRegex
        else { return [] }

        let nsHTML = html as NSString
        let range = NSRange(location: 0, length: nsHTML.length)
        let anchors = anchorRegex.matches(in: html, range: range)
        var items: [VideoItem] = []
        var seen = Set<String>()

        for anchor in anchors {
            let attributes = nsHTML.substring(with: anchor.range(at: 1))
            let body = nsHTML.substring(with: anchor.range(at: 2))
            let values = attributeValues(attributes, regex: attributeRegex)
            let rawHref = values["href"] ?? ""
            guard
                let baseURL = URL(string: pageBase),
                let itemURL = URL(string: decodeHTML(rawHref), relativeTo: baseURL)?.absoluteURL,
                hostBelongsToSource(itemURL, source: source),
                isVideoPage(itemURL, source: source)
            else { continue }

            let cardHTML = cardContext(in: nsHTML, around: anchor.range)
            // Always scan the anchor's own inner HTML first so nested <div></div> wrappers
            // inside <a> (such as badge overlays before <img>) are never truncated by cardContext.
            let bodyImageMatches = imageRegex.matches(in: body, range: NSRange(location: 0, length: (body as NSString).length))
            let imageHTML: String
            let imageMatches: [NSTextCheckingResult]
            if !bodyImageMatches.isEmpty || cardHTML.isEmpty {
                imageHTML = body
                imageMatches = bodyImageMatches
            } else {
                imageHTML = cardHTML
                imageMatches = imageRegex.matches(in: cardHTML, range: NSRange(location: 0, length: (cardHTML as NSString).length))
            }
            let coverKeys = ["data-poster", "data-original", "data-src", "data-thumb", "data-lazy-src", "data-sfwthumb", "data-mediumthumb", "poster"]
            let validCoverURL: (String?) -> Bool = { value in
                guard let value else { return false }
                let lower = value.lowercased()
                return !value.isEmpty
                    && !lower.hasPrefix("data:image")
                    && !lower.hasPrefix("javascript:")
                    && !lower.contains("/img/flags/")
                    && !lower.contains("preview.mp4")
            }
            let imageCandidateDicts = imageMatches.map { imageMatch in
                let candidateAttributes = (imageHTML as NSString).substring(with: imageMatch.range(at: 1))
                return attributeValues(candidateAttributes, regex: attributeRegex)
            }
            var imageValues = imageCandidateDicts.first ?? [:]
            var rawCover = ""
            for candidateValues in imageCandidateDicts {
                if let candidateCover = coverKeys.compactMap({ candidateValues[$0] }).first(where: validCoverURL) {
                    imageValues = candidateValues
                    rawCover = candidateCover
                    break
                }
            }
            if rawCover.isEmpty {
                rawCover = coverKeys.compactMap { values[$0] }.first(where: validCoverURL) ?? ""
            }
            if rawCover.isEmpty {
                for candidateValues in imageCandidateDicts {
                    if let candidateSrc = candidateValues["src"], validCoverURL(candidateSrc) {
                        imageValues = candidateValues
                        rawCover = candidateSrc
                        break
                    }
                }
            }
            if rawCover.isEmpty, let anchorSrc = values["src"], validCoverURL(anchorSrc) {
                rawCover = anchorSrc
            }
            let cleanRawCover = decodeHTML(rawCover).trimmingCharacters(in: .whitespacesAndNewlines)
            let encodedRawCover = cleanRawCover.contains(" ") ? cleanRawCover.replacingOccurrences(of: " ", with: "%20") : cleanRawCover
            let cover = URL(string: encodedRawCover, relativeTo: URL(string: pageBase))?.absoluteURL.absoluteString ?? ""

            let title = [values["title"], imageValues["alt"], values["aria-label"], textContent(body)]
                .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
                .first { !$0.isEmpty } ?? ""

            if let existingIndex = items.firstIndex(where: { $0.playbackUrl == itemURL.absoluteString }) {
                let old = items[existingIndex]
                let updatedTitle = (!title.isEmpty && title != "精选视频") ? title : old.title
                let updatedCover = old.coverUrl.isEmpty ? cover : old.coverUrl
                if updatedTitle != old.title || updatedCover != old.coverUrl {
                    items[existingIndex] = VideoItem(
                        id: old.id,
                        title: updatedTitle,
                        subtitle: old.subtitle,
                        coverUrl: updatedCover,
                        playbackUrl: old.playbackUrl,
                        duration: old.duration,
                        rating: old.rating,
                        tags: old.tags,
                        category: old.category,
                        isTop10: old.isTop10,
                        rankNumber: old.rankNumber,
                        sourceName: old.sourceName
                    )
                }
                continue
            }

            guard (!title.isEmpty || !cover.isEmpty), seen.insert(itemURL.absoluteString).inserted else { continue }

            items.append(VideoItem(
                id: "\(source.id)-\(itemURL.absoluteString)",
                title: title.isEmpty ? "精选视频" : title,
                subtitle: "\(source.name) · \(category)",
                coverUrl: cover,
                playbackUrl: itemURL.absoluteString,
                tags: [source.name, category],
                category: category,
                sourceName: source.name
            ))
        }
        if (source.id == "missav" || source.id == "ohentai"), items.contains(where: { !$0.coverUrl.isEmpty }) {
            return items.filter { !$0.coverUrl.isEmpty }
        }
        return items
    }

    static func extractMediaURL(from html: String, pageURL: URL) -> URL? {
        let normalized = decodeHTML(html)
            .replacingOccurrences(of: "\\/", with: "/")
            .replacingOccurrences(of: "\\u002F", with: "/", options: .caseInsensitive)
            .replacingOccurrences(of: "\\u0026", with: "&", options: .caseInsensitive)
        let unpacked = unpackLegacyPlayerScripts(in: normalized)
        let searchableHTML = unpacked.isEmpty ? normalized : unpacked + "\n" + normalized
        let patterns = [
            // EPORNER's page exposes signed CDN files through a short-lived
            // download redirect. Resolve that route instead of the stale
            // gvideo URL embedded in JSON-LD.
            #"(?is)(?:href|data-src)=['\"]([^'\"]*/dload/[^'\"]+\.mp4[^'\"]*)['\"]"#,
            #"(?is)html5player\.setVideo(?:HLS|UrlHigh|UrlLow)\(['\"]([^'\"]+)['\"]\)"#,
            #"(?is)(?:src|href)=['\"]([^'\"]*/vsrc/(?:sd|hd)/[^'\"]+)['\"]"#,
            #"(?is)['\"]videoUrl['\"]\s*:\s*['\"]([^'\"]+)['\"]"#,
            #"(?is)['\"]?(?:file|video_url|video_alt_url|videoUrl|hlsUrl|contentUrl|url_inline|source|source1280|source842)['\"]?\s*[=:]\s*['\"]([^'\"]+\.(?:m3u8|mp4)(?:/?\?[^'\"]*)?/?)["'\"]"#,
            #"(?is)<(?:source|video)[^>]+(?:src|data-src)=['\"]([^'\"]+\.(?:m3u8|mp4)(?:\/?\?[^'\"]*)?)['\"]"#
        ]
        for pattern in patterns {
            guard let regex = try? NSRegularExpression(pattern: pattern) else { continue }
            let searchNSString = searchableHTML as NSString
            let matches = regex.matches(in: searchableHTML, range: NSRange(location: 0, length: searchNSString.length))
            for match in matches {
                let range = match.numberOfRanges > 1 && match.range(at: 1).location != NSNotFound ? match.range(at: 1) : match.range
                let candidate = searchNSString.substring(with: range)
                    .replacingOccurrences(of: "\\u0026", with: "&", options: .caseInsensitive)
                    .replacingOccurrences(of: "\\/", with: "/")
                    .replacingOccurrences(of: "\\&", with: "&")
                    .replacingOccurrences(of: "&amp;", with: "&")
                let parsedURL = URL(string: candidate, relativeTo: pageURL)?.absoluteURL
                    ?? (candidate.addingPercentEncoding(withAllowedCharacters: .urlFragmentAllowed).flatMap { URL(string: $0, relativeTo: pageURL)?.absoluteURL })
                guard let url = parsedURL,
                      ["http", "https"].contains(url.scheme?.lowercased() ?? ""),
                      isSupportedMediaPath(url.path),
                      !isAdOrPreviewMediaURL(url) else { continue }
                return url
            }
        }

        // Several legacy players base64-wrap the actual media URL in a `video_url`
        // field. Decode only values that look like a base64 payload and validate the
        // resulting URL with the same media extension guard above.
        let encodedPattern = #"(?i)['\"]?(?:video_url|videoUrl)['\"]?\s*[:=]\s*['\"]([A-Za-z0-9+/=_-]{32,})['\"]"#
        if let regex = try? NSRegularExpression(pattern: encodedPattern),
           let match = regex.firstMatch(in: searchableHTML, range: NSRange(location: 0, length: (searchableHTML as NSString).length)) {
            var value = (searchableHTML as NSString).substring(with: match.range(at: 1)).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
            value += String(repeating: "=", count: (4 - value.count % 4) % 4)
            if let decoded = Data(base64Encoded: value, options: .ignoreUnknownCharacters),
               let decodedString = String(data: decoded, encoding: .utf8),
               let decodedURL = URL(string: decodedString.addingPercentEncoding(withAllowedCharacters: .urlFragmentAllowed) ?? decodedString),
               ["http", "https"].contains(decodedURL.scheme?.lowercased() ?? ""),
               (decodedURL.path.contains("/get_file/") || ["m3u8", "mp4"].contains(decodedURL.pathExtension.lowercased())) {
                return decodedURL
            }
        }
        return nil
    }

    private static func isSupportedMediaPath(_ path: String) -> Bool {
        let extensionName = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            .split(separator: "/").last?.split(separator: ".").last.map(String.init)?.lowercased()
        return ["m3u8", "mp4"].contains(extensionName)
            || path.range(of: #"/vsrc/(?:sd|hd)/[A-Za-z0-9]+$"#, options: .regularExpression) != nil
    }

    private static func isAdOrPreviewMediaURL(_ url: URL) -> Bool {
        let host = url.host?.lowercased() ?? ""
        let path = url.path.lowercased()
        if path.contains("/preview.mp4") || url.lastPathComponent.lowercased().hasPrefix("preview.") {
            return true
        }
        if path.contains("/vast") || path.contains("/preroll") || path.contains("/ad_vast")
            || path.contains("/ads/") || path.contains("_ad.") || path.contains("-ad.") {
            return true
        }
        let blockedAdHosts = [
            "tsyndicate.com", "exoclick.com", "realsrv.com", "magsrv.com",
            "trafficjunky.net", "trafficjunky.com", "juicyads.com", "popads.net",
            "doubleclick.net", "googlesyndication.com", "biomanos.org", "benchform.org",
            "myavlive.com", "chaturbate.com", "stripchat.com", "livejasmin.com",
            "bongacams.com", "adserver.com", "adsco.re"
        ]
        return blockedAdHosts.contains(where: { host == $0 || host.hasSuffix(".\($0)") })
    }

    private static func unpackLegacyPlayerScripts(in html: String) -> String {
        guard let regex = try? NSRegularExpression(
            pattern: #"(?is)eval\(function\(p,a,c,k,e,d\)\{.*?\}\('((?:\\.|[^'])*)',\s*(\d+),\s*(\d+),\s*'((?:\\.|[^'])*)'\.split\(['"]\|['"]\)(?:,[^)]*)?\)\)"#
        ) else { return "" }
        let nsHTML = html as NSString
        let matches = regex.matches(in: html, range: NSRange(location: 0, length: nsHTML.length))
        return matches.compactMap { match -> String? in
            guard match.numberOfRanges == 5,
                  let radix = Int(nsHTML.substring(with: match.range(at: 2))),
                  (2...36).contains(radix),
                  let tokenCount = Int(nsHTML.substring(with: match.range(at: 3))),
                  tokenCount > 0, tokenCount <= 2_000 else { return nil }

            var decoded = unescapeJSLiteral(nsHTML.substring(with: match.range(at: 1)))
            let dictionary = unescapeJSLiteral(nsHTML.substring(with: match.range(at: 4))).components(separatedBy: "|")
            guard !dictionary.isEmpty else { return nil }
            for index in stride(from: min(tokenCount, dictionary.count) - 1, through: 0, by: -1) {
                guard !dictionary[index].isEmpty else { continue }
                let token = String(index, radix: radix)
                guard let tokenRegex = try? NSRegularExpression(pattern: "(?<![A-Za-z0-9_])\(NSRegularExpression.escapedPattern(for: token))(?![A-Za-z0-9_])") else { continue }
                let range = NSRange(location: 0, length: (decoded as NSString).length)
                decoded = tokenRegex.stringByReplacingMatches(in: decoded, range: range, withTemplate: NSRegularExpression.escapedTemplate(for: dictionary[index]))
            }
            return decoded
        }.joined(separator: "\n")
    }

    private static func unescapeJSLiteral(_ value: String) -> String {
        value
            .replacingOccurrences(of: "\\'", with: "'")
            .replacingOccurrences(of: "\\\"", with: "\"")
            .replacingOccurrences(of: "\\/", with: "/")
            .replacingOccurrences(of: "\\\\", with: "\\")
            .replacingOccurrences(of: "\\n", with: "\n")
            .replacingOccurrences(of: "\\r", with: "\r")
    }

    static func playerFrameURLs(from html: String, pageURL: URL, source: VideoSourceDefinition) -> [URL] {
        guard let frameRegex = try? NSRegularExpression(pattern: #"(?is)<iframe\b([^>]*)>"#),
              let srcRegex = try? NSRegularExpression(pattern: #"(?i)\bsrc\s*=\s*(['\"])(.*?)\1"#) else {
            return []
        }

        let sourceHost = URL(string: baseURL(for: source))?.host?.lowercased()
        let htmlNSString = html as NSString
        let fullRange = NSRange(location: 0, length: htmlNSString.length)
        let frames: [URL] = frameRegex.matches(in: html, range: fullRange).compactMap { frameMatch -> URL? in
            let attributes = htmlNSString.substring(with: frameMatch.range(at: 1))
            let attributesNSString = attributes as NSString
            let attributesRange = NSRange(location: 0, length: attributesNSString.length)
            guard let srcMatch = srcRegex.firstMatch(in: attributes, range: attributesRange),
                  srcMatch.numberOfRanges > 2,
                  let frameURL = URL(string: decodeHTML(attributesNSString.substring(with: srcMatch.range(at: 2))), relativeTo: pageURL)?.absoluteURL,
                  ["http", "https"].contains(frameURL.scheme?.lowercased() ?? ""),
                  let frameHost = frameURL.host?.lowercased() else {
                return nil
            }
            let isSameSource = sourceHost.map { frameHost == $0 || frameHost.hasSuffix(".\($0)") } ?? false
            let isKnownJavnoniPlayer = source.id == "javnoni"
                && javnoniPlayerHosts.contains(where: { frameHost == $0 || frameHost.hasSuffix(".\($0)") })
            guard isSameSource || isKnownJavnoniPlayer else { return nil }
            return frameURL
        }

        guard source.id == "javnoni",
              let playerDataRegex = try? NSRegularExpression(pattern: #"(?is)<script\b[^>]*class\s*=\s*['\"][^'\"]*javhd-player-data[^'\"]*['\"][^>]*>(.*?)</script>"#),
              let dataMatch = playerDataRegex.firstMatch(in: html, range: fullRange),
              dataMatch.numberOfRanges > 1 else {
            return frames
        }
        let json = decodeHTML(htmlNSString.substring(with: dataMatch.range(at: 1)))
        guard let providers = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [[String: Any]] else {
            return frames
        }
        let externalFrames = providers.flatMap { $0["episodes"] as? [[String: Any]] ?? [] }
            .compactMap { $0["url"] as? String }
            .compactMap { URL(string: decodeHTML($0), relativeTo: pageURL)?.absoluteURL }
            .filter { url in
                guard let host = url.host?.lowercased() else { return false }
                return javnoniPlayerHosts.contains(where: { host == $0 || host.hasSuffix(".\($0)") })
            }
        return frames + externalFrames
    }

    private static let javnoniPlayerHosts: [String] = [
        "luluvid.com",
        "luluvdo.com",
        "luluvido.com",
        "lulustream.com",
        "nuvorel.top",
        "streamruby.com",
        "doodstream.com",
        "playmogo.com",
    ]

    private static func isVideoPage(_ url: URL, source: VideoSourceDefinition) -> Bool {
        let path = url.path.lowercased()
        switch source.id {
        case "missav":
            let components = path.split(separator: "/")
            guard components.count >= 2 else { return false }
            let nonVideoSections: Set<String> = [
                "actresses", "genres", "makers", "labels", "series", "site", "cast",
                "tags", "search", "playlists", "articles", "user", "account", "auth"
            ]
            if components.dropLast().contains(where: { nonVideoSections.contains(String($0)) }) {
                return false
            }
            let last = String(components.last ?? "")
            let reserved: Set<String> = [
                "vip", "search", "popular", "release", "new", "recommended",
                "chinese-subtitle", "uncensored-leak", "english-subtitle",
                "today-hot", "weekly-hot", "monthly-hot",
                "siro", "luxu", "gana", "maan", "scute", "ara",
                "fc2", "heyzo", "tokyohot", "1pondo", "caribbeancom", "caribbeancompr",
                "10musume", "pacopacomama", "gachinco", "xxxav", "marriedslash",
                "naughty4610", "naughty0930", "madou", "twav", "furuke", "klive", "clive",
                "saved", "playlists", "history", "contact", "ads", "terms", "privacy",
                "upload", "articles", "login", "register", "forgot-password",
                "actresses", "genres", "makers", "labels", "series", "ranking", "vr"
            ]
            return !reserved.contains(last)
        case "85po": return path.contains("/v/")
        case "pornhub": return url.path.contains("view_video.php") && url.query?.contains("viewkey=") == true
        case "javnoni": return path.contains("/archives/") && !path.contains("/category/") && !path.contains("/tag/")
        case "javsb": return path.hasPrefix("/ja/jav/") || path.hasPrefix("/ja/video/") || path.hasPrefix("/video/")
        case "tokyomotion": return path.hasPrefix("/video/")
        case "kissjav", "mrdouga": return path.hasPrefix("/video/")
        case "spankbang": return path.contains("/video/") || path.contains("/v/")
        case "xhamster": return path.contains("/videos/") || path.contains("/movies/")
        case "eporner": return path.contains("/video-") || path.contains("/video/")
        case "ohentai": return path == "/detail.php" && URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.contains(where: { $0.name == "vid" && !($0.value ?? "").isEmpty }) == true
        case "xnxx": return path.contains("/video-")
        default: return false
        }
    }

    private static func hostBelongsToSource(_ url: URL, source: VideoSourceDefinition) -> Bool {
        guard let candidateHost = url.host?.lowercased(),
              let sourceHost = URL(string: baseURL(for: source))?.host?.lowercased() else { return false }
        let candidate = candidateHost.hasPrefix("www.") ? String(candidateHost.dropFirst(4)) : candidateHost
        let expected = sourceHost.hasPrefix("www.") ? String(sourceHost.dropFirst(4)) : sourceHost
        if candidate == expected { return true }
        if source.id == "missav" && candidate.contains("missav") {
            return true
        }
        // Pornhub serves regional catalogue pages that link to the canonical
        // www host. They are the same upstream source and share playback URLs.
        return source.id == "pornhub"
            && (candidate == "pornhub.com" || candidate.hasSuffix(".pornhub.com"))
    }

    private static func cardContext(in html: NSString, around range: NSRange) -> String {
        guard range.location != NSNotFound else { return "" }
        let prefixRange = NSRange(location: 0, length: range.location)
        let prefix = html.substring(with: prefixRange) as NSString
        var start = prefix.range(of: "<div", options: .backwards).location
        guard start != NSNotFound, range.location - start < 2400 else { return "" }
        let remainder = NSRange(location: range.location, length: html.length - range.location)
        let closing = html.range(of: "</div>", options: [], range: remainder)
        let end = closing.location == NSNotFound ? NSMaxRange(range) : NSMaxRange(closing)
        start = min(start, range.location)
        guard end > start, end - start < 12000 else { return "" }
        return html.substring(with: NSRange(location: start, length: end - start))
    }

    private static func attributeValues(_ fragment: String, regex: NSRegularExpression) -> [String: String] {
        let nsFragment = fragment as NSString
        let matches = regex.matches(in: fragment, range: NSRange(location: 0, length: nsFragment.length))
        var values: [String: String] = [:]
        for match in matches where match.numberOfRanges == 4 {
            let key = nsFragment.substring(with: match.range(at: 1)).lowercased()
            values[key] = decodeHTML(nsFragment.substring(with: match.range(at: 3)))
        }
        return values
    }

    private static func textContent(_ fragment: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: #"(?is)<(script|style)\b[^>]*>.*?</\1>|<[^>]+>"#) else { return fragment }
        let nsFragment = fragment as NSString
        return decodeHTML(regex.stringByReplacingMatches(in: fragment, range: NSRange(location: 0, length: nsFragment.length), withTemplate: " "))
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
    }

    private static func decodeHTML(_ value: String) -> String {
        value
            .replacingOccurrences(of: "\\/", with: "/")
            .replacingOccurrences(of: "\\u002F", with: "/", options: .caseInsensitive)
            .replacingOccurrences(of: "\\u0026", with: "&", options: .caseInsensitive)
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
    }
}
