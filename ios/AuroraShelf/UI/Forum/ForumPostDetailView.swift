import SwiftUI

public struct ForumPostDetailView: View {
    public let post: ForumPostItem
    public let onDismiss: () -> Void
    
    public init(post: ForumPostItem, onDismiss: @escaping () -> Void) {
        self.post = post
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        NavigationStack {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: AuroraMetrics.spacingL) {
                    // 标题
                    Text(post.title)
                        .font(.title2.bold())
                        .foregroundStyle(.white)
                    
                    // 楼主与元信息
                    HStack(spacing: AuroraMetrics.spacingM) {
                        Circle()
                            .fill(AuroraColor.brand.opacity(0.25))
                            .frame(width: 36, height: 36)
                            .overlay(
                                Text(String(post.author.prefix(1)))
                                    .font(.headline)
                                    .foregroundStyle(.white)
                            )
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(post.author)
                                .font(.subheadline.bold())
                                .foregroundStyle(.white)
                            
                            HStack(spacing: AuroraMetrics.spacingXS) {
                                Text(post.postTime)
                                Text("·")
                                Text("\(post.views) 次浏览")
                                Text("·")
                                Text("\(post.replies) 条回复")
                            }
                            .font(.caption)
                            .foregroundStyle(Color.secondary)
                        }
                    }
                    
                    Divider()
                        .overlay(AuroraColor.hairline)
                    
                    // 文字内容
                    Text(post.contentText)
                        .font(.body)
                        .foregroundStyle(AuroraColor.textPrimary)
                        .lineSpacing(6)
                    
                    // 预览图片
                    if let imgUrl = post.previewImageUrl {
                        AsyncImage(url: URL(string: imgUrl)) { phase in
                            if let img = phase.image {
                                img.resizable()
                                    .aspectRatio(contentMode: .fit)
                                    .clipShape(RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
                            }
                        }
                    }
                }
                .padding(AuroraMetrics.contentInset)
            }
            .background(AuroraColor.canvas)
            .navigationTitle(post.boardName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: {
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                        onDismiss()
                    }) {
                        Image(systemName: "xmark")
                            .font(.headline)
                            .foregroundStyle(.white)
                    }
                    .accessibilityLabel("关闭")
                }
                
                ToolbarItem(placement: .topBarTrailing) {
                    Text(post.boardName)
                        .font(.caption.bold())
                        .foregroundStyle(post.boardName.contains("草榴") ? AuroraColor.forestGreen : AuroraColor.brand)
                        .padding(.horizontal, AuroraMetrics.spacingS)
                        .padding(.vertical, AuroraMetrics.spacingXS)
                        .background(
                            (post.boardName.contains("草榴") ? AuroraColor.forestGreen : AuroraColor.brand).opacity(0.18),
                            in: .capsule
                        )
                }
            }
        }
    }
}
