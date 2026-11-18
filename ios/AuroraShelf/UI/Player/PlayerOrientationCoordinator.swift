import Observation
import UIKit

/// YouTube 式屏幕方向与物理重力联动状态机（纯逻辑部分可独立单元测试）
public struct PlayerGravityRotationStateMachine: Equatable, Sendable {
    public private(set) var isFullScreen: Bool = false
    public private(set) var isLandscapeLocked: Bool = false
    /// 记录进入横屏全屏后，用户手机是否已经真实处于横向握持姿态。
    /// - 当用户竖握手机手动点击「全屏」进入横屏时为 `false`（防止因手机仍竖握而立即误退全屏）；
    /// - 一旦用户将手机横过来（或原本就是靠横握重力触发进的全屏），置为 `true`；此后当用户把手机重新竖回时，自动顺滑退回二级详情页。
    public private(set) var hasAlignedWithPhysicalLandscape: Bool = false

    public init() {}

    public enum GravityAction: Equatable, Sendable {
        case none
        case enterLandscapeFullscreen
        case exitToInlinePortrait
    }

    public mutating func enterFullscreen(preferLandscape: Bool, triggeredByGravity: Bool = false) {
        isFullScreen = true
        isLandscapeLocked = preferLandscape
        hasAlignedWithPhysicalLandscape = preferLandscape && triggeredByGravity
    }

    public mutating func setLandscapeInFullscreen(_ landscape: Bool, currentlyPhysicallyLandscape: Bool = false) {
        guard isFullScreen else { return }
        isLandscapeLocked = landscape
        hasAlignedWithPhysicalLandscape = landscape && currentlyPhysicallyLandscape
    }

    public mutating func exitFullscreen() {
        isFullScreen = false
        isLandscapeLocked = false
        hasAlignedWithPhysicalLandscape = false
    }

    /// 处理物理设备姿态变化，返回应当自动执行的 YouTube 式全屏进出动作
    public mutating func handleDeviceOrientation(
        _ orientation: UIDeviceOrientation,
        videoKind: VideoOrientationKind,
        idiom: UIUserInterfaceIdiom
    ) -> GravityAction {
        guard idiom != .pad else { return .none }
        let isDeviceLandscape = (orientation == .landscapeLeft || orientation == .landscapeRight)
        let isDevicePortrait = (orientation == .portrait)
        guard isDeviceLandscape || isDevicePortrait else { return .none }

        if !isFullScreen {
            // 二级页内嵌态下：播放横屏视频时把手机横过来 -> 自动进入横屏全屏
            if isDeviceLandscape && videoKind == .landscape {
                enterFullscreen(preferLandscape: true, triggeredByGravity: true)
                return .enterLandscapeFullscreen
            }
            return .none
        } else {
            // 全屏态下：若处于横屏全屏
            if isLandscapeLocked {
                if isDeviceLandscape {
                    hasAlignedWithPhysicalLandscape = true
                    return .none
                }
                if isDevicePortrait && hasAlignedWithPhysicalLandscape {
                    exitFullscreen()
                    return .exitToInlinePortrait
                }
            } else if isDeviceLandscape && videoKind == .landscape {
                // 在竖屏全屏下横握横片，顺应手势切入横屏全屏
                isLandscapeLocked = true
                hasAlignedWithPhysicalLandscape = true
                return .enterLandscapeFullscreen
            }
            return .none
        }
    }
}

/// 全局播放器窗口方向协调器：打通 `UIApplicationDelegate` 与 `UIWindowScene.requestGeometryUpdate`
@Observable
@MainActor
public final class PlayerOrientationCoordinator {
    public static let shared = PlayerOrientationCoordinator()

    public enum PhoneOrientationMode: Equatable, Sendable {
        /// 默认模式：iPhone 锁定竖屏（防止列表与二级详情页斜躺乱转），iPad 四向全开
        case portraitOnly
        /// 横屏全屏模式：iPhone 放开左右双向横屏（`.landscapeLeft` + `.landscapeRight`）并主动旋转系统窗口
        case landscapeFullscreen
    }

    public private(set) var phoneMode: PhoneOrientationMode = .portraitOnly
    /// 极端多任务窗口拒绝几何更新时的视图级旋转兜底标志（正常真机/模拟器窗口旋转时恒为 false）
    public private(set) var needsViewRotationFallback: Bool = false

    private init() {}

    /// 供 `AppDelegate` 与 `ImmersiveNavigationController` 查询当前允许的界面方向掩码
    public func supportedInterfaceOrientations(
        for idiom: UIUserInterfaceIdiom = UIDevice.current.userInterfaceIdiom
    ) -> UIInterfaceOrientationMask {
        if idiom == .pad {
            return .all
        }
        switch phoneMode {
        case .portraitOnly:
            return .portrait
        case .landscapeFullscreen:
            return .landscape
        }
    }

    /// 请求切换 iPhone 窗口方向（支持双向横屏与竖屏平滑回正）
    public func applyPhoneOrientation(landscape: Bool, preferredDeviceOrientation: UIDeviceOrientation? = nil) {
        guard UIDevice.current.userInterfaceIdiom != .pad else {
            needsViewRotationFallback = false
            return
        }

        let nextMode: PhoneOrientationMode = landscape ? .landscapeFullscreen : .portraitOnly
        phoneMode = nextMode
        needsViewRotationFallback = false

        guard let scene = Self.activeWindowScene() else { return }

        // 先通知顶层与子视图控制器刷新允许的方向掩码，再请求系统几何旋转
        for window in scene.windows {
            window.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
            notifyChildrenOrientationUpdate(from: window.rootViewController)
        }

        let targetMask: UIInterfaceOrientationMask
        if landscape {
            let devOri = preferredDeviceOrientation ?? UIDevice.current.orientation
            switch devOri {
            case .landscapeRight:
                // 注意：设备物理左倾（Home 在左）对应 UIInterfaceOrientation.landscapeLeft
                targetMask = .landscapeLeft
            case .landscapeLeft:
                targetMask = .landscapeRight
            default:
                targetMask = .landscape
            }
        } else {
            targetMask = .portrait
        }

        scene.requestGeometryUpdate(.iOS(interfaceOrientations: targetMask)) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                if self.phoneMode == .landscapeFullscreen {
                    let bounds = scene.windows.first(where: { $0.isKeyWindow })?.bounds.size ?? .zero
                    if bounds.height > bounds.width {
                        self.needsViewRotationFallback = true
                    }
                }
            }
        }

        // 若已开放双向 `.landscape`，在初始定向完成后保持 `.landscape` 掩码，允许用户左右翻转 180°
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { [weak self] in
            guard let self, self.phoneMode == .landscapeFullscreen else { return }
            for window in scene.windows {
                window.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
            }
        }
    }

    /// 退出播放页或收起全屏时强制回正至竖屏
    public func resetToPortraitIfNeeded() {
        needsViewRotationFallback = false
        guard UIDevice.current.userInterfaceIdiom != .pad else { return }
        if phoneMode != .portraitOnly {
            applyPhoneOrientation(landscape: false)
        }
    }

    public static func activeWindowScene() -> UIWindowScene? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first
    }

    private func notifyChildrenOrientationUpdate(from controller: UIViewController?) {
        guard let controller else { return }
        controller.setNeedsUpdateOfSupportedInterfaceOrientations()
        for child in controller.children {
            notifyChildrenOrientationUpdate(from: child)
        }
        if let presented = controller.presentedViewController {
            notifyChildrenOrientationUpdate(from: presented)
        }
    }
}

/// 应用级方向代理：确保 SwiftUI `WindowGroup` 宿主窗口严格遵守 `PlayerOrientationCoordinator` 的动态方向策略
public final class AuroraAppDelegate: NSObject, UIApplicationDelegate {
    public func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        PlayerOrientationCoordinator.shared.supportedInterfaceOrientations(
            for: UIDevice.current.userInterfaceIdiom
        )
    }
}
