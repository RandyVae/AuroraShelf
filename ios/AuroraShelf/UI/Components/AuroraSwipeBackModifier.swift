import SwiftUI
import UIKit

/// iOS 原生流体边缘右滑返回手势修饰符
/// 严格遵循 Apple Human Interface Guidelines（HIG）及 iOS 原生交互式转场规范：
/// 1. 深度层级（Parallax Depth）：滑动时底层上一层界面同步以 1/3 视差平滑滑入，伴随原生微渐变遮罩自然显露，彻底根绝黑屏过渡；
/// 2. 原生引擎（UIKit Interactive Pop）：交由 UINavigationController.interactivePopGestureRecognizer 原生手势转场引擎驱动，100% 系统级丝滑跟手与物理弹性；
/// 3. 全态生命周期守护：全屏播放与交互态自适应启闭，退出时无缝协同，杜绝手势穿透与层级割裂。
public struct AuroraSwipeBackModifier: ViewModifier {
    public let isEnabled: Bool
    public let onDismiss: () -> Void
    @Environment(\.dismiss) private var dismiss

    public init(isEnabled: Bool = true, onDismiss: @escaping () -> Void = {}) {
        self.isEnabled = isEnabled
        self.onDismiss = onDismiss
    }

    public func body(content: Content) -> some View {
        // 交由 iOS 26/27 SDK 原生 UINavigationController 交互式转场接管：
        // 同时启用 interactivePopGestureRecognizer（边缘侧滑）与 interactiveContentPopGestureRecognizer（全内容区右滑），
        // 消除生硬的瞬间 dismiss() 跳切。
        content
            .background(NavigationPopGestureEnabler(isEnabled: isEnabled))
    }
}

/// iOS 原生流体手势容器（兼具无导航控制器时的平滑过渡能力）
public struct SwipeBackContainer<Content: View>: View {
    public let isEnabled: Bool
    public let onDismiss: () -> Void
    @ViewBuilder public let content: () -> Content

    @State private var dragOffset: CGFloat = 0
    @State private var isDragging: Bool = false
    @State private var hasTriggeredThresholdHaptic: Bool = false

    public init(
        isEnabled: Bool = true,
        onDismiss: @escaping () -> Void = {},
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.isEnabled = isEnabled
        self.onDismiss = onDismiss
        self.content = content
    }

    public var body: some View {
        GeometryReader { geo in
            let screenWidth = geo.size.width
            let isPad = UIDevice.current.userInterfaceIdiom == .pad
            let edgeHitZoneWidth: CGFloat = isPad ? 36 : 28
            let progress = min(1.0, max(0.0, dragOffset / max(screenWidth, 1)))

            // iOS HIG 原生流体微动参数
            let cardScale = isDragging ? (1.0 - (progress * (isPad ? 0.03 : 0.05))) : 1.0
            let cornerRadius: CGFloat = isDragging ? (40.0 * progress) : 0

            ZStack(alignment: .leading) {
                // 1. 底层透明过渡（严禁纯黑遮罩破坏上一层页面透视）
                if isDragging && dragOffset > 0 {
                    Color.clear
                        .ignoresSafeArea()
                        .allowsHitTesting(false)
                }

                // 2. 主内容卡片：未拖拽时绝不加任何 clipShape、scale 或 offset，100% 原始沉浸式直通渲染
                if isDragging && dragOffset > 0 {
                    content()
                        .offset(x: max(0, dragOffset))
                        .scaleEffect(cardScale, anchor: .center)
                        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
                        .shadow(
                            color: Color.black.opacity(Double(0.25 * (1.0 - progress))),
                            radius: 20,
                            x: -6,
                            y: 0
                        )
                } else {
                    content()
                }

                // 3. Apple 液态玻璃返回指示胶囊（Liquid Glass Indicator）
                if isDragging && dragOffset > 8 {
                    HStack {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(.white)
                        }
                        .frame(width: 32, height: 32)
                        .background(.ultraThinMaterial, in: Circle())
                        .overlay(
                            Circle()
                                .stroke(Color.white.opacity(0.22), lineWidth: 0.5)
                        )
                        .shadow(color: Color.black.opacity(0.2), radius: 8, x: 0, y: 2)
                        .offset(x: max(14, min(dragOffset * 0.22, 38)))
                        .opacity(min(1.0, Double(progress * 3.5)))

                        Spacer()
                    }
                    .allowsHitTesting(false)
                    .zIndex(999)
                }

                // 4. UIKit 高保真屏幕边缘手势层（仅覆盖左侧 28pt/36pt）
                if isEnabled {
                    ScreenEdgePanRecognizer(
                        isEnabled: isEnabled,
                        onBegan: {
                            isDragging = true
                            hasTriggeredThresholdHaptic = false
                        },
                        onChanged: { translationX, _ in
                            isDragging = true
                            dragOffset = max(0, translationX)
                            checkHapticThreshold(progress: min(1.0, max(0.0, dragOffset / max(screenWidth, 1))))
                        },
                        onEnded: { translationX, velocityX in
                            handleGestureEnd(screenWidth: screenWidth, translationX: translationX, velocityX: velocityX)
                        },
                        onCancelled: {
                            handleGestureCancel()
                        }
                    )
                    .frame(width: edgeHitZoneWidth)
                    .frame(maxHeight: .infinity)
                    .ignoresSafeArea()
                }
            }
            .contentShape(Rectangle())
            // 5. SwiftUI 边缘轻扫手势兜底（同样严格限制在 startLocation.x <= edgeHitZoneWidth）
            .simultaneousGesture(
                isEnabled ? edgeFallbackGesture(screenWidth: screenWidth, edgeHitZone: edgeHitZoneWidth) : nil
            )
        }
        .ignoresSafeArea()
        .background(Color.clear.ignoresSafeArea())
    }

    // MARK: - 触觉与手势物理逻辑

    private func checkHapticThreshold(progress: CGFloat) {
        if progress >= 0.28 && !hasTriggeredThresholdHaptic {
            hasTriggeredThresholdHaptic = true
            let impact = UIImpactFeedbackGenerator(style: .soft)
            impact.impactOccurred()
        } else if progress < 0.22 && hasTriggeredThresholdHaptic {
            hasTriggeredThresholdHaptic = false
        }
    }

    private func handleGestureEnd(screenWidth: CGFloat, translationX: CGFloat, velocityX: CGFloat) {
        guard isDragging else { return }
        let progress = min(1.0, max(0.0, translationX / max(screenWidth, 1)))
        let projectedOffset = translationX + (velocityX * 0.16)
        let shouldDismiss = progress > 0.30 || velocityX > 400 || projectedOffset > (screenWidth * 0.45)

        if shouldDismiss {
            let impact = UIImpactFeedbackGenerator(style: .light)
            impact.impactOccurred()

            // 依据 Apple Fluid Spring 物理特性滑出屏幕边界
            withAnimation(.spring(response: 0.32, dampingFraction: 0.88)) {
                dragOffset = screenWidth + 40
            } completion: {
                isDragging = false
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    onDismiss()
                }
            }
        } else {
            // 未达到阈值，以 Apple 自然弹性回弹归位
            withAnimation(.spring(response: 0.30, dampingFraction: 0.82)) {
                dragOffset = 0
            } completion: {
                isDragging = false
            }
        }
    }

    private func handleGestureCancel() {
        withAnimation(.spring(response: 0.28, dampingFraction: 0.85)) {
            dragOffset = 0
        } completion: {
            isDragging = false
        }
    }

    private func edgeFallbackGesture(screenWidth: CGFloat, edgeHitZone: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 10, coordinateSpace: .local)
            .onChanged { value in
                // 仅响应从最左侧边缘（x <= edgeHitZone）发起的水平右滑意图
                guard value.startLocation.x <= edgeHitZone else { return }
                guard value.translation.width > 0 && abs(value.translation.width) > abs(value.translation.height) * 1.25 else { return }

                if !isDragging {
                    isDragging = true
                    hasTriggeredThresholdHaptic = false
                }
                dragOffset = value.translation.width
                checkHapticThreshold(progress: min(1.0, max(0.0, dragOffset / max(screenWidth, 1))))
            }
            .onEnded { value in
                guard isDragging else { return }
                let velocityX = value.predictedEndTranslation.width - value.translation.width
                handleGestureEnd(screenWidth: screenWidth, translationX: value.translation.width, velocityX: velocityX)
            }
    }
}

// MARK: - UIKit 高保真屏幕边缘手势桥接器

public struct ScreenEdgePanRecognizer: UIViewRepresentable {
    public let isEnabled: Bool
    public let onBegan: () -> Void
    public let onChanged: (_ translationX: CGFloat, _ velocityX: CGFloat) -> Void
    public let onEnded: (_ translationX: CGFloat, _ velocityX: CGFloat) -> Void
    public let onCancelled: () -> Void

    public init(
        isEnabled: Bool = true,
        onBegan: @escaping () -> Void = {},
        onChanged: @escaping (CGFloat, CGFloat) -> Void = { _, _ in },
        onEnded: @escaping (CGFloat, CGFloat) -> Void = { _, _ in },
        onCancelled: @escaping () -> Void = {}
    ) {
        self.isEnabled = isEnabled
        self.onBegan = onBegan
        self.onChanged = onChanged
        self.onEnded = onEnded
        self.onCancelled = onCancelled
    }

    public func makeUIView(context: Context) -> EdgePanHostView {
        let view = EdgePanHostView()
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = isEnabled

        let edgePan = UIScreenEdgePanGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handlePan(_:))
        )
        edgePan.edges = .left
        edgePan.delegate = context.coordinator
        view.addGestureRecognizer(edgePan)
        context.coordinator.gesture = edgePan

        return view
    }

    public func updateUIView(_ uiView: EdgePanHostView, context: Context) {
        uiView.isUserInteractionEnabled = isEnabled
        context.coordinator.parent = self
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    public final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: ScreenEdgePanRecognizer
        weak var gesture: UIScreenEdgePanGestureRecognizer?

        init(_ parent: ScreenEdgePanRecognizer) {
            self.parent = parent
        }

        @objc func handlePan(_ recognizer: UIScreenEdgePanGestureRecognizer) {
            guard let view = recognizer.view else { return }
            let translation = recognizer.translation(in: view)
            let velocity = recognizer.velocity(in: view)

            switch recognizer.state {
            case .began:
                parent.onBegan()
                parent.onChanged(max(0, translation.x), velocity.x)
            case .changed:
                parent.onChanged(max(0, translation.x), velocity.x)
            case .ended:
                parent.onEnded(max(0, translation.x), velocity.x)
            case .cancelled, .failed:
                parent.onCancelled()
            default:
                break
            }
        }

        public func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            return false
        }
    }
}

public final class EdgePanHostView: UIView {
    public override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let maxEdge: CGFloat = UIDevice.current.userInterfaceIdiom == .pad ? 36 : 28
        guard point.x <= maxEdge else {
            return nil
        }

        // 若处于 NavigationController 二级页面，让位给系统原生交互式返回
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

    public override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
    }

    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if let nav = findNavigationController() {
            nav.setNavigationBarHidden(true, animated: animated)
        }
        attachPopGesture()
    }

    public override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if let nav = findNavigationController() {
            nav.setNavigationBarHidden(true, animated: false)
        }
        attachPopGesture()
    }

    public override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if let nav = findNavigationController(), nav.viewControllers.count <= 2 {
            nav.setNavigationBarHidden(false, animated: animated)
        }
    }

    public override func didMove(toParent parent: UIViewController?) {
        super.didMove(toParent: parent)
        if let nav = findNavigationController() {
            nav.setNavigationBarHidden(true, animated: false)
        }
        attachPopGesture()
    }

    func updatePopState(isEnabled: Bool) {
        self.isEnabled = isEnabled
        coordinator?.isEnabled = isEnabled
        attachPopGesture()
    }

    private func attachPopGesture() {
        guard let nav = findNavigationController() else { return }
        coordinator?.navigationController = nav
        nav.delegate = coordinator
        nav.setNavigationBarHidden(true, animated: false)
        nav.interactivePopGestureRecognizer?.isEnabled = isEnabled
        nav.interactivePopGestureRecognizer?.delegate = isEnabled ? coordinator : nil
        if #available(iOS 26.0, *) {
            nav.interactiveContentPopGestureRecognizer?.isEnabled = isEnabled
            nav.interactiveContentPopGestureRecognizer?.delegate = isEnabled ? coordinator : nil
        }
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
            // iPadOS: 优先检查 secondary 详情与主视口导航控制器
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

    public init(isEnabled: Bool = true) {
        self.isEnabled = isEnabled
    }

    public func makeUIViewController(context: Context) -> PopGestureEnablerController {
        let controller = PopGestureEnablerController()
        controller.coordinator = context.coordinator
        controller.isEnabled = isEnabled
        return controller
    }

    public func updateUIViewController(_ uiViewController: PopGestureEnablerController, context: Context) {
        uiViewController.coordinator = context.coordinator
        uiViewController.updatePopState(isEnabled: isEnabled)
    }

    public func makeCoordinator() -> NavigationPopCoordinator {
        NavigationPopCoordinator(isEnabled: isEnabled)
    }
}

public final class NavigationPopCoordinator: NSObject, UIGestureRecognizerDelegate, UINavigationControllerDelegate {
    public var isEnabled: Bool
    public weak var navigationController: UINavigationController?

    public init(isEnabled: Bool) {
        self.isEnabled = isEnabled
    }

    public func navigationController(
        _ navigationController: UINavigationController,
        didShow viewController: UIViewController,
        animated: Bool
    ) {
        if navigationController.viewControllers.count > 1 {
            navigationController.setNavigationBarHidden(true, animated: false)
            navigationController.interactivePopGestureRecognizer?.isEnabled = isEnabled
            navigationController.interactivePopGestureRecognizer?.delegate = isEnabled ? self : nil
            if #available(iOS 26.0, *) {
                navigationController.interactiveContentPopGestureRecognizer?.isEnabled = isEnabled
                navigationController.interactiveContentPopGestureRecognizer?.delegate = isEnabled ? self : nil
            }
        }
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard isEnabled else { return false }
        guard let nav = navigationController, nav.viewControllers.count > 1 else {
            return false
        }
        if let pan = gestureRecognizer as? UIPanGestureRecognizer, let view = pan.view {
            let velocity = pan.velocity(in: view)
            // 非边缘全内容区右滑需要明确正向水平意图，防止垂直滚动或反向滑动误触发
            if !(gestureRecognizer is UIScreenEdgePanGestureRecognizer) {
                if velocity.x <= 0 || abs(velocity.x) <= abs(velocity.y) * 1.15 {
                    return false
                }
            } else if velocity.x < -100 {
                return false
            }
        }
        return true
    }

    public func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        // 边缘返回手势允许与下层手势共存；iOS 26/27 全内容区返回不与横向滚动冲突
        if gestureRecognizer is UIScreenEdgePanGestureRecognizer {
            return true
        }
        return !(otherGestureRecognizer.view is UIScrollView)
    }

    public func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        // 原生返回手势优先，严禁被其他内容手势阻断强迫失败
        return false
    }
}

// MARK: - View 扩展便利入口

extension View {
    /// 为全屏模态、详情页或二级页面挂载 iOS 原生流体右滑返回手势
    public func enableSwipeBack(isEnabled: Bool = true, onDismiss: @escaping () -> Void = {}) -> some View {
        modifier(AuroraSwipeBackModifier(isEnabled: isEnabled, onDismiss: onDismiss))
    }
}
