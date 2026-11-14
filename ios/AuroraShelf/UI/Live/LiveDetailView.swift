import SwiftUI

/// Apple TV 风格直播二级页：单播放源原位全屏无缝展开、主播信息、动作工具栏与原生实时评论联动
public struct LiveDetailView: View {
    public let stream: LiveStreamItem
    public let relatedStreams: [LiveStreamItem]
    public let onSelectStream: ((LiveStreamItem) -> Void)?
    public let onDismiss: () -> Void
    @Environment(\.dismiss) private var dismiss
    @AppStorage private var isBookmarked: Bool
    @AppStorage private var localLikes: Int
    @State private var showChat = false
    @State private var isCommentsExpanded = true

    // 全屏与画幅控制（单一播放源驱动，零重新缓冲，零黑屏）
    @State private var isFullScreen: Bool = false
    @State private var isLandscape: Bool = false
    @State private var isFillMode: Bool = false
    @State private var hudMessage: String?
    @State private var hudTask: DispatchWorkItem?
    @State private var stripchatComments: [LiveComment] = []

    public init(
        stream: LiveStreamItem,
        relatedStreams: [LiveStreamItem] = [],
        onSelectStream: ((LiveStreamItem) -> Void)? = nil,
        onDismiss: @escaping () -> Void
    ) {
        self.stream = stream
        self.relatedStreams = relatedStreams
        self.onSelectStream = onSelectStream
        self.onDismiss = onDismiss
        _isBookmarked = AppStorage(wrappedValue: false, "live.bookmarked.\(stream.id)")
        _localLikes = AppStorage(wrappedValue: 0, "live.likes.\(stream.id)")
    }

    public var body: some View {
        GeometryReader { screenGeo in
            let isPad = UIDevice.current.userInterfaceIdiom == .pad
            let layoutMode = VideoDetailLayout.mode(size: screenGeo.size, idiom: UIDevice.current.userInterfaceIdiom)
            let isSplitMode = layoutMode == .padLandscapeSplit && !isFullScreen
            let columns = VideoDetailLayout.columns(for: screenGeo.size, idiom: UIDevice.current.userInterfaceIdiom)
            let playerWidth = columns.playerWidth(for: screenGeo.size.width)
            let recommendationWidth = columns.recommendationWidth(for: screenGeo.size.width)
            let isPhysicallyLandscape = screenGeo.size.width > screenGeo.size.height
            let isRotated = !isPad && isFullScreen && isLandscape && !isPhysicallyLandscape
            let containerWidth = isSplitMode ? playerWidth : screenGeo.size.width
            let targetWidth = isRotated ? screenGeo.size.height : containerWidth
            let safeTop = DeviceLayout.safeAreaTop(in: screenGeo.size)
            let targetHeight: CGFloat = {
                if isFullScreen {
                    return isRotated ? screenGeo.size.width : screenGeo.size.height
                }
                if isSplitMode {
                    return min(playerWidth * 9.0 / 16.0, screenGeo.size.height * 0.62) + safeTop
                }
                if isPad {
                    return min((screenGeo.size.width * 9.0 / 16.0), screenGeo.size.height * 0.50) + safeTop
                }
                return (screenGeo.size.width * 9.0 / 16.0) + safeTop
            }()

            ZStack(alignment: .topLeading) {
                AuroraColor.canvas.ignoresSafeArea()

                // 主播信息、评论与推荐主播（始终维持单一 LivePlayerView 身份，进入/退出全屏零重建）
                if !isFullScreen {
                    if isSplitMode {
                        HStack(alignment: .top, spacing: 0) {
                            ScrollView(.vertical, showsIndicators: false) {
                                VStack(alignment: .leading, spacing: AuroraMetrics.spacingXL) {
                                    headerInfoSection
                                        .padding(.top, AuroraMetrics.spacingM)

                                    actionButtonsSection

                                    Spacer().frame(height: 48)
                                }
                                .padding(.horizontal, AuroraMetrics.contentInset)
                            }
                            .padding(.top, targetHeight)
                            .frame(width: playerWidth, height: screenGeo.size.height)

                            Rectangle()
                                .fill(AuroraColor.hairline)
                                .frame(width: 1, height: screenGeo.size.height)

                            landscapeRightRail(
                                width: recommendationWidth,
                                safeTop: safeTop,
                                screenHeight: screenGeo.size.height
                            )
                            .frame(width: recommendationWidth, height: screenGeo.size.height)
                        }
                        .transition(.opacity)
                    } else {
                        ScrollView(.vertical, showsIndicators: false) {
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingXL) {
                                // 主播信息与在线状态（Apple TV 标题层级）
                                headerInfoSection
                                    .padding(.top, AuroraMetrics.spacingM)

                                // 核心动作按钮组（点赞、收藏、聊天室）
                                actionButtonsSection

                                // 原生实时评论嵌入区（支持折叠/展开）
                                LiveInlineCommentsView(
                                    stream: stream,
                                    externalComments: stripchatComments,
                                    isLivePlaying: true,
                                    isExpanded: $isCommentsExpanded,
                                    maxBodyHeight: 240
                                )

                                if !relatedStreams.isEmpty {
                                    stackedRelatedSection
                                }

                                Spacer().frame(height: 48)
                            }
                            .padding(.horizontal, AuroraMetrics.contentInset)
                        }
                        .padding(.top, targetHeight)
                        .frame(width: screenGeo.size.width, height: screenGeo.size.height)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }

                // 顶部直播播放器视口（单一实例支持原位动态展开至全屏或分栏，不中断、不重载）
                livePlayerContainer(
                    in: screenGeo,
                    isRotated: isRotated,
                    containerWidth: containerWidth,
                    targetWidth: targetWidth,
                    targetHeight: targetHeight
                )
                .zIndex(10)

                // 全局 HUD 提示胶囊（画幅模式、旋转模式与全屏状态浮层）
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
        .toolbar(.hidden, for: .navigationBar)
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

    // MARK: - 1. 顶部直播播放器视口（内嵌 / 分栏 / 全屏自适应）

    @ViewBuilder
    private func livePlayerContainer(
        in screenGeo: GeometryProxy,
        isRotated: Bool,
        containerWidth: CGFloat,
        targetWidth: CGFloat,
        targetHeight: CGFloat
    ) -> some View {
        let effectiveContainerWidth = isFullScreen ? screenGeo.size.width : containerWidth
        let effectiveContainerHeight = isFullScreen ? screenGeo.size.height : targetHeight
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
        .id(stream.id)
        .frame(width: targetWidth, height: targetHeight)
        .clipped()
        .rotationEffect(isRotated ? .degrees(90) : .zero)
        .position(
            x: effectiveContainerWidth / 2,
            y: isFullScreen ? screenGeo.size.height / 2 : targetHeight / 2
        )
        .frame(
            width: effectiveContainerWidth,
            height: effectiveContainerHeight
        )
        .ignoresSafeArea()
        .clipped()
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: isFullScreen)
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: isLandscape)
    }

    // MARK: - iPad 横屏右侧栏（可折叠实时评论 + 更多直播推荐）

    private func landscapeRightRail(width: CGFloat, safeTop: CGFloat, screenHeight: CGFloat) -> some View {
        let horizontalPadding: CGFloat = 14
        let thumbWidth = min(148, max(112, (width - horizontalPadding * 2 - 10) * 0.43))
        let thumbHeight = (thumbWidth * 9.0 / 16.0).rounded()

        return VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
            LiveInlineCommentsView(
                stream: stream,
                externalComments: stripchatComments,
                isLivePlaying: true,
                isExpanded: $isCommentsExpanded,
                maxBodyHeight: min(240, max(120, screenHeight * 0.28))
            )

            if !relatedStreams.isEmpty {
                Text("更多直播")
                    .font(.title3.bold())
                    .foregroundStyle(AuroraColor.textPrimary)

                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                        ForEach(relatedStreams.prefix(20)) { related in
                            relatedStreamRow(
                                related,
                                thumbWidth: thumbWidth,
                                thumbHeight: thumbHeight
                            )
                        }
                    }
                    .padding(.bottom, 48)
                }
            } else {
                Spacer(minLength: 0)
            }
        }
        .padding(.horizontal, horizontalPadding)
        .padding(.top, max(20, safeTop + 8))
    }

    private var stackedRelatedSection: some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
            Text("更多直播")
                .font(.title3.bold())
                .foregroundStyle(AuroraColor.textPrimary)

            LazyVStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                ForEach(relatedStreams.prefix(12)) { related in
                    relatedStreamRow(related, thumbWidth: 140, thumbHeight: 79)
                }
            }
        }
    }

    @ViewBuilder
    private func relatedStreamRow(_ related: LiveStreamItem, thumbWidth: CGFloat, thumbHeight: CGFloat) -> some View {
        Button {
            onSelectStream?(related)
        } label: {
            HStack(alignment: .top, spacing: 10) {
                ZStack(alignment: .topLeading) {
                    AuroraCoverImage(
                        url: related.snapshotUrl,
                        title: related.username,
                        aspectRatio: AuroraMetrics.liveAspect,
                        contentMode: .fill,
                        cornerRadius: 10
                    )
                    .frame(width: thumbWidth, height: thumbHeight)
                    .clipped()

                    Text("LIVE")
                        .font(.system(size: 9, weight: .heavy))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Color.red.opacity(0.88), in: Capsule())
                        .padding(6)
                }

                VStack(alignment: .leading, spacing: 5) {
                    Text(related.username)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AuroraColor.textPrimary)
                        .lineLimit(1)

                    if !related.title.isEmpty {
                        Text(related.title)
                            .font(.caption)
                            .foregroundStyle(AuroraColor.textSecondary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }

                    Label("\(related.viewCount)", systemImage: "person.2.fill")
                        .font(.caption2)
                        .foregroundStyle(AuroraColor.textTertiary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
        .frame(minHeight: thumbHeight)
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
