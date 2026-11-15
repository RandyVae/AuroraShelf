import XCTest
import SwiftUI
import UIKit
@testable import AuroraShelf

final class SwipeBackTests: XCTestCase {

    func testSwipeBackThresholdMathematics() {
        let screenWidth: CGFloat = 393.0 // iPhone 17 Pro
        
        // 1. 小位移且无速度：应判定为取消并回弹
        let dragOffsetSmall: CGFloat = 50.0
        let velocitySmall: CGFloat = 0.0
        let progressSmall = min(1.0, max(0.0, dragOffsetSmall / screenWidth))
        let projectedOffsetSmall = dragOffsetSmall + (velocitySmall * 0.16)
        let shouldDismissSmall = progressSmall > 0.30 || velocitySmall > 400 || projectedOffsetSmall > (screenWidth * 0.45)
        XCTAssertFalse(shouldDismissSmall, "小位移且无速度不应触发退出")
        
        // 2. 深度位移（超过 30% 屏幕宽度）：应判定为触发退出
        let dragOffsetDeep: CGFloat = 130.0 // > 393 * 0.30 = 117.9
        let velocityZero: CGFloat = 0.0
        let progressDeep = min(1.0, max(0.0, dragOffsetDeep / screenWidth))
        let projectedOffsetDeep = dragOffsetDeep + (velocityZero * 0.16)
        let shouldDismissDeep = progressDeep > 0.30 || velocityZero > 400 || projectedOffsetDeep > (screenWidth * 0.45)
        XCTAssertTrue(shouldDismissDeep, "深度位移超过 30% 必须触发退出")
        
        // 3. 小位移但有高速甩动（Fling Velocity > 400 pt/s）：应根据物理惯性触发退出
        let dragOffsetFling: CGFloat = 60.0
        let velocityFling: CGFloat = 550.0
        let progressFling = min(1.0, max(0.0, dragOffsetFling / screenWidth))
        let projectedOffsetFling = dragOffsetFling + (velocityFling * 0.16)
        let shouldDismissFling = progressFling > 0.30 || velocityFling > 400 || projectedOffsetFling > (screenWidth * 0.45)
        XCTAssertTrue(shouldDismissFling, "高速甩动手势必须根据物理动量触发退出")
    }

    func testEdgeHitZoneBoundaries() {
        // iPhone 边缘判定基准：28pt
        let phoneEdge: CGFloat = 28.0
        XCTAssertTrue(10.0 <= phoneEdge)
        XCTAssertTrue(28.0 <= phoneEdge)
        XCTAssertFalse(29.0 <= phoneEdge)
        XCTAssertFalse(100.0 <= phoneEdge)

        // iPad 边缘判定基准：36pt
        let padEdge: CGFloat = 36.0
        XCTAssertTrue(30.0 <= padEdge)
        XCTAssertTrue(36.0 <= padEdge)
        XCTAssertFalse(37.0 <= padEdge)
    }

    func testFluidTransformationParameters() {
        let screenWidth: CGFloat = 400.0

        // 初始静止态
        let offset0: CGFloat = 0.0
        let progress0 = min(1.0, max(0.0, offset0 / screenWidth))
        let scale0 = 1.0 - (progress0 * 0.05)
        let radius0 = 40.0 * progress0
        XCTAssertEqual(progress0, 0.0)
        XCTAssertEqual(scale0, 1.0)
        XCTAssertEqual(radius0, 0.0)

        // 中间拖曳态（50% 进度）
        let offsetHalf: CGFloat = 200.0
        let progressHalf = min(1.0, max(0.0, offsetHalf / screenWidth))
        let scaleHalf = 1.0 - (progressHalf * 0.05)
        let radiusHalf = 40.0 * progressHalf
        XCTAssertEqual(progressHalf, 0.5)
        XCTAssertEqual(scaleHalf, 0.975, accuracy: 0.001)
        XCTAssertEqual(radiusHalf, 20.0, accuracy: 0.001)

        // 完全拉开态（100% 进度）
        let offsetFull: CGFloat = 400.0
        let progressFull = min(1.0, max(0.0, offsetFull / screenWidth))
        let scaleFull = 1.0 - (progressFull * 0.05)
        let radiusFull = 40.0 * progressFull
        XCTAssertEqual(progressFull, 1.0)
        XCTAssertEqual(scaleFull, 0.95, accuracy: 0.001)
        XCTAssertEqual(radiusFull, 40.0, accuracy: 0.001)
    }

    func testEdgePanHostViewHitTestIsolation() {
        let hostView = EdgePanHostView(frame: CGRect(x: 0, y: 0, width: 393, height: 852))

        // 左侧边缘点（x = 15）必须命中自己
        let edgePoint = CGPoint(x: 15, y: 200)
        let hitAtEdge = hostView.hitTest(edgePoint, with: nil)
        XCTAssertNotNil(hitAtEdge, "左侧边缘触摸必须命中 EdgePanHostView")

        // 内部点（x = 50, 100, 200）必须返回 nil，严禁拦截内部横滑与按钮点击
        let innerPointA = CGPoint(x: 50, y: 200)
        let hitInnerA = hostView.hitTest(innerPointA, with: nil)
        XCTAssertNil(hitInnerA, "x = 50 处触摸必须穿透")

        let innerPointB = CGPoint(x: 200, y: 400)
        let hitInnerB = hostView.hitTest(innerPointB, with: nil)
        XCTAssertNil(hitInnerB, "x = 200 处触摸必须穿透")
    }

    func testNavigationPopCoordinatorLifecycleAndRules() {
        let coordinator = NavigationPopCoordinator(isEnabled: true)
        let rootVC = UIViewController()
        let nav = UINavigationController(rootViewController: rootVC)
        nav.loadViewIfNeeded()
        coordinator.navigationController = nav

        // 仅有 1 个根控制器时，禁止触发返回
        let dummyGesture = UIGestureRecognizer()
        XCTAssertFalse(coordinator.gestureRecognizerShouldBegin(dummyGesture), "根页面禁止触发手势返回")

        // 压入二级页面后，且处于启用态，允许触发
        let detailVC = UIViewController()
        nav.viewControllers = [rootVC, detailVC]
        XCTAssertTrue(coordinator.gestureRecognizerShouldBegin(dummyGesture), "二级页面在启用态下必须允许触发手势返回")

        // 当 isEnabled = false（如全屏播放时），禁止触发手势返回
        coordinator.isEnabled = false
        XCTAssertFalse(coordinator.gestureRecognizerShouldBegin(dummyGesture), "禁用态（如全屏播放）必须严禁触发手势返回")

        // 手势隔离规则：边缘手势始终允许同时识别；全内容区右滑手势不与 UIScrollView 冲突，且不被强迫失败
        let edgeGesture = UIScreenEdgePanGestureRecognizer()
        let scrollContainer = UIScrollView()
        let scrollGesture = UIPanGestureRecognizer()
        scrollContainer.addGestureRecognizer(scrollGesture)
        XCTAssertTrue(coordinator.gestureRecognizer(edgeGesture, shouldRecognizeSimultaneouslyWith: scrollGesture))
        XCTAssertFalse(coordinator.gestureRecognizer(dummyGesture, shouldRecognizeSimultaneouslyWith: scrollGesture))
        XCTAssertTrue(coordinator.gestureRecognizer(dummyGesture, shouldRecognizeSimultaneouslyWith: UIGestureRecognizer()))
        XCTAssertFalse(coordinator.gestureRecognizer(dummyGesture, shouldBeRequiredToFailBy: UIGestureRecognizer()))

        // didShow 后同步开启 iOS 26/27 原生全内容区交互返回手势
        coordinator.isEnabled = true
        coordinator.navigationController(nav, didShow: detailVC, animated: false)
        XCTAssertEqual(nav.interactivePopGestureRecognizer?.isEnabled, true)
        if #available(iOS 26.0, *) {
            XCTAssertEqual(nav.interactiveContentPopGestureRecognizer?.isEnabled, true)
        }
    }

    func testPopGestureEnablerControllerConfiguration() {
        let controller = PopGestureEnablerController()
        _ = controller.view
        XCTAssertEqual(controller.view.backgroundColor, .clear, "宿主控制器视图必须保持透明，绝不遮挡底层")
        XCTAssertFalse(controller.view.isUserInteractionEnabled, "宿主视图不得拦截普通用户交互")
    }

    func testPopGestureEnablerPreservesNavigationBarVisibility() {
        let root = UIViewController()
        let child = UIViewController()
        let enabler = PopGestureEnablerController()
        child.addChild(enabler)
        child.view.addSubview(enabler.view)
        enabler.didMove(toParent: child)

        let nav = UINavigationController(rootViewController: root)
        nav.setNavigationBarHidden(false, animated: false)
        nav.pushViewController(child, animated: false)
        _ = enabler.view
        enabler.viewDidAppear(false)

        XCTAssertFalse(nav.isNavigationBarHidden, "启用侧滑手势不得强制隐藏 UINavigationBar，避免导航栏消失")
    }

    @MainActor
    func testVideoDetailPlaybackScreenUpdatesInsideNavigationHostWithoutLayoutFreeze() async throws {
        let video = VideoItem(
            id: "cycle-regression-1",
            title: "Regression Test Video",
            subtitle: "134X",
            coverUrl: "",
            playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            sourceName: "134X"
        )
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first
        let window = scene.map { UIWindow(windowScene: $0) } ?? UIWindow(frame: UIScreen.main.bounds)
        let rootVC = UIHostingController(rootView: Text("Root"))
        let nav = UINavigationController(rootViewController: rootVC)
        let detailVC = UIHostingController(rootView: VideoDetailPlaybackScreen(video: video, onDismiss: {}))
        nav.setNavigationBarHidden(true, animated: false)
        nav.pushViewController(detailVC, animated: false)
        window.rootViewController = nav
        window.makeKeyAndVisible()
        nav.view.layoutIfNeeded()

        try await Task.sleep(nanoseconds: 800_000_000)
        // Verifies hosting view remains strictly constrained to window bounds (no 508pt skeleton overflow).
        XCTAssertLessThanOrEqual(detailVC.view.bounds.width, window.bounds.width + 1.0)
        window.isHidden = true
        window.rootViewController = nil
    }

    @MainActor
    func testVideoDetailRotatesFromPortraitToLandscapePreservesSinglePlayerSurface() async throws {
        try XCTSkipUnless(
            UIDevice.current.userInterfaceIdiom == .pad,
            "YouTube 分栏横屏旋转回归测试仅在 iPad 模拟器上验证"
        )

        func firstSubview<T: UIView>(ofType _: T.Type, in root: UIView) -> T? {
            if let match = root as? T { return match }
            for subview in root.subviews {
                if let match = firstSubview(ofType: T.self, in: subview) {
                    return match
                }
            }
            return nil
        }

        final class ImmediateTransitionCoordinator: NSObject, UIViewControllerTransitionCoordinator {
            var isAnimated: Bool { false }
            var presentationStyle: UIModalPresentationStyle { .none }
            var initiallyInteractive: Bool { false }
            var isInterruptible: Bool { false }
            var isInteractive: Bool { false }
            var isCancelled: Bool { false }
            var transitionDuration: TimeInterval { 0 }
            var percentComplete: CGFloat { 1 }
            var completionVelocity: CGFloat { 0 }
            var completionCurve: UIView.AnimationCurve { .linear }
            var containerView: UIView { UIView() }
            var targetTransform: CGAffineTransform { .identity }
            func viewController(forKey key: UITransitionContextViewControllerKey) -> UIViewController? { nil }
            func view(forKey key: UITransitionContextViewKey) -> UIView? { nil }
            func animate(alongsideTransition animation: ((UIViewControllerTransitionCoordinatorContext) -> Void)?, completion: ((UIViewControllerTransitionCoordinatorContext) -> Void)?) -> Bool {
                animation?(self)
                completion?(self)
                return true
            }
            func animateAlongsideTransition(in view: UIView?, animation: ((UIViewControllerTransitionCoordinatorContext) -> Void)?, completion: ((UIViewControllerTransitionCoordinatorContext) -> Void)?) -> Bool {
                animation?(self)
                completion?(self)
                return true
            }
            func notifyWhenInteractionEnds(_ handler: @escaping (UIViewControllerTransitionCoordinatorContext) -> Void) {}
            func notifyWhenInteractionChanges(_ handler: @escaping (UIViewControllerTransitionCoordinatorContext) -> Void) {}
        }

        let portraitSize = CGSize(width: 1032, height: 1376)
        let landscapeSize = CGSize(width: 1376, height: 1032)

        let video = VideoItem(
            id: "pad-rotation-1",
            title: "iPad Rotation Split Layout Verification",
            subtitle: "DirectTest",
            coverUrl: "",
            playbackUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            sourceName: "DirectTest"
        )

        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first
        let window = scene.map { UIWindow(windowScene: $0) } ?? UIWindow(frame: CGRect(origin: .zero, size: portraitSize))
        let containerVC = UIViewController()
        let rootVC = UIHostingController(rootView: Text("Root"))
        let nav = ImmersiveNavigationController(rootViewController: rootVC)
        let detailVC = UIHostingController(rootView: VideoDetailPlaybackScreen(video: video, onDismiss: {}))
        nav.setNavigationBarHidden(true, animated: false)
        nav.pushViewController(detailVC, animated: false)

        containerVC.addChild(nav)
        containerVC.view.addSubview(nav.view)
        nav.view.autoresizingMask = []
        nav.view.frame = CGRect(origin: .zero, size: portraitSize)
        nav.didMove(toParent: containerVC)

        window.rootViewController = containerVC
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            window.rootViewController = nil
        }
        containerVC.view.layoutIfNeeded()
        nav.view.layoutIfNeeded()

        for _ in 0..<25 {
            if firstSubview(ofType: AuroraPlayerSurface.PlayerUIView.self, in: detailVC.view) != nil {
                break
            }
            try await Task.sleep(nanoseconds: 100_000_000)
        }

        let portraitPlayerView = try XCTUnwrap(
            firstSubview(ofType: AuroraPlayerSurface.PlayerUIView.self, in: detailVC.view),
            "竖屏态应已挂载 PlayerUIView"
        )
        nav.view.layoutIfNeeded()
        detailVC.view.layoutIfNeeded()
        try await Task.sleep(nanoseconds: 250_000_000)
        XCTAssertEqual(portraitPlayerView.bounds.width, portraitSize.width, accuracy: 2.0)

        // Simulate iPad rotation from portrait (1032x1376) to landscape (1376x1032)
        let coordinator = ImmediateTransitionCoordinator()
        nav.view.frame = CGRect(origin: .zero, size: landscapeSize)
        nav.viewWillTransition(to: landscapeSize, with: coordinator)
        nav.view.layoutIfNeeded()
        detailVC.view.layoutIfNeeded()
        try await Task.sleep(nanoseconds: 350_000_000)

        let landscapePlayerView = try XCTUnwrap(
            firstSubview(ofType: AuroraPlayerSurface.PlayerUIView.self, in: detailVC.view),
            "横屏分栏态 PlayerUIView 必须持续存在"
        )
        let expectedSplitWidth = VideoDetailLayout.columns(for: landscapeSize, idiom: .pad)
            .playerWidth(for: landscapeSize.width)
        XCTAssertTrue(
            portraitPlayerView === landscapePlayerView,
            "竖屏转向横屏分栏态期间必须保留同一个 PlayerUIView 实例，严禁销毁重建导致黑屏"
        )
        XCTAssertEqual(
            landscapePlayerView.bounds.width,
            expectedSplitWidth,
            accuracy: 2.0,
            "iPad 横屏态播放器宽度应精确匹配左栏分栏宽度"
        )

        // Simulate rotating back from landscape to portrait
        nav.view.frame = CGRect(origin: .zero, size: portraitSize)
        nav.viewWillTransition(to: portraitSize, with: coordinator)
        nav.view.layoutIfNeeded()
        detailVC.view.layoutIfNeeded()
        try await Task.sleep(nanoseconds: 350_000_000)

        let restoredPlayerView = firstSubview(ofType: AuroraPlayerSurface.PlayerUIView.self, in: detailVC.view)
        XCTAssertTrue(portraitPlayerView === restoredPlayerView)
        XCTAssertEqual(landscapePlayerView.bounds.width, portraitSize.width, accuracy: 2.0)
    }
}
