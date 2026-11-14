import AVKit
import Observation

/// Owns the system Picture in Picture controller for the custom AVPlayer surface.
@Observable
@MainActor
public final class PictureInPictureManager: NSObject {
    public static let shared = PictureInPictureManager()
    @ObservationIgnored private var controller: AVPictureInPictureController?
    @ObservationIgnored private var possibleObserver: NSKeyValueObservation?
    @ObservationIgnored private var activeObserver: NSKeyValueObservation?
    @ObservationIgnored private var retainedPlayer: AVPlayer?

    public private(set) var isPossible: Bool = false
    public private(set) var isActive: Bool = false
    public private(set) var activePiPVideo: VideoItem? = nil
    @ObservationIgnored public var onRestoreUserInterface: ((@escaping (Bool) -> Void) -> Void)?

    public func attach(playerLayer: AVPlayerLayer) {
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        if controller?.playerLayer !== playerLayer {
            if controller?.isPictureInPictureActive == true {
                controller?.stopPictureInPicture()
            }
            possibleObserver?.invalidate()
            activeObserver?.invalidate()
            if let next = AVPictureInPictureController(playerLayer: playerLayer) {
                next.delegate = self
                next.canStartPictureInPictureAutomaticallyFromInline = true
                controller = next
                isPossible = next.isPictureInPicturePossible
                isActive = next.isPictureInPictureActive
                possibleObserver = next.observe(\.isPictureInPicturePossible, options: [.new, .initial]) { [weak self] pip, _ in
                    let possible = pip.isPictureInPicturePossible
                    Task { @MainActor [weak self] in
                        self?.isPossible = possible
                    }
                }
                activeObserver = next.observe(\.isPictureInPictureActive, options: [.new, .initial]) { [weak self] pip, _ in
                    let active = pip.isPictureInPictureActive
                    Task { @MainActor [weak self] in
                        self?.isActive = active
                    }
                }
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

    public func retainPlayerForPiP(player: AVPlayer, video: VideoItem) {
        retainedPlayer = player
        activePiPVideo = video
    }

    public func consumeRetainedPlayer(for video: VideoItem) -> AVPlayer? {
        guard let retainedPlayer else { return nil }
        if activePiPVideo?.playbackUrl == video.playbackUrl {
            self.retainedPlayer = nil
            return retainedPlayer
        }
        // Opening a different video replaces any lingering PiP playback.
        controller?.stopPictureInPicture()
        retainedPlayer.pause()
        self.retainedPlayer = nil
        self.activePiPVideo = nil
        return nil
    }

    public func stopAndReleasePiPIfNeeded() {
        if controller?.isPictureInPictureActive == true {
            controller?.stopPictureInPicture()
        }
        retainedPlayer?.pause()
        retainedPlayer = nil
        activePiPVideo = nil
        isActive = false
    }

    public func detach() {
        guard controller?.isPictureInPictureActive != true else { return }
        possibleObserver?.invalidate()
        possibleObserver = nil
        activeObserver?.invalidate()
        activeObserver = nil
        controller = nil
        isPossible = false
        isActive = false
    }
}

// MARK: - AVPictureInPictureControllerDelegate
extension PictureInPictureManager: @preconcurrency AVPictureInPictureControllerDelegate {
    public func pictureInPictureControllerDidStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        isActive = true
    }

    public func pictureInPictureControllerDidStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        isActive = false
        // Release retained background player if PiP was closed rather than restored into UI.
        if let player = retainedPlayer {
            player.pause()
            retainedPlayer = nil
            activePiPVideo = nil
        }
    }

    public func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, failedToStartPictureInPictureWithError error: any Error) {
        isActive = false
        retainedPlayer = nil
        activePiPVideo = nil
    }

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
