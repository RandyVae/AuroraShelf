import Foundation

/// 豆瓣电影海报智能解析与备用封面解析引擎（用于自动补齐三方源缺失或死链封面）
public actor DoubanPosterResolver {
    public static let shared = DoubanPosterResolver()

    private let memoryCache = NSCache<NSString, NSString>()
    private let fileManager = FileManager.default
    private let cacheFile: URL
    private let session: URLSession
    private var inFlightQueries: [String: Task<String?, Never>] = [:]

    /// 华视源已确认死链（dbzy.tv / dbzy5.com）的高清官方豆瓣海报映射表（0延迟秒级命中）
    private let staticCoverMap: [String: String] = [
        "眩晕": "https://img9.doubanio.com/view/photo/s_ratio_poster/public/p2932533736.jpg",
        "给阿嬷的情书": "https://img3.doubanio.com/view/photo/s_ratio_poster/public/p2931851430.jpg",
        "地球超新鲜第二季": "https://img3.doubanio.com/view/photo/s_ratio_poster/public/p2933553320.jpg",
        "地球超新鲜 第二季": "https://img3.doubanio.com/view/photo/s_ratio_poster/public/p2933553320.jpg",
        "不能笑的孤独粉丝见面会": "https://asset.doubanio.com/cuphead/movie-static/pics/tv_default_small.png",
        "寻梦环游记": "https://img1.doubanio.com/view/photo/s_ratio_poster/public/p2503997609.jpg",
        "疯狂动物城2": "https://img9.doubanio.com/view/photo/s_ratio_poster/public/p2928373804.jpg",
        "霜花店": "https://img9.doubanio.com/view/photo/s_ratio_poster/public/p453856276.jpg"
    ]

    private init() {
        let paths = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)
        let dir = paths[0].appendingPathComponent("AuroraDoubanPosters", isDirectory: true)
        try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        self.cacheFile = dir.appendingPathComponent("poster_cache.json")

        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 4
        config.timeoutIntervalForResource = 6
        self.session = URLSession.permissive(configuration: config)

        // 预热内存缓存
        for (k, v) in staticCoverMap {
            memoryCache.setObject(v as NSString, forKey: k as NSString)
        }

        // 加载磁盘缓存
        if let data = try? Data(contentsOf: cacheFile),
           let dict = try? JSONSerialization.jsonObject(with: data) as? [String: String] {
            for (k, v) in dict {
                memoryCache.setObject(v as NSString, forKey: k as NSString)
            }
        }
    }

    /// 同步高速匹配（优先内存与内置静态映射表）
    public nonisolated func quickLookup(for title: String) -> String? {
        let clean = sanitizeTitle(title)
        return staticCoverMap[clean] ?? staticCoverMap[title]
    }

    /// 异步解析海报（支持内存、磁盘缓存与动态网络检索）
    public func resolvePosterURL(for title: String) async -> String? {
        let clean = Self.sanitizeTitle(title)
        guard !clean.isEmpty else { return nil }

        // 1. 静态映射表
        if let direct = staticCoverMap[clean] ?? staticCoverMap[title] {
            return direct
        }

        // 2. 内存缓存
        if let cached = memoryCache.object(forKey: clean as NSString) as String? {
            return cached
        }

        // 3. 去重并发任务
        if let ongoing = inFlightQueries[clean] {
            return await ongoing.value
        }

        let session = self.session
        let cacheFile = self.cacheFile

        let task = Task { [session, cacheFile] () -> String? in
            guard let encoded = clean.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                  let url = URL(string: "https://movie.douban.com/j/subject_suggest?q=\(encoded)") else {
                return nil
            }

            var request = URLRequest(url: url)
            request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
            request.setValue("https://movie.douban.com/", forHTTPHeaderField: "Referer")
            request.timeoutInterval = 4

            do {
                let (data, response) = try await session.data(for: request)
                guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
                    return nil
                }

                guard let list = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]],
                      !list.isEmpty else {
                    return nil
                }

                // 优先寻找标题完全匹配或首个有 img 的项目
                var bestImg: String? = nil
                for item in list {
                    if let img = item["img"] as? String, !img.isEmpty {
                        bestImg = img
                        if let itemTitle = item["title"] as? String, itemTitle == clean {
                            break
                        }
                    }
                }

                if let found = bestImg {
                    Self.persistDiskCache(cacheFile: cacheFile, key: clean, value: found)
                    return found
                }
            } catch {
                return nil
            }
            return nil
        }

        inFlightQueries[clean] = task
        defer { inFlightQueries[clean] = nil }

        if let result = await task.value {
            memoryCache.setObject(result as NSString, forKey: clean as NSString)
            return result
        }
        return nil
    }

    private static func sanitizeTitle(_ raw: String) -> String {
        var clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        // 去除常见的清晰度与季数后缀以提高命中率
        let noiseList = ["在线观看", "高清版", "中字", "国语", "粤语", "无删减", "HD", "4K", "1080P", "抢先版"]
        for noise in noiseList {
            clean = clean.replacingOccurrences(of: noise, with: "")
        }
        return clean.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private nonisolated func sanitizeTitle(_ raw: String) -> String {
        Self.sanitizeTitle(raw)
    }

    private static func persistDiskCache(cacheFile: URL, key: String, value: String) {
        var existing: [String: String] = [:]
        if let data = try? Data(contentsOf: cacheFile),
           let dict = try? JSONSerialization.jsonObject(with: data) as? [String: String] {
            existing = dict
        }
        existing[key] = value
        if let data = try? JSONSerialization.data(withJSONObject: existing, options: .fragmentsAllowed) {
            try? data.write(to: cacheFile)
        }
    }
}
