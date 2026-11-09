import XCTest
@testable import AuroraShelf

final class LiveVideoSourceAuditTests: XCTestCase {
    private struct Probe: Sendable {
        let sourceID: String
        let itemCount: Int
        let coverDecoded: Bool
        let error: String?
    }

    func testEveryConfiguredVideoSourceLoadsAndResolvesLiveItemsWhenOptedIn() async throws {
        guard ProcessInfo.processInfo.environment["RUN_IOS_LIVE_SOURCE_AUDIT"] == "1" else {
            throw XCTSkip("Set RUN_IOS_LIVE_SOURCE_AUDIT=1 to probe live video sites.")
        }

        let requestedIDs = Set(
            ProcessInfo.processInfo.environment["IOS_LIVE_SOURCE_IDS"]?
                .split(separator: ",")
                .map(String.init) ?? []
        )
        let sources = VideoSourceCatalog.supportedSources.filter {
            requestedIDs.isEmpty || requestedIDs.contains($0.id)
        }
        var probes: [Probe] = []
        for source in sources {
            var itemCount = 0
            var coverDecoded = false
            do {
                let feed = try await VideoRepository.shared.fetchVideosForSource(source)
                itemCount = feed.count
                guard !feed.isEmpty else {
                    probes.append(Probe(sourceID: source.id, itemCount: itemCount, coverDecoded: false, error: "Feed returned no parsed items"))
                    continue
                }
                for candidate in feed.prefix(3) where !candidate.coverUrl.isEmpty {
                    if await CoverImageLoader.shared.loadImage(
                        from: candidate.coverUrl,
                        baseURL: URL(string: source.baseUrl)
                    ) != nil {
                        coverDecoded = true
                        break
                    }
                }
                var playbackError: String?
                var resolved = false
                for candidate in feed.prefix(3) {
                    do {
                        let playback = try await VideoRepository.shared.resolvePlayback(video: candidate)
                        if ["http", "https"].contains(playback.url.scheme?.lowercased() ?? "") {
                            try await Self.probeMediaResponse(playback)
                            resolved = true
                            break
                        }
                        playbackError = "Playback resolver returned a non-HTTP(S) URL"
                    } catch {
                        playbackError = error.localizedDescription
                        print("LIVE_VIDEO_RESOLVE_ATTEMPT source=\(source.id) error=\(playbackError ?? "unknown")")
                    }
                }
                var errors: [String] = []
                if !coverDecoded { errors.append("No decodable cover in first 3 feed items") }
                if !resolved { errors.append("No playable URL in first 3 feed items: \(playbackError ?? "unknown")") }
                probes.append(Probe(
                    sourceID: source.id,
                    itemCount: itemCount,
                    coverDecoded: coverDecoded,
                    error: errors.isEmpty ? nil : errors.joined(separator: "; ")
                ))
            } catch {
                probes.append(Probe(sourceID: source.id, itemCount: itemCount, coverDecoded: coverDecoded, error: "\(itemCount == 0 ? "feed" : "playback"): \(error.localizedDescription)"))
            }
        }

        for probe in probes {
            print("LIVE_VIDEO_SOURCE \(probe.sourceID) items=\(probe.itemCount) cover=\(probe.coverDecoded ? "decoded" : "failed") playback=\(probe.error == nil ? "resolved" : "failed") error=\(probe.error ?? "none")")
            XCTAssertNil(probe.error, "\(probe.sourceID) live source check failed: \(probe.error ?? "unknown error")")
            XCTAssertGreaterThan(probe.itemCount, 0, "\(probe.sourceID) feed returned no parsed upstream items")
            XCTAssertTrue(probe.coverDecoded, "\(probe.sourceID) has no decodable cover in the first 3 feed items")
        }
    }

    func testHuavodDetailedLivePlaybackAndSegmentDownload() async throws {
        guard ProcessInfo.processInfo.environment["RUN_IOS_LIVE_SOURCE_AUDIT"] == "1" else {
            throw XCTSkip("Set RUN_IOS_LIVE_SOURCE_AUDIT=1 to probe live video sites.")
        }

        let source = VideoSourceCatalog.huavod
        let feed = try await VideoRepository.shared.fetchVideosForSource(source)
        XCTAssertFalse(feed.isEmpty, "华视影院 feed 列表为空")
        print("HUAVOD_LIVE_FEED count=\(feed.count) firstTitle=\(feed[0].title) coverUrl=\(feed[0].coverUrl)")

        let firstVideo = feed[0]
        let cover = await CoverImageLoader.shared.loadImage(from: firstVideo.coverUrl, baseURL: URL(string: source.baseUrl))
        XCTAssertNotNil(cover, "华视影院封面图片解码失败")
        print("HUAVOD_LIVE_COVER size=\(cover?.size.width ?? 0)x\(cover?.size.height ?? 0)")

        let playback = try await VideoRepository.shared.resolvePlayback(video: firstVideo)
        print("HUAVOD_LIVE_PLAYBACK url=\(playback.url) referer=\(playback.referer ?? "")")

        var m3u8Req = URLRequest(url: playback.url)
        playback.headers.forEach { m3u8Req.setValue($0.value, forHTTPHeaderField: $0.key) }
        let (m3u8Data, m3u8Resp) = try await URLSession.shared.data(for: m3u8Req)
        let m3u8Status = (m3u8Resp as? HTTPURLResponse)?.statusCode ?? 0
        XCTAssertTrue((200...299).contains(m3u8Status), "M3U8 请求失败: HTTP \(m3u8Status)")
        let m3u8String = String(data: m3u8Data, encoding: .utf8) ?? ""
        XCTAssertTrue(m3u8String.contains("#EXTM3U"), "返回的内容不是有效的 M3U8 播放列表")
        print("HUAVOD_LIVE_M3U8 bytes=\(m3u8Data.count) isM3U8=\(m3u8String.contains("#EXTM3U"))")

        // 提取首个媒体分片或子播放列表
        let lines = m3u8String.components(separatedBy: .newlines)
        let segmentLine = lines.first(where: {
            let trimmed = $0.trimmingCharacters(in: .whitespacesAndNewlines)
            return !trimmed.isEmpty && !trimmed.hasPrefix("#")
        })
        XCTAssertNotNil(segmentLine, "M3U8 中未找到分片或子列表 URL")
        guard let segmentRaw = segmentLine else { return }
        let segmentURL = URL(string: segmentRaw, relativeTo: playback.url)?.absoluteURL ?? playback.url
        print("HUAVOD_LIVE_SEGMENT url=\(segmentURL.host ?? "") pathExt=\(segmentURL.pathExtension)")

        var segReq = URLRequest(url: segmentURL)
        segReq.setValue("bytes=0-1024", forHTTPHeaderField: "Range")
        playback.headers.forEach { segReq.setValue($0.value, forHTTPHeaderField: $0.key) }
        let (segData, segResp) = try await URLSession.shared.data(for: segReq)
        let segStatus = (segResp as? HTTPURLResponse)?.statusCode ?? 0
        XCTAssertTrue((200...299).contains(segStatus), "分片媒体请求失败: HTTP \(segStatus)")
        XCTAssertFalse(segData.isEmpty, "分片返回数据为空")
        print("HUAVOD_LIVE_SEGMENT_DOWNLOAD status=\(segStatus) bytes=\(segData.count)")
    }

    private static func probeMediaResponse(_ playback: PlaybackSourceResult) async throws {
        var request = URLRequest(url: playback.url)
        request.setValue("bytes=0-255", forHTTPHeaderField: "Range")
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        if let referer = playback.referer {
            request.setValue(referer, forHTTPHeaderField: "Referer")
        }
        playback.headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse,
              (200...299).contains(http.statusCode), !data.isEmpty else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw NSError(domain: "LiveVideoSourceAudit", code: 1, userInfo: [NSLocalizedDescriptionKey: "Resolved media response was not successful (HTTP \(status), bytes=\(data.count))"])
        }
        let mimeType = (http.value(forHTTPHeaderField: "Content-Type") ?? "").lowercased()
        guard !mimeType.contains("text/html") else {
            throw NSError(domain: "LiveVideoSourceAudit", code: 2, userInfo: [NSLocalizedDescriptionKey: "Resolved media URL returned HTML instead of media"])
        }
        if playback.url.path.lowercased().hasSuffix(".m3u8") || mimeType.contains("mpegurl") {
            let prefix = String(decoding: data.prefix(256), as: UTF8.self)
            guard prefix.contains("#EXTM3U") else {
                throw NSError(domain: "LiveVideoSourceAudit", code: 3, userInfo: [NSLocalizedDescriptionKey: "Resolved playlist did not contain an HLS manifest"])
            }
        }
    }
}
