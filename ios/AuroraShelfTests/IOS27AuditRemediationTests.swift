import XCTest
import SwiftUI
import UIKit
@testable import AuroraShelf

@MainActor
final class IOS27AuditRemediationTests: XCTestCase {

    override func tearDown() {
        LocalizationManager.shared.setLanguage(.system)
        super.tearDown()
    }

    func testComicZoomGeometryTrueContentWidthAndZoomToPointReachability() {
        let viewport = CGSize(width: 393, height: 852)

        XCTAssertEqual(ComicZoomGeometry.snappedScale(1.03), 1.0, accuracy: 0.001)
        XCTAssertEqual(ComicZoomGeometry.snappedScale(2.0), 2.0, accuracy: 0.001)
        XCTAssertEqual(ComicZoomGeometry.clampedScale(4.5), 3.0, accuracy: 0.001)
        XCTAssertEqual(ComicZoomGeometry.contentWidth(viewportWidth: viewport.width, zoomScale: 1.0), 393, accuracy: 0.01)
        XCTAssertEqual(ComicZoomGeometry.contentWidth(viewportWidth: viewport.width, zoomScale: 2.0), 786, accuracy: 0.01)
        XCTAssertEqual(ComicZoomGeometry.contentWidth(viewportWidth: viewport.width, zoomScale: 3.0), 1179, accuracy: 0.01)

        let leftTarget = ComicZoomGeometry.targetOffsetForDoubleTap(
            tapLocationInViewport: CGPoint(x: 24, y: 200),
            currentOffset: CGPoint(x: 0, y: 1200),
            viewportSize: viewport,
            oldScale: 1.0,
            newScale: 2.0
        )
        XCTAssertEqual(leftTarget.x, 0, accuracy: 0.01)
        XCTAssertGreaterThan(leftTarget.y, 2000)

        let rightTarget = ComicZoomGeometry.targetOffsetForDoubleTap(
            tapLocationInViewport: CGPoint(x: 380, y: 400),
            currentOffset: CGPoint(x: 0, y: 600),
            viewportSize: viewport,
            oldScale: 1.0,
            newScale: 2.0
        )
        XCTAssertEqual(rightTarget.x, viewport.width, accuracy: 0.01)

        let resetTarget = ComicZoomGeometry.targetOffsetForDoubleTap(
            tapLocationInViewport: CGPoint(x: 200, y: 300),
            currentOffset: CGPoint(x: 393, y: 2400),
            viewportSize: viewport,
            oldScale: 2.0,
            newScale: 1.0
        )
        XCTAssertEqual(resetTarget.x, 0, accuracy: 0.01)
        XCTAssertEqual(resetTarget.y, 1200, accuracy: 0.01)
    }

    func testWindowRelativeRelatedVideoGridColumnsNeverCollapseInNarrowIPadWindow() {
        let narrowCols = DeviceLayout.relatedVideoGridColumns(for: 375, spacing: 12)
        XCTAssertEqual(narrowCols.count, 2)

        let mediumCols = DeviceLayout.relatedVideoGridColumns(for: 680, spacing: 16)
        XCTAssertEqual(mediumCols.count, 3)

        let wideCols = DeviceLayout.relatedVideoGridColumns(for: 1024, spacing: 16)
        XCTAssertEqual(wideCols.count, 4)
    }

    func testLocalizationCompletenessAndHongKongTraditionalOrthography() {
        let probeKeys = [
            "channels.all_sources_count",
            "live.watch_now",
            "forum.enter_discussion",
            "player.watch_fullscreen",
            "dialog.discard_title",
            "settings.version",
            "settings.airplay_select",
            "comics.unconfigured_hint"
        ]

        for lang in [AppLanguage.en, .zhHant, .zhHans, .ja, .ko] {
            LocalizationManager.shared.setLanguage(lang)
            for key in probeKeys {
                let value = key.localized
                XCTAssertNotEqual(value, key, "Key \(key) must be localized in \(lang.rawValue)")
                XCTAssertFalse(value.isEmpty)
            }
        }

        LocalizationManager.shared.setLanguage(.en)
        XCTAssertEqual("全部".localizedCategoryLabel, "All")
        XCTAssertEqual("女性主播".localizedCategoryLabel, "Female")

        LocalizationManager.shared.setLanguage(.zhHant)
        XCTAssertEqual("settings.version".localized, "版本資料")
        XCTAssertEqual("settings.airplay_select".localized, "選擇設備")
        XCTAssertFalse("comics.unconfigured_hint".localized.contains("設定檔"))
    }

    func testCustomEdgeSwipeInterceptionAttachesLeftEdgeGestureWithoutPoppingStack() {
        let rootVC = UIViewController()
        let hostVC = UIViewController()
        let nav = UINavigationController(rootViewController: rootVC)
        nav.loadViewIfNeeded()
        nav.setViewControllers([rootVC, hostVC], animated: false)
        hostVC.loadViewIfNeeded()

        let enabler = PopGestureEnablerController()
        let coordinator = NavigationPopCoordinator(isEnabled: false, allowsContentSwipe: false)
        enabler.coordinator = coordinator
        var customDismissCount = 0
        enabler.isEnabled = false
        enabler.allowsContentSwipe = false
        enabler.onCustomEdgeSwipeBack = {
            customDismissCount += 1
        }

        hostVC.addChild(enabler)
        hostVC.view.addSubview(enabler.view)
        enabler.didMove(toParent: hostVC)
        enabler.viewDidAppear(false)

        let edgeRecognizers = (hostVC.view.gestureRecognizers ?? []).compactMap { $0 as? UIScreenEdgePanGestureRecognizer }
        XCTAssertEqual(edgeRecognizers.count, 1, "Must attach left-edge UIScreenEdgePanGestureRecognizer for custom graceful dismissal")
        XCTAssertEqual(edgeRecognizers.first?.edges, .left)
        XCTAssertFalse(coordinator.gestureRecognizerShouldBegin(UIGestureRecognizer()), "Native pop must stay disabled while custom interception is active")
        XCTAssertEqual(customDismissCount, 0)

        enabler.willMove(toParent: nil)
        enabler.view.removeFromSuperview()
        enabler.removeFromParent()
        let remaining = (hostVC.view.gestureRecognizers ?? []).compactMap { $0 as? UIScreenEdgePanGestureRecognizer }
        XCTAssertTrue(remaining.isEmpty, "Custom edge gesture must detach cleanly on removal")
    }

    func testZoomTransitionSourceRegistryWeakStorageEvictsDeallocatedViews() {
        let dummyVideo = VideoItem(
            id: "zoom-\(UUID().uuidString)",
            title: "Zoom Test",
            subtitle: "",
            coverUrl: "https://example.com/cover.jpg",
            playbackUrl: "https://example.com/play.m3u8",
            duration: "10:00",
            rating: "9.0",
            tags: [],
            category: "Movie",
            sourceName: "Test"
        )
        let route = AppImmersiveRoute.video(dummyVideo)
        let key = ZoomTransitionSourceRegistry.routeKey(for: route)!

        autoreleasepool {
            let anchor = UIView(frame: CGRect(x: 0, y: 0, width: 120, height: 180))
            let window = UIWindow()
            window.frame = CGRect(x: 0, y: 0, width: 393, height: 852)
            window.addSubview(anchor)
            ZoomTransitionSourceRegistry.shared.register(view: anchor, forKey: key)
            if !UIAccessibility.isReduceMotionEnabled {
                XCTAssertNotNil(ZoomTransitionSourceRegistry.shared.sourceView(for: route))
            }
            anchor.removeFromSuperview()
        }
        XCTAssertNil(ZoomTransitionSourceRegistry.shared.sourceView(for: route))
    }
}
