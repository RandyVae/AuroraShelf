import SwiftUI

public struct ComicReaderView: View {
    public let comic: ComicItem
    @State public var currentChapterIndex: Int = 0
    public let onDismiss: () -> Void
    
    @State private var pages: [String] = []
    @State private var showControls: Bool = true
    @State private var isLoading: Bool = false
    
    public init(comic: ComicItem, initialChapterIndex: Int = 0, onDismiss: @escaping () -> Void) {
        self.comic = comic
        self._currentChapterIndex = State(initialValue: initialChapterIndex)
        self.onDismiss = onDismiss
    }
    
    public var currentChapter: ComicChapter {
        if currentChapterIndex >= 0 && currentChapterIndex < comic.chapters.count {
            return comic.chapters[currentChapterIndex]
        }
        return ComicChapter(id: "c1", title: "正文")
    }
    
    public var body: some View {
        ZStack {
            // 背景纯黑
            Color.black.ignoresSafeArea()
            
            // 漫画页面瀑布流
            ScrollViewReader { proxy in
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(spacing: 0) {
                        // 顶部定位锚点（换章置顶核心锚点，杜绝滚动越界）
                        Color.clear.frame(height: 1).id("top_anchor")
                        
                        ForEach(Array(pages.enumerated()), id: \.offset) { index, pageUrl in
                            AsyncImage(url: URL(string: pageUrl)) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .frame(maxWidth: .infinity)
                                case .failure:
                                    ZStack {
                                        AuroraColor.surfaceCard
                                        VStack(spacing: AuroraMetrics.spacingS) {
                                            Image(systemName: "photo.badge.exclamationmark")
                                                .font(.title2)
                                                .foregroundStyle(.secondary)
                                            Text("第 \(index + 1) 页加载失败，点击重试")
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                    .frame(height: 480)
                                default:
                                    ZStack {
                                        AuroraColor.surface
                                        ProgressView()
                                            .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                                    }
                                    .frame(height: 480)
                                }
                            }
                        }
                        
                        // 换章操作栏
                        VStack(spacing: AuroraMetrics.spacingL) {
                            HStack(spacing: AuroraMetrics.spacingXL) {
                                Button(action: {
                                    if currentChapterIndex > 0 {
                                        switchChapter(to: currentChapterIndex - 1, proxy: proxy)
                                    }
                                }) {
                                    Text("上一话")
                                        .font(.headline)
                                        .foregroundStyle(currentChapterIndex > 0 ? .white : .secondary)
                                        .padding(.horizontal, AuroraMetrics.spacingXL)
                                        .frame(height: AuroraMetrics.minTouchTarget)
                                        .background(AuroraColor.surfaceCard, in: .capsule)
                                }
                                .buttonStyle(.plain)
                                .disabled(currentChapterIndex <= 0)
                                
                                Button(action: {
                                    if currentChapterIndex < comic.chapters.count - 1 {
                                        switchChapter(to: currentChapterIndex + 1, proxy: proxy)
                                    }
                                }) {
                                    Text("下一话")
                                        .font(.headline)
                                        .foregroundStyle(currentChapterIndex < comic.chapters.count - 1 ? .white : .secondary)
                                        .padding(.horizontal, AuroraMetrics.spacingXL)
                                        .frame(height: AuroraMetrics.minTouchTarget)
                                        .background(AuroraColor.brand, in: .capsule)
                                }
                                .buttonStyle(.plain)
                                .disabled(currentChapterIndex >= comic.chapters.count - 1)
                            }
                            
                            Text("已阅至本话末尾")
                                .font(.footnote)
                                .foregroundStyle(AuroraColor.textTertiary)
                        }
                        .padding(.vertical, AuroraMetrics.spacingXXL)
                    }
                }
                .onTapGesture {
                    withAnimation(AuroraMotion.quick) {
                        showControls.toggle()
                    }
                }
            }
            
            // Apple HIG 浮动控制栏
            if showControls {
                VStack {
                    // 统一顶栏
                    PlayerOverlayBar(
                        title: comic.title,
                        subtitle: "\(currentChapter.title) · 连载瀑布流",
                        onDismiss: {
                            onDismiss()
                        }
                    )
                    
                    Spacer()
                    
                    // 底部当前章节指示胶囊（采用原生 Liquid Glass）
                    HStack {
                        Text("\(currentChapter.title) · 连载瀑布流")
                            .font(.subheadline)
                            .foregroundStyle(.white)
                            .padding(.horizontal, AuroraMetrics.spacingL)
                            .frame(height: AuroraMetrics.minTouchTarget)
                            .glassEffect(.regular.interactive(), in: .capsule)
                    }
                    .padding(.bottom, AuroraMetrics.spacingXL)
                }
                .transition(.opacity)
            }
        }
        .onAppear {
            loadPages()
        }
    }
    
    private func switchChapter(to newIndex: Int, proxy: ScrollViewProxy) {
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
        self.currentChapterIndex = newIndex
        
        // 关键踩坑经验：换章必须自动回顶，杜绝偏移量越界
        withAnimation(AuroraMotion.smooth) {
            proxy.scrollTo("top_anchor", anchor: .top)
        }
        loadPages()
    }
    
    private func loadPages() {
        self.isLoading = true
        Task {
            let loaded = await ComicRepository.shared.fetchChapterPages(comicId: comic.id, chapterId: currentChapter.id)
            await MainActor.run {
                self.pages = loaded
                self.isLoading = false
            }
        }
    }
}
