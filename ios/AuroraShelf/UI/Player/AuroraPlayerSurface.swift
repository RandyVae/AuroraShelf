import SwiftUI
import AVKit

/// 基于原生 AVPlayerLayer 的硬件解码播放表面，支持无黑边满铺（Fill）与等比自适应（Fit）平滑切换
public struct AuroraPlayerSurface: UIViewRepresentable {
    public let player: AVPlayer
    public let videoGravity: AVLayerVideoGravity
    
    public init(player: AVPlayer, videoGravity: AVLayerVideoGravity = .resizeAspect) {
        self.player = player
        self.videoGravity = videoGravity
    }
    
    public func makeUIView(context: Context) -> PlayerUIView {
        let view = PlayerUIView()
        view.playerLayer.player = player
        view.playerLayer.videoGravity = videoGravity
        view.backgroundColor = .black
        return view
    }
    
    public func updateUIView(_ uiView: PlayerUIView, context: Context) {
        uiView.playerLayer.player = player
        if uiView.playerLayer.videoGravity != videoGravity {
            CATransaction.begin()
            CATransaction.setAnimationDuration(0.3)
            uiView.playerLayer.videoGravity = videoGravity
            CATransaction.commit()
        }
    }
    
    public class PlayerUIView: UIView {
        public override static var layerClass: AnyClass {
            AVPlayerLayer.self
        }
        
        public var playerLayer: AVPlayerLayer {
            layer as! AVPlayerLayer
        }
    }
}
