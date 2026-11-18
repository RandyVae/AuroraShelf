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
    @ObservationIgnored private var retainedDuration: Double = 0

    public private(set) var isPossible: Bool = false
    public private(set) var isActive: Bool = false
    public private(set) var isStartingPiP: Bool = false
    public private(set) var activePiPVideo: VideoItem?
    @ObservationIgnored public var onRestoreUserInterface: ((@escaping (Bool) -> Void) -> Void)?

    public var isPiPActiveOrStarting: Bool {
        isActive || isStartingPiP
    }

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
                isStartingPiP = false
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
                        if active {
                            self?.isStartingPiP = false
                        }
                    }
                }
            }
        }
    }

    public func toggle() {
        guard let controller else { return }
        if controller.isPictureInPictureActive {
            isStartingPiP = false
            controller.stopPictureInPicture()
        } else if controller.isPictureInPicturePossible {
            isStartingPiP = true
            controller.startPictureInPicture()
        }
    }

    public func retainPlayerForPiP(player: AVPlayer, video: VideoItem, duration: Double = 0) {
        retainedPlayer = player
        activePiPVideo = video
        retainedDuration = duration
    }

    public func consumeRetainedPlayer(for video: VideoItem) -> AVPlayer? {
        guard let retainedPlayer else { return nil }
        if activePiPVideo?.playbackUrl == video.playbackUrl {
            self.retainedPlayer = nil
            self.activePiPVideo = nil
            self.retainedDuration = 0
            return retainedPlayer
        }
        flushRetainedPlayerProgressIfNeeded()
        controller?.stopPictureInPicture()
        retainedPlayer.pause()
        retainedPlayer.replaceCurrentItem(with: nil)
        self.retainedPlayer = nil
        self.activePiPVideo = nil
        self.retainedDuration = 0
        self.isStartingPiP = false
        return nil
    }

    public func stopAndReleasePiPIfNeeded() {
        flushRetainedPlayerProgressIfNeeded()
        if controller?.isPictureInPictureActive == true {
            controller?.stopPictureInPicture()
        }
        retainedPlayer?.pause()
        retainedPlayer?.replaceCurrentItem(with: nil)
        retainedPlayer = nil
        activePiPVideo = nil
        retainedDuration = 0
        isActive = false
        isStartingPiP = false
    }

    public func detach() {
        guard controller?.isPictureInPictureActive != true && !isStartingPiP else { return }
        possibleObserver?.invalidate()
        possibleObserver = nil
        activeObserver?.invalidate()
        activeObserver = nil
        controller = nil
        isPossible = false
        isActive = false
        isStartingPiP = false
    }

    private func flushRetainedPlayerProgressIfNeeded() {
        guard let player = retainedPlayer, let video = activePiPVideo else { return }
        let posSec = CMTimeGetSeconds(player.currentTime())
        let itemDur = player.currentItem.map { CMTimeGetSeconds($0.duration) } ?? 0
        let durSec = (itemDur.isFinite && itemDur > 0) ? itemDur : retainedDuration
        if posSec.isFinite && posSec > 2.0 && durSec.isFinite && durSec > 5.0 {
            if posSec >= durSec - 1.0 {
                PlaybackProgressStore.shared.markCompleted(for: video)
            } else {
                PlaybackProgressStore.shared.updateProgress(
                    video: video,
                    positionMs: Int64(posSec * 1000),
                    durationMs: Int64(durSec * 1000)
                )
                PlaybackProgressStore.shared.flushPendingPersist()
            }
        }
    }
}

// MARK: - AVPictureInPictureControllerDelegate
extension PictureInPictureManager: @preconcurrency AVPictureInPictureControllerDelegate {
    public func pictureInPictureControllerWillStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        isStartingPiP = true
    }

    public func pictureInPictureControllerDidStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        isStartingPiP = false
        isActive = true
    }

    public func pictureInPictureControllerDidStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        isStartingPiP = false
        isActive = false
        if let player = retainedPlayer {
            flushRetainedPlayerProgressIfNeeded()
            player.pause()
            player.replaceCurrentItem(with: nil)
            retainedPlayer = nil
            activePiPVideo = nil
            retainedDuration = 0
            Task.detached(priority: .utility) {
                try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
            }
        }
    }

    public func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController, failedToStartPictureInPictureWithError error: any Error) {
        isStartingPiP = false
        isActive = false
        retainedPlayer = nil
        activePiPVideo = nil
        retainedDuration = 0
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
