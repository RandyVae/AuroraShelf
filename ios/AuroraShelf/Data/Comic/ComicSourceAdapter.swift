import Foundation

enum ComicSourceAdapter {
    static let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1"

    static func categoryID(sourceID: String, label: String) -> String {
        let maps: [String: [String: String]] = [
            "baozi": ["全部": "all", "恋爱": "lianai", "纯爱": "chunai", "古风": "gufeng", "都市": "dushi", "热血": "rexie", "搞笑": "gaoxiao"],
            "jm": ["全部": "0", "同人": "doujin", "单本": "single", "短篇": "short", "韩漫": "hanman", "美漫": "meiman", "3D": "3D"],
            "komiic": ["全部": "0", "爱情": "1", "冒险": "8", "魔幻": "11", "科幻": "17", "热血": "21", "恐怖": "27", "百合": "9"],
            "dongman91": ["全部": "tongrenzhi", "精选漫画": "tongrenzhi", "日韩专区": "hanman", "欧美连载": "books"],
            "8muses": ["全部": "browse", "Popular": "popular", "New": "new", "Classics": "top"],
            "gedecomix": ["全部": "", "Latest": "", "Trending": "trending", "Manhwa": "", "热度": "views", "评分": "rating"],
            "copy": ["全部": "", "日漫": "riman", "韩漫": "hanman", "美漫": "meiman", "爱情": "aiqing", "欢乐向": "huanlexiang", "冒险": "maoxian", "奇幻": "qihuan", "百合": "baihe", "校园": "xiaoyuan", "科幻": "kehuan"],
            "ikmmh": ["全部": "全部"],
            "ehentai": ["全部": "0", "Doujinshi": "2", "Manga": "4", "Artist CG": "8"]
        ]
        return maps[sourceID]?[label] ?? label
    }

    static func browseURL(source: ComicSourceDefinition, category: String, page: Int) -> URL? {
        let page = max(page, 1)
        let id = categoryID(sourceID: source.id, label: category)
        let encoded = query(id)
        switch source.id {
        case "baozi":
            return URL(string: "https://cn.webmota.com/api/bzmhq/amp_comic_list?type=\(id)&region=all&state=all&filter=%2A&page=\(page)&limit=36&language=cn")
        case "jm":
            return URL(string: "https://www.cdnhjk.net/categories/filter?o=mr&c=\(encoded)&page=\(page)")
        case "komiic":
            return URL(string: "https://komiic.com/api/query")
        case "copy":
            return URL(string: "https://api.2024manga.com/api/v3/comics?limit=24&offset=\((page - 1) * 24)&ordering=-datetime_updated&theme=\(encoded)&top=&platform=3")
        case "picacg":
            let sort = ["官方推荐": "dd", "生肉": "ld", "Cosplay": "vd", "重口": "vd", "纯爱": "dd"][category] ?? "dd"
            return URL(string: "https://picaapi.picacomic.com/comics?page=\(page)&s=\(sort)")
        case "dongman91":
            let path = id == "ranking" ? "/ranking/" : "/manga/\(id)/"
            return URL(string: "https://91dongman.net\(page == 1 ? path : path + "\(page)/")")
        case "8muses":
            let path = id.isEmpty ? "browse" : id
            return URL(string: "https://8muses.io/\(path)\(page == 1 ? "" : "?page=\(page)")")
        case "gedecomix":
            let path = page == 1 ? "/porn-comics/" : "/porn-comics/page/\(page)/"
            let order = id.isEmpty ? "" : "?m_orderby=\(encoded)"
            return URL(string: "https://gedecomix.com\(path)\(order)")
        case "ikmmh":
            return URL(string: "https://ymcdnyfqdapp.ikmmh.com/booklists/9/\(encoded)/3/\(page).html")
        case "ehentai":
            var components = URLComponents(string: "https://e-hentai.org/")
            var items: [URLQueryItem] = []
            if page > 1 { items.append(URLQueryItem(name: "page", value: String(page - 1))) }
            if id != "0", let mask = Int(id) { items.append(URLQueryItem(name: "f_cats", value: String(0x3ff ^ mask))) }
            components?.queryItems = items.isEmpty ? nil : items
            return components?.url
        default: return nil
        }
    }

    static func searchURL(source: ComicSourceDefinition, query: String, page: Int) -> URL? {
        let q = queryValue(query)
        switch source.id {
        case "baozi": return URL(string: "https://cn.webmota.com/search?q=\(q)")
        case "jm": return URL(string: "https://www.cdnhjk.net/search?search_query=\(q)&o=mr&page=\(max(page, 1))")
        case "komiic": return URL(string: "https://komiic.com/api/query")
        case "copy": return URL(string: "https://api.2024manga.com/api/v3/search/comic?limit=24&offset=\((page - 1) * 24)&q=\(q)&q_type=&platform=3")
        case "dongman91": return URL(string: "https://91dongman.net/search-result/?q=\(q)\(page > 1 ? "&page=\(page)" : "")")
        case "8muses": return URL(string: "https://8muses.io/search?q=\(q)\(page > 1 ? "&page=\(page)" : "")")
        case "gedecomix": return URL(string: "https://gedecomix.com/?s=\(q)&post_type=wp-manga\(page > 1 ? "&paged=\(page)" : "")")
        case "ikmmh": return URL(string: "https://ymcdnyfqdapp.ikmmh.com/search?searchkey=\(q)")
        case "ehentai": return URL(string: "https://e-hentai.org/?f_search=\(q)\(page > 1 ? "&page=\(page - 1)" : "")")
        default: return nil
        }
    }

    static func decodeCopyComicFeed(_ data: Data) throws -> [ComicItem] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let results = root["results"] as? [String: Any],
              let list = results["list"] as? [[String: Any]] else {
            throw ComicFetchError.malformedResponse
        }
        return list.compactMap { raw in
            let item = raw["comic"] as? [String: Any] ?? raw
            guard let id = item["path_word"] as? String, !id.isEmpty,
                  let title = item["name"] as? String, !title.isEmpty else { return nil }
            let author = ((item["author"] as? [[String: Any]])?.first?["name"] as? String) ?? ""
            let tags = (item["theme"] as? [[String: Any]] ?? []).compactMap { $0["name"] as? String }
            return ComicItem(id: id, sourceId: "copy", title: title, author: author, coverUrl: item["cover"] as? String ?? "", tags: tags)
        }
    }

    static func decodeKomiicFeed(_ data: Data, operation: String) throws -> [ComicItem] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ComicFetchError.malformedResponse
        }
        if let errors = root["errors"] as? [[String: Any]], !errors.isEmpty {
            throw ComicFetchError.networkError(errors.first?["message"] as? String ?? "Komiic 查询失败。")
        }
        guard let data = root["data"] as? [String: Any] else {
            throw ComicFetchError.malformedResponse
        }
        let responseKey = (data[operation] != nil) ? operation : (operation == "recentUpdate" ? "recentUpdate" : "comicByCategories")
        guard let list = data[responseKey] as? [[String: Any]] else {
            throw ComicFetchError.malformedResponse
        }
        return list.compactMap { raw in
            guard let id = raw["id"] as? String, !id.isEmpty,
                  let title = raw["title"] as? String, !title.isEmpty else { return nil }
            let authors = (raw["authors"] as? [[String: Any]] ?? []).compactMap { $0["name"] as? String }
            let tags = (raw["categories"] as? [[String: Any]] ?? []).compactMap { $0["name"] as? String }
            return ComicItem(id: id, sourceId: "komiic", title: title, author: authors.first ?? "", coverUrl: raw["imageUrl"] as? String ?? "", tags: tags)
        }
    }

    static func decodeIkmmhPageBatch(_ data: Data, referer: String) throws -> (pages: [ComicPage], total: Int) {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let payload = root["data"] as? [String: Any],
              let rawPages = payload["pic"] as? [[String: Any]],
              let baseURL = URL(string: "https://ymcdnyfqdapp.ikmmh.com") else {
            throw ComicFetchError.malformedResponse
        }
        let headers = requestHeaders(sourceID: "ikmmh", referer: referer)
        let pages = rawPages.compactMap { row -> ComicPage? in
            guard let rawURL = row["pic"] as? String, !rawURL.isEmpty,
                  let url = URL(string: rawURL, relativeTo: baseURL)?.absoluteURL,
                  url.scheme == "https", url.host != nil else { return nil }
            return ComicPage(imageUrl: url.absoluteString, referer: referer, headers: headers)
        }
        return (pages, payload["total"] as? Int ?? 0)
    }

    static func decodePicacgComicFeed(_ data: Data) throws -> [ComicItem] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let data = root["data"] as? [String: Any],
              let comics = data["comics"] as? [String: Any],
              let docs = comics["docs"] as? [[String: Any]] else {
            throw ComicFetchError.malformedResponse
        }
        return docs.compactMap { raw in
            guard let id = raw["_id"] as? String ?? raw["id"] as? String, !id.isEmpty,
                  let title = raw["title"] as? String, !title.isEmpty else { return nil }
            let thumb = raw["thumb"] as? [String: Any] ?? [:]
            let server = thumb["fileServer"] as? String ?? ""
            let path = thumb["path"] as? String ?? ""
            let categories = raw["categories"] as? [String] ?? []
            let cover = server.isEmpty || path.isEmpty ? "" : "\(server)/static/\(path)"
            return ComicItem(id: id, sourceId: "picacg", title: title, author: raw["author"] as? String ?? "", coverUrl: cover, tags: categories)
        }
    }

    static func parseHTMLList(_ html: String, source: ComicSourceDefinition, finalURL: URL) -> [ComicItem] {
        let pathPattern: String
        switch source.id {
        case "baozi": pathPattern = #"/comic/([^/?#]+)"#
        case "dongman91": pathPattern = #"/comic/(\d+)"#
        case "8muses": pathPattern = #"/g/(\d+)"#
        case "gedecomix": pathPattern = #"/porncomic/([^/?#]+)"#
        case "ikmmh": pathPattern = #"/book/([^/?#]+)"#
        case "ehentai": pathPattern = #"/g/(\d+)/([a-f0-9]+)"#
        default: return []
        }
        guard let anchorRegex = try? NSRegularExpression(pattern: #"(?is)<a\b([^>]*)>(.*?)</a>"#),
              let idRegex = try? NSRegularExpression(pattern: pathPattern, options: [.caseInsensitive]) else { return [] }
        let ns = html as NSString
        let all = NSRange(location: 0, length: ns.length)
        var items: [ComicItem] = []
        var seen = Set<String>()
        for match in anchorRegex.matches(in: html, range: all) {
            let attributes = ns.substring(with: match.range(at: 1))
            guard let href = attribute("href", in: attributes),
                  let url = URL(string: decodeHTML(href), relativeTo: finalURL)?.absoluteURL,
                  url.host?.lowercased() == finalURL.host?.lowercased(),
                  let idMatch = idRegex.firstMatch(in: url.absoluteString, range: NSRange(url.absoluteString.startIndex..., in: url.absoluteString)),
                  let idRange = Range(idMatch.range(at: 1), in: url.absoluteString) else { continue }
            var id = String(url.absoluteString[idRange])
            if source.id == "ehentai", idMatch.numberOfRanges > 2,
               let tokenRange = Range(idMatch.range(at: 2), in: url.absoluteString) {
                id += "/" + url.absoluteString[tokenRange]
            }
            guard seen.insert(id).inserted else { continue }
            let body = ns.substring(with: match.range(at: 2))
            let context = cardContext(in: ns, around: match.range)
            let titleCandidates = [
                attribute("title", in: attributes),
                attribute("alt", in: body),
                textContent(matching: #"(?is)<(?:span|h[1-6])\b[^>]*class=['\"][^'\"]*(?:grid-card__title|card-title|title)[^'\"]*['\"][^>]*>(.*?)</(?:span|h[1-6])>"#, in: body),
                attribute("alt", in: context),
                stripHTML(body)
            ]
            let title = titleCandidates
                .compactMap { $0.map(decodeHTML) }
                .map { stripHTML($0).trimmingCharacters(in: .whitespacesAndNewlines) }
                .first { !$0.isEmpty } ?? ""
            guard !title.isEmpty else { continue }
            let rawImage = ["data-src", "data-original", "data-lazy-src", "src"]
                .compactMap { attribute($0, in: context) ?? attribute($0, in: body) }
                .first { !$0.hasPrefix("data:") && !$0.contains("default_cover") }
            let cover = rawImage.flatMap { URL(string: decodeHTML($0), relativeTo: finalURL)?.absoluteURL.absoluteString } ?? ""
            let chapter = source.id == "ehentai" ? [ComicChapter(id: id, title: "完整画廊")] : []
            items.append(ComicItem(id: id, sourceId: source.id, title: title, author: "", coverUrl: cover, chapters: chapter, latestChapterTitle: chapter.first?.title ?? ""))
        }
        return items
    }

    static func parseChapters(_ html: String, sourceID: String, finalURL: URL) -> [ComicChapter] {
        guard let regex = try? NSRegularExpression(pattern: #"(?is)<a\b([^>]*)>(.*?)</a>"#) else { return [] }
        let ns = html as NSString
        var chapters: [ComicChapter] = []
        var seen = Set<String>()
        for match in regex.matches(in: html, range: NSRange(location: 0, length: ns.length)) {
            let attributes = ns.substring(with: match.range(at: 1))
            guard let href = attribute("href", in: attributes), isChapterLink(href, sourceID: sourceID, finalURL: finalURL),
                  let url = URL(string: decodeHTML(href), relativeTo: finalURL)?.absoluteURL,
                  seen.insert(url.absoluteString).inserted else { continue }
            let title = stripHTML(ns.substring(with: match.range(at: 2)))
            guard !title.isEmpty else { continue }
            chapters.append(ComicChapter(id: url.absoluteString, title: title))
            if chapters.count == 500 { break }
        }
        return chapters
    }

    static func parseEHentaiImagePageLinks(_ html: String, baseURL: URL) -> [String] {
        guard let regex = try? NSRegularExpression(pattern: #"(?is)<a\b([^>]*)>(.*?)</a>"#) else { return [] }
        let ns = html as NSString
        var links: [(number: Int, url: String)] = []
        var seen = Set<String>()
        for match in regex.matches(in: html, range: NSRange(location: 0, length: ns.length)) {
            let attributes = ns.substring(with: match.range(at: 1))
            guard let rawHref = attribute("href", in: attributes),
                  let url = URL(string: decodeHTML(rawHref), relativeTo: baseURL)?.absoluteURL,
                  url.host?.lowercased() == "e-hentai.org" || url.host?.lowercased().hasSuffix(".e-hentai.org") == true,
                  url.path.contains("/s/"), seen.insert(url.absoluteString).inserted else { continue }
            let pageToken = url.path.split(separator: "/").last.map(String.init) ?? ""
            let pageNumber = pageToken.split(separator: "-").last.flatMap { Int($0) } ?? links.count + 1
            links.append((pageNumber, url.absoluteString))
        }
        return links.sorted { $0.number < $1.number }.map(\.url)
    }

    static func parseEHentaiPageCount(_ html: String) -> Int? {
        guard let regex = try? NSRegularExpression(pattern: #"(?i)Length:\s*(\d+)\s+pages?"#) else { return nil }
        let plainText = html
            .replacingOccurrences(of: #"(?is)<[^>]+>"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: "&nbsp;", with: " ")
        let ns = plainText as NSString
        guard let match = regex.firstMatch(in: plainText, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return Int(ns.substring(with: match.range(at: 1)))
    }

    static func parseEightMusesPageCount(_ html: String) -> Int? {
        guard let regex = try? NSRegularExpression(pattern: #"(?i)\"numberOfPages\"\s*:\s*(\d+)|(\d+)\s+pages"#) else { return nil }
        let ns = html as NSString
        guard let match = regex.firstMatch(in: html, range: NSRange(location: 0, length: ns.length)) else { return nil }
        let range = match.range(at: 1).location == NSNotFound ? match.range(at: 2) : match.range(at: 1)
        return Int(ns.substring(with: range))
    }

    static func eightMusesPageURLs(comicID: String, count: Int) -> [URL] {
        guard !comicID.isEmpty else { return [] }
        let pageCount = min(max(count, 0), 1_000)
        guard pageCount > 0 else { return [] }
        return (1...pageCount).compactMap { page in
            URL(string: "https://8muses.io/api/legacy-media/\(comicID)/\(page)")
        }
    }

    static func parseEHentaiOriginalImageURL(_ html: String, pageURL: URL) -> URL? {
        guard let imageRegex = try? NSRegularExpression(pattern: #"(?is)<img\b([^>]*\bid=['\"]img['\"][^>]*)>"#) else { return nil }
        let ns = html as NSString
        guard let match = imageRegex.firstMatch(in: html, range: NSRange(location: 0, length: ns.length)) else { return nil }
        let attributes = ns.substring(with: match.range(at: 1))
        guard let source = attribute("src", in: attributes),
              let url = URL(string: decodeHTML(source), relativeTo: pageURL)?.absoluteURL,
              url.scheme?.lowercased() == "https",
              url.host != nil else { return nil }
        return url
    }

    static func parseJMScrambleID(_ html: String) -> Int? {
        guard let regex = try? NSRegularExpression(pattern: #"var\s+scramble_id\s*=\s*(\d+)"#) else { return nil }
        let ns = html as NSString
        guard let match = regex.firstMatch(in: html, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return Int(ns.substring(with: match.range(at: 1)))
    }

    /// 禁漫天堂动态反混淆分段计算公式（与 Android 端 JmImageScrambler 保持 100% 像素级一致）
    static func jmSegmentCount(scrambleID: Int, photoID: String, imageName: String) -> Int {
        guard let id = Int(photoID), id >= scrambleID else { return 0 }
        if id < 268_850 { return 10 }
        let modulus = id < 421_926 ? 10 : 8
        let digest = ComicProtocolSecrets.md5Hex("\(id)\(imageName)")
        guard let lastChar = digest.last, let asciiVal = lastChar.asciiValue else { return 0 }
        return (Int(asciiVal) % modulus) * 2 + 2
    }

    static func parseImagePages(_ html: String, baseURL: URL, referer: String, sourceID: String) -> [ComicPage] {
        let pageHTML = sourceID == "gedecomix"
            ? firstElementContents(tag: "div", classToken: "reading-content", in: html) ?? ""
            : html
        guard !pageHTML.isEmpty else { return [] }
        guard let regex = try? NSRegularExpression(pattern: #"(?is)<img\b([^>]*)>"#) else { return [] }
        let ns = pageHTML as NSString
        var pages: [ComicPage] = []
        var seen = Set<String>()
        for match in regex.matches(in: pageHTML, range: NSRange(location: 0, length: ns.length)) {
            let attributes = ns.substring(with: match.range(at: 1))
            let alt = attribute("alt", in: attributes) ?? ""
            let candidates = ["data-src", "data-original", "data-lazy-src", "src"].compactMap { attribute($0, in: attributes) }
            guard let raw = candidates.first,
                  !raw.hasPrefix("data:"), let url = URL(string: decodeHTML(raw), relativeTo: baseURL)?.absoluteURL,
                  seen.insert(url.absoluteString).inserted else { continue }
            if sourceID == "dongman91" {
                guard alt.range(of: #"^第\d+页$"#, options: .regularExpression) != nil,
                      !raw.lowercased().contains("cover-placeholder"),
                      !raw.lowercased().contains("/static/web/images/reader/") else { continue }
            }
            let value = url.absoluteString.lowercased()
            guard ["jpg", "jpeg", "png", "webp", "avif"].contains(where: value.contains) else { continue }
            pages.append(ComicPage(imageUrl: url.absoluteString, referer: referer, headers: requestHeaders(sourceID: sourceID, referer: referer)))
            if pages.count == 500 { break }
        }
        return pages
    }

    private static func firstElementContents(tag: String, classToken: String, in html: String) -> String? {
        let openingPattern = "(?is)<\(tag)\\b(?=[^>]*\\bclass=['\"][^'\"]*\\b\(NSRegularExpression.escapedPattern(for: classToken))\\b[^'\"]*['\"])[^>]*>"
        guard let openingRegex = try? NSRegularExpression(pattern: openingPattern),
              let opening = openingRegex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
              let openingRange = Range(opening.range, in: html),
              let tokenRegex = try? NSRegularExpression(pattern: "(?is)</?\(tag)\\b[^>]*>") else { return nil }
        let contentStart = openingRange.upperBound
        var depth = 1
        for token in tokenRegex.matches(in: html, range: NSRange(contentStart..., in: html)) {
            guard let tokenRange = Range(token.range, in: html) else { continue }
            let value = html[tokenRange]
            if value.hasPrefix("</") {
                depth -= 1
                if depth == 0 { return String(html[contentStart..<tokenRange.lowerBound]) }
            } else if !value.hasSuffix("/>") {
                depth += 1
            }
        }
        return nil
    }

    static func requestHeaders(sourceID: String, referer: String) -> [String: String] {
        var headers = ["User-Agent": userAgent, "Referer": referer]
        if sourceID == "ehentai" {
            headers["Cookie"] = "nw=1"
            headers["Accept-Language"] = "zh-CN,zh;q=0.9,en;q=0.8"
        }
        if sourceID == "copy" {
            headers.merge(["User-Agent": "COPY/2.2.0", "source": "copyApp", "webp": "1", "region": "1", "version": "2.2.0", "authorization": "Token", "platform": "3"]) { _, new in new }
        }
        return headers
    }

    private static func query(_ value: String) -> String { queryValue(value) }

    private static func queryValue(_ value: String) -> String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&+=#?")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private static func attribute(_ name: String, in html: String) -> String? {
        let pattern = "(?i)\\b\(NSRegularExpression.escapedPattern(for: name))\\s*=\\s*(['\\\"])(.*?)\\1"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let ns = html as NSString
        guard let match = regex.firstMatch(in: html, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return ns.substring(with: match.range(at: 2))
    }

    private static func textContent(matching pattern: String, in html: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let ns = html as NSString
        guard let match = regex.firstMatch(in: html, range: NSRange(location: 0, length: ns.length)),
              let range = Range(match.range(at: 1), in: html) else { return nil }
        return String(html[range])
    }

    private static func cardContext(in html: NSString, around range: NSRange) -> String {
        let before = NSRange(location: 0, length: range.location)
        let previous = html.range(of: "<div", options: .backwards, range: before)
        let start = previous.location == NSNotFound ? range.location : previous.location
        let after = NSRange(location: NSMaxRange(range), length: html.length - NSMaxRange(range))
        let closing = html.range(of: "</div>", range: after)
        let end = closing.location == NSNotFound ? NSMaxRange(range) : NSMaxRange(closing)
        guard end >= start, end - start < 8_000 else { return html.substring(with: range) }
        return html.substring(with: NSRange(location: start, length: end - start))
    }

    private static func isChapterLink(_ href: String, sourceID: String, finalURL: URL) -> Bool {
        let value = href.lowercased()
        switch sourceID {
        case "baozi": return value.contains("chapter") || value.contains("page_direct")
        case "dongman91": return value.contains("/reader/")
        case "gedecomix":
            guard let chapterURL = URL(string: decodeHTML(href), relativeTo: finalURL)?.absoluteURL,
                  chapterURL.host?.lowercased() == finalURL.host?.lowercased(),
                  chapterURL.path.hasPrefix(finalURL.path) else { return false }
            return chapterURL.path.split(separator: "/").count > finalURL.path.split(separator: "/").count
        case "ehentai": return value.contains("/s/")
        default: return value.contains("chapter") || value.contains("reader")
        }
    }

    private static func stripHTML(_ value: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: #"(?is)<script\b[^>]*>.*?</script>|<style\b[^>]*>.*?</style>|<[^>]+>"#) else { return value }
        let ns = value as NSString
        return decodeHTML(regex.stringByReplacingMatches(in: value, range: NSRange(location: 0, length: ns.length), withTemplate: " "))
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func decodeHTML(_ value: String) -> String {
        value.replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
    }
}
