import SwiftUI

/// 全局封面安全渲染組件（集成 CoverImageLoader 自動 AES 解密、雙層緩存與嚴格防溢出幾何容器）
public struct AuroraCoverImage: View {
    public let url: String
    public let aspectRatio: CGFloat?
    public let contentMode: ContentMode
    public let cornerRadius: CGFloat
    
    @State private var loadedImage: UIImage? = nil
    @State private var isLoading: Bool = true
    
    public init(
        url: String,
        aspectRatio: CGFloat? = AuroraMetrics.posterAspect,
        contentMode: ContentMode = .fill,
        cornerRadius: CGFloat = AuroraMetrics.cardRadius
    ) {
        self.url = url
        self.aspectRatio = aspectRatio
        self.contentMode = contentMode
        self.cornerRadius = cornerRadius
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
                contentBody
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .task(id: url) {
            guard !url.isEmpty else {
                self.isLoading = false
                return
            }
            self.isLoading = true
            let img = await CoverImageLoader.shared.loadImage(from: url)
            await MainActor.run {
                withAnimation(AuroraMotion.smooth) {
                    self.loadedImage = img
                    self.isLoading = false
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
                    AuroraColor.surfaceCard
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: AuroraColor.brand))
                        .scaleEffect(0.8)
                }
            } else {
                ZStack {
                    AuroraColor.surfaceCard
                    Image(systemName: "film")
                        .font(.title2)
                        .foregroundStyle(.tertiary)
                }
            }
        }
    }
}
