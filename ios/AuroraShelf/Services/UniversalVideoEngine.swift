import Foundation

public enum VideoFeedError: LocalizedError {
    case invalidURL(source: String)
    case invalidResponse(source: String)
    case httpStatus(source: String, statusCode: Int)
    case malformedResponse(source: String)
    case unsupportedSource(source: String)

    public var errorDescription: String? {
        switch self {
        case .invalidURL(let source):
            return "\(source) 的内容地址无效。"
        case .invalidResponse(let source):
            return "\(source) 返回了无效响应。"
        case .httpStatus(let source, let statusCode):
            return "\(source) 请求失败（HTTP \(statusCode)）。"
        case .malformedResponse(let source):
            return "无法读取 \(source) 返回的内容。"
        case .unsupportedSource(let source):
            return "暂不支持加载\(source)的视频列表。"
        }
    }
}

/// Loads feeds only from sources with an implemented upstream integration.
public enum UniversalVideoEngine {
    public static func fetchXVideosFeed(category: String, session: URLSession, page: Int = 1) async throws -> [VideoItem] {
        let page = max(page, 1)
        let categoryPath: String
        switch category {
        case "热播", "最佳": categoryPath = "/best/"
        case "自拍": categoryPath = "/c/Amateur-65/"
        case "日本": categoryPath = "/tags/japanese/"
        case "亚洲": categoryPath = "/c/Asian_Woman-32/"
        case "4K超清": categoryPath = "/tags/4k/"
        default: categoryPath = "/"
        }

        let finalPath: String
        if page == 1 {
            finalPath = categoryPath
        } else {
            finalPath = categoryPath == "/" ? "/new/\(page - 1)" : "\(categoryPath)\(page - 1)"
        }

        guard let url = URL(string: "https://www.xvideos.com\(finalPath)") else {
            throw VideoFeedError.invalidURL(source: "XVideos")
        }

        var request = URLRequest(url: url)
        request.setValue("https://www.xvideos.com/", forHTTPHeaderField: "Referer")
        request.setValue(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15",
            forHTTPHeaderField: "User-Agent"
        )

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw VideoFeedError.invalidResponse(source: "XVideos")
        }
        return try decodeXVideosFeed(
            data: data,
            statusCode: httpResponse.statusCode,
            category: category
        )
    }

    static func decodeXVideosFeed(data: Data, statusCode: Int, category: String) throws -> [VideoItem] {
        guard (200...299).contains(statusCode) else {
            throw VideoFeedError.httpStatus(source: "XVideos", statusCode: statusCode)
        }
        guard let html = String(data: data, encoding: .utf8) else {
            throw VideoFeedError.malformedResponse(source: "XVideos")
        }

        return parseXVideosHtml(html, category: category)
    }

    private static let xvideosHrefRegex = try? NSRegularExpression(pattern: #"href="(/video\.[0-9a-zA-Z_]+/[^"]*)""#)
    private static let xvideosTitleRegex = try? NSRegularExpression(pattern: #"title="([^"]+)""#)
    private static let xvideosImgRegex = try? NSRegularExpression(pattern: #"data-src="([^"]+)""#)
    private static let xvideosSrcImgRegex = try? NSRegularExpression(pattern: #"src="([^"]+)""#)
    private static let xvideosDurationRegex = try? NSRegularExpression(pattern: #"class="duration">([^<]+)</span>"#)

    private static func parseXVideosHtml(_ html: String, category: String) -> [VideoItem] {
        let blocks = html.components(separatedBy: "thumb-block")
        guard blocks.count > 1 else { return [] }

        var items: [VideoItem] = []
        for block in blocks.dropFirst() {
            let nsBlock = block as NSString
            let blockRange = NSRange(location: 0, length: nsBlock.length)

            guard
                let hrefMatch = xvideosHrefRegex?.firstMatch(in: block, options: [], range: blockRange),
                hrefMatch.numberOfRanges > 1,
                let titleMatch = xvideosTitleRegex?.firstMatch(in: block, options: [], range: blockRange),
                titleMatch.numberOfRanges > 1
            else {
                continue
            }

            let href = nsBlock.substring(with: hrefMatch.range(at: 1))
            let rawTitle = nsBlock.substring(with: titleMatch.range(at: 1))
            let cleanTitle = rawTitle
                .replacingOccurrences(of: "&#039;", with: "'")
                .replacingOccurrences(of: "&#39;", with: "'")
                .replacingOccurrences(of: "&apos;", with: "'")
                .replacingOccurrences(of: "&quot;", with: "\"")
                .replacingOccurrences(of: "&#34;", with: "\"")
                .replacingOccurrences(of: "&amp;", with: "&")
                .replacingOccurrences(of: "&#38;", with: "&")
                .replacingOccurrences(of: "&lt;", with: "<")
                .replacingOccurrences(of: "&gt;", with: ">")
                .trimmingCharacters(in: .whitespacesAndNewlines)

            guard !href.isEmpty, !cleanTitle.isEmpty else { continue }

            let pageURL = href.hasPrefix("http") ? href : "https://www.xvideos.com\(href)"
            var coverURL = ""
            if let imgMatch = xvideosImgRegex?.firstMatch(in: block, options: [], range: blockRange),
               imgMatch.numberOfRanges > 1 {
                coverURL = nsBlock.substring(with: imgMatch.range(at: 1))
            }
            if coverURL.isEmpty || coverURL.contains("blank.gif") {
                if let srcMatch = xvideosSrcImgRegex?.firstMatch(in: block, options: [], range: blockRange),
                   srcMatch.numberOfRanges > 1 {
                    let srcCandidate = nsBlock.substring(with: srcMatch.range(at: 1))
                    if !srcCandidate.contains("blank.gif") {
                        coverURL = srcCandidate
                    }
                }
            }

            var duration = ""
            if let durMatch = xvideosDurationRegex?.firstMatch(in: block, options: [], range: blockRange),
               durMatch.numberOfRanges > 1 {
                duration = nsBlock.substring(with: durMatch.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
            }

            items.append(VideoItem(
                id: "xv-\(pageURL)",
                title: cleanTitle,
                subtitle: "XVideos · \(category)",
                coverUrl: coverURL,
                playbackUrl: pageURL,
                duration: duration,
                rating: "",
                tags: ["XVideos", category],
                category: category,
                isTop10: false,
                rankNumber: nil,
                sourceName: "XVideos"
            ))
        }
        return items
    }

    public static func fetchEPORNERFeed(category: String, session: URLSession, page: Int = 1) async throws -> [VideoItem] {
        var components = URLComponents(string: "https://www.eporner.com/api/v2/video/search/")
        components?.queryItems = [
            URLQueryItem(name: "query", value: category == "全部" ? "featured" : category),
            URLQueryItem(name: "per_page", value: "24"),
            URLQueryItem(name: "page", value: String(max(page, 1)))
        ]
        guard let url = components?.url else {
            throw VideoFeedError.invalidURL(source: "EPORNER")
        }

        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw VideoFeedError.invalidResponse(source: "EPORNER")
        }
        return try decodeEPORNERFeed(data: data, statusCode: httpResponse.statusCode, category: category)
    }

    static func decodeEPORNERFeed(data: Data, statusCode: Int, category: String) throws -> [VideoItem] {
        guard (200...299).contains(statusCode) else {
            throw VideoFeedError.httpStatus(source: "EPORNER", statusCode: statusCode)
        }
        guard
            let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let videos = object["videos"] as? [[String: Any]]
        else {
            throw VideoFeedError.malformedResponse(source: "EPORNER")
        }

        return videos.compactMap { video in
            guard
                let id = video["id"] as? String,
                let title = video["title"] as? String,
                !id.isEmpty,
                !title.isEmpty,
                let pageURL = video["url"] as? String,
                let pageComponents = URLComponents(string: pageURL),
                let host = pageComponents.host?.lowercased(),
                host == "eporner.com" || host.hasSuffix(".eporner.com")
            else {
                return nil
            }

            let length = video["length_sec"] as? Int ?? 0
            let duration = length > 0 ? String(format: "%02d:%02d", length / 60, length % 60) : ""
            let coverURL = (video["default_thumb"] as? [String: Any])?["src"] as? String ?? ""
            let ratingNumber = (video["rate"] as? NSNumber)?.doubleValue
                ?? (video["rate"] as? String).flatMap(Double.init)
            let rating = ratingNumber.map { String(format: "%.1f", $0) } ?? ""

            return VideoItem(
                id: "ep-\(id)",
                title: title,
                subtitle: "EPORNER · \(category)",
                coverUrl: coverURL,
                playbackUrl: pageURL,
                duration: duration,
                rating: rating,
                tags: ["EPORNER", category],
                category: category,
                isTop10: false,
                rankNumber: nil,
                sourceName: "EPORNER"
            )
        }
    }
}
