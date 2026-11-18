import CoreGraphics
import UIKit

/// 视频画面比例分类（对齐 YouTube 播放器自适应舞台与全屏方向决策）
public enum VideoOrientationKind: String, Equatable, Sendable {
    /// 横屏宽幅视频（如 16:9 影视、21:9 电影、4:3 番剧，aspect > 1.05）
    case landscape
    /// 方形或准方形视频（0.85 ... 1.05）
    case square
    /// 竖屏视频（如 9:16 短剧、竖版自拍，aspect < 0.85）
    case portrait
}

/// 视频画幅感知与多端舞台尺寸计算纯函数集（覆盖标准 iPhone、iPhone Duo 折叠/展开态与 iPad 分栏/全屏）
public enum VideoAspectProfile {
    /// 横屏判定阈值：宽高比大于 1.05 视为横屏片源，全屏时在 iPhone 上自动旋转为横屏满铺
    public static let landscapeThreshold: CGFloat = 1.05
    /// 竖屏判定阈值：宽高比小于 0.85 视为竖屏片源，内嵌态纵向展开、全屏态保持竖屏满铺
    public static let portraitThreshold: CGFloat = 0.85

    /// 根据解码器上报的 `presentationSize` 与来源特征推导当前有效宽高比
    public static func resolvedAspectRatio(
        presentationSize: CGSize,
        sourceName: String = "",
        category: String = ""
    ) -> CGFloat {
        if presentationSize.width > 1, presentationSize.height > 1,
           presentationSize.width.isFinite, presentationSize.height.isFinite {
            let raw = presentationSize.width / presentationSize.height
            return min(max(raw, 0.35), 2.60)
        }
        if sourceName.contains("短剧") || sourceName.contains("黄果") || category.contains("竖屏") {
            return 9.0 / 16.0
        }
        return 16.0 / 9.0
    }

    /// 判定视频方向类别
    public static func kind(forAspect aspect: CGFloat) -> VideoOrientationKind {
        if aspect > landscapeThreshold {
            return .landscape
        }
        if aspect < portraitThreshold {
            return .portrait
        }
        return .square
    }

    /// 点击「全屏」时是否应当旋转至横屏（YouTube 核心直觉：横片自动横屏全屏，竖片/方片保持竖屏全屏铺满；iPad 遵循机身自然旋转）
    public static func prefersLandscapeFullscreen(
        aspectRatio: CGFloat,
        idiom: UIUserInterfaceIdiom
    ) -> Bool {
        guard idiom != .pad else { return false }
        return kind(forAspect: aspectRatio) == .landscape
    }

    /// 计算二级详情页（非全屏内嵌态）播放器容器总高度（含顶部安全区垫片）
    /// - Parameters:
    ///   - screenSize: 当前窗口视口尺寸
    ///   - playerColumnWidth: 播放器所在列宽度（单栏为屏宽，iPad 分栏为左栏 70% 宽度）
    ///   - safeTop: 顶部安全区高度
    ///   - aspectRatio: 当前视频宽高比（width / height）
    ///   - collapseProgress: 列表上滑收折进度（0 = 初始展开态，1 = 列表上滑后收折为紧凑 16:9 吸顶态）
    ///   - idiom: 设备类型
    ///   - isSplitMode: 是否处于 iPad 横屏 YouTube 双栏模式
    public static func inlineContainerHeight(
        screenSize: CGSize,
        playerColumnWidth: CGFloat,
        safeTop: CGFloat,
        aspectRatio: CGFloat,
        collapseProgress: CGFloat = 0,
        idiom: UIUserInterfaceIdiom,
        isSplitMode: Bool
    ) -> CGFloat {
        let clampedCollapse = min(max(collapseProgress, 0), 1)
        let width = max(playerColumnWidth, 1)
        let height = max(screenSize.height, 1)
        let orientationKind = kind(forAspect: aspectRatio)

        if isSplitMode {
            let baseMediaHeight = min(width * 9.0 / 16.0, height * 0.62)
            let expandedMediaHeight: CGFloat
            switch orientationKind {
            case .landscape:
                expandedMediaHeight = baseMediaHeight
            case .square, .portrait:
                expandedMediaHeight = min(max(baseMediaHeight, height * 0.56), min(height * 0.64, 520))
            }
            let mediaHeight = expandedMediaHeight - (expandedMediaHeight - baseMediaHeight) * clampedCollapse
            return mediaHeight + safeTop
        }

        if idiom == .pad {
            let baseMediaHeight = min(width * 9.0 / 16.0, height * 0.48, 440)
            let expandedMediaHeight: CGFloat
            switch orientationKind {
            case .landscape:
                expandedMediaHeight = baseMediaHeight
            case .square:
                expandedMediaHeight = min(max(baseMediaHeight, height * 0.46), 480)
            case .portrait:
                expandedMediaHeight = min(max(baseMediaHeight, height * 0.52), 520)
            }
            let mediaHeight = expandedMediaHeight - (expandedMediaHeight - baseMediaHeight) * clampedCollapse
            return mediaHeight + safeTop
        }

        // iPhone 物理横屏非全屏兜底
        if screenSize.width > screenSize.height {
            return min(width * 9.0 / 16.0, height * 0.62) + safeTop
        }

        // iPhone 竖屏（标准窄屏 393pt / Duo 外屏折叠态 466×678pt / Duo 内屏展开态 669×951pt）
        let base16x9 = width * 9.0 / 16.0
        let expandedMediaHeight: CGFloat
        switch orientationKind {
        case .landscape:
            expandedMediaHeight = base16x9
        case .square:
            let squareTarget = width / max(aspectRatio, 0.85)
            let maxCap = height <= 740 ? height * 0.40 : height * 0.44
            expandedMediaHeight = max(base16x9, min(squareTarget, maxCap))
        case .portrait:
            if height <= 740 {
                // iPhone Duo 外屏折叠态（678pt 高）：限高 44% 确保下方标题与胶囊工具栏完整露头
                expandedMediaHeight = max(base16x9, min(width * 1.10, height * 0.44))
            } else if width >= 500 {
                // iPhone Duo 内屏展开态（669×951pt）
                expandedMediaHeight = max(base16x9, min(width * 1.12, height * 0.48))
            } else {
                // 标准 iPhone（如 393×852pt）：纵向展开至约 52% 屏高，显示面积较 16:9 邮票框提升约 3.8 倍
                expandedMediaHeight = max(base16x9, min(width * 1.22, height * 0.52))
            }
        }

        let mediaHeight = expandedMediaHeight - (expandedMediaHeight - base16x9) * clampedCollapse
        return mediaHeight + safeTop
    }
}
