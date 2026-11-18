import XCTest
@testable import AuroraShelf

final class LiveSourceAdapterTests: XCTestCase {
    @MainActor
    func testEveryLiveSourceLoadsRealStreamsAndDecodableCoversWhenOptedIn() async throws {
        guard ProcessInfo.processInfo.environment["RUN_IOS_LIVE_SOURCE_AUDIT"] == "1" else {
            throw XCTSkip("Set RUN_IOS_LIVE_SOURCE_AUDIT=1 to probe live-stream sources.")
        }

        let repository = LiveRepository.shared
        for source in LiveSourceCatalog.allSources where source.id != LiveSourceCatalog.allSourcesID {
            repository.selectSource(source.id)
            await repository.refreshStreams()
            XCTAssertNil(repository.loadErrorMessage, "\(source.name) failed to load: \(repository.loadErrorMessage ?? "unknown error")")
            let streams = repository.liveStreams.filter { $0.sourceID == source.id }
            XCTAssertFalse(streams.isEmpty, "\(source.name) returned no live rooms")
            let stream = try XCTUnwrap(streams.first)
            let cover = await CoverImageLoader.shared.loadImage(from: stream.snapshotUrl)
            XCTAssertNotNil(cover, "\(source.name) room cover did not decode")
            if source.id == LiveSourceCatalog.stripchatID {
                let numericId = stream.id.replacingOccurrences(of: "sc-", with: "").filter(\.isNumber)
                let resolved = await StripchatStreamAuthenticator.shared.resolvePlayableURL(
                    rawHls: stream.hlsStreamUrl,
                    fallbackNumericId: numericId
                )
                if case .authenticated(let url) = resolved {
                    XCTAssertTrue(url.absoluteString.contains("_auto.m3u8"))
                    XCTAssertTrue(url.absoluteString.contains("pkey="))
                    print("LIVE_STREAM_SOURCE stripchat authenticated_hls=\(url.absoluteString)")
                } else {
                    XCTFail("Stripchat live room did not resolve to an authenticated native HLS URL: \(resolved)")
                }
            }
            print("LIVE_STREAM_SOURCE \(source.id) rooms=\(streams.count) cover=decoded room=\(stream.roomUrl)")
        }
    }

    func testStripchatFeedURLUsesLiveListEndpointAndPageOffset() throws {
        let url = LiveSourceAdapter.stripchatFeedURL(page: 3)
        XCTAssertEqual(url.host, "zh.stripchat.com")
        XCTAssertEqual(url.path, "/api/front/models")
        let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        let queryItems = try XCTUnwrap(components.queryItems)
        let query = Dictionary(uniqueKeysWithValues: queryItems.map { ($0.name, $0.value ?? "") })
        XCTAssertEqual(query["limit"], "30")
        XCTAssertEqual(query["offset"], "60")
        XCTAssertEqual(query["removeShows"], "true")
    }

    func testStripchatFeedURLAddsValidPrimaryTagForLiveCategories() throws {
        let url = LiveSourceAdapter.stripchatFeedURL(page: 1, primaryTag: "girls")
        let queryItems = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems)
        XCTAssertEqual(queryItems.first(where: { $0.name == "primaryTag" })?.value, "girls")
    }

    func testStripchatParserKeepsOnlyLivePublicItemsWithHTTPSImages() throws {
        let payload: [String: Any] = ["models": [
            ["id": 42, "username": "live_user", "isLive": true, "status": "public", "previewUrlThumbSmall": "https://img.example/live.jpg", "avatarUrl": "https://img.example/avatar.jpg", "viewersCount": 87, "gender": "f", "country": "US", "hlsPlaylist": "https://edge-hls.example/master.m3u8"],
            ["id": 43, "username": "fallback_user", "isLive": true, "status": "public", "previewUrlThumbSmall": "https://img.example/fb.jpg", "viewersCount": 12, "gender": "f", "country": "JP"],
            ["id": 44, "username": "private_user", "isLive": true, "status": "private", "previewUrlThumbSmall": "https://img.example/private.jpg"],
            ["id": 45, "username": "offline_user", "isLive": false, "status": "public", "previewUrlThumbSmall": "https://img.example/offline.jpg"],
            ["id": 46, "username": "invalid_image", "isLive": true, "status": "public", "previewUrlThumbSmall": "http://img.example/insecure.jpg"]
        ]]
        let data = try JSONSerialization.data(withJSONObject: payload)

        let streams = try LiveSourceAdapter.parseStripchat(data: data)

        XCTAssertEqual(streams.count, 2)
        XCTAssertEqual(streams[0].sourceID, LiveSourceCatalog.stripchatID)
        XCTAssertEqual(streams[0].username, "live_user")
        XCTAssertEqual(streams[0].viewCount.value, 87)
        XCTAssertEqual(streams[0].roomUrl, "https://zh.stripchat.com/live_user")
        XCTAssertEqual(streams[0].hlsStreamUrl, "https://edge-hls.example/master.m3u8")

        // 验证没有 hlsPlaylist 时自动通过 numeric id 构造自适应 _auto.m3u8 CDN 直链
        XCTAssertEqual(streams[1].username, "fallback_user")
        XCTAssertEqual(streams[1].hlsStreamUrl, "https://edge-hls.doppiocdn.media/hls/43/master/43_auto.m3u8")
    }

    func testStripchatStreamAuthenticatorNormalizesMasterAndExtractsMmpPkey() {
        XCTAssertEqual(
            StripchatStreamAuthenticator.normalizeMasterURL(
                rawUrl: "https://edge-hls.doppiocdn.media/hls/83306615/master/83306615_240p.m3u8"
            ),
            "https://edge-hls.doppiocdn.media/hls/83306615/master/83306615_auto.m3u8"
        )
        XCTAssertEqual(
            StripchatStreamAuthenticator.attachPkey(
                masterUrl: "https://edge-hls.doppiocdn.media/hls/83306615/master/83306615_auto.m3u8",
                pkey: "B0p93vi8Uj6AYyZb"
            ),
            "https://edge-hls.doppiocdn.media/hls/83306615/master/83306615_auto.m3u8?pkey=B0p93vi8Uj6AYyZb"
        )
        let html = #"{"MMPExternalUnitedSourceOrigin":"https:\u002F\u002Fmmp.doppiocdn.com\u002Fplayer\u002Fmmp","playerModuleExternalLoading":{"mmpVersion":"v2.16.1"}}"#
        XCTAssertEqual(
            StripchatStreamAuthenticator.extractMmpBundleURL(fromHtml: html)?.absoluteString,
            "https://mmp.doppiocdn.com/player/mmp/v2.16.1/main.js"
        )
        let js = #"function Sr(e){const t=new URL(e);return t.searchParams.set("pkey","B0p93vi8Uj6AYyZb"),t.toString()}"#
        XCTAssertEqual(StripchatStreamAuthenticator.extractPkey(fromMmpJS: js), "B0p93vi8Uj6AYyZb")
        XCTAssertTrue(StripchatStreamAuthenticator.isMouflonLockedManifest("#EXTM3U\n#EXT-X-MOUFLON-ADVERT"))
        XCTAssertFalse(StripchatStreamAuthenticator.isMouflonLockedManifest("#EXTM3U\n#EXTINF:2.000\nhttps://media-hls.doppiocdn.media/seg.mp4"))
    }

    func testMalformedStripchatResponseFailsInsteadOfShowingSyntheticItems() {
        XCTAssertThrowsError(try LiveSourceAdapter.parseStripchat(data: Data("{}".utf8)))
    }
}
