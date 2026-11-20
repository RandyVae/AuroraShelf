import SwiftUI
import UIKit

/// 原生沉浸式 Hero 海报顶栏与滚动边缘磨砂控制规约（iOS 26 / iOS 27 Liquid Glass）
///
/// 常态（未滚动且存在顶部 Hero 海报）下完全隐藏顶栏背景与 `UIScrollView.topEdgeEffect` 磨砂遮罩，
/// 让海报无遮挡通透直达屏幕顶部；仅在用户向上滑动浏览内容（或无 Hero 海报的空/错误态）时平滑浮现原生顶栏磨砂。
public enum ImmersiveHeroTopBarChrome {
    /// 触发顶部磨砂栏显现的归一化滚动阈值（pt）
    public static let scrollActivationThreshold: CGFloat = 20

    /// 依据 `ScrollGeometry` 的 `contentOffset.y` 与 `contentInsets.top` 计算归一化滚动状态，
    /// 兼容 `.ignoresSafeArea(edges: .top)`、下拉刷新弹性回弹与模态 Sheet 容器。
    public static func isScrolled(contentOffsetY: CGFloat, topContentInset: CGFloat = 0) -> Bool {
        (contentOffsetY + topContentInset) > scrollActivationThreshold
    }

    /// 是否应当展示顶部导航栏磨砂背景与滚动边缘效果
    public static func shouldShowTopBarMaterial(isScrolled: Bool, hasHeroContent: Bool) -> Bool {
        isScrolled || !hasHeroContent
    }

    /// 导航栏背景可见性：常态有海报时 `.hidden`，滑动后或无海报时 `.visible`
    public static func toolbarBackgroundVisibility(isScrolled: Bool, hasHeroContent: Bool) -> Visibility {
        shouldShowTopBarMaterial(isScrolled: isScrolled, hasHeroContent: hasHeroContent) ? .visible : .hidden
    }

    /// 顶部 `ScrollEdgeEffect` 是否隐藏：常态有海报时隐藏（避免 `.ignoresSafeArea(edges: .top)` 触发静态硬切磨砂带），滑动后显现
    public static func isTopScrollEdgeEffectHidden(isScrolled: Bool, hasHeroContent: Bool) -> Bool {
        !shouldShowTopBarMaterial(isScrolled: isScrolled, hasHeroContent: hasHeroContent)
    }
}

/// 弥补 iOS 26/27 SwiftUI `HostingScrollView` 在 `.scrollEdgeEffectHidden(true -> false)` 动态切换时
/// 未回写 `UIScrollView.topEdgeEffect.isHidden = false` 的单向滞留问题，确保上滑显现与回顶隐藏双向即时同步。
private struct TopScrollEdgeEffectSyncBridge: UIViewRepresentable {
    let isHidden: Bool

    func makeUIView(context: Context) -> SyncProbeView {
        let view = SyncProbeView()
        view.isUserInteractionEnabled = false
        view.backgroundColor = .clear
        view.targetIsHidden = isHidden
        return view
    }

    func updateUIView(_ uiView: SyncProbeView, context: Context) {
        uiView.targetIsHidden = isHidden
        uiView.syncNow()
    }

    final class SyncProbeView: UIView {
        var targetIsHidden: Bool = false

        override func didMoveToWindow() {
            super.didMoveToWindow()
            syncNow()
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            syncNow()
        }

        func syncNow() {
            guard let scrollView = resolveVerticalScrollView() else { return }
            if scrollView.topEdgeEffect.isHidden != targetIsHidden {
                scrollView.topEdgeEffect.isHidden = targetIsHidden
            }
        }

        private func resolveVerticalScrollView() -> UIScrollView? {
            var current: UIView? = superview
            while let node = current {
                if let sv = node as? UIScrollView {
                    return sv
                }
                if let sibling = Self.findDirectOrNestedScrollView(in: node, excluding: self) {
                    return sibling
                }
                current = node.superview
            }
            return nil
        }

        private static func findDirectOrNestedScrollView(in root: UIView, excluding skipped: UIView) -> UIScrollView? {
            for child in root.subviews where child !== skipped {
                if let sv = child as? UIScrollView {
                    return sv
                }
            }
            for child in root.subviews where child !== skipped {
                if let sv = findDirectOrNestedScrollView(in: child, excluding: skipped) {
                    return sv
                }
            }
            return nil
        }
    }
}

public extension View {
    /// 挂载于沉浸式 `ScrollView` 上：在常态未滚动时隐藏顶部 `ScrollEdgeEffect` 硬切磨砂层，滑动后恢复系统原生边缘磨砂过渡。
    func auroraImmersiveHeroScrollEdge(
        isScrolled: Binding<Bool>,
        hasHeroContent: Bool,
        reduceMotion: Bool
    ) -> some View {
        let hidden = ImmersiveHeroTopBarChrome.isTopScrollEdgeEffectHidden(
            isScrolled: isScrolled.wrappedValue,
            hasHeroContent: hasHeroContent
        )
        return self
            .scrollEdgeEffectHidden(hidden, for: .top)
            .background(
                TopScrollEdgeEffectSyncBridge(isHidden: hidden)
                    .frame(width: 0, height: 0)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            )
            .onScrollGeometryChange(for: Bool.self) { geo in
                ImmersiveHeroTopBarChrome.isScrolled(
                    contentOffsetY: geo.contentOffset.y,
                    topContentInset: geo.contentInsets.top
                )
            } action: { _, newValue in
                if isScrolled.wrappedValue != newValue {
                    AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                        isScrolled.wrappedValue = newValue
                    }
                }
            }
    }

    /// 挂载于包含顶部 Hero 海报的 `NavigationStack` 根页面上：常态隐藏顶栏磨砂背板，滑动后平滑浮现。
    func auroraImmersiveHeroNavigationBar(
        isScrolled: Bool,
        hasHeroContent: Bool
    ) -> some View {
        self
            .toolbarBackgroundVisibility(
                ImmersiveHeroTopBarChrome.toolbarBackgroundVisibility(
                    isScrolled: isScrolled,
                    hasHeroContent: hasHeroContent
                ),
                for: .navigationBar
            )
    }
}
