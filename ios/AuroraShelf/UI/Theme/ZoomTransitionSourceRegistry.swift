import SwiftUI
import UIKit

/// iOS 18–27 原生空间缩放导航转场源视图注册中心（供 UIKit `ImmersiveNavigationHost` `preferredTransition = .zoom` 查询）
@MainActor
public final class ZoomTransitionSourceRegistry {
    public static let shared = ZoomTransitionSourceRegistry()

    private final class WeakViewBox {
        weak var view: UIView?
        init(view: UIView) { self.view = view }
    }

    private var viewsByKey: [String: WeakViewBox] = [:]

    private init() {}

    public func register(view: UIView, forKey key: String) {
        viewsByKey[key] = WeakViewBox(view: view)
    }

    public func unregister(view: UIView, forKey key: String) {
        if viewsByKey[key]?.view === view {
            viewsByKey.removeValue(forKey: key)
        }
    }

    public func sourceView(for route: AppImmersiveRoute) -> UIView? {
        guard !UIAccessibility.isReduceMotionEnabled else { return nil }
        let key = Self.routeKey(for: route)
        guard let key, let candidate = viewsByKey[key]?.view else { return nil }
        guard candidate.window != nil,
              !candidate.isHidden,
              candidate.alpha > 0.01,
              candidate.bounds.width > 8,
              candidate.bounds.height > 8 else {
            return nil
        }
        return candidate
    }

    public static func routeKey(for route: AppImmersiveRoute) -> String? {
        switch route {
        case .video(let video):
            return "video:\(video.id)"
        case .comic(let comic, _, _):
            return "comic:\(comic.id)"
        case .live(let stream, _):
            return "live:\(stream.id)"
        case .forumPost(let post):
            return "forum:\(post.id)"
        case .game:
            return "game:hub"
        }
    }
}

private struct ZoomSourceAnchorRepresentable: UIViewRepresentable {
    let sourceKey: String

    final class AnchorView: UIView {
        var sourceKey: String = ""

        override func didMoveToWindow() {
            super.didMoveToWindow()
            guard !sourceKey.isEmpty else { return }
            if window != nil {
                ZoomTransitionSourceRegistry.shared.register(view: self, forKey: sourceKey)
            } else {
                ZoomTransitionSourceRegistry.shared.unregister(view: self, forKey: sourceKey)
            }
        }
    }

    func makeUIView(context: Context) -> AnchorView {
        let view = AnchorView()
        view.sourceKey = sourceKey
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: AnchorView, context: Context) {
        if uiView.sourceKey != sourceKey {
            if !uiView.sourceKey.isEmpty {
                ZoomTransitionSourceRegistry.shared.unregister(view: uiView, forKey: uiView.sourceKey)
            }
            uiView.sourceKey = sourceKey
            if uiView.window != nil, !sourceKey.isEmpty {
                ZoomTransitionSourceRegistry.shared.register(view: uiView, forKey: sourceKey)
            }
        }
    }
}

public extension View {
    /// 将当前卡片注册为 iOS 27 原生 `.zoom` 空间展开与手势下拉缩回源视图
    func auroraZoomSource(_ key: String) -> some View {
        background(ZoomSourceAnchorRepresentable(sourceKey: key))
    }
}
