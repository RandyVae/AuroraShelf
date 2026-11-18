import SwiftUI
import AVKit

public struct LivePlayerView: View {
    public let stream: LiveStreamItem
    @Binding public var isFullScreen: Bool
    @Binding public var isLandscape: Bool
    @Binding public var isFillMode: Bool
    public var topSafeArea: CGFloat = 0
    public var isChatPresented: Binding<Bool>? = nil
    public let onDismiss: () -> Void
    public var onToggleHUD: ((String) -> Void)?
    public var onComment: ((LiveComment) -> Void)?
    public var onCommentBridgeStateChange: ((Bool) -> Void)?

    @State private var player: AVPlayer?
    @State private var isPlaying: Bool = false
    @State private var isLoadingLive: Bool = true
    @State private var isWebFallbackActive: Bool = false
    @State private var liveError: String?
    @State private var showControls: Bool = true
    @State private var hideControlsTask: DispatchWorkItem?
    @State private var itemStatusObserver: NSKeyValueObservation?
    @State private var presentationSizeObserver: NSKeyValueObservation?
    @State private var playbackEndObserver: NSObjectProtocol?
    @State private var playbackStartedAt: Date?
    @State private var isPortraitStream: Bool = false
    @State private var streamRequestTask: Task<Void, Never>?
    @State private var activeStreamRequestID = UUID()
    private static var streamContextCache: [String: (url: URL, timestamp: Date)] = [:]
    @State private var showRoomChat = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOverEnabled
    private var coordinator: PlayerCoordinator { PlayerCoordinator.shared }

    public init(
        stream: LiveStreamItem,
        isFullScreen: Binding<Bool> = .constant(false),
        isLandscape: Binding<Bool> = .constant(false),
        isFillMode: Binding<Bool> = .constant(false),
        topSafeArea: CGFloat = 0,
        isChatPresented: Binding<Bool>? = nil,
        onDismiss: @escaping () -> Void,
        onToggleHUD: ((String) -> Void)? = nil,
        onComment: ((LiveComment) -> Void)? = nil,
        onCommentBridgeStateChange: ((Bool) -> Void)? = nil
    ) {
        self.stream = stream
        self._isFullScreen = isFullScreen
        self._isLandscape = isLandscape
        self._isFillMode = isFillMode
        self.topSafeArea = topSafeArea
        self.isChatPresented = isChatPresented
        self.onDismiss = onDismiss
        self.onToggleHUD = onToggleHUD
        self.onComment = onComment
        self.onCommentBridgeStateChange = onCommentBridgeStateChange
    }

    private var effectiveChatOpen: Bool {
        isChatPresented?.wrappedValue ?? showRoomChat
    }

    public var body: some View {
        GeometryReader { geo in
            let isPad = UIDevice.current.userInterfaceIdiom == .pad
            let safeTop = max(topSafeArea, DeviceLayout.safeAreaTop(in: geo.size))
            let needsTopOffset = !isFullScreen && (!isLandscape || isPad)

            ZStack(alignment: .top) {
                Color.black

                VStack(spacing: 0) {
                    if needsTopOffset {
                        Color.black.frame(height: safeTop)
                    }

                    ZStack {
                        if let player = player {
                            AuroraPlayerSurface(
                                player: player,
                                videoGravity: isFillMode ? .resizeAspectFill : .resizeAspect,
                                allowsPictureInPicture: false
                            )
                        } else if isWebFallbackActive && liveError == nil {
                            let fallbackURL: URL? = {
                                if let url = URL(string: stream.roomUrl), !stream.roomUrl.isEmpty { return url }
                                let encodedUser = stream.username.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? ""
                                guard !encodedUser.isEmpty else { return nil }
                                if stream.sourceID == LiveSourceCatalog.stripchatID {
                                    return URL(string: "https://zh.stripchat.com/\(encodedUser)")
                                } else {
                                    return URL(string: "https://www.hornystudents.com/\(encodedUser)/")
                                }
                            }()
                            if let defaultRoomUrl = fallbackURL {
                                StripchatLiveWebView(
                                    roomURL: defaultRoomUrl,
                                    isFillMode: isFillMode,
                                    isPlaying: $isPlaying,
                                    isLoading: $isLoadingLive,
                                    errorMessage: $liveError,
                                    onComment: onComment,
                                    onVideoDimension: { size in
                                        if size.width > 0 && size.height > 0 {
                                            isPortraitStream = (size.height > size.width)
                                        }
                                    }
                                )
                            } else {
                                Color.clear.onAppear {
                                    isLoadingLive = false
                                    liveError = "live.invalid_url".localized
                                }
                            }
                        }

                        if liveError == nil {
                            liveGestureOverlay(in: geo)
                        }

                        if isLoadingLive && liveError == nil {
                            ZStack {
                                AuroraCoverImage(url: stream.snapshotUrl, aspectRatio: nil, contentMode: .fill, cornerRadius: 0)
                                    .opacity(0.35)
                                    .blur(radius: 16)

                                VStack(spacing: AuroraMetrics.spacingL) {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brandSolid))
                                        .scaleEffect(1.4)

                                    Text(String(format: "live.connecting_user".localized, stream.username))
                                        .font(.headline)
                                        .foregroundStyle(.white)
                                }
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .transition(.opacity)
                            .allowsHitTesting(false)
                        }

                        if let error = liveError {
                            VStack(spacing: AuroraMetrics.spacingL) {
                                Image(systemName: "tv.slash.fill")
                                    .font(.largeTitle)
                                    .foregroundStyle(AuroraColor.brandSolid)

                                Text("live.stream_unavailable".localized)
                                    .font(.title3.bold())
                                    .foregroundStyle(.white)

                                Text(error)
                                    .font(.callout)
                                    .foregroundStyle(.secondary)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, AuroraMetrics.spacingXXXL)

                                Button(action: {
                                    fetchAndStartLiveStream()
                                }) {
                                    Text("category.reconnect".localized)
                                        .font(.headline)
                                        .padding(.horizontal, AuroraMetrics.spacingL)
                                        .padding(.vertical, AuroraMetrics.spacingXS)
                                }
                                .buttonStyle(.glassProminent)
                                .buttonBorderShape(.capsule)
                                .tint(AuroraColor.brandSolid)
                                .accessibilityLabel("live.reconnect_a11y".localized)
                            }
                            .padding(.top, safeTop + 44)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(Color.black.opacity(0.85))
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }

                if showControls || isLoadingLive || liveError != nil || voiceOverEnabled {
                    topControlsBar(in: geo)
                        .transition(.opacity)
                        .zIndex(20)
                }
            }
            .clipped()
        }
        .clipped()
        .onAppear {
            PictureInPictureManager.shared.stopAndReleasePiPIfNeeded()
            fetchAndStartLiveStream()
        }
        .onChange(of: coordinator.togglePlayPauseTrigger) { _, _ in
            guard let player else { return }
            if isPlaying {
                player.pause()
                isPlaying = false
                onToggleHUD?("player.pause".localized)
            } else {
                player.play()
                isPlaying = true
                onToggleHUD?("player.play".localized)
            }
        }
        .onChange(of: isPlaying) { _, playing in
            UIApplication.shared.isIdleTimerDisabled = playing
        }
        .onChange(of: isWebFallbackActive) { _, active in
            onCommentBridgeStateChange?(active)
        }
        .onChange(of: voiceOverEnabled) { _, enabled in
            if enabled {
                hideControlsTask?.cancel()
                showControls = true
            }
        }
        .onDisappear {
            cleanupLivePlayer()
            UIApplication.shared.isIdleTimerDisabled = false
        }
        .onChange(of: effectiveChatOpen) { _, isShowing in
            if isShowing {
                player?.pause()
            } else if isPlaying {
                player?.play()
            }
        }
        .sheet(isPresented: $showRoomChat) {
            LiveChatSheet(stream: stream)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
    }

    // MARK: - 顶部与右下角控制栏（iOS 27 Glass.clear 液态玻璃 + Apple TV 拓扑）
    @ViewBuilder
    private func topControlsBar(in geo: GeometryProxy) -> some View {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        let isPhysicallyLandscape = geo.size.width > geo.size.height
        let safeTop = max(topSafeArea, DeviceLayout.safeAreaTop(in: geo.size))
        let topPadding: CGFloat = isPad
            ? max(20, safeTop + 6)
            : ((isFullScreen && isPhysicallyLandscape) ? 14 : (safeTop + 6))
        let horizontalInset: CGFloat = isPad
            ? 20
            : ((isFullScreen && isPhysicallyLandscape) ? max(18, DeviceLayout.safeAreaHorizontalInsets.leading + 4) : 12)
        let bottomPadding: CGFloat = isFullScreen ? max(16, DeviceLayout.safeAreaBottom) : 10

        ZStack {
            VStack {
                Spacer()
                LinearGradient(
                    colors: [Color.clear, Color.black.opacity(0.28)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 64)
            }
            .allowsHitTesting(false)

            VStack {
                HStack(spacing: AuroraMetrics.spacingS) {
                    PlayerGlassIconButton(
                        systemName: isFullScreen ? "chevron.down" : "chevron.left",
                        accessibilityTitle: isFullScreen ? "live.exit_fullscreen".localized : "common.back".localized,
                        visualDiameter: 40,
                        symbolSize: 16,
                        symbolWeight: .bold
                    ) {
                        AuroraHaptics.impact(.medium)
                        if isFullScreen {
                            toggleAdaptiveFullscreen(in: geo)
                        } else {
                            PlayerOrientationCoordinator.shared.resetToPortraitIfNeeded()
                            cleanupLivePlayer()
                            onDismiss()
                        }
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(stream.username)
                                .font(.subheadline.bold())
                                .foregroundStyle(.white)
                                .shadow(color: .black.opacity(0.5), radius: 3, y: 1)
                                .lineLimit(1)
                                .truncationMode(.tail)

                            HStack(spacing: 3) {
                                Circle().fill(Color.white).frame(width: 5, height: 5)
                                Text("live.badge_live".localized)
                                    .font(.caption2.weight(.black))
                                    .foregroundStyle(.white)
                            }
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.red.opacity(0.88), in: Capsule())
                        }

                        HStack(spacing: 4) {
                            Text(LiveSourceCatalog.name(for: stream.sourceID))
                                .font(.caption2.bold())
                                .foregroundStyle(.white.opacity(0.88))
                                .shadow(color: .black.opacity(0.5), radius: 2, y: 1)
                                .lineLimit(1)

                            if let viewers = stream.viewCount.value {
                                Text("•")
                                    .font(.caption2)
                                    .foregroundStyle(Color.white.opacity(0.6))

                                Text(String(format: "live.online_count".localized, "\(viewers)"))
                                    .font(.caption2)
                                    .foregroundStyle(Color.white.opacity(0.85))
                                    .shadow(color: .black.opacity(0.5), radius: 2, y: 1)
                                    .lineLimit(1)
                            }
                        }
                    }
                    .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)

                    Spacer(minLength: 4)

                    GlassEffectContainer(spacing: 8) {
                        HStack(spacing: 8) {
                            controlCircleButton(
                                systemImage: "bubble.left.and.bubble.right",
                                label: "live.open_chat_a11y".localized
                            ) {
                                if let isChatPresented {
                                    isChatPresented.wrappedValue = true
                                } else {
                                    showRoomChat = true
                                }
                            }

                            AirPlayRoutePicker(tint: .white)
                                .frame(width: 38, height: 38)
                                .glassEffect(.clear.interactive(), in: .circle)
                                .frame(minWidth: 44, minHeight: 44)
                        }
                    }
                }

                Spacer()

                HStack {
                    Spacer()
                    GlassEffectContainer(spacing: 8) {
                        HStack(spacing: 8) {
                            controlCircleButton(
                                systemImage: isFillMode ? "rectangle.arrowtriangle.2.inward" : "rectangle.arrowtriangle.2.outward",
                                label: isFillMode ? "live.aspect_fit_btn".localized : "live.aspect_fill_btn".localized
                            ) {
                                AuroraHaptics.impact(.medium)
                                AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                                    isFillMode.toggle()
                                }
                                onToggleHUD?(isFillMode ? "live.hud_fill_short".localized : "live.hud_fit_short".localized)
                            }

                            if isFullScreen && !isPad {
                                controlCircleButton(
                                    systemImage: "rotate.right",
                                    label: isLandscape ? "live.switch_portrait".localized : "live.switch_landscape".localized
                                ) {
                                    AuroraHaptics.impact(.medium)
                                    let nextLandscape = !isLandscape
                                    PlayerOrientationCoordinator.shared.applyPhoneOrientation(landscape: nextLandscape)
                                    AuroraMotion.perform(AuroraMotion.cinema, reduceMotion: reduceMotion) {
                                        isLandscape = nextLandscape
                                    }
                                    onToggleHUD?(nextLandscape ? "live.hud_landscape".localized : "live.hud_portrait".localized)
                                }
                                .glassEffectTransition(.materialize)
                            }

                            controlCircleButton(
                                systemImage: isFullScreen ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right",
                                label: isFullScreen ? "live.exit_fullscreen".localized : "live.auto_fullscreen".localized
                            ) {
                                toggleAdaptiveFullscreen(in: geo)
                            }
                            .glassEffectTransition(.matchedGeometry)
                        }
                    }
                }
            }
            .padding(.horizontal, horizontalInset)
            .padding(.top, topPadding)
            .padding(.bottom, bottomPadding)
            .environment(\.colorScheme, .dark)
        }
    }

    @ViewBuilder
    private func liveGestureOverlay(in geo: GeometryProxy) -> some View {
        Color.clear
            .contentShape(Rectangle())
            .onTapGesture(count: 2) {
                AuroraHaptics.impact(.medium)
                AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                    isFillMode.toggle()
                }
                let msg = isFillMode ? "live.hud_fill_full".localized : "live.hud_fit_full".localized
                onToggleHUD?(msg)
            }
            .onTapGesture(count: 1) {
                AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                    showControls.toggle()
                }
                if showControls {
                    scheduleHideControls()
                }
            }
            .gesture(
                DragGesture(minimumDistance: 40)
                    .onEnded { value in
                        let dy = value.translation.height
                        let dx = value.translation.width
                        guard abs(dy) > abs(dx) * 1.3 else { return }
                        if dy < -56 && !isFullScreen {
                            toggleAdaptiveFullscreen(in: geo)
                        } else if dy > 56 && isFullScreen {
                            toggleAdaptiveFullscreen(in: geo)
                        }
                    }
            )
    }

    private func toggleAdaptiveFullscreen(in geo: GeometryProxy) {
        AuroraHaptics.impact(.medium)
        if isFullScreen {
            PlayerOrientationCoordinator.shared.applyPhoneOrientation(landscape: false)
            AuroraMotion.perform(AuroraMotion.cinema, reduceMotion: reduceMotion) {
                isFullScreen = false
                isLandscape = false
            }
            onToggleHUD?("live.hud_exited_fullscreen".localized)
        } else {
            let isPad = UIDevice.current.userInterfaceIdiom == .pad
            let targetLandscape = !isPad && !isPortraitStream
            PlayerOrientationCoordinator.shared.applyPhoneOrientation(landscape: targetLandscape)
            AuroraMotion.perform(AuroraMotion.cinema, reduceMotion: reduceMotion) {
                isFullScreen = true
                isLandscape = targetLandscape
            }
            if isPortraitStream {
                onToggleHUD?("live.hud_portrait_fullscreen".localized)
            } else {
                onToggleHUD?(targetLandscape ? "live.hud_landscape_fullscreen".localized : "live.hud_fill_fullscreen".localized)
            }
        }
    }

    private func controlCircleButton(systemImage: String, label: String, action: @escaping () -> Void) -> some View {
        PlayerGlassIconButton(
            systemName: systemImage,
            accessibilityTitle: label,
            visualDiameter: 38,
            symbolSize: 14,
            symbolWeight: .semibold,
            action: action
        )
    }

    private func scheduleHideControls() {
        hideControlsTask?.cancel()
        guard !voiceOverEnabled else { return }
        let task = DispatchWorkItem {
            guard !self.voiceOverEnabled else { return }
            AuroraMotion.perform(AuroraMotion.quick, reduceMotion: self.reduceMotion) {
                self.showControls = false
            }
        }
        hideControlsTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 4.0, execute: task)
    }

    // MARK: - 官方直播流拉取与原生硬件秒播

    private func fetchAndStartLiveStream() {
        streamRequestTask?.cancel()
        streamRequestTask = nil
        let requestID = UUID()
        activeStreamRequestID = requestID
        self.isLoadingLive = true
        self.isWebFallbackActive = false
        self.liveError = nil
        cleanupLivePlayer(cancelRequest: false)

        if stream.sourceID == LiveSourceCatalog.stripchatID {
            startStripchatPlayback(requestID: requestID)
        } else {
            startChaturbatePlayback(requestID: requestID)
        }
    }

    private func startStripchatPlayback(requestID: UUID) {
        let numericId = stream.id.replacingOccurrences(of: "sc-", with: "").filter(\.isNumber)
        let rawHls = stream.hlsStreamUrl
        streamRequestTask = Task { @MainActor in
            defer {
                if activeStreamRequestID == requestID {
                    streamRequestTask = nil
                }
            }
            let outcome = await StripchatStreamAuthenticator.shared.resolvePlayableURL(
                rawHls: rawHls,
                fallbackNumericId: numericId
            )
            guard !Task.isCancelled, activeStreamRequestID == requestID else { return }
            switch outcome {
            case .authenticated(let authenticatedURL):
                startNativePlayback(
                    hlsUrl: authenticatedURL,
                    referer: "https://zh.stripchat.com/",
                    requestID: requestID
                )
            case .offlineOrPrivate:
                self.player = nil
                self.isLoadingLive = false
                self.isWebFallbackActive = false
                self.liveError = "live.err_offline_or_private".localized
            case .webFallback:
                self.player = nil
                self.isLoadingLive = false
                self.isWebFallbackActive = true
                self.liveError = nil
            }
        }
    }

    private func startChaturbatePlayback(requestID: UUID) {
        let username = stream.username
        guard !username.isEmpty,
              let apiUrl = URL(string: "https://chaturbate.com/api/chatvideocontext/\(username)/") else {
            self.isLoadingLive = false
            self.isWebFallbackActive = false
            self.liveError = "live.err_invalid_user".localized
            return
        }

        if let cached = Self.streamContextCache[username], Date().timeIntervalSince(cached.timestamp) < 10.0 {
            startNativePlayback(hlsUrl: cached.url, referer: "https://chaturbate.com/", requestID: requestID)
            return
        }

        streamRequestTask = Task { @MainActor in
            defer {
                if activeStreamRequestID == requestID {
                    streamRequestTask = nil
                }
            }

            do {
                var hlsFoundUrl: URL? = nil

                var apiReq = URLRequest(url: apiUrl, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10.0)
                apiReq.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15", forHTTPHeaderField: "User-Agent")
                apiReq.setValue("https://chaturbate.com/\(username)/", forHTTPHeaderField: "Referer")
                apiReq.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")
                apiReq.setValue("agreeterms=1; isAdult=1; hasConfirmedAge=1; lang=en;", forHTTPHeaderField: "Cookie")

                if let (apiData, apiResp) = try? await URLSession.permissiveShared.data(for: apiReq),
                   let httpResp = apiResp as? HTTPURLResponse, (200...299).contains(httpResp.statusCode),
                   let json = try? JSONSerialization.jsonObject(with: apiData) as? [String: Any] {
                    try Task.checkCancellation()
                    guard activeStreamRequestID == requestID else { return }

                    let status = (json["room_status"] as? String)?.lowercased() ?? "public"
                    if status == "private" {
                        self.isLoadingLive = false
                        self.isWebFallbackActive = false
                        self.liveError = "live.err_private_show".localized
                        return
                    } else if status == "offline" {
                        self.isLoadingLive = false
                        self.isWebFallbackActive = false
                        self.liveError = "live.err_offline".localized
                        return
                    } else if status == "away" {
                        self.isLoadingLive = false
                        self.isWebFallbackActive = false
                        self.liveError = "live.err_away".localized
                        return
                    }

                    if let hlsSource = json["hls_source"] as? String, !hlsSource.isEmpty,
                       let directUrl = URL(string: hlsSource) {
                        hlsFoundUrl = directUrl
                    }
                }

                if hlsFoundUrl == nil {
                    let candidatePages = [
                        URL(string: "https://chaturbate.com/\(username)/"),
                        URL(string: "https://www.hornystudents.com/\(username)/")
                    ].compactMap { $0 }

                    for roomPageUrl in candidatePages {
                        if hlsFoundUrl != nil { break }
                        var roomReq = URLRequest(url: roomPageUrl, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 12.0)
                        roomReq.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15", forHTTPHeaderField: "User-Agent")
                        roomReq.setValue("https://chaturbate.com/", forHTTPHeaderField: "Referer")
                        roomReq.setValue("agreeterms=1; isAdult=1; hasConfirmedAge=1; lang=en;", forHTTPHeaderField: "Cookie")

                        if let (pageData, _) = try? await URLSession.permissiveShared.data(for: roomReq),
                           let rawHtml = String(data: pageData, encoding: .utf8) {
                            try Task.checkCancellation()
                            guard activeStreamRequestID == requestID else { return }

                            if rawHtml.contains(#""room_status": "private""#) ||
                               rawHtml.contains(#""room_status":"private""#) ||
                               rawHtml.contains(#"\u0022room_status\u0022: \u0022private\u0022"#) ||
                               rawHtml.contains(#"\u0022room_status\u0022:\u0022private\u0022"#) {
                                self.isLoadingLive = false
                                self.isWebFallbackActive = false
                                self.liveError = "live.err_private_show".localized
                                return
                            }
                            if rawHtml.contains(#""room_status": "offline""#) ||
                               rawHtml.contains(#""room_status":"offline""#) ||
                               rawHtml.contains(#"\u0022room_status\u0022: \u0022offline\u0022"#) ||
                               rawHtml.contains(#"\u0022room_status\u0022:\u0022offline\u0022"#) {
                                self.isLoadingLive = false
                                self.isWebFallbackActive = false
                                self.liveError = "live.err_offline".localized
                                return
                            }
                            if rawHtml.contains(#""room_status": "away""#) ||
                               rawHtml.contains(#""room_status":"away""#) ||
                               rawHtml.contains(#"\u0022room_status\u0022: \u0022away\u0022"#) ||
                               rawHtml.contains(#"\u0022room_status\u0022:\u0022away\u0022"#) {
                                self.isLoadingLive = false
                                self.isWebFallbackActive = false
                                self.liveError = "live.err_away".localized
                                return
                            }

                            let unescaped = Self.unescapeUnicodeEntities(rawHtml)
                            if let hlsRegex = try? NSRegularExpression(pattern: #""hls_source"\s*:\s*"(https?:\/\/[^"\s\\]+?\.m3u8[^"\s\\]*)""#),
                               let mMatch = hlsRegex.firstMatch(in: unescaped, range: NSRange(location: 0, length: (unescaped as NSString).length)) {
                                let rawM3u8 = (unescaped as NSString).substring(with: mMatch.range(at: 1))
                                if let directUrl = URL(string: rawM3u8) {
                                    hlsFoundUrl = directUrl
                                }
                            }
                        }
                    }
                }

                guard let finalHlsUrl = hlsFoundUrl else {
                    self.player = nil
                    self.isLoadingLive = false
                    self.isWebFallbackActive = true
                    self.liveError = nil
                    return
                }

                Self.streamContextCache[username] = (finalHlsUrl, Date())
                startNativePlayback(hlsUrl: finalHlsUrl, referer: "https://chaturbate.com/", requestID: requestID)
            } catch {
                guard !Task.isCancelled, activeStreamRequestID == requestID else { return }
                self.player = nil
                self.isLoadingLive = false
                self.isWebFallbackActive = true
                self.liveError = nil
            }
        }
    }

    private static func unescapeUnicodeEntities(_ str: String) -> String {
        var result = ""
        result.reserveCapacity(str.count)
        var index = str.startIndex
        while index < str.endIndex {
            if str[index] == "\\" {
                let nextIndex = str.index(after: index)
                if nextIndex < str.endIndex {
                    let nextChar = str[nextIndex]
                    if nextChar == "u" {
                        let uStart = str.index(index, offsetBy: 2)
                        if let uEnd = str.index(uStart, offsetBy: 4, limitedBy: str.endIndex) {
                            let hex = String(str[uStart..<uEnd])
                            if let code = UInt32(hex, radix: 16), let scalar = UnicodeScalar(code) {
                                result.append(Character(scalar))
                                index = uEnd
                                continue
                            }
                        }
                    } else if nextChar == "/" {
                        result.append("/")
                        index = str.index(after: nextIndex)
                        continue
                    } else if nextChar == "\"" {
                        result.append("\"")
                        index = str.index(after: nextIndex)
                        continue
                    } else if nextChar == "\\" {
                        result.append("\\")
                        index = str.index(after: nextIndex)
                        continue
                    }
                }
            }
            result.append(str[index])
            index = str.index(after: index)
        }
        return result
    }

    private func startNativePlayback(hlsUrl: URL, referer: String = "https://chaturbate.com/", requestID: UUID) {
        guard activeStreamRequestID == requestID else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback, options: [])
        try? AVAudioSession.sharedInstance().setActive(true)
        let origin = referer.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let headers: [String: String] = [
            "Referer": referer,
            "Origin": origin,
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        ]
        let asset = AVURLAsset(url: hlsUrl, options: ["AVURLAssetHTTPHeaderFieldsKey": headers])
        let playerItem = AVPlayerItem(asset: asset)
        let avPlayer = AVPlayer(playerItem: playerItem)
        self.playbackStartedAt = nil
        self.player = avPlayer

        itemStatusObserver = playerItem.observe(\.status, options: [.new, .initial]) { item, _ in
            Task { @MainActor in
                guard self.player?.currentItem === item else { return }
                if item.status == .readyToPlay {
                    if self.playbackStartedAt == nil {
                        self.playbackStartedAt = Date()
                    }
                    self.isLoadingLive = false
                    self.isPlaying = true
                    let size = item.presentationSize
                    if size.width > 0 && size.height > 0 {
                        self.isPortraitStream = (size.height > size.width)
                    }
                    UIApplication.shared.isIdleTimerDisabled = true
                    avPlayer.play()
                    self.scheduleHideControls()
                } else if item.status == .failed {
                    self.itemStatusObserver?.invalidate()
                    self.itemStatusObserver = nil
                    self.presentationSizeObserver?.invalidate()
                    self.presentationSizeObserver = nil
                    avPlayer.pause()
                    avPlayer.replaceCurrentItem(with: nil)
                    self.player = nil
                    self.isLoadingLive = false
                    if self.liveError == nil {
                        self.isWebFallbackActive = true
                    }
                }
            }
        }

        playbackEndObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: playerItem,
            queue: .main
        ) { _ in
            Task { @MainActor in
                guard self.player?.currentItem === playerItem else { return }
                let elapsed = self.playbackStartedAt.map { Date().timeIntervalSince($0) } ?? 0
                self.cleanupLivePlayer(cancelRequest: false)
                self.isLoadingLive = false
                if elapsed < 25.0 {
                    self.isWebFallbackActive = true
                } else {
                    self.liveError = "live.err_offline".localized
                }
            }
        }

        presentationSizeObserver = playerItem.observe(\.presentationSize, options: [.new]) { item, _ in
            Task { @MainActor in
                let size = item.presentationSize
                if size.width > 0 && size.height > 0 {
                    self.isPortraitStream = (size.height > size.width)
                }
            }
        }
    }

    private func cleanupLivePlayer(cancelRequest: Bool = true) {
        UIApplication.shared.isIdleTimerDisabled = false
        hideControlsTask?.cancel()
        hideControlsTask = nil
        if cancelRequest {
            streamRequestTask?.cancel()
            streamRequestTask = nil
            activeStreamRequestID = UUID()
            isWebFallbackActive = false
        }
        itemStatusObserver?.invalidate()
        itemStatusObserver = nil
        presentationSizeObserver?.invalidate()
        presentationSizeObserver = nil
        if let endObs = playbackEndObserver {
            NotificationCenter.default.removeObserver(endObs)
            playbackEndObserver = nil
        }
        playbackStartedAt = nil
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        player = nil
        isPlaying = false
        if cancelRequest {
            Task.detached(priority: .utility) {
                try? AVAudioSession.sharedInstance().setActive(false, options: [.notifyOthersOnDeactivation])
            }
        }
    }
}
