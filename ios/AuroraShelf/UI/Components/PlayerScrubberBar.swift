import SwiftUI

/// Apple TV / iOS 27 原生风格无旋钮流体膨胀液态胶囊进度条（Fluid Expanding Capsule Scrubber）
/// - 常态为 6pt 半透明流光胶囊轨（含缓冲进度轨与纯白已播进度）；
/// - 触控拖拽瞬间整条轨道弹性膨胀至 12~14pt 高亮液态胶囊，上方浮现 `Glass.clear` 单宽时间戳预览气泡；
/// - 使用 SwiftUI `.sensoryFeedback(.selection, trigger:)` 替代值类型 View 内重复分配 `UISelectionFeedbackGenerator`，并保证 44pt 最小触控热区。
public struct PlayerScrubberBar: View {
    @Binding public var currentTime: Double
    public let bufferedTime: Double
    public let duration: Double
    public let onSeek: (Double) -> Void
    public var onEditingChanged: ((Bool) -> Void)?

    @Environment(\.auroraContainerSize) private var containerSize
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isScrubbing: Bool = false
    @State private var scrubProgress: CGFloat = 0
    @State private var lastHapticStep: Int = 0

    public init(
        currentTime: Binding<Double>,
        bufferedTime: Double = 0,
        duration: Double,
        onSeek: @escaping (Double) -> Void,
        onEditingChanged: ((Bool) -> Void)? = nil
    ) {
        self._currentTime = currentTime
        self.bufferedTime = bufferedTime
        self.duration = duration
        self.onSeek = onSeek
        self.onEditingChanged = onEditingChanged
    }

    private var displayTime: Double {
        if isScrubbing {
            return Double(scrubProgress) * max(duration, 0)
        }
        return currentTime
    }

    public var body: some View {
        let isWide = DeviceLayout.resolveCurrent(
            containerSize: containerSize,
            horizontalSizeClass: horizontalSizeClass
        ).isWide
        let restingHeight: CGFloat = isWide ? 7 : 6
        let activeHeight: CGFloat = isWide ? 14 : 12
        let trackHeight: CGFloat = isScrubbing ? activeHeight : restingHeight

        GeometryReader { geo in
            let width = max(geo.size.width, 1)
            let progress: CGFloat = {
                if isScrubbing {
                    return scrubProgress
                }
                guard duration > 0 else { return 0 }
                return min(max(CGFloat(currentTime / duration), 0), 1)
            }()
            let bufferedFraction: CGFloat = {
                guard duration > 0, bufferedTime > 0 else { return 0 }
                return min(max(CGFloat(bufferedTime / duration), progress), 1)
            }()
            let activeX = max(0, min(width * progress, width))

            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.white.opacity(isScrubbing ? 0.30 : 0.22))
                    .overlay(
                        Capsule()
                            .strokeBorder(Color.white.opacity(isScrubbing ? 0.34 : 0.16), lineWidth: 0.6)
                    )
                    .frame(height: trackHeight)

                if bufferedFraction > 0 {
                    Capsule()
                        .fill(Color.white.opacity(isScrubbing ? 0.42 : 0.34))
                        .frame(width: max(trackHeight, width * bufferedFraction), height: trackHeight)
                }

                if progress > 0 {
                    Capsule()
                        .fill(Color.white.opacity(isScrubbing ? 1.0 : 0.92))
                        .frame(width: max(trackHeight, activeX), height: trackHeight)
                        .shadow(color: Color.white.opacity(isScrubbing ? 0.42 : 0.0), radius: 6, x: 0, y: 0)
                }

                if isScrubbing {
                    Text(formatTime(displayTime))
                        .font(.system(.caption, design: .monospaced).weight(.bold))
                        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .glassEffect(.clear, in: .capsule)
                        .shadow(color: .black.opacity(0.28), radius: 8, x: 0, y: 3)
                        .offset(x: max(0, min(activeX - 34, max(0, width - 68))), y: -34)
                        .transition(.opacity)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .animation(AuroraMotion.quick(reduceMotion: reduceMotion), value: isScrubbing)
            .sensoryFeedback(.selection, trigger: lastHapticStep)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if !isScrubbing {
                            isScrubbing = true
                            onEditingChanged?(true)
                        }
                        let clampedX = min(max(value.location.x, 0), width)
                        let percent = width > 0 ? clampedX / width : 0
                        scrubProgress = percent

                        let currentStep = Int(percent * 100)
                        if currentStep != lastHapticStep {
                            lastHapticStep = currentStep
                        }
                    }
                    .onEnded { value in
                        let clampedX = min(max(value.location.x, 0), width)
                        let percent = width > 0 ? clampedX / width : 0
                        let targetTime = Double(percent) * max(duration, 0)
                        currentTime = targetTime
                        isScrubbing = false
                        onEditingChanged?(false)
                        onSeek(targetTime)
                    }
            )
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("player.scrubber_a11y".localized)
            .accessibilityValue(formatTime(displayTime))
            .accessibilityAdjustableAction { direction in
                let step = 10.0
                switch direction {
                case .increment:
                    onSeek(min(currentTime + step, duration))
                case .decrement:
                    onSeek(max(currentTime - step, 0))
                @unknown default:
                    break
                }
            }
        }
        .frame(height: AuroraMetrics.minTouchTarget)
    }

    private func formatTime(_ seconds: Double) -> String {
        guard seconds.isFinite && seconds >= 0 else { return "00:00" }
        let total = Int(seconds)
        let h = total / 3600
        let m = (total % 3600) / 60
        let s = total % 60
        if h > 0 {
            return String(format: "%02d:%02d:%02d", h, m, s)
        }
        return String(format: "%02d:%02d", m, s)
    }
}
