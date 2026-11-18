import SwiftUI
import AVKit

/// Apple 原生 AirPlay 投屏选择器（遵循设计令牌单一真源，支持浅色/深色自适应着色）
public struct AirPlayRoutePicker: UIViewRepresentable {
    private let tint: UIColor

    public init(tint: UIColor = .label) {
        self.tint = tint
    }

    public func makeUIView(context: Context) -> AVRoutePickerView {
        let picker = AVRoutePickerView()
        picker.tintColor = tint
        picker.activeTintColor = UIColor(AuroraColor.brand)
        picker.prioritizesVideoDevices = true
        picker.backgroundColor = .clear
        return picker
    }

    public func updateUIView(_ uiView: AVRoutePickerView, context: Context) {
        uiView.tintColor = tint
    }
}
