import Foundation
struct Probe {
    public static func canonicalKeys(for url: String) -> [String] {
        var keys = [url]
        guard !url.isEmpty else { return keys }
        if url.contains("huavod.com") {
            if let match = url.range(of: #"/voddetail/(\d+)"#, options: .regularExpression) {
                let vodId = String(url[match]).replacingOccurrences(of: "/voddetail/", with: "")
                let playUrl = "https://huavod.com/vodplay/\(vodId)-1-1.html"
                if !keys.contains(playUrl) { keys.append(playUrl) }
            } else if let match = url.range(of: #"/vodplay/(\d+)-1-1"#, options: .regularExpression) {
                let vodId = String(url[match]).replacingOccurrences(of: "/vodplay/", with: "").components(separatedBy: "-").first ?? ""
                if !vodId.isEmpty {
                    let detailUrl = "https://huavod.com/voddetail/\(vodId).html"
                    if !keys.contains(detailUrl) { keys.append(detailUrl) }
                }
            }
        } else if url.contains("huangguoai.com") {
            if let match = url.range(of: #"/(?:detail|video|play)/(\d+)"#, options: .regularExpression) {
                let raw = String(url[match])
                let id = raw.components(separatedBy: "/").filter({ !$0.isEmpty }).last ?? ""
                if !id.isEmpty {
                    let detailKey = "https://huangguoai.com/detail/\(id)/"
                    let videoKey = "https://huangguoai.com/video/\(id)/"
                    if !keys.contains(detailKey) { keys.append(detailKey) }
                    if !keys.contains(videoKey) { keys.append(videoKey) }
                }
            }
        }
        return keys
    }

}
let first = Probe.canonicalKeys(for: "https://huangguoai.com/video/123/")
let second = Probe.canonicalKeys(for: "https://huangguoai.com/video/123/ep-2/")
print("EP1", first)
print("EP2", second)
print("COLLISION", Set(first).intersection(Set(second)).sorted())
