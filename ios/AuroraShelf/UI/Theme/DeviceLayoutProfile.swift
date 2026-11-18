import SwiftUI
import UIKit

/// 设备形态枚举：精准支持标准 iPhone、iPhone Duo 外屏折叠态、内屏展开态与 iPad
public enum DeviceFormFactor: String, Equatable, Sendable {
    case standardPhone  // 标准 iPhone（如 iPhone 17 Pro, 16 等）
    case duoFolded      // iPhone Duo 外屏折叠态（466 × 678 pt @3x）
    case duoUnfolded    // iPhone Duo 内屏双联展开态（669 × 951 pt @3x）
    case pad            // iPadOS 设备（如 iPad Pro, iPad Air 等）

    public var isDuo: Bool {
        self == .duoFolded || self == .duoUnfolded
    }

    public var isWide: Bool {
        self == .duoUnfolded || self == .pad
    }
}

private struct AuroraContainerSizeKey: EnvironmentKey {
    static let defaultValue: CGSize = .zero
}

public extension EnvironmentValues {
    var auroraContainerSize: CGSize {
        get { self[AuroraContainerSizeKey.self] }
        set { self[AuroraContainerSizeKey.self] = newValue }
    }
}

/// 设备屏幕尺寸与折叠形态判定引擎
@MainActor
public struct DeviceLayout {
    /// 判定当前上下文形态（以实际可用窗口宽度为首要判定标准，杜绝 iPad 分屏时强判 pad 导致布局溢出）
    public static func resolve(
        size: CGSize,
        horizontalSizeClass: UserInterfaceSizeClass? = nil,
        idiom: UIUserInterfaceIdiom = UIDevice.current.userInterfaceIdiom
    ) -> DeviceFormFactor {
        // A compact iPad window behaves like a phone-sized scene even though
        // UIScreen may still report the physical display bounds. This keeps
        // Split View and Slide Over from selecting the wide iPad card scale.
        if idiom == .pad, horizontalSizeClass == .compact {
            return .standardPhone
        }

        // 1. 若当前视口宽度紧凑（< 500pt，如 iPad 1/3 Split View、Slide Over、自由窗口窄窗、标准 iPhone），
        // 必须判定为紧凑形态，杜绝机械按硬件 idiom 判定
        if size.width < 500 {
            let isDuoFoldedDimensions = (size.width >= 445 && size.width < 520 && size.height <= 740)
                || (size.height >= 445 && size.height < 520 && size.width <= 740)
            if isDuoFoldedDimensions {
                return .duoFolded
            }
            if isDuoHardware && size.width < 550 {
                return .duoFolded
            }
            return .standardPhone
        }

        // 2. 宽屏大底形态（宽 >= 740pt，如 iPad 全屏/大分屏、Mac Catalyst）
        if size.width >= 740 {
            return .pad
        }

        // 3. 中屏双联或 1/2 分屏（500..<740pt，如 iPhone Duo 展开态、iPad 半屏）
        return .duoUnfolded
    }

    /// 获取当前活动窗口真实尺寸（仅作为未注入 `@Environment(\.auroraContainerSize)` 时的后备）
    public static var currentWindowSize: CGSize {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let activeScene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first
        let window = activeScene?.windows.first(where: { $0.isKeyWindow }) ?? activeScene?.windows.first
        if let size = window?.bounds.size, size.width > 0 && size.height > 0 {
            return size
        }
        return CGSize(width: 393, height: 852)
    }

    /// 判定当前上下文形态（优先使用按窗口 Scene 隔离下发的 `containerSize`）
    public static func resolveCurrent(
        containerSize: CGSize = .zero,
        horizontalSizeClass: UserInterfaceSizeClass? = nil,
        idiom: UIUserInterfaceIdiom = UIDevice.current.userInterfaceIdiom
    ) -> DeviceFormFactor {
        let effectiveSize = (containerSize.width > 0 && containerSize.height > 0) ? containerSize : currentWindowSize
        return resolve(size: effectiveSize, horizontalSizeClass: horizontalSizeClass, idiom: idiom)
    }

    /// 针对任意窗口宽度计算相关推荐网格自适应列数（彻底消除 iPadOS 27 自由窄窗强塞 4 列至 73.75pt 的坍塌）
    public static func relatedVideoGridColumns(for availableWidth: CGFloat, spacing: CGFloat = 14) -> [GridItem] {
        let usable = max(240, availableWidth - AuroraMetrics.contentInset * 2)
        let columnCount: Int
        if usable >= 920 {
            columnCount = 4
        } else if usable >= 600 {
            columnCount = 3
        } else {
            columnCount = 2
        }
        return Array(repeating: GridItem(.flexible(), spacing: spacing), count: columnCount)
    }

    /// 检测是否为 iPhone Duo 硬件/模拟器环境
    public static var isDuoHardware: Bool {
        var systemInfo = utsname()
        uname(&systemInfo)
        let machine = withUnsafePointer(to: &systemInfo.machine) {
            $0.withMemoryRebound(to: CChar.self, capacity: 1) {
                String(validatingCString: $0) ?? ""
            }
        }
        if machine.contains("iPhone19,4") || machine.contains("V68") {
            return true
        }
        if let simDevice = ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"],
           simDevice.localizedCaseInsensitiveContains("Duo") {
            return true
        }
        if let simModel = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"],
           simModel.contains("iPhone19,4") {
            return true
        }
        return false
    }

    /// 针对不同设备形态计算 HeroBanner 黄金高度
    public static func heroHeight(for form: DeviceFormFactor, screenHeight: CGFloat = 852) -> CGFloat {
        switch form {
        case .pad:
            return 520
        case .duoUnfolded:
            return 460
        case .duoFolded:
            // 外屏总高仅 678pt，紧凑 340pt 黄金画幅，确保下方 Up Next 待播卡片露头可被发现
            return min(max(screenHeight * 0.48, 300), 340)
        case .standardPhone:
            return 420
        }
    }

    /// 针对不同设备形态计算 HeroBanner 动作按钮区最大宽度
    public static func heroActionsMaxWidth(for form: DeviceFormFactor) -> CGFloat? {
        switch form {
        case .pad:
            return 440
        case .duoUnfolded:
            return 420
        case .duoFolded:
            return 380
        case .standardPhone:
            return nil // 撑满标准手机屏宽
        }
    }

    /// 针对不同设备形态计算 UpNext 待播卡片宽度（锁定 16:9 比例）
    public static func upNextCardWidth(for form: DeviceFormFactor) -> CGFloat {
        switch form {
        case .pad:
            return 320
        case .duoUnfolded:
            return 280
        case .duoFolded, .standardPhone:
            return 250
        }
    }

    /// 针对不同设备形态计算标准海报卡片宽度（2:3 纵向海报，Apple TV 原生阶梯）
    public static func posterCardWidth(for form: DeviceFormFactor) -> CGFloat {
        switch form {
        case .pad:
            return 190
        case .duoUnfolded:
            return 160
        case .duoFolded, .standardPhone:
            return 140
        }
    }

    /// 针对不同设备形态计算标准海报卡片高度（2:3 纵向海报，高 = 宽 * 1.5）
    public static func posterCardHeight(for form: DeviceFormFactor) -> CGFloat {
        posterCardWidth(for: form) * 1.5
    }

    /// 针对不同设备形态计算 Top 10 排行榜数字基线字号
    public static func top10RankFontSize(for form: DeviceFormFactor) -> CGFloat {
        switch form {
        case .pad:
            return 52
        case .duoUnfolded:
            return 42
        case .duoFolded, .standardPhone:
            return 34
        }
    }

    /// 针对不同设备形态计算选集卡片宽度（16:9 比例）
    public static func episodeCardWidth(for form: DeviceFormFactor) -> CGFloat {
        switch form {
        case .pad:
            return 260
        case .duoUnfolded:
            return 220
        case .duoFolded, .standardPhone:
            return 196
        }
    }

    /// 针对不同设备形态计算选集卡片高度（16:9 比例）
    public static func episodeCardHeight(for form: DeviceFormFactor) -> CGFloat {
        episodeCardWidth(for: form) * (9.0 / 16.0)
    }

    /// 针对不同设备形态计算分类网格自适应最小宽度（防止 iPad 上产生 8~10 列微缩邮票）
    public static func gridMinimumWidth(for form: DeviceFormFactor) -> CGFloat {
        switch form {
        case .pad:
            return 190
        case .duoUnfolded:
            return 160
        case .duoFolded, .standardPhone:
            return 140
        }
    }

    private final class SafeAreaCache: @unchecked Sendable {
        static let shared = SafeAreaCache()
        private let lock = NSLock()
        private var portraitTop: CGFloat = 0
        private var portraitBottom: CGFloat = 0
        private var currentLeft: CGFloat = 0
        private var currentRight: CGFloat = 0
        private var isRefreshScheduled = false
        private var lastRefreshUptime: TimeInterval = 0

        private init() {
            scheduleRefreshIfNeeded()
        }

        // Reading `UIWindow.safeAreaInsets` synchronously inside SwiftUI `View.body` triggers an
        // AttributeGraph dependency cycle on iOS 27.2+, so cache insets outside body evaluation.
        func scheduleRefreshIfNeeded() {
            let now = ProcessInfo.processInfo.systemUptime
            lock.lock()
            let shouldSchedule = !isRefreshScheduled && (now - lastRefreshUptime > 0.25)
            if shouldSchedule {
                isRefreshScheduled = true
            }
            lock.unlock()
            guard shouldSchedule else { return }

            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
                let activeScene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first
                let window = activeScene?.windows.first(where: { $0.isKeyWindow }) ?? activeScene?.windows.first
                let insets = window?.safeAreaInsets ?? .zero
                let bounds = window?.bounds.size ?? .zero
                let isLandscape = bounds.width > bounds.height

                self.lock.lock()
                self.isRefreshScheduled = false
                self.lastRefreshUptime = ProcessInfo.processInfo.systemUptime
                self.currentLeft = insets.left
                self.currentRight = insets.right
                if !isLandscape || UIDevice.current.userInterfaceIdiom == .pad {
                    if insets.top > 0 { self.portraitTop = insets.top }
                    if insets.bottom > 0 { self.portraitBottom = insets.bottom }
                }
                self.lock.unlock()
            }
        }

        func readPortraitTop() -> CGFloat {
            scheduleRefreshIfNeeded()
            lock.lock()
            let value = portraitTop
            lock.unlock()
            return value
        }

        func readBottom() -> CGFloat {
            scheduleRefreshIfNeeded()
            lock.lock()
            let value = portraitBottom
            lock.unlock()
            return value
        }

        func readHorizontal() -> (leading: CGFloat, trailing: CGFloat) {
            scheduleRefreshIfNeeded()
            lock.lock()
            let result = (currentLeft, currentRight)
            lock.unlock()
            return result
        }
    }

    /// 获取当前窗口真实安全区顶部高度（动态适配 Dynamic Island 59pt、刘海屏 47/44pt 与 iPad 24pt）
    public static var safeAreaTop: CGFloat {
        safeAreaTop(in: nil)
    }

    /// 针对特定尺寸或当前窗口计算安全区顶部高度
    public static func safeAreaTop(in size: CGSize? = nil) -> CGFloat {
        // 如果给定了尺寸且为物理横屏（width > height 且非 iPad）
        if let s = size, s.width > s.height, UIDevice.current.userInterfaceIdiom != .pad {
            return 12
        }
        let cachedTop = SafeAreaCache.shared.readPortraitTop()
        if cachedTop > 0 {
            return cachedTop
        }
        if UIDevice.current.userInterfaceIdiom == .pad {
            return 24
        }
        return 59 // 现代 iPhone (Dynamic Island) 默认安全区高度
    }

    /// 获取当前窗口真实安全区底部高度（Home 条 34pt / 21pt）
    public static var safeAreaBottom: CGFloat {
        let cachedBottom = SafeAreaCache.shared.readBottom()
        if cachedBottom > 0 {
            return cachedBottom
        }
        return UIDevice.current.userInterfaceIdiom == .pad ? 20 : 34
    }

    /// 获取横屏下避让灵动岛或刘海的侧边安全边距
    public static var safeAreaHorizontalInsets: (leading: CGFloat, trailing: CGFloat) {
        SafeAreaCache.shared.readHorizontal()
    }
}

/// Adaptive information architecture for the video detail player.
///
/// Full-width iPad landscape uses a YouTube-style split surface only when the current
/// container is wide enough to keep both the player and the recommendation rail useful.
public enum VideoDetailLayoutMode: Equatable {
    case stacked
    case padLandscapeSplit
}

public struct VideoDetailColumns: Equatable {
    public let playerFraction: CGFloat
    public let recommendationFraction: CGFloat
    private let spacing: CGFloat

    public init(playerFraction: CGFloat = 0.70, recommendationFraction: CGFloat = 0.30, spacing: CGFloat = 0) {
        self.playerFraction = playerFraction
        self.recommendationFraction = recommendationFraction
        self.spacing = spacing
    }

    public func playerWidth(for totalWidth: CGFloat) -> CGFloat {
        max(0, totalWidth * playerFraction - spacing / 2)
    }

    public func recommendationWidth(for totalWidth: CGFloat) -> CGFloat {
        max(0, totalWidth * recommendationFraction - spacing / 2)
    }
}

public enum VideoDetailLayout {
    public static func mode(size: CGSize, idiom: UIUserInterfaceIdiom) -> VideoDetailLayoutMode {
        guard idiom == .pad, size.width > size.height, size.width >= 700 else { return .stacked }
        return .padLandscapeSplit
    }

    public static func columns(for size: CGSize, idiom: UIUserInterfaceIdiom) -> VideoDetailColumns {
        mode(size: size, idiom: idiom) == .padLandscapeSplit
            ? VideoDetailColumns()
            : VideoDetailColumns(playerFraction: 1, recommendationFraction: 0)
    }
}
