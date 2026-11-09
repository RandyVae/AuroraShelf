import AVKit

/// Owns the system Picture in Picture controller for the custom AVPlayer surface.
@MainActor
public final class PictureInPictureManager: NSObject {
    public static let shared = PictureInPictureManager()
    private var controller: AVPictureInPictureController?

    public var isPossible: Bool { controller?.isPictureInPicturePossible ?? false }
    public var isActive: Bool { controller?.isPictureInPictureActive ?? false }
    public var onRestoreUserInterface: ((@escaping (Bool) -> Void) -> Void)?

    public func attach(playerLayer: AVPlayerLayer) {
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        if controller?.playerLayer !== playerLayer {
            controller?.stopPictureInPicture()
            if let next = AVPictureInPictureController(playerLayer: playerLayer) {
                next.delegate = self
                next.canStartPictureInPictureAutomaticallyFromInline = true
                controller = next
            }
        }
    }

    public func toggle() {
        guard let controller else { return }
        if controller.isPictureInPictureActive {
            controller.stopPictureInPicture()
        } else if controller.isPictureInPicturePossible {
            controller.startPictureInPicture()
        }
    }

    public func detach() {
        guard controller?.isPictureInPictureActive != true else { return }
        controller = nil
    }
}

// MARK: - AVPictureInPictureControllerDelegate
extension PictureInPictureManager: @preconcurrency AVPictureInPictureControllerDelegate {
    public func pictureInPictureController(
        _ pictureInPictureController: AVPictureInPictureController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void
    ) {
        if let restore = self.onRestoreUserInterface {
            restore(completionHandler)
        } else {
            completionHandler(true)
        }
    }
}
