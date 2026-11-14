import SwiftUI
import AVKit

public struct LivePlayerView: View {
    public let stream: LiveStreamItem
    @Binding public var isFullScreen: Bool
    @Binding public var isLandscape: Bool
    @Binding public var isFillMode: Bool
    public var topSafeArea: CGFloat = 0
    public let onDismiss: () -> Void
    public var onToggleHUD: ((String) -> Void)?
    public var onComment: ((LiveComment) -> Void)?

    @State private var player: AVPlayer?
    @State private var isPlaying: Bool = false
    @State private var isLoadingLive: Bool = true
    @State private var isWebFallbackActive: Bool = false
    @State private var liveError: String?
    @State private var showControls: Bool = true
    @State private var hideControlsTask: DispatchWorkItem?
    @State private var itemStatusObserver: NSKeyValueObservation?
    @State private var presentationSizeObserver: NSKeyValueObservation?
    @State private var isPortraitStream: Bool = false
    @State private var streamRequestTask: Task<Void, Never>?
    @State private var activeStreamRequestID = UUID()
    private static var streamContextCache: [String: (url: URL, timestamp: Date)] = [:]
    @State private var showRoomChat = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(
        stream: LiveStreamItem,
        isFullScreen: Binding<Bool> = .constant(false),
        isLandscape: Binding<Bool> = .constant(false),
        isFillMode: Binding<Bool> = .constant(false),
        topSafeArea: CGFloat = 0,
        onDismiss: @escaping () -> Void,
        onToggleHUD: ((String) -> Void)? = nil,
        onComment: ((LiveComment) -> Void)? = nil
    ) {
        self.stream = stream
        self._isFullScreen = isFullScreen
        self._isLandscape = isLandscape
        self._isFillMode = isFillMode
        self.topSafeArea = topSafeArea
        self.onDismiss = onDismiss
        self.onToggleHUD = onToggleHUD
        self.onComment = onComment
    }

    public var body: some View {
        GeometryReader { geo in
            let safeTop = max(topSafeArea, DeviceLayout.safeAreaTop(in: geo.size))
            let needsTopOffset = !isFullScreen && !isLandscape

            ZStack(alignment: .top) {
                // 背景纯黑
                Color.black

                VStack(spacing: 0) {
                    // 非全屏竖屏嵌入态：顶部垫入 safeTop 高度的纯黑安全区隔离底衬，使视频物理起始于灵动岛/状态栏下方，彻底杜绝向上位移
                    if needsTopOffset {
                        Color.black.frame(height: safeTop)
                    }

                    ZStack {
                        // 核心播放视口层：优先采用原生 AVPlayer 硬件解码秒开（0.3s 直连）
                        if let player = player {
                            AuroraPlayerSurface(
                                player: player,
                                videoGravity: isFillMode ? .resizeAspectFill : .resizeAspect
                            )
                        } else if isWebFallbackActive && liveError == nil {
                            // 纯净无头 WebRTC 视口（原生 AVPlayer 403 握手受限自动平滑降级，或平台专属推流）
                            let defaultRoomUrl: URL = {
                                if let url = URL(string: stream.roomUrl), !stream.roomUrl.isEmpty { return url }
                                if stream.sourceID == LiveSourceCatalog.stripchatID {
                                    return URL(string: "https://zh.stripchat.com/\(stream.username)")!
                                } else {
                                    return URL(string: "https://www.hornystudents.com/\(stream.username)/")!
                                }
                            }()
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
                        }

                        // 原生手势感知层（双击切换画幅、单击唤出控制栏）
                        if liveError == nil {
                            Color.clear
                                .contentShape(Rectangle())
                                .onTapGesture(count: 2) {
                                    let impact = UIImpactFeedbackGenerator(style: .medium)
                                    impact.impactOccurred()
                                    withAnimation(AuroraMotion.selection(reduceMotion: reduceMotion)) {
                                        isFillMode.toggle()
                                    }
                                    onToggleHUD?(isFillMode ? "已切换为填满全屏（无黑边）" : "已切换为自适应画幅（原片比例）")
                                }
                                .onTapGesture(count: 1) {
                                    withAnimation(reduceMotion ? nil : AuroraMotion.quick) {
                                        showControls.toggle()
                                    }
                                    if showControls {
                                        scheduleHideControls()
                                    }
                                }
                        }

                        // 加载中过渡底图与 Spinner（视频起播后平滑淡出）
                        if isLoadingLive && liveError == nil {
                            ZStack {
                                AuroraCoverImage(url: stream.snapshotUrl, aspectRatio: nil, contentMode: .fill, cornerRadius: 0)
                                    .opacity(0.35)
                                    .blur(radius: 16)

                                VStack(spacing: AuroraMetrics.spacingL) {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                                        .scaleEffect(1.4)

                                    Text("正在连接 \(stream.username) 的直播流…")
                                        .font(.headline)
                                        .foregroundStyle(.white)
                                }
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .transition(.opacity)
                            .allowsHitTesting(false)
                        }

                        // 错误提示状态层（向下避让顶栏控制按键，杜绝视觉穿透与重叠）
                        if let error = liveError {
                            VStack(spacing: AuroraMetrics.spacingL) {
                                Image(systemName: "tv.slash.fill")
                                    .font(.system(size: 40))
                                    .foregroundStyle(AuroraColor.brand)

                                Text("直播暂不可用")
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
                                    Text("重新连接")
                                        .font(.headline)
                                        .padding(.horizontal, AuroraMetrics.spacingL)
                                        .padding(.vertical, AuroraMetrics.spacingXS)
                                }
                                .buttonStyle(.glassProminent)
                                .buttonBorderShape(.capsule)
                                .tint(AuroraColor.brand)
                                .accessibilityLabel("重新连接直播流")
                            }
                            .padding(.top, safeTop + 44)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(Color.black.opacity(0.85))
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }

                // 浮动控制栏（带磨砂与柔和暗调渐变避让）
                if showControls || isLoadingLive || liveError != nil {
                    topControlsBar(in: geo)
                        .transition(.opacity)
                        .zIndex(20)
                }
            }
            .clipped()
        }
        .clipped()
        .onAppear {
            fetchAndStartLiveStream()
        }
        .onDisappear {
            cleanupLivePlayer()
            UIApplication.shared.isIdleTimerDisabled = false
        }
        .onChange(of: showRoomChat) { _, isShowing in
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

    // MARK: - 顶部控制栏
    @ViewBuilder
    private func topControlsBar(in geo: GeometryProxy) -> some View {
        let safeTop = max(topSafeArea, DeviceLayout.safeAreaTop(in: geo.size))
        let topPadding: CGFloat = isLandscape ? 16 : (safeTop + 6)
        let gradientHeight: CGFloat = max(topPadding + 64, 130)

        ZStack(alignment: .top) {
            // 暗调渐变底，确保强对比度与清晰度
            LinearGradient(
                colors: [Color.black.opacity(0.85), Color.black.opacity(0.35), Color.clear],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: gradientHeight)
            .allowsHitTesting(false)

            HStack(spacing: AuroraMetrics.spacingM) {
                // 左侧：返回 / 退出全屏按钮（iOS 27 原生 Liquid Glass 按键样式）
                Button(action: {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    if isFullScreen {
                        toggleAdaptiveFullscreen(in: geo)
                    } else {
                        cleanupLivePlayer()
                        onDismiss()
                        dismiss()
                    }
                }) {
                    Image(systemName: isFullScreen ? "arrow.down.right.and.arrow.up.left" : "chevron.left")
                        .font(.system(size: 16, weight: .bold))
                        .frame(width: 26, height: 26)
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .frame(minWidth: 44, minHeight: 44)
                .accessibilityLabel(isFullScreen ? "退出全屏" : "返回")

                // 主播信息与 LIVE 状态（严格单行、杜绝换行挤压）
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(stream.username)
                            .font(.subheadline.bold())
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .truncationMode(.tail)

                        HStack(spacing: 3) {
                            Circle().fill(Color.red).frame(width: 5, height: 5)
                            Text("LIVE")
                                .font(.system(size: 9, weight: .black))
                                .foregroundStyle(.white)
                        }
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Color.red.opacity(0.85), in: Capsule())
                    }

                    HStack(spacing: 4) {
                        Text(stream.sourceID == LiveSourceCatalog.stripchatID ? "Stripchat" : "HornyStudents")
                            .font(.caption2.bold())
                            .foregroundStyle(AuroraColor.brandLabel)
                            .lineLimit(1)

                        Text("•")
                            .font(.caption2)
                            .foregroundStyle(Color.white.opacity(0.5))

                        Text("在线 \(stream.viewCount)")
                            .font(.caption2)
                            .foregroundStyle(Color.white.opacity(0.8))
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: 220, alignment: .leading)
                .layoutPriority(1)

                Spacer()

                // 右侧功能键区（iOS 27 GlassEffectContainer 融合按键群组）
                GlassEffectContainer(spacing: 6) {
                    HStack(spacing: 6) {
                        // 1. 聊天室按钮
                        controlCircleButton(systemImage: "bubble.left.and.bubble.right", label: "打开聊天室") {
                            showRoomChat = true
                        }

                        // 2. 画幅切换（Fit / Fill）
                        controlCircleButton(
                            systemImage: isFillMode ? "arrow.down.forward.and.arrow.up.backward" : "arrow.up.left.and.arrow.down.right",
                            label: isFillMode ? "自适应画幅" : "填满全屏"
                        ) {
                            let impact = UIImpactFeedbackGenerator(style: .medium)
                            impact.impactOccurred()
                            withAnimation(AuroraMotion.selection(reduceMotion: reduceMotion)) {
                                isFillMode.toggle()
                            }
                            onToggleHUD?(isFillMode ? "已切换为填满全屏（无黑边）" : "已切换为自适应画幅（原片比例）")
                        }

                        // 3. 全屏模式下的横竖屏手动微调旋转
                        if isFullScreen {
                            controlCircleButton(
                                systemImage: isLandscape ? "iphone.portrait" : "iphone.landscape",
                                label: isLandscape ? "切换竖屏" : "切换横屏"
                            ) {
                                let impact = UIImpactFeedbackGenerator(style: .medium)
                                impact.impactOccurred()
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                                    isLandscape.toggle()
                                }
                                onToggleHUD?(isLandscape ? "已切换为横屏沉浸" : "已切换为竖屏模式")
                            }
                        }

                        // 4. 自适应全屏按钮（纵向流纵向全屏，横向流横向全屏）
                        controlCircleButton(
                            systemImage: isFullScreen ? "arrow.down.right.and.arrow.up.left" : "viewfinder",
                            label: isFullScreen ? "退出全屏" : "自适应全屏"
                        ) {
                            toggleAdaptiveFullscreen(in: geo)
                        }

                        // 5. AirPlay 原生投屏
                        AirPlayRoutePicker()
                            .frame(width: 38, height: 38)
                            .glassEffect(.regular.interactive(), in: .circle)
                            .frame(minWidth: 44, minHeight: 44)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, topPadding)
            .environment(\.colorScheme, .dark)
        }
    }

    private func toggleAdaptiveFullscreen(in geo: GeometryProxy) {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            if isFullScreen {
                isFullScreen = false
                isLandscape = false
                onToggleHUD?("已退出全屏")
            } else {
                isFullScreen = true
                if isPortraitStream {
                    // 纵向直播流：自适应纵向全屏，不旋转
                    isLandscape = false
                    onToggleHUD?("自适应全屏 · 纵向铺满")
                } else {
                    // 横向直播流：如果是在手机竖屏下，自动横向全屏；若在 iPad 或横握时无需强制旋转
                    let isPhonePortrait = geo.size.width < geo.size.height
                    isLandscape = isPhonePortrait
                    onToggleHUD?(isLandscape ? "自适应全屏 · 横屏沉浸" : "自适应全屏 · 铺满")
                }
            }
        }
    }

    private func controlCircleButton(systemImage: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 14, weight: .bold))
                .frame(width: 26, height: 26)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .frame(minWidth: 44, minHeight: 44)
        .accessibilityLabel(label)
    }

    private func scheduleHideControls() {
        hideControlsTask?.cancel()
        let task = DispatchWorkItem {
            withAnimation(.easeInOut(duration: 0.25)) {
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
            var directHlsUrl: URL? = nil
            if let hlsStr = stream.hlsStreamUrl, let url = URL(string: hlsStr) {
                directHlsUrl = url
            } else {
                let numericId = stream.id.replacingOccurrences(of: "sc-", with: "")
                if !numericId.isEmpty && Int(numericId) != nil {
                    directHlsUrl = URL(string: "https://edge-hls.doppiocdn.media/hls/\(numericId)/master/\(numericId).m3u8")
                }
            }
            if let targetUrl = directHlsUrl {
                startNativePlayback(hlsUrl: targetUrl, referer: "https://zh.stripchat.com/", requestID: requestID)
            } else {
                self.player = nil
                self.isLoadingLive = false
                self.isWebFallbackActive = true
                self.liveError = nil
            }
        } else {
            startChaturbatePlayback(requestID: requestID)
        }
    }

    private func startChaturbatePlayback(requestID: UUID) {
        let username = stream.username
        guard !username.isEmpty,
              let apiUrl = URL(string: "https://chaturbate.com/api/chatvideocontext/\(username)/") else {
            self.isLoadingLive = false
            self.isWebFallbackActive = false
            self.liveError = "无效的主播用户名"
            return
        }

        // 10 秒短生命周期内存缓存拦截：避免切房重复建立流解析往返
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

                // 1. 优先直接请求官方轻量 JSON 接口（~2KB，响应耗时 <200ms），秒级识别开播与私密秀状态
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
                        self.liveError = "主播当前正在进行私密秀（需付费观看）"
                        return
                    } else if status == "offline" {
                        self.isLoadingLive = false
                        self.isWebFallbackActive = false
                        self.liveError = "主播当前已离线或暂未开播"
                        return
                    } else if status == "away" {
                        self.isLoadingLive = false
                        self.isWebFallbackActive = false
                        self.liveError = "主播暂时离开，请稍后刷新"
                        return
                    }

                    if let hlsSource = json["hls_source"] as? String, !hlsSource.isEmpty,
                       let directUrl = URL(string: hlsSource) {
                        hlsFoundUrl = directUrl
                    }
                }

                // 2. 备用线路：当 API 受限时，解析 HTML 页面中的 window.initialRoomDossier
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

                            // 2.1 状态检测（规避进入私密秀房间陷入无限加载）
                            if rawHtml.contains(#""room_status": "private""#) ||
                               rawHtml.contains(#""room_status":"private""#) ||
                               rawHtml.contains(#"\u0022room_status\u0022: \u0022private\u0022"#) ||
                               rawHtml.contains(#"\u0022room_status\u0022:\u0022private\u0022"#) {
                                self.isLoadingLive = false
                                self.isWebFallbackActive = false
                                self.liveError = "主播当前正在进行私密秀（需付费观看）"
                                return
                            }
                            if rawHtml.contains(#""room_status": "offline""#) ||
                               rawHtml.contains(#""room_status":"offline""#) ||
                               rawHtml.contains(#"\u0022room_status\u0022: \u0022offline\u0022"#) ||
                               rawHtml.contains(#"\u0022room_status\u0022:\u0022offline\u0022"#) {
                                self.isLoadingLive = false
                                self.isWebFallbackActive = false
                                self.liveError = "主播当前已离线或暂未开播"
                                return
                            }
                            if rawHtml.contains(#""room_status": "away""#) ||
                               rawHtml.contains(#""room_status":"away""#) ||
                               rawHtml.contains(#"\u0022room_status\u0022: \u0022away\u0022"#) ||
                               rawHtml.contains(#"\u0022room_status\u0022:\u0022away\u0022"#) {
                                self.isLoadingLive = false
                                self.isWebFallbackActive = false
                                self.liveError = "主播暂时离开，请稍后刷新"
                                return
                            }

                            // 2.2 解码 Unicode 转义符并提取 hls_source
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
                    // 未直接嗅探到可用直链且主播在线，平滑降级至纯净 WebRTC 视口起播
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
                // 发生异常时平滑降级至纯净 WebRTC 视口起播
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
        let headers: [String: String] = [
            "Referer": referer,
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        ]
        let asset = AVURLAsset(url: hlsUrl, options: ["AVURLAssetHTTPHeaderFieldsKey": headers])
        let playerItem = AVPlayerItem(asset: asset)
        let avPlayer = AVPlayer(playerItem: playerItem)
        self.player = avPlayer

        itemStatusObserver = playerItem.observe(\.status, options: [.new, .initial]) { item, _ in
            DispatchQueue.main.async {
                guard self.player?.currentItem === item else { return }
                if item.status == .readyToPlay {
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
                    // 原生直链解码失败或 MMCDN 403 握手受限时，全量平滑降级至纯净 WebRTC 视口起播
                    self.player = nil
                    self.isLoadingLive = false
                    if self.liveError == nil {
                        self.isWebFallbackActive = true
                    }
                }
            }
        }

        presentationSizeObserver = playerItem.observe(\.presentationSize, options: [.new]) { item, _ in
            DispatchQueue.main.async {
                let size = item.presentationSize
                if size.width > 0 && size.height > 0 {
                    self.isPortraitStream = (size.height > size.width)
                }
            }
        }
    }

    private func cleanupLivePlayer(cancelRequest: Bool = true) {
        UIApplication.shared.isIdleTimerDisabled = false
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
        player?.pause()
        player = nil
        isPlaying = false
    }
}
