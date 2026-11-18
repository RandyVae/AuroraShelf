import SwiftUI

/// 全局封面安全渲染組件（集成 CoverImageLoader 自動 AES 解密、雙層緩存與嚴格防溢出幾何容器）
public struct AuroraCoverImage: View {
    public enum CoverPlaceholderStyle {
        case video
        case comic
        case live
        case forum
    }

    public let url: String
    public let title: String?
    public let sourceBaseURL: URL?
    public let aspectRatio: CGFloat?
    public let contentMode: ContentMode
    public let cornerRadius: CGFloat
    public let placeholderStyle: CoverPlaceholderStyle?
    public let onImageLoaded: ((UIImage) -> Void)?
    
    @State private var loadedImage: UIImage? = nil
    @State private var isLoading: Bool = true
    @State private var didFailToLoad = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var resolvedPlaceholderStyle: CoverPlaceholderStyle {
        placeholderStyle ?? (aspectRatio == AuroraMetrics.liveAspect ? .live : .video)
    }

    private var placeholderIconName: String {
        switch resolvedPlaceholderStyle {
        case .live: return "antenna.radiowaves.left.and.right"
        case .comic: return "book.closed.fill"
        case .video: return "film.fill"
        case .forum: return "bubble.left.and.bubble.right.fill"
        }
    }

    private var placeholderLabel: String {
        switch resolvedPlaceholderStyle {
        case .live: return "card.fallback_live".localized
        case .comic: return "card.fallback_comic".localized
        case .video: return "card.fallback_video".localized
        case .forum: return "card.fallback_forum".localized
        }
    }
    
    public init(
        url: String,
        title: String? = nil,
        sourceBaseURL: URL? = nil,
        aspectRatio: CGFloat? = AuroraMetrics.posterAspect,
        contentMode: ContentMode = .fill,
        cornerRadius: CGFloat = AuroraMetrics.cardRadius,
        placeholderStyle: CoverPlaceholderStyle? = nil,
        onImageLoaded: ((UIImage) -> Void)? = nil
    ) {
        self.url = url
        self.title = title
        self.sourceBaseURL = sourceBaseURL
        self.aspectRatio = aspectRatio
        self.contentMode = contentMode
        self.cornerRadius = cornerRadius
        self.placeholderStyle = placeholderStyle
        self.onImageLoaded = onImageLoaded
    }
    
    public var body: some View {
        Group {
            if let ratio = aspectRatio {
                // 固定比例安全容器：以透明比例基座報告尺寸，overlay 內部完全裁剪，絕對不會推擠或重疊父級網格
                Color.clear
                    .aspectRatio(ratio, contentMode: .fit)
                    .overlay(
                        GeometryReader { proxy in
                            contentBody
                                .frame(width: proxy.size.width, height: proxy.size.height)
                                .clipped()
                        }
                    )
            } else {
                // 无固定比例模式同样通过透明基座 + GeometryReader 严格锁定父容器边界，防止超宽图（如 2560x650 / 1920x800）在 .fill 模式下横向撑开容器或溢出 TabView
                Color.clear
                    .overlay(
                        GeometryReader { proxy in
                            contentBody
                                .frame(width: proxy.size.width, height: proxy.size.height)
                                .clipped()
                        }
                    )
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .task(id: "\(url)|\(title ?? "")") {
            loadedImage = nil
            didFailToLoad = false
            guard !url.isEmpty || (title != nil && !title!.isEmpty) else {
                self.isLoading = false
                didFailToLoad = true
                return
            }
            self.isLoading = true
            let img = await CoverImageLoader.shared.loadImage(from: url, title: title, baseURL: sourceBaseURL)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                withAnimation(reduceMotion ? nil : AuroraMotion.smooth) {
                    self.loadedImage = img
                    self.didFailToLoad = img == nil
                    self.isLoading = false
                }
                if let img {
                    self.onImageLoaded?(img)
                }
            }
        }
    }
    
    @ViewBuilder
    private var contentBody: some View {
        ZStack {
            if let image = loadedImage {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
                    .transition(.opacity)
            } else if isLoading {
                ZStack {
                    LinearGradient(
                        colors: [
                            AuroraColor.surfaceCard,
                            AuroraColor.surfaceCard.opacity(0.7)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                        .scaleEffect(0.75)
                }
            } else {
                ZStack {
                    LinearGradient(
                        colors: [
                            Color(red: 0.18, green: 0.08, blue: 0.12),
                            Color(red: 0.08, green: 0.08, blue: 0.10)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )

                    if let t = title, !t.isEmpty {
                        VStack(spacing: 8) {
                            Spacer()
                            Image(systemName: placeholderIconName)
                                .font(.title3)
                                .foregroundStyle(AuroraColor.brand.opacity(0.8))
                            Text(t)
                                .font(.footnote.weight(.bold))
                                .foregroundStyle(.white)
                                .multilineTextAlignment(.center)
                                .lineLimit(3)
                                .padding(.horizontal, 6)
                            Spacer()
                            Text(placeholderLabel)
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.white.opacity(0.5))
                                .padding(.bottom, 6)
                        }
                    } else {
                        VStack(spacing: 6) {
                            Image(systemName: placeholderIconName)
                                .font(.title2.weight(.light))
                                .foregroundStyle(AuroraColor.brand.opacity(0.7))
                            Text(placeholderLabel)
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.white.opacity(0.5))
                        }
                    }
                }
            }
        }
    }
}
