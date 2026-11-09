import SwiftUI
import UIKit

/// 原生播放器进度拖拽条（Apple HIG 规范：支持高灵敏度轻点与滑动、Apple TV 悬浮时间气泡、机械顿挫触觉与弹性放大）
public struct PlayerScrubberBar: View {
    @Binding public var currentTime: Double
    public let duration: Double
    public let onSeek: (Double) -> Void
    
    @State private var isScrubbing: Bool = false
    @State private var scrubProgress: CGFloat = 0
    @State private var lastHapticStep: Int = 0
    
    private let hapticGenerator = UISelectionFeedbackGenerator()
    
    public init(
        currentTime: Binding<Double>,
        duration: Double,
        onSeek: @escaping (Double) -> Void
    ) {
        self._currentTime = currentTime
        self.duration = duration
        self.onSeek = onSeek
    }
    
    private var displayTime: Double {
        if isScrubbing {
            return Double(scrubProgress) * max(duration, 0)
        }
        return currentTime
    }
    
    public var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            let progress: CGFloat = {
                if isScrubbing {
                    return scrubProgress
                }
                guard duration > 0 else { return 0 }
                return min(max(CGFloat(currentTime / duration), 0), 1)
            }()
            
            let knobX = max(0, min(width * progress, width))
            
            ZStack(alignment: .leading) {
                // 背景轨道（半透明磨砂白）
                Capsule()
                    .fill(Color.white.opacity(0.24))
                    .frame(height: isScrubbing ? 8 : 4)
                
                // 已播放进度轨道（品牌色至亮红渐变）
                Capsule()
                    .fill(
                        LinearGradient(
                            colors: [AuroraColor.brand, Color(rgb: 0xFF5252)],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .frame(width: max(0, width * progress), height: isScrubbing ? 8 : 4)
                
                // 滑块旋钮（白色微光圆点，拖动时弹性放大至 18pt）
                Circle()
                    .fill(Color.white)
                    .frame(width: isScrubbing ? 18 : 10, height: isScrubbing ? 18 : 10)
                    .shadow(color: .black.opacity(0.6), radius: 4, x: 0, y: 1.5)
                    .offset(x: max(0, min(width * progress - (isScrubbing ? 9 : 5), width - (isScrubbing ? 18 : 10))))

                // Apple TV 标志性悬浮时间戳气泡（拖拽激活时浮显在旋钮正上方，跟随手指位移）
                if isScrubbing {
                    Text(formatTime(displayTime))
                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(.ultraThinMaterial, in: Capsule())
                        .overlay(
                            Capsule().stroke(Color.white.opacity(0.3), lineWidth: 0.5)
                        )
                        .shadow(color: .black.opacity(0.4), radius: 4, x: 0, y: 2)
                        .offset(x: max(0, min(knobX - 32, width - 64)), y: -34)
                        .transition(.scale(scale: 0.85).combined(with: .opacity))
                }
            }
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .animation(.spring(response: 0.22, dampingFraction: 0.8), value: isScrubbing)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if !isScrubbing {
                            isScrubbing = true
                            hapticGenerator.prepare()
                        }
                        let clampedX = min(max(value.location.x, 0), width)
                        let percent = width > 0 ? clampedX / width : 0
                        scrubProgress = percent

                        // 机械旋钮般的极细微触觉顿挫（每滑动 1% 或 15 秒触发一次）
                        let currentStep = Int(percent * 100)
                        if currentStep != lastHapticStep {
                            lastHapticStep = currentStep
                            hapticGenerator.selectionChanged()
                        }
                    }
                    .onEnded { value in
                        let clampedX = min(max(value.location.x, 0), width)
                        let percent = width > 0 ? clampedX / width : 0
                        let targetTime = Double(percent) * duration
                        isScrubbing = false
                        onSeek(targetTime)
                    }
            )
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("播放进度条")
            .accessibilityValue(formatTime(displayTime))
            .accessibilityAdjustableAction { direction in
                let step = 10.0
                switch direction {
                case .increment:
                    let target = min(currentTime + step, duration)
                    onSeek(target)
                case .decrement:
                    let target = max(currentTime - step, 0)
                    onSeek(target)
                @unknown default:
                    break
                }
            }
        }
        .frame(height: 32)
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
