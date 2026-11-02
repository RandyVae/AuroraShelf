import SwiftUI
import AVKit

/// Apple 原生 AirPlay 投屏选择器
public struct AirPlayRoutePicker: UIViewRepresentable {
    public init() {}
    
    public func makeUIView(context: Context) -> AVRoutePickerView {
        let picker = AVRoutePickerView()
        picker.tintColor = .white
        picker.activeTintColor = UIColor(red: 1.0, green: 0.31, blue: 0.48, alpha: 1.0)
        picker.prioritizesVideoDevices = true
        picker.backgroundColor = .clear
        return picker
    }
    
    public func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}
