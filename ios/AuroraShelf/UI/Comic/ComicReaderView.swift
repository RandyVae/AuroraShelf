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
                        // 顶部定位锚点
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
                                        Color(red: 0.1, green: 0.1, blue: 0.15)
                                        VStack(spacing: 6) {
                                            Image(systemName: "photo.badge.exclamationmark")
                                                .font(.system(size: 28))
                                                .foregroundColor(.white.opacity(0.3))
                                            Text("第 \(index + 1) 页加载失败，点击重试")
                                                .font(.system(size: 11))
                                                .foregroundColor(.white.opacity(0.5))
                                        }
                                    }
                                    .frame(height: 480)
                                default:
                                    ZStack {
                                        Color(red: 0.08, green: 0.08, blue: 0.12)
                                        ProgressView()
                                            .progressViewStyle(CircularProgressViewStyle(tint: AuroraTheme.crimson))
                                    }
                                    .frame(height: 480)
                                }
                            }
                        }
                        
                        // 换章操作栏
                        VStack(spacing: 16) {
                            HStack(spacing: 20) {
                                Button(action: {
                                    if currentChapterIndex > 0 {
                                        switchChapter(to: currentChapterIndex - 1, proxy: proxy)
                                    }
                                }) {
                                    Text("上一话")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(currentChapterIndex > 0 ? .white : .white.opacity(0.3))
                                        .padding(.horizontal, 20)
                                        .padding(.vertical, 10)
                                        .background(Capsule().fill(AuroraTheme.surfaceCard))
                                }
                                .disabled(currentChapterIndex <= 0)
                                
                                Button(action: {
                                    if currentChapterIndex < comic.chapters.count - 1 {
                                        switchChapter(to: currentChapterIndex + 1, proxy: proxy)
                                    }
                                }) {
                                    Text("下一话")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(currentChapterIndex < comic.chapters.count - 1 ? .white : .white.opacity(0.3))
                                        .padding(.horizontal, 24)
                                        .padding(.vertical, 10)
                                        .background(Capsule().fill(AuroraTheme.crimson))
                                }
                                .disabled(currentChapterIndex >= comic.chapters.count - 1)
                            }
                            
                            Text("已阅至本话末尾")
                                .font(.system(size: 12))
                                .foregroundColor(AuroraTheme.textTertiary)
                        }
                        .padding(.vertical, 32)
                    }
                }
                .onTapGesture {
                    withAnimation(AuroraTheme.quickSpring) {
                        showControls.toggle()
                    }
                }
            }
            
            // Apple HIG 浮动控制栏
            if showControls {
                VStack {
                    // 顶栏
                    HStack {
                        Button(action: {
                            let impact = UIImpactFeedbackGenerator(style: .medium)
                            impact.impactOccurred()
                            onDismiss()
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: "chevron.left")
                                    .font(.system(size: 14, weight: .bold))
                                Text("返回")
                                    .font(.system(size: 14, weight: .semibold))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(
                                Capsule()
                                    .fill(.ultraThinMaterial)
                                    .environment(\.colorScheme, .dark)
                                    .overlay(Capsule().stroke(Color.white.opacity(0.2), lineWidth: 1))
                            )
                        }
                        .buttonStyle(PlainButtonStyle())
                        
                        Spacer()
                        
                        VStack(alignment: .trailing, spacing: 2) {
                            Text(comic.title)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                            Text(currentChapter.title)
                                .font(.system(size: 12))
                                .foregroundColor(AuroraTheme.crimsonGlow)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    
                    Spacer()
                    
                    // 底部当前章节与页码指示
                    HStack {
                        Text("\(currentChapter.title) · 连载瀑布流")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white.opacity(0.85))
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(
                                Capsule()
                                    .fill(.ultraThinMaterial)
                                    .environment(\.colorScheme, .dark)
                                    .overlay(Capsule().stroke(AuroraTheme.glassBorder, lineWidth: 1))
                            )
                    }
                    .padding(.bottom, 24)
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
        withAnimation(AuroraTheme.appleSpring) {
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
