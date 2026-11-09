import XCTest
import SwiftUI
import UIKit
@testable import AuroraShelf

@MainActor
final class ChannelVisualSpecTests: XCTestCase {
    func testAll19SupportedSourcesHaveAppleDesignChannelVisualSpecs() {
        let sources = VideoSourceCatalog.supportedSources

        XCTAssertEqual(sources.count, 19, "应有完整 19 个支持的视频流媒体源")

        for source in sources {
            let spec = ChannelVisualSpec.forSource(source.id)

            // 1. 验证图标名称非空且非兜底默认 "tv.fill"（每家均有定制 Apple 图标）
            XCTAssertFalse(spec.iconName.isEmpty, "频道 \(source.name) (\(source.id)) 图标名不能为空")
            XCTAssertNotEqual(spec.iconName, "tv.fill", "频道 \(source.name) (\(source.id)) 应当拥有官方专属设计的 SF Symbol 图标，而非兜底 tv.fill")

            // 2. 验证图标在苹果官方 SDK 系统符号库中真实存在
            let image = UIImage(systemName: spec.iconName)
            XCTAssertNotNil(image, "频道 \(source.name) 的 SF Symbol 图标 [\(spec.iconName)] 必须在 iOS SDK 中有效存在")

            // 3. 验证专属品牌渐变色彩（至少双色）
            XCTAssertGreaterThanOrEqual(spec.gradientColors.count, 2, "频道 \(source.name) 必须定义至少两色品牌专属渐变")
        }
    }

    func testSpecificKeyChannelsHaveCorrectAppleStyleVisualMappings() {
        // 华视影院：电影剧院深红
        let huavod = ChannelVisualSpec.forSource("huavod")
        XCTAssertEqual(huavod.iconName, "film.stack.fill")

        // 91动漫：电光紫罗兰二次元
        let dongman = ChannelVisualSpec.forSource("91dongman")
        XCTAssertEqual(dongman.iconName, "sparkles.tv.fill")

        // Beeg：黑曜石 + 闪电
        let beeg = ChannelVisualSpec.forSource("beeg")
        XCTAssertEqual(beeg.iconName, "bolt.fill")

        // SpankBang：4K 超清专属
        let spankbang = ChannelVisualSpec.forSource("spankbang")
        XCTAssertEqual(spankbang.iconName, "4k.tv.fill")

        // XNXX：全球流媒体
        let xnxx = ChannelVisualSpec.forSource("xnxx")
        XCTAssertEqual(xnxx.iconName, "globe.americas.fill")
    }

    func testAllComicSourcesHaveAppleDesignVisualSpecs() {
        for source in ComicSourceCatalog.allSources {
            let spec = ComicVisualSpec.forSource(source.id)
            XCTAssertFalse(spec.iconName.isEmpty, "漫画源 \(source.name) 图标不能为空")
            let image = UIImage(systemName: spec.iconName)
            XCTAssertNotNil(image, "漫画源 \(source.name) 的 SF Symbol 图标 [\(spec.iconName)] 必须在 iOS SDK 中有效存在")
            XCTAssertGreaterThanOrEqual(spec.gradientColors.count, 2, "漫画源 \(source.name) 必须定义至少两色品牌专属渐变")
        }
    }

    func testAllLiveSourcesHaveAppleDesignVisualSpecs() {
        for source in LiveSourceCatalog.allSources {
            let spec = LiveVisualSpec.forSource(source.id)
            XCTAssertFalse(spec.iconName.isEmpty, "直播源 \(source.name) 图标不能为空")
            let image = UIImage(systemName: spec.iconName)
            XCTAssertNotNil(image, "直播源 \(source.name) 的 SF Symbol 图标 [\(spec.iconName)] 必须在 iOS SDK 中有效存在")
            XCTAssertGreaterThanOrEqual(spec.gradientColors.count, 2, "直播源 \(source.name) 必须定义至少两色品牌专属渐变")
        }
    }

    func testAllForumBoardsHaveAppleDesignVisualSpecs() {
        // 全板块（nil）
        let allSpec = ForumBoardVisualSpec.forBoard(nil)
        XCTAssertFalse(allSpec.iconName.isEmpty)
        XCTAssertNotNil(UIImage(systemName: allSpec.iconName))
        XCTAssertGreaterThanOrEqual(allSpec.gradientColors.count, 2)

        for board in ForumSourceCatalog.allBoards {
            let spec = ForumBoardVisualSpec.forBoard(board.fid)
            XCTAssertFalse(spec.iconName.isEmpty, "板块 \(board.name) 图标不能为空")
            let image = UIImage(systemName: spec.iconName)
            XCTAssertNotNil(image, "板块 \(board.name) 的 SF Symbol 图标 [\(spec.iconName)] 必须在 iOS SDK 中有效存在")
            XCTAssertGreaterThanOrEqual(spec.gradientColors.count, 2, "板块 \(board.name) 必须定义至少两色品牌专属渐变")
        }
    }
}
