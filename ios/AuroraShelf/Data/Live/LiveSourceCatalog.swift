import Foundation

public struct LiveStreamItem: Identifiable, Hashable, Sendable {
    public let id: String
    public let username: String
    public let title: String
    public let roomUrl: String
    public let avatarUrl: String
    public let snapshotUrl: String
    public let viewCount: Int
    public let tags: [String]
    public let gender: String
    public let isPrivateShow: Bool
    
    public init(
        id: String,
        username: String,
        title: String,
        roomUrl: String,
        avatarUrl: String,
        snapshotUrl: String,
        viewCount: Int,
        tags: [String] = ["LIVE", "1080P"],
        gender: String = "f",
        isPrivateShow: Bool = false
    ) {
        self.id = id
        self.username = username
        self.title = title
        self.roomUrl = roomUrl
        self.avatarUrl = avatarUrl
        self.snapshotUrl = snapshotUrl
        self.viewCount = viewCount
        self.tags = tags
        self.gender = gender
        self.isPrivateShow = isPrivateShow
    }
}

public final class LiveRepository: ObservableObject, @unchecked Sendable {
    public static let shared = LiveRepository()
    
    @Published public var liveStreams: [LiveStreamItem] = []
    @Published public var selectedGender: String = "全部"
    @Published public var isLoading: Bool = false
    
    public let categories = ["全部", "女性主播", "情侣互动", "男性主播", "跨性别"]
    private let session: URLSession
    
    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Accept": "application/xml, text/xml, */*"
        ]
        self.session = URLSession(configuration: config)
        self.loadStreams()
    }
    
    public func loadStreams() {
        self.isLoading = true
        Task {
            let loaded = await fetchRealHornyStudentsFeed()
            await MainActor.run {
                self.liveStreams = loaded
                self.isLoading = false
            }
        }
    }
    
    // MARK: - 真实请求 HornyStudents RSS 2.0 在线主播流
    
    private func fetchRealHornyStudentsFeed() async -> [LiveStreamItem] {
        guard let url = URL(string: "https://www.hornystudents.com/feed/latest/") else { return [] }
        
        do {
            let (data, response) = try await session.data(from: url)
            if let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode),
               let xmlString = String(data: data, encoding: .utf8) {
                
                return parseRssXml(xmlString)
            }
        } catch {
            print("Fetch real HornyStudents error: \(error)")
        }
        
        return []
    }
    
    private func parseRssXml(_ xml: String) -> [LiveStreamItem] {
        var items: [LiveStreamItem] = []
        
        // 正则逐个匹配 <item> 节点
        let itemPattern = #"<item>([\s\S]*?)</item>"#
        guard let itemRegex = try? NSRegularExpression(pattern: itemPattern) else { return [] }
        let nsXml = xml as NSString
        let matches = itemRegex.matches(in: xml, options: [], range: NSRange(location: 0, length: nsXml.length))
        
        for m in matches {
            let block = nsXml.substring(with: m.range)
            
            let username = extractTag(from: block, tag: "cb:username")
                ?? extractTag(from: block, tag: "title")
                ?? ""
            guard !username.isEmpty else { continue }
            
            let viewersStr = extractTag(from: block, tag: "cb:number_of_users_watching") ?? "0"
            let viewers = Int(viewersStr) ?? 0
            
            let gender = extractTag(from: block, tag: "cb:gender") ?? "f"
            let location = extractTag(from: block, tag: "cb:location") ?? "Online"
            
            let currentShow = extractTag(from: block, tag: "cb:current_show") ?? "public"
            let isPrivate = currentShow != "public"
            
            let imgUrl = extractTag(from: block, tag: "cb:image_url")
                ?? "https://thumb.live.mmcdn.com/ri/\(username).jpg"
            
            items.append(LiveStreamItem(
                id: "hs-\(username)",
                username: username,
                title: "\(location) · 正在直播",
                roomUrl: "https://chaturbate.com/\(username)/",
                avatarUrl: imgUrl,
                snapshotUrl: imgUrl,
                viewCount: viewers,
                tags: ["LIVE", location],
                gender: gender,
                isPrivateShow: isPrivate
            ))
            
            if items.count >= 30 { break }
        }
        
        return items
    }
    
    private func extractTag(from text: String, tag: String) -> String? {
        let pattern = "<\(tag)>([^<]*?)</\(tag)>"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let ns = text as NSString
        if let match = regex.firstMatch(in: text, options: [], range: NSRange(location: 0, length: ns.length)) {
            return ns.substring(with: match.range(at: 1)).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return nil
    }
}
