import SwiftUI
import UIKit

/// iOS 原生流体边缘右滑返回手势修饰符
/// 严格遵循 Apple Human Interface Guidelines（HIG）及 iOS 原生交互式转场规范：
/// 1. 深度层级（Parallax Depth）：由 `UINavigationController.interactivePopGestureRecognizer` 原生手势转场引擎驱动；
/// 2. 手势防误触隔离：对带水平进度条（视频播放页）或全屏手势交互（WebGL 游戏页）支持关闭 `interactiveContentPopGestureRecognizer`，仅保留屏幕左边缘侧滑（或按需完全禁用）。
public struct AuroraSwipeBackModifier: ViewModifier {
    public let isEnabled: Bool
    public let allowsContentSwipe: Bool
    public let onDismiss: () -> Void

    public init(
        isEnabled: Bool = true,
        allowsContentSwipe: Bool = true,
        onDismiss: @escaping () -> Void = {}
    ) {
        self.isEnabled = isEnabled
        self.allowsContentSwipe = allowsContentSwipe
        self.onDismiss = onDismiss
    }

    public func body(content: Content) -> some View {
        content
            .background(NavigationPopGestureEnabler(isEnabled: isEnabled, allowsContentSwipe: allowsContentSwipe))
    }
}

public final class EdgePanHostView: UIView {
    public override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let maxEdge: CGFloat = UIDevice.current.userInterfaceIdiom == .pad ? 36 : 28
        guard point.x <= maxEdge else {
            return nil
        }

        if let nav = parentViewController?.navigationController, nav.viewControllers.count > 1 {
            nav.interactivePopGestureRecognizer?.isEnabled = true
            return nil
        }

        return self
    }
}

private extension UIView {
    var parentViewController: UIViewController? {
        var responder: UIResponder? = self
        while let r = responder {
            if let vc = r as? UIViewController {
                return vc
            }
            responder = r.next
        }
        return nil
    }
}

// MARK: - NavigationStack 原生手势激活器

public final class PopGestureEnablerController: UIViewController {
    weak var coordinator: NavigationPopCoordinator?
    var isEnabled: Bool = true
    var allowsContentSwipe: Bool = true
    private weak var attachedNavigationController: UINavigationController?
    private weak var previousPopDelegate: UIGestureRecognizerDelegate?
    private var previousPopEnabled: Bool?
    private weak var previousContentPopDelegate: UIGestureRecognizerDelegate?
    private var previousContentPopEnabled: Bool?

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
    }

    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        attachPopGesture()
    }

    public override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        attachPopGesture()
    }

    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        restorePopGesture()
    }

    public override func didMove(toParent parent: UIViewController?) {
        super.didMove(toParent: parent)
        if parent == nil {
            restorePopGesture()
        } else {
            attachPopGesture()
        }
    }

    func updatePopState(isEnabled: Bool, allowsContentSwipe: Bool = true) {
        self.isEnabled = isEnabled
        self.allowsContentSwipe = allowsContentSwipe
        coordinator?.isEnabled = isEnabled
        coordinator?.allowsContentSwipe = allowsContentSwipe
        attachPopGesture()
    }

    private func attachPopGesture() {
        guard let nav = findNavigationController(), nav.viewControllers.count > 1 else {
            restorePopGesture()
            return
        }
        if attachedNavigationController !== nav {
            restorePopGesture()
            attachedNavigationController = nav
            previousPopDelegate = nav.interactivePopGestureRecognizer?.delegate
            previousPopEnabled = nav.interactivePopGestureRecognizer?.isEnabled
            if #available(iOS 26.0, *) {
                previousContentPopDelegate = nav.interactiveContentPopGestureRecognizer?.delegate
                previousContentPopEnabled = nav.interactiveContentPopGestureRecognizer?.isEnabled
            }
        }
        coordinator?.navigationController = nav
        nav.interactivePopGestureRecognizer?.isEnabled = isEnabled
        nav.interactivePopGestureRecognizer?.delegate = isEnabled ? coordinator : previousPopDelegate
        if #available(iOS 26.0, *) {
            let enableContentPop = isEnabled && allowsContentSwipe
            nav.interactiveContentPopGestureRecognizer?.isEnabled = enableContentPop
            nav.interactiveContentPopGestureRecognizer?.delegate = enableContentPop ? coordinator : previousContentPopDelegate
        }
    }

    private func restorePopGesture() {
        guard let nav = attachedNavigationController else { return }
        if let previousPopEnabled {
            nav.interactivePopGestureRecognizer?.isEnabled = previousPopEnabled
        }
        nav.interactivePopGestureRecognizer?.delegate = previousPopDelegate
        if #available(iOS 26.0, *) {
            if let previousContentPopEnabled {
                nav.interactiveContentPopGestureRecognizer?.isEnabled = previousContentPopEnabled
            }
            nav.interactiveContentPopGestureRecognizer?.delegate = previousContentPopDelegate
        }
        attachedNavigationController = nil
        previousPopDelegate = nil
        previousPopEnabled = nil
        previousContentPopDelegate = nil
        previousContentPopEnabled = nil
    }

    private func findNavigationController() -> UINavigationController? {
        if let nav = self.navigationController { return nav }
        var current: UIViewController? = self.parent
        while let p = current {
            if let nav = p.navigationController { return nav }
            if let nav = p as? UINavigationController { return nav }
            current = p.parent
        }
        if let window = view.window ?? UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow }),
           let root = window.rootViewController {
            return findNavigation(in: root)
        }
        return nil
    }

    private func findNavigation(in vc: UIViewController) -> UINavigationController? {
        if let nav = vc as? UINavigationController {
            if nav.viewControllers.count > 1 { return nav }
            for child in nav.children.reversed() {
                if let childNav = findNavigation(in: child), childNav.viewControllers.count > 1 {
                    return childNav
                }
            }
            return nav
        }
        if let tab = vc as? UITabBarController, let selected = tab.selectedViewController {
            if let nav = findNavigation(in: selected) { return nav }
        }
        if let split = vc as? UISplitViewController {
            if let secondary = split.viewController(for: .secondary),
               let nav = findNavigation(in: secondary) {
                return nav
            }
            if let compact = split.viewController(for: .compact),
               let nav = findNavigation(in: compact) {
                return nav
            }
            for sub in split.viewControllers.reversed() {
                if let nav = findNavigation(in: sub) { return nav }
            }
        }
        for child in vc.children.reversed() {
            if let nav = findNavigation(in: child) { return nav }
        }
        return nil
    }
}

public struct NavigationPopGestureEnabler: UIViewControllerRepresentable {
    public let isEnabled: Bool
    public let allowsContentSwipe: Bool

    public init(isEnabled: Bool = true, allowsContentSwipe: Bool = true) {
        self.isEnabled = isEnabled
        self.allowsContentSwipe = allowsContentSwipe
    }

    public func makeUIViewController(context: Context) -> PopGestureEnablerController {
        let controller = PopGestureEnablerController()
        controller.coordinator = context.coordinator
        controller.isEnabled = isEnabled
        controller.allowsContentSwipe = allowsContentSwipe
        return controller
    }

    public func updateUIViewController(_ uiViewController: PopGestureEnablerController, context: Context) {
        uiViewController.coordinator = context.coordinator
        uiViewController.updatePopState(isEnabled: isEnabled, allowsContentSwipe: allowsContentSwipe)
    }

    public func makeCoordinator() -> NavigationPopCoordinator {
        NavigationPopCoordinator(isEnabled: isEnabled, allowsContentSwipe: allowsContentSwipe)
    }
}

public final class NavigationPopCoordinator: NSObject, UIGestureRecognizerDelegate, UINavigationControllerDelegate {
    public var isEnabled: Bool
    public var allowsContentSwipe: Bool
    public weak var navigationController: UINavigationController?

    public init(isEnabled: Bool, allowsContentSwipe: Bool = true) {
        self.isEnabled = isEnabled
        self.allowsContentSwipe = allowsContentSwipe
    }

    public func navigationController(
        _ navigationController: UINavigationController,
        didShow viewController: UIViewController,
        animated: Bool
    ) {
        if navigationController.viewControllers.count > 1 {
            navigationController.interactivePopGestureRecognizer?.isEnabled = isEnabled
            navigationController.interactivePopGestureRecognizer?.delegate = isEnabled ? self : nil
            if #available(iOS 26.0, *) {
                let enableContentPop = isEnabled && allowsContentSwipe
                navigationController.interactiveContentPopGestureRecognizer?.isEnabled = enableContentPop
                navigationController.interactiveContentPopGestureRecognizer?.delegate = enableContentPop ? self : nil
            }
        }
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard isEnabled else { return false }
        guard let nav = navigationController, nav.viewControllers.count > 1 else {
            return false
        }
        if let pan = gestureRecognizer as? UIPanGestureRecognizer, let view = pan.view {
            if !(gestureRecognizer is UIScreenEdgePanGestureRecognizer) {
                guard allowsContentSwipe else { return false }
                let velocity = pan.velocity(in: view)
                if velocity.x <= 0 || abs(velocity.x) <= abs(velocity.y) * 1.15 {
                    return false
                }
            } else {
                let velocity = pan.velocity(in: view)
                if velocity.x < -100 {
                    return false
                }
            }
        }
        return true
    }

    public func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        if gestureRecognizer is UIScreenEdgePanGestureRecognizer {
            return true
        }
        return !(otherGestureRecognizer.view is UIScrollView)
    }

    public func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        false
    }
}

// MARK: - View 扩展便利入口

extension View {
    /// 为全屏模态、详情页或二级页面挂载 iOS 原生流体右滑返回手势
    public func enableSwipeBack(
        isEnabled: Bool = true,
        allowsContentSwipe: Bool = true,
        onDismiss: @escaping () -> Void = {}
    ) -> some View {
        modifier(AuroraSwipeBackModifier(
            isEnabled: isEnabled,
            allowsContentSwipe: allowsContentSwipe,
            onDismiss: onDismiss
        ))
    }
}
