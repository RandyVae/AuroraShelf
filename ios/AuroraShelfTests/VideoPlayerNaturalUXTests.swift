import XCTest
import UIKit
@testable import AuroraShelf

@MainActor
final class VideoPlayerNaturalUXTests: XCTestCase {

    override func tearDown() {
        MainActor.assumeIsolated {
            PlayerOrientationCoordinator.shared.resetToPortraitIfNeeded()
        }
        super.tearDown()
    }

    func testAspectClassificationAndFullscreenDirection() {
        let wideAspect = VideoAspectProfile.resolvedAspectRatio(
            presentationSize: CGSize(width: 1920, height: 1080)
        )
        XCTAssertEqual(VideoAspectProfile.kind(forAspect: wideAspect), .landscape)
        XCTAssertTrue(
            VideoAspectProfile.prefersLandscapeFullscreen(aspectRatio: wideAspect, idiom: .phone),
            "16:9 横屏视频在 iPhone 点击全屏时必须自动进入横屏全屏"
        )
        XCTAssertFalse(
            VideoAspectProfile.prefersLandscapeFullscreen(aspectRatio: wideAspect, idiom: .pad),
            "iPad 遵循四向自然重力旋转，不强制锁死单一方向"
        )

        let verticalAspect = VideoAspectProfile.resolvedAspectRatio(
            presentationSize: CGSize(width: 1080, height: 1920)
        )
        XCTAssertEqual(VideoAspectProfile.kind(forAspect: verticalAspect), .portrait)
        XCTAssertFalse(
            VideoAspectProfile.prefersLandscapeFullscreen(aspectRatio: verticalAspect, idiom: .phone),
            "9:16 竖屏短剧在 iPhone 点击全屏时必须保持竖屏满铺，绝不横转留黑边"
        )

        let squareAspect = VideoAspectProfile.resolvedAspectRatio(
            presentationSize: CGSize(width: 1080, height: 1080)
        )
        XCTAssertEqual(VideoAspectProfile.kind(forAspect: squareAspect), .square)
        XCTAssertFalse(
            VideoAspectProfile.prefersLandscapeFullscreen(aspectRatio: squareAspect, idiom: .phone)
        )

        // 首帧未解码前的源特征预判
        let shortDramaFallback = VideoAspectProfile.resolvedAspectRatio(
            presentationSize: .zero,
            sourceName: "黄果短剧"
        )
        XCTAssertEqual(shortDramaFallback, 9.0 / 16.0, accuracy: 0.001)

        let movieFallback = VideoAspectProfile.resolvedAspectRatio(
            presentationSize: .zero,
            sourceName: "华视影院"
        )
        XCTAssertEqual(movieFallback, 16.0 / 9.0, accuracy: 0.001)
    }

    func testInlineStageHeightAdaptsAcrossFormFactorsAndCollapsesOnScroll() {
        let phoneSize = CGSize(width: 393, height: 852)
        let safeTop: CGFloat = 59

        let wideHeight = VideoAspectProfile.inlineContainerHeight(
            screenSize: phoneSize,
            playerColumnWidth: phoneSize.width,
            safeTop: safeTop,
            aspectRatio: 16.0 / 9.0,
            collapseProgress: 0,
            idiom: .phone,
            isSplitMode: false
        )
        XCTAssertEqual(wideHeight, 393.0 * 9.0 / 16.0 + safeTop, accuracy: 0.5)

        let portraitExpanded = VideoAspectProfile.inlineContainerHeight(
            screenSize: phoneSize,
            playerColumnWidth: phoneSize.width,
            safeTop: safeTop,
            aspectRatio: 9.0 / 16.0,
            collapseProgress: 0,
            idiom: .phone,
            isSplitMode: false
        )
        XCTAssertGreaterThan(portraitExpanded, wideHeight + 180, "竖屏视频在二级页初始应显著撑高舞台消除邮票黑边")

        let portraitCollapsed = VideoAspectProfile.inlineContainerHeight(
            screenSize: phoneSize,
            playerColumnWidth: phoneSize.width,
            safeTop: safeTop,
            aspectRatio: 9.0 / 16.0,
            collapseProgress: 1.0,
            idiom: .phone,
            isSplitMode: false
        )
        XCTAssertEqual(portraitCollapsed, wideHeight, accuracy: 0.5, "向上滑动列表到底时应平滑收折回 16:9 紧凑吸顶高度")

        // iPhone Duo 外屏折叠态（466×678）：确保竖片展开后仍为下方标题与胶囊工具栏留足至少 280pt 可视空间
        let duoFolded = CGSize(width: 466, height: 678)
        let duoPortraitHeight = VideoAspectProfile.inlineContainerHeight(
            screenSize: duoFolded,
            playerColumnWidth: duoFolded.width,
            safeTop: 59,
            aspectRatio: 9.0 / 16.0,
            collapseProgress: 0,
            idiom: .phone,
            isSplitMode: false
        )
        XCTAssertLessThanOrEqual(duoPortraitHeight, duoFolded.height - 280, "Duo 折叠屏外屏竖片高度不得遮没下方标题与操作胶囊")
    }

    func testYouTubeGravityStateMachineManualAndPhysicalTransitions() {
        var machine = PlayerGravityRotationStateMachine()

        // 1. 用户竖握手机手动点击 16:9 视频的「全屏」按钮：进入横屏全屏，且因手机尚未横握不会立即误退回竖屏
        machine.enterFullscreen(preferLandscape: true, triggeredByGravity: false)
        XCTAssertTrue(machine.isFullScreen)
        XCTAssertTrue(machine.isLandscapeLocked)
        XCTAssertFalse(machine.hasAlignedWithPhysicalLandscape)
        XCTAssertEqual(
            machine.handleDeviceOrientation(.portrait, videoKind: .landscape, idiom: .phone),
            .none,
            "竖握手动点全屏后，不能因为设备仍处于竖握姿态而立刻弹回二级页"
        )

        // 2. 用户随后把手机横过来观看，再竖回手机 -> 自动顺滑退出全屏回到二级页
        XCTAssertEqual(
            machine.handleDeviceOrientation(.landscapeLeft, videoKind: .landscape, idiom: .phone),
            .none
        )
        XCTAssertTrue(machine.hasAlignedWithPhysicalLandscape)
        XCTAssertEqual(
            machine.handleDeviceOrientation(.portrait, videoKind: .landscape, idiom: .phone),
            .exitToInlinePortrait
        )
        XCTAssertFalse(machine.isFullScreen)
        XCTAssertFalse(machine.isLandscapeLocked)

        // 3. 二级页内嵌播放横屏片时，直接把手机横握 -> 自动进横屏全屏；竖回 -> 自动退回二级页
        XCTAssertEqual(
            machine.handleDeviceOrientation(.landscapeRight, videoKind: .landscape, idiom: .phone),
            .enterLandscapeFullscreen
        )
        XCTAssertTrue(machine.isFullScreen)
        XCTAssertTrue(machine.isLandscapeLocked)
        XCTAssertEqual(
            machine.handleDeviceOrientation(.portrait, videoKind: .landscape, idiom: .phone),
            .exitToInlinePortrait
        )

        // 4. 二级页播放 9:16 竖屏短剧时横倾手机 -> 不自动强切横屏全屏
        XCTAssertEqual(
            machine.handleDeviceOrientation(.landscapeLeft, videoKind: .portrait, idiom: .phone),
            .none
        )
    }

    func testPlayerOrientationCoordinatorSupportsBidirectionalLandscapeAndReset() {
        let coordinator = PlayerOrientationCoordinator.shared
        coordinator.resetToPortraitIfNeeded()
        XCTAssertEqual(coordinator.supportedInterfaceOrientations(for: .phone), .portrait)
        XCTAssertEqual(coordinator.supportedInterfaceOrientations(for: .pad), .all)

        coordinator.applyPhoneOrientation(landscape: true)
        if UIDevice.current.userInterfaceIdiom != .pad {
            let mask = coordinator.supportedInterfaceOrientations(for: .phone)
            XCTAssertTrue(mask.contains(.landscapeLeft) && mask.contains(.landscapeRight), "横屏全屏必须同时放行左右双向横屏，杜绝反向横握倒置 180°")
        }

        coordinator.resetToPortraitIfNeeded()
        XCTAssertEqual(coordinator.supportedInterfaceOrientations(for: .phone), .portrait)
    }
}
