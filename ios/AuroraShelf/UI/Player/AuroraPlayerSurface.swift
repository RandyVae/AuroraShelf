import SwiftUI
import AVKit

/// 基于原生 AVPlayerLayer 的硬件解码播放表面，支持无黑边满铺（Fill）、等比自适应（Fit）以及零延迟长按 2.0x 倍速快放手势
public struct AuroraPlayerSurface: UIViewRepresentable {
    public let player: AVPlayer
    public let videoGravity: AVLayerVideoGravity
    public let allowsPictureInPicture: Bool
    public var onLongPressBoostChanged: ((Bool) -> Void)?

    public init(
        player: AVPlayer,
        videoGravity: AVLayerVideoGravity = .resizeAspect,
        allowsPictureInPicture: Bool = true,
        onLongPressBoostChanged: ((Bool) -> Void)? = nil
    ) {
        self.player = player
        self.videoGravity = videoGravity
        self.allowsPictureInPicture = allowsPictureInPicture
        self.onLongPressBoostChanged = onLongPressBoostChanged
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(onLongPressBoostChanged: onLongPressBoostChanged)
    }

    public func makeUIView(context: Context) -> PlayerUIView {
        let view = PlayerUIView()
        view.playerLayer.player = player
        view.playerLayer.videoGravity = videoGravity
        if allowsPictureInPicture {
            PictureInPictureManager.shared.attach(playerLayer: view.playerLayer)
        }
        view.backgroundColor = .black

        let holdRecognizer = UILongPressGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleHoldGesture(_:))
        )
        holdRecognizer.minimumPressDuration = 0.42
        holdRecognizer.allowableMovement = 16
        holdRecognizer.cancelsTouchesInView = false
        holdRecognizer.delaysTouchesBegan = false
        holdRecognizer.delaysTouchesEnded = false
        holdRecognizer.delegate = context.coordinator
        view.addGestureRecognizer(holdRecognizer)
        return view
    }

    public func updateUIView(_ uiView: PlayerUIView, context: Context) {
        context.coordinator.onLongPressBoostChanged = onLongPressBoostChanged
        if uiView.playerLayer.player !== player {
            uiView.playerLayer.player = player
            if allowsPictureInPicture {
                PictureInPictureManager.shared.attach(playerLayer: uiView.playerLayer)
            }
        }
        if uiView.playerLayer.videoGravity != videoGravity {
            CATransaction.begin()
            CATransaction.setAnimationDuration(0.3)
            uiView.playerLayer.videoGravity = videoGravity
            CATransaction.commit()
        }
    }

    public static func dismantleUIView(_ uiView: PlayerUIView, coordinator: Coordinator) {
        coordinator.cancelBoostIfNeeded()
        if !PictureInPictureManager.shared.isActive {
            uiView.playerLayer.player = nil
        }
    }

    @MainActor
    public final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onLongPressBoostChanged: ((Bool) -> Void)?
        private var isBoosting = false

        init(onLongPressBoostChanged: ((Bool) -> Void)?) {
            self.onLongPressBoostChanged = onLongPressBoostChanged
        }

        @objc func handleHoldGesture(_ recognizer: UILongPressGestureRecognizer) {
            switch recognizer.state {
            case .began:
                guard !isBoosting else { return }
                isBoosting = true
                onLongPressBoostChanged?(true)
            case .ended, .cancelled, .failed:
                cancelBoostIfNeeded()
            default:
                break
            }
        }

        func cancelBoostIfNeeded() {
            guard isBoosting else { return }
            isBoosting = false
            onLongPressBoostChanged?(false)
        }

        public func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
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
