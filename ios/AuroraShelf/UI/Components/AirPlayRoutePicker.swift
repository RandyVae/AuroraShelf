import SwiftUI
import AVKit

/// Apple 原生 AirPlay 投屏选择器（遵循设计令牌单一真源）
public struct AirPlayRoutePicker: UIViewRepresentable {
    public init() {}
    
    public func makeUIView(context: Context) -> AVRoutePickerView {
        let picker = AVRoutePickerView()
        picker.tintColor = .white
        picker.activeTintColor = UIColor(AuroraColor.brand)
        picker.prioritizesVideoDevices = true
        picker.backgroundColor = .clear
        return picker
    }
    
    public func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}
