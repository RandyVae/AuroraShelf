import Foundation

public enum ComicFetchError: LocalizedError {
    case networkError(String)
    case notSupported(String)
    
    public var errorDescription: String? {
        switch self {
        case .networkError(let msg), .notSupported(let msg):
            return msg
        }
    }
}

public final class ComicRepository: ObservableObject, @unchecked Sendable {
    public static let shared = ComicRepository()
    
    @Published public var currentSource: ComicSourceDefinition = ComicSourceCatalog.baozi
    @Published public var comics: [ComicItem] = []
    @Published public var selectedCategory: String = "全部"
    @Published public var isLoading: Bool = false
    @Published public var errorMessage: String?
    
    private let session: URLSession
    private var loadTask: Task<Void, Never>?
    
    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        ]
        self.session = URLSession(configuration: config)
        self.loadComics(for: ComicSourceCatalog.baozi)
    }
    
    public func switchSource(to source: ComicSourceDefinition) {
        self.currentSource = source
        self.selectedCategory = source.categories.first ?? "全部"
        self.loadComics(for: source)
    }
    
    public func loadComics(for source: ComicSourceDefinition) {
        loadTask?.cancel()
        self.isLoading = true
        self.errorMessage = nil
        loadTask = Task { [weak self] in
            guard let self else { return }
            let result = await fetchRealComics(source: source)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard self.currentSource.id == source.id else { return }
                switch result {
                case .success(let loaded):
                    self.comics = loaded
                    self.errorMessage = loaded.isEmpty ? "此来源暂时没有可显示的内容" : nil
                case .failure(let error):
                    self.comics = []
                    self.errorMessage = error.localizedDescription
                }
                self.isLoading = false
            }
        }
    }
    
    // MARK: - 真实包子漫画与多源网络抓取
    
    private func fetchRealComics(source: ComicSourceDefinition) async -> Result<[ComicItem], ComicFetchError> {
        if source.id == ComicSourceCatalog.baozi.id {
            let items = await fetchBaoziList()
            return items.isEmpty ? .failure(.networkError("包子漫画暂时无法加载，请稍后重试")) : .success(items)
        }
        return .failure(.notSupported("\(source.name) 的 iOS 解析器正在适配中"))
    }
    
    private func fetchBaoziList(sourceOverride: ComicSourceDefinition? = nil) async -> [ComicItem] {
        let urlStr = "https://cn.webmota.com/api/bzmhq/amp_comic_list?type=all&region=all&state=all&filter=%2a&page=1&limit=24&language=cn"
        guard let url = URL(string: urlStr) else { return [] }
        
        do {
            var request = URLRequest(url: url)
            request.setValue("https://cn.webmota.com", forHTTPHeaderField: "Referer")
            let (data, response) = try await session.data(for: request)
            if let httpResp = response as? HTTPURLResponse, httpResp.statusCode == 200 {
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let items = json["items"] as? [[String: Any]] {
                    
                    var results: [ComicItem] = []
                    for item in items {
                        let comicId = item["comic_id"] as? String ?? ""
                        let name = item["name"] as? String ?? ""
                        let author = item["author"] as? String ?? "佚名"
                        let topicImg = item["topic_img"] as? String ?? ""
                        let typeNames = item["type_names"] as? [String] ?? ["热血"]
                        
                        let coverUrl = topicImg.isEmpty ? "" : "https://s1.bzcdn.net/cover/\(topicImg)"
                        
                        results.append(ComicItem(
                            id: comicId,
                            sourceId: sourceOverride?.id ?? "baozi",
                            title: name,
                            author: author,
                            coverUrl: coverUrl,
                            category: typeNames.first ?? "精选",
                            tags: typeNames,
                            chapters: [],
                            latestChapterTitle: "章节待加载"
                        ))
                    }
                    if !results.isEmpty { return results }
                }
            }
        } catch {
            print("Fetch real comics error: \(error)")
        }
        
        return []
    }
    
    // 真实抓取章节正文全部图片
    public func fetchChapterPages(comicId: String, chapterId: String) async -> [String] {
        let chapterUrlStr = "https://cn.webmota.com/comic/chapter/\(comicId)/\(chapterId).html"
        guard let url = URL(string: chapterUrlStr) else { return [] }
        
        do {
            var request = URLRequest(url: url)
            request.setValue("https://cn.webmota.com", forHTTPHeaderField: "Referer")
            let (data, response) = try await session.data(for: request)
            if let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode),
               let html = String(data: data, encoding: .utf8) {
                
                // 正则提取所有 amp-img 或 noscript 中的真实图片链接
                let pattern = #"https://s\d+\.bzcdn\.net/scomic/[^"'\s<>]+\.jpg"#
                let regex = try NSRegularExpression(pattern: pattern, options: [])
                let nsString = html as NSString
                let matches = regex.matches(in: html, options: [], range: NSRange(location: 0, length: nsString.length))
                
                var imageUrls: [String] = []
                for match in matches {
                    let matchedUrl = nsString.substring(with: match.range)
                    if !imageUrls.contains(matchedUrl) {
                        imageUrls.append(matchedUrl)
                    }
                }
                
                if !imageUrls.isEmpty {
                    return imageUrls
                }
            }
        } catch {
            print("Fetch chapter pages error: \(error)")
        }
        
        return []
    }
}
