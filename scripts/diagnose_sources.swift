import Foundation

class PermissiveDelegate: NSObject, URLSessionDelegate {
    func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
           let serverTrust = challenge.protectionSpace.serverTrust {
            completionHandler(.useCredential, URLCredential(trust: serverTrust))
        } else {
            completionHandler(.performDefaultHandling, nil)
        }
    }
}

let config = URLSessionConfiguration.default
config.timeoutIntervalForRequest = 10
config.timeoutIntervalForResource = 15
let session = URLSession(configuration: config, delegate: PermissiveDelegate(), delegateQueue: nil)

struct SourceProbe {
    let type: String
    let name: String
    let id: String
    let url: String
    var method: String = "GET"
    var body: Data? = nil
    var headers: [String: String] = [:]
}

let probes: [SourceProbe] = [
    // 视频源
    SourceProbe(type: "视频", name: "华视影院", id: "huavod", url: "https://huavod.com/"),
    SourceProbe(type: "视频", name: "黄果短剧", id: "huangguo", url: "https://huangguoai.com/api/edge/request", method: "POST", body: "{\"method\":\"GET\",\"path\":\"/api/videos?page=1&size=20\",\"body\":\"\",\"content_type\":\"\"}".data(using: .utf8), headers: ["Content-Type": "application/json", "Origin": "https://huangguoai.com", "Referer": "https://huangguoai.com/"]),
    SourceProbe(type: "视频", name: "91视频", id: "91porn", url: "https://h1014.sol148.com/index.php", headers: ["Referer": "https://h1014.sol148.com/"]),
    SourceProbe(type: "视频", name: "91动漫", id: "91dongman", url: "https://91dongman.net/"),
    SourceProbe(type: "视频", name: "Beeg", id: "beeg", url: "https://beeg.com/api/v6/index/main/0/pc"),
    SourceProbe(type: "视频", name: "XVideos", id: "xvideos", url: "https://www.xvideos.com/new/1/"),
    SourceProbe(type: "视频", name: "MissAV", id: "missav", url: "https://missav.media/new"),
    SourceProbe(type: "视频", name: "KissJAV", id: "kissjav", url: "https://kissjav.li/most-popular/?sort_by=rating"),
    SourceProbe(type: "视频", name: "85po", id: "85po", url: "https://www.85po.com/ja/latest-updates/"),
    SourceProbe(type: "视频", name: "Pornhub", id: "pornhub", url: "https://jp.pornhub.com/video?o=cm"),
    SourceProbe(type: "视频", name: "JAV-NONI", id: "javnoni", url: "https://javnoni.com/"),
    SourceProbe(type: "视频", name: "JAVSB", id: "javsb", url: "https://jav.sb/ja/"),
    SourceProbe(type: "视频", name: "TOKYO Motion", id: "tokyomotion", url: "https://www.tokyomotion.net/videos"),
    SourceProbe(type: "视频", name: "SpankBang", id: "spankbang", url: "https://www.spankbang.com/", headers: ["Cookie": "age_pass=1; pg_interstitial_v5=1; pg_pop_v5=1; player_quality=1080; preroll_skip=1; backend_version=main; videos_layout=four-col", "Accept-Language": "en-US,en;q=0.9"]),
    SourceProbe(type: "视频", name: "xHamster", id: "xhamster", url: "https://xhamster.com/best"),
    SourceProbe(type: "视频", name: "EPORNER", id: "eporner", url: "https://www.eporner.com/api/v2/video/search/?query=featured&per_page=24&page=1"),
    SourceProbe(type: "视频", name: "MRDOUGA", id: "mrdouga", url: "https://mrdouga.com/latest-updates/"),
    SourceProbe(type: "视频", name: "OHentai", id: "ohentai", url: "https://ohentai.org/"),
    SourceProbe(type: "视频", name: "XNXX", id: "xnxx", url: "https://www.xnxx.com/best/"),

    // 漫画源
    SourceProbe(type: "漫画", name: "包子漫画", id: "baozi", url: "https://cn.webmota.com/api/bzmhq/amp_comic_list?type=all&region=all&state=all&filter=%2A&page=1&limit=36&language=cn"),
    SourceProbe(type: "漫画", name: "禁漫天堂", id: "jm", url: "https://www.cdnhjk.net/categories/filter?o=mr&c=0&page=1"),
    SourceProbe(type: "漫画", name: "Komiic", id: "komiic", url: "https://komiic.com/api/query", method: "POST", body: "{\"query\":\"query recentUpdate($pagination: Pagination!) { recentUpdate(pagination: $pagination) { id title cover } }\",\"variables\":{\"pagination\":{\"limit\":20,\"offset\":0}}}".data(using: .utf8), headers: ["Content-Type": "application/json"]),
    SourceProbe(type: "漫画", name: "91动漫漫画", id: "dongman91_comic", url: "https://91dongman.net/manga/tongrenzhi/"),
    SourceProbe(type: "漫画", name: "8Muses", id: "8muses", url: "https://8muses.io/browse"),
    SourceProbe(type: "漫画", name: "GEDE Comix", id: "gedecomix", url: "https://gedecomix.com/porn-comics/"),
    SourceProbe(type: "漫画", name: "拷贝漫画", id: "copy", url: "https://api.2024manga.com/api/v3/comics?limit=24&offset=0&ordering=-datetime_updated&theme=&top=&platform=3"),
    SourceProbe(type: "漫画", name: "爱看漫画", id: "ikmmh", url: "https://ymcdnyfqdapp.ikmmh.com/booklists/9/%E5%85%A8%E9%83%A8/3/1.html"),
    SourceProbe(type: "漫画", name: "E-Hentai", id: "ehentai", url: "https://e-hentai.org/"),

    // 直播源
    SourceProbe(type: "直播", name: "Chaturbate", id: "chaturbate", url: "https://chaturbate.com/api/public/affiliates/onlinerooms/?wm=9RhSM&client_ip=request_ip&gender=f&limit=5"),
    SourceProbe(type: "直播", name: "Stripchat", id: "stripchat", url: "https://stripchat.com/api/front/v2/models?limit=5&primaryTag=girls"),

    // 论坛源
    SourceProbe(type: "论坛", name: "91自拍论坛", id: "91forum", url: "https://t924.sol140.com/forumdisplay.php?fid=4"),
    SourceProbe(type: "论坛", name: "草榴社区", id: "t66y", url: "https://t66y.com/thread0806.php?fid=7")
]

let group = DispatchGroup()
var results: [(probe: SourceProbe, status: Int, bytes: Int, duration: Double, error: String?)] = []
let lock = NSLock()

print("\n================================================================================")
print("🔍 极光视界 (Netgrey) 全源真实连通性与可用性诊断测试")
print("================================================================================\n")

for p in probes {
    group.enter()
    guard let targetURL = URL(string: p.url) else {
        lock.lock()
        results.append((p, 0, 0, 0, "无效 URL"))
        lock.unlock()
        group.leave()
        continue
    }

    var req = URLRequest(url: targetURL)
    req.httpMethod = p.method
    req.httpBody = p.body
    req.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
    req.setValue("*/*", forHTTPHeaderField: "Accept")
    p.headers.forEach { req.setValue($0.value, forHTTPHeaderField: $0.key) }

    let start = Date()
    let task = session.dataTask(with: req) { data, response, error in
        let duration = Date().timeIntervalSince(start)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let bytes = data?.count ?? 0
        let errStr = error?.localizedDescription

        lock.lock()
        results.append((p, status, bytes, duration, errStr))
        lock.unlock()
        group.leave()
    }
    task.resume()
}

group.wait()

// 输出结果
var passCount = 0
var failCount = 0

for item in results.sorted(by: { $0.probe.type < $1.probe.type || ($0.probe.type == $1.probe.type && $0.probe.name < $1.probe.name) }) {
    let p = item.probe
    let isOk = item.status >= 200 && item.status < 400
    let mark = isOk ? "✅ PASS" : "❌ FAIL"
    let statusDesc = item.status > 0 ? "HTTP \(item.status)" : "连接阻断"
    let durMs = Int(item.duration * 1000)
    let err = item.error != nil ? " [错误: \(item.error!)]" : ""
    print(String(format: "%@ [%@] %-14@ -> %-10@ | %5d ms | %8d bytes%@", mark, p.type, p.name, statusDesc, durMs, item.bytes, err))
    if isOk { passCount += 1 } else { failCount += 1 }
}

print("\n--------------------------------------------------------------------------------")
print("📊 统计: 全部 \(probes.count) 个探测项 | 连通成功: \(passCount) | 异常阻断: \(failCount)")
print("--------------------------------------------------------------------------------\n")
