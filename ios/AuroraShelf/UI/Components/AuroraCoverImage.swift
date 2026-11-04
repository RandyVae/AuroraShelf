import SwiftUI

/// 全局封面安全渲染組件（集成 CoverImageLoader 自動 AES 解密與雙層緩存）
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
        .frame(minWidth: 0, maxWidth: .infinity)
        .ifLet(aspectRatio) { view, ratio in
            view.aspectRatio(ratio, contentMode: .fit)
        }
        .clipped()
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
}

private extension View {
    @ViewBuilder
    func ifLet<T, Content: View>(_ value: T?, transform: (Self, T) -> Content) -> some View {
        if let value = value {
            transform(self, value)
        } else {
            self
        }
    }
}
