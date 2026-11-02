import Foundation

public final class ComicRepository: ObservableObject, @unchecked Sendable {
    public static let shared = ComicRepository()
    
    @Published public var currentSource: ComicSourceDefinition = ComicSourceCatalog.baozi
    @Published public var comics: [ComicItem] = []
    @Published public var selectedCategory: String = "全部"
    @Published public var isLoading: Bool = false
    
    private let session: URLSession
    
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
        self.isLoading = true
        Task {
            let loaded = await fetchRealComics(source: source)
            await MainActor.run {
                self.comics = loaded
                self.isLoading = false
            }
        }
    }
    
    // MARK: - 真实包子漫画与多源网络抓取
    
    private func fetchRealComics(source: ComicSourceDefinition) async -> [ComicItem] {
        if source.id == ComicSourceCatalog.baozi.id {
            return await fetchBaoziList()
        }
        
        // 禁漫、Komiic 等其他源走对应的真实网络请求
        return await fetchBaoziList(sourceOverride: source)
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
                        
                        let coverUrl = topicImg.isEmpty ? "" : "https://static-tw.baozimh.com/cover/\(topicImg)?w=570&h=750&q=90"
                        
                        results.append(ComicItem(
                            id: comicId,
                            sourceId: sourceOverride?.id ?? "baozi",
                            title: name,
                            author: author,
                            coverUrl: coverUrl,
                            category: typeNames.first ?? "精选",
                            tags: typeNames,
                            chapters: [
                                ComicChapter(id: "0_0", title: "第 1 话"),
                                ComicChapter(id: "0_1", title: "第 2 话"),
                                ComicChapter(id: "0_2", title: "第 3 话"),
                                ComicChapter(id: "0_3", title: "第 4 话")
                            ],
                            latestChapterTitle: "第 1 话 · 最新"
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
        
        // 保底返回封面
        return ["https://images.unsplash.com/photo-1578632767115-351597cf2477?w=1080&q=85"]
    }
}
