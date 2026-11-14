import XCTest
import SwiftUI
@testable import AuroraShelf

@MainActor
final class DeviceLayoutTests: XCTestCase {
    func testStandardPhoneDimensionsResolveToStandardPhone() {
        let size = CGSize(width: 393, height: 852) // iPhone 17 Pro
        let form = DeviceLayout.resolve(size: size, idiom: .phone)

        XCTAssertEqual(form, .standardPhone)
        XCTAssertFalse(form.isDuo)
        XCTAssertFalse(form.isWide)
        XCTAssertEqual(DeviceLayout.heroHeight(for: form, screenHeight: size.height), 420)
        XCTAssertNil(DeviceLayout.heroActionsMaxWidth(for: form))
        XCTAssertEqual(DeviceLayout.upNextCardWidth(for: form), 250)
    }

    func testIPhoneDuoFoldedOuterScreenDimensionsResolveToDuoFolded() {
        // iPhone Duo 外屏官方规格：1398 × 2034 @3x -> 466 × 678 pt
        let size = CGSize(width: 466, height: 678)
        let form = DeviceLayout.resolve(size: size, idiom: .phone)

        XCTAssertEqual(form, .duoFolded)
        XCTAssertTrue(form.isDuo)
        XCTAssertFalse(form.isWide)
        // 外屏总高仅 678pt，黄金画幅高度锁定 340pt，确保下方内容与待播货架露头可被发现
        XCTAssertEqual(DeviceLayout.heroHeight(for: form, screenHeight: size.height), 325.44, accuracy: 0.1)
        XCTAssertEqual(DeviceLayout.heroActionsMaxWidth(for: form), 380)
        XCTAssertEqual(DeviceLayout.upNextCardWidth(for: form), 250)
    }

    func testIPhoneDuoUnfoldedInnerScreenDimensionsResolveToDuoUnfolded() {
        // iPhone Duo 内屏双联官方规格：2007 × 2853 @3x -> 669 × 951 pt
        let size = CGSize(width: 669, height: 951)
        let form = DeviceLayout.resolve(size: size, idiom: .phone)

        XCTAssertEqual(form, .duoUnfolded)
        XCTAssertTrue(form.isDuo)
        XCTAssertTrue(form.isWide)
        // 内屏宽幅黄金画幅：460pt 剧院高度与 420pt 居中操作组
        XCTAssertEqual(DeviceLayout.heroHeight(for: form, screenHeight: size.height), 460)
        XCTAssertEqual(DeviceLayout.heroActionsMaxWidth(for: form), 420)
        // 待播卡片自适应拓宽至 280pt（16:9 画幅）
        XCTAssertEqual(DeviceLayout.upNextCardWidth(for: form), 280)
    }

    func testPadDimensionsResolveToPad() {
        let size = CGSize(width: 834, height: 1210) // iPad Pro 11-inch
        let form = DeviceLayout.resolve(size: size, idiom: .pad)

        XCTAssertEqual(form, .pad)
        XCTAssertFalse(form.isDuo)
        XCTAssertTrue(form.isWide)
        XCTAssertEqual(DeviceLayout.heroHeight(for: form, screenHeight: size.height), 520)
        XCTAssertEqual(DeviceLayout.heroActionsMaxWidth(for: form), 440)
        XCTAssertEqual(DeviceLayout.upNextCardWidth(for: form), 320)
    }

    func testFluidGridColumnDistributionAcrossAllFourDeviceForms() {
        // 布局算法：min 160pt, max 340pt, spacing 12pt, horizontal inset 16pt (总可用宽度 = 宽度 - 32)
        func columnCount(for width: CGFloat) -> Int {
            let availableWidth = width - 32
            // 尝试从 1 到 6 列匹配
            for cols in stride(from: 6, through: 1, by: -1) {
                let totalSpacing = CGFloat(cols - 1) * 12
                let itemWidth = (availableWidth - totalSpacing) / CGFloat(cols)
                if itemWidth >= 160 && itemWidth <= 340 {
                    return cols
                }
            }
            return 1
        }

        XCTAssertEqual(columnCount(for: 393), 2, "标准 iPhone 竖屏应呈现 2 列")
        XCTAssertEqual(columnCount(for: 466), 2, "iPhone Duo 外屏折叠态应呈现 2 列舒展卡片")
        XCTAssertEqual(columnCount(for: 669), 3, "iPhone Duo 内屏双联展开态应呈现 3 列大图卡片")
        XCTAssertEqual(columnCount(for: 834), 4, "iPad Pro 竖屏应呈现 4 列海报卡片")
    }

    func testVideoDetailUsesYouTubeSplitLayoutOnlyForPadLandscape() {
        XCTAssertEqual(
            VideoDetailLayout.mode(size: CGSize(width: 1366, height: 1024), idiom: .pad),
            .padLandscapeSplit
        )
        XCTAssertEqual(
            VideoDetailLayout.mode(size: CGSize(width: 1024, height: 1366), idiom: .pad),
            .stacked
        )
        XCTAssertEqual(
            VideoDetailLayout.mode(size: CGSize(width: 852, height: 393), idiom: .phone),
            .stacked
        )
    }

    func testVideoDetailLandscapeColumnsKeepPlayerAndRecommendationsVisible() {
        let columns = VideoDetailLayout.columns(for: CGSize(width: 1366, height: 1024), idiom: .pad)
        XCTAssertEqual(columns.playerFraction, 0.70, accuracy: 0.001)
        XCTAssertEqual(columns.recommendationFraction, 0.30, accuracy: 0.001)
        XCTAssertGreaterThan(columns.recommendationWidth(for: 1366), 360)
        XCTAssertLessThanOrEqual(columns.recommendationWidth(for: 1366), 420)
    }
}
