import XCTest
@testable import AuroraShelf

final class X134SourceServiceTests: XCTestCase {
    func testSerovalPayloadEncodingAndResponseDecodingWithBackref() throws {
        let encoded = try X134SerovalCodec.encodePayload(
            .object([
                "data": .object([
                    "page": .number(2),
                    "ranking": .string("popular"),
                    "flag": .bool(true),
                    "empty": .null
                ])
            ])
        )
        let rawObj = try XCTUnwrap(
            JSONSerialization.jsonObject(with: Data(encoded.utf8)) as? [String: Any]
        )
        XCTAssertEqual(rawObj["f"] as? Int, 127)
        let innerData = try XCTUnwrap(rawObj["t"] as? [String: Any])
        let innerJSON = try JSONSerialization.data(withJSONObject: innerData)
        let roundtripped = try X134SerovalCodec.decodeNode(data: innerJSON)
        XCTAssertEqual(roundtripped["data"]?["page"]?.intValue, 2)
        XCTAssertEqual(roundtripped["data"]?["ranking"]?.stringValue, "popular")
        XCTAssertEqual(roundtripped["data"]?["flag"]?.boolValue, true)
        XCTAssertEqual(roundtripped["data"]?["empty"], .null)

        let responseJSON = """
        {
          "t": 10,
          "i": 0,
          "p": {
            "k": ["result", "error"],
            "v": [
              {
                "t": 11,
                "i": 1,
                "p": {
                  "k": ["tweets", "shared", "alias"],
                  "v": [
                    {
                      "t": 9,
                      "i": 2,
                      "a": [{"t": 1, "s": "tweet-1\\\\nline2 \\\\\\"ok\\\\\\" &amp; \\\\u4e2d"}]
                    },
                    {"t": 10, "i": 3, "p": {"k": ["count"], "v": [{"t": 0, "s": 99}]}},
                    {"t": 4, "i": 3}
                  ]
                }
              },
              {"t": 2, "s": 1}
            ]
          }
        }
        """
        let decoded = try X134SerovalCodec.decodeNode(data: Data(responseJSON.utf8))
        XCTAssertEqual(decoded["error"], .null)
        let rawTweet = decoded["result"]?["tweets"]?.arrayValue?.first?.stringValue
        XCTAssertEqual(rawTweet, "tweet-1\nline2 \"ok\" &amp; 中")
        XCTAssertEqual(
            X134SourceService.cleanTweetTitle(rawTweet ?? "", fallbackAuthor: "fallback"),
            "tweet-1 line2 \"ok\" & 中"
        )
        XCTAssertEqual(decoded["result"]?["shared"]?["count"]?.intValue, 99)
        XCTAssertEqual(decoded["result"]?["alias"]?["count"]?.intValue, 99)
    }

    func testExtractsAppShellPathAndHashesAndParsesTweets() throws {
        let html = #"<script type="module" src="/assets/AppShell-abc123.js"></script>"#
        XCTAssertEqual(X134SourceService.extractAppShellPath(fromHTML: html), "assets/AppShell-abc123.js")

        let hashes = (0..<11).map { String(repeating: String($0, radix: 16), count: 64) }
        let js = hashes.enumerated().map { idx, hash in
            "var f\(idx)=S({method:`GET`}).handler(Y(`\(hash)`))"
        }.joined(separator: ",")
        let parsed = try XCTUnwrap(X134SourceService.extractHashes(fromAppShellJS: js))
        XCTAssertEqual(parsed.latest, hashes[1])
        XCTAssertEqual(parsed.ranking, hashes[3])
        XCTAssertEqual(parsed.search, hashes[4])
        XCTAssertEqual(parsed.detail, hashes[7])

        let tweets: [X134SerovalValue] = [
            .object([
                "tweetId": .string("2121514973625200843"),
                "name": .string("中文字幕 AV"),
                "screenName": .string("CU_JAV"),
                "profileImage": .string("https://pbs.twimg.com/profile_images/1/pic_normal.png"),
                "text": .string("高清无广告长视频\n第二行 https://t.co/bvz3w78efg"),
                "durationMillis": .number(4_558_848),
                "playTimes": .number(12_600)
            ]),
            .object([
                "tweetId": .string("2121514218914808037"),
                "name": .string(""),
                "screenName": .string("handle_only"),
                "profileImage": .string("https://pbs.twimg.com/profile_images/2/avatar_normal.jpg"),
                "text": .string("https://t.co/only"),
                "durationMillis": .number(725_000),
                "playTimes": .number(0)
            ])
        ]
        let meta = [
            "2121514973625200843": X134SourceService.TweetMetadata(
                posterUrl: "https://pbs.twimg.com/amplify_video_thumb/2052979519930441728/img/poster.jpg",
                hlsUrl: "https://video.twimg.com/amplify_video/2052979519930441728/pl/master.m3u8",
                mp4Url: nil,
                updatedAt: Date()
            )
        ]

        let items = X134SourceService.parseFeedItems(rawTweets: tweets, metadata: meta, category: "最新")
        XCTAssertEqual(items.count, 2)
        XCTAssertEqual(items[0].id, "134x-2121514973625200843")
        XCTAssertEqual(items[0].title, "高清无广告长视频 第二行")
        XCTAssertEqual(items[0].duration, "1:15:58")
        XCTAssertEqual(items[0].rating, "1.3万")
        XCTAssertEqual(items[0].sourceName, "134X")
        XCTAssertEqual(items[0].coverUrl, "https://pbs.twimg.com/amplify_video_thumb/2052979519930441728/img/poster.jpg")

        XCTAssertEqual(items[1].title, "@handle_only")
        XCTAssertEqual(items[1].duration, "12:05")
        XCTAssertEqual(items[1].rating, "长视频")
        XCTAssertEqual(items[1].coverUrl, "https://pbs.twimg.com/profile_images/2/avatar_400x400.jpg")
    }

    func testCatalogRegistrationAndLiveFeedWhenOptedIn() async throws {
        XCTAssertEqual(VideoSourceCatalog.allSources.count, 20)
        XCTAssertTrue(VideoSourceCatalog.supportedSourceIDs.contains("134x"))
        XCTAssertTrue(VideoSourceCatalog.searchableSourceIDs.contains("134x"))
        XCTAssertEqual(VideoSourceCatalog.find(by: "134x").name, "134X")

        guard ProcessInfo.processInfo.environment["RUN_IOS_LIVE_SOURCE_AUDIT"] == "1" else {
            throw XCTSkip("Set RUN_IOS_LIVE_SOURCE_AUDIT=1 to test live 134x.com endpoints.")
        }

        let latest = try await X134SourceService.shared.fetchFeed(category: "最新", page: 1)
        XCTAssertFalse(latest.isEmpty)
        XCTAssertTrue(latest.prefix(3).contains(where: { $0.coverUrl.contains("pbs.twimg.com") }))

        let latestPage2 = try await X134SourceService.shared.fetchFeed(category: "最新", page: 2)
        XCTAssertFalse(latestPage2.isEmpty)
        XCTAssertTrue(Set(latest.map(\.playbackUrl)).isDisjoint(with: Set(latestPage2.map(\.playbackUrl))))

        let popular = try await X134SourceService.shared.fetchFeed(category: "热门", page: 1)
        XCTAssertFalse(popular.isEmpty)

        let featured = try await X134SourceService.shared.fetchFeed(category: "精选", page: 1)
        XCTAssertFalse(featured.isEmpty)

        let searched = try await X134SourceService.shared.search(query: "中文", page: 1)
        XCTAssertFalse(searched.isEmpty)

        let playback = try await X134SourceService.shared.resolvePlayback(
            pageUrl: latest[0].playbackUrl,
            title: latest[0].title
        )
        XCTAssertTrue(playback.url.absoluteString.contains("video.twimg.com") || playback.url.absoluteString.contains("134x.com"))
    }
}
