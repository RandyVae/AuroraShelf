import SwiftUI

/// Apple TV 风格直播二级页：单播放源原位全屏无缝展开、主播信息、动作工具栏与原生实时评论联动
public struct LiveDetailView: View {
    public let stream: LiveStreamItem
    public let onDismiss: () -> Void
    @Environment(\.dismiss) private var dismiss
    @AppStorage private var isBookmarked: Bool
    @AppStorage private var localLikes: Int
    @State private var showChat = false

    // 全屏与画幅控制（单一播放源驱动，零重新缓冲，零黑屏）
    @State private var isFullScreen: Bool = false
    @State private var isLandscape: Bool = false
    @State private var isFillMode: Bool = false
    @State private var hudMessage: String?
    @State private var hudTask: DispatchWorkItem?
    @State private var stripchatComments: [LiveComment] = []

    public init(stream: LiveStreamItem, onDismiss: @escaping () -> Void) {
        self.stream = stream
        self.onDismiss = onDismiss
        _isBookmarked = AppStorage(wrappedValue: false, "live.bookmarked.\(stream.id)")
        _localLikes = AppStorage(wrappedValue: 0, "live.likes.\(stream.id)")
    }

    public var body: some View {
        GeometryReader { screenGeo in
            let isPad = UIDevice.current.userInterfaceIdiom == .pad
            let isPhysicallyLandscape = screenGeo.size.width > screenGeo.size.height
            let isRotated = !isPad && isFullScreen && isLandscape && !isPhysicallyLandscape
            let targetWidth = isRotated ? screenGeo.size.height : screenGeo.size.width
            let safeTop = DeviceLayout.safeAreaTop(in: screenGeo.size)
            let targetHeight: CGFloat = {
                if isFullScreen {
                    return isRotated ? screenGeo.size.width : screenGeo.size.height
                }
                if isPad {
                    return min((screenGeo.size.width * 9.0 / 16.0) + safeTop, screenGeo.size.height * 0.50)
                }
                return (screenGeo.size.width * 9.0 / 16.0) + safeTop
            }()

            ZStack(alignment: .top) {
                AuroraColor.canvas.ignoresSafeArea()

                VStack(spacing: 0) {
                    // 1. 顶部直播播放器视口（支持原位动态展开至全屏，同一个 AVPlayer 实例不中断、不重载）
                    livePlayerContainer(
                        in: screenGeo,
                        isRotated: isRotated,
                        targetWidth: targetWidth,
                        targetHeight: targetHeight
                    )
                    .zIndex(10)

                    // 2. 下方主播信息、动作按钮与实时评论流（全屏时平滑折叠退场）
                    if !isFullScreen {
                        ScrollView(.vertical, showsIndicators: false) {
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingXL) {
                                // 主播信息与在线状态（Apple TV 标题层级）
                                headerInfoSection
                                    .padding(.top, AuroraMetrics.spacingM)

                                // 核心动作按钮组（点赞、收藏、聊天室）
                                actionButtonsSection

                                // 原生实时评论嵌入区
                                LiveInlineCommentsView(
                                    stream: stream,
                                    externalComments: stripchatComments,
                                    isLivePlaying: true
                                )

                                Spacer().frame(height: 48)
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                        }
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }

                // 3. 全局 HUD 提示胶囊（画幅模式、旋转模式与全屏状态浮层）
                if let hud = hudMessage {
                    Text(hud)
                        .font(.callout.bold())
                        .foregroundStyle(.white)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: Capsule())
                        .overlay(Capsule().stroke(Color.white.opacity(0.2), lineWidth: 1))
                        .shadow(color: .black.opacity(0.5), radius: 8, y: 3)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                        .transition(.scale(scale: 0.9).combined(with: .opacity))
                        .zIndex(30)
                }
            }
        }
        .ignoresSafeArea()
        .statusBarHidden(isFullScreen)
        .persistentSystemOverlays(isFullScreen ? .hidden : .automatic)
        .onAppear {
            let args = CommandLine.arguments
            if args.contains("--live-fullscreen") {
                isFullScreen = true
            }
            if args.contains("--live-landscape") {
                isFullScreen = true
                isLandscape = true
            }
            if args.contains("--live-fill") {
                isFillMode = true
            }
        }
        .sheet(isPresented: $showChat) {
            LiveChatSheet(stream: stream)
        }
        .enableSwipeBack(isEnabled: !isFullScreen) {
            onDismiss()
        }
    }

    // MARK: - 1. 顶部直播播放器视口（内嵌 / 全屏自适应）

    @ViewBuilder
    private func livePlayerContainer(
        in screenGeo: GeometryProxy,
        isRotated: Bool,
        targetWidth: CGFloat,
        targetHeight: CGFloat
    ) -> some View {
        LivePlayerView(
            stream: stream,
            isFullScreen: $isFullScreen,
            isLandscape: $isLandscape,
            isFillMode: $isFillMode,
            topSafeArea: DeviceLayout.safeAreaTop(in: screenGeo.size),
            onDismiss: {
                onDismiss()
            },
            onToggleHUD: { message in
                showHUD(message)
            },
            onComment: { comment in
                stripchatComments = Array((stripchatComments + [comment]).suffix(50))
            }
        )
        .frame(width: targetWidth, height: targetHeight)
        .clipped()
        .rotationEffect(isRotated ? .degrees(90) : .zero)
        .position(
            x: screenGeo.size.width / 2,
            y: isFullScreen ? screenGeo.size.height / 2 : targetHeight / 2
        )
        .frame(
            width: isFullScreen ? screenGeo.size.width : screenGeo.size.width,
            height: isFullScreen ? screenGeo.size.height : targetHeight
        )
        .ignoresSafeArea()
        .clipped()
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: isFullScreen)
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: isLandscape)
    }

    // MARK: - 2. 主播信息与在线标签

    private var headerInfoSection: some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
            HStack(spacing: 8) {
                Text(stream.username)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(AuroraColor.textPrimary)

                HStack(spacing: 4) {
                    Circle()
                        .fill(Color.red)
                        .frame(width: 7, height: 7)
                    Text("LIVE")
                        .font(.system(size: 10, weight: .heavy))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(Color.red.opacity(0.85), in: Capsule())

                Spacer()

                HStack(spacing: 4) {
                    Image(systemName: "person.2.fill")
                        .font(.caption2)
                    Text("\(stream.viewCount)")
                        .font(.caption.bold())
                }
                .foregroundStyle(AuroraColor.brandLabel)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(AuroraColor.brand.opacity(0.15), in: Capsule())
            }

            Text(stream.title.isEmpty ? "正在直播中" : stream.title)
                .font(.body)
                .foregroundStyle(AuroraColor.textSecondary)
        }
    }

    // MARK: - 3. 核心动作按钮组

    private var actionButtonsSection: some View {
        GlassEffectContainer(spacing: AuroraMetrics.spacingM) {
            HStack(spacing: AuroraMetrics.spacingM) {
                actionButton("heart.fill", title: "点赞 \(localLikes)", tint: .pink) {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    localLikes += 1
                }
                actionButton(
                    isBookmarked ? "bookmark.fill" : "bookmark",
                    title: isBookmarked ? "已收藏" : "收藏",
                    tint: AuroraColor.brand,
                    isProminent: isBookmarked
                ) {
                    let impact = UIImpactFeedbackGenerator(style: .medium)
                    impact.impactOccurred()
                    isBookmarked.toggle()
                }
                actionButton("bubble.left.and.bubble.right.fill", title: "聊天室", tint: AuroraColor.brand) {
                    showChat = true
                }
            }
        }
    }

    @ViewBuilder
    private func actionButton(_ icon: String, title: String, tint: Color, isProminent: Bool = false, action: @escaping () -> Void) -> some View {
        let base = Button(action: action) {
            Label(title, systemImage: icon)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
        }
        .buttonBorderShape(.capsule)
        .tint(tint)
        .accessibilityLabel(title)

        if isProminent {
            base.buttonStyle(.glassProminent)
        } else {
            base.buttonStyle(.glass)
        }
    }

    private func showHUD(_ text: String) {
        hudTask?.cancel()
        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
            hudMessage = text
        }
        let task = DispatchWorkItem {
            withAnimation(.easeOut(duration: 0.25)) {
                if self.hudMessage == text {
                    self.hudMessage = nil
                }
            }
        }
        hudTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0, execute: task)
    }
}
