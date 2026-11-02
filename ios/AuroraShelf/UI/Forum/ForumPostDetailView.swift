import SwiftUI

public struct ForumPostDetailView: View {
    public let post: ForumPostItem
    public let onDismiss: () -> Void
    
    public init(post: ForumPostItem, onDismiss: @escaping () -> Void) {
        self.post = post
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        ZStack {
            AuroraTheme.background.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // 顶栏
                HStack {
                    Button(action: {
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                        onDismiss()
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 14, weight: .bold))
                            Text("返回")
                                .font(.system(size: 14, weight: .semibold))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(
                            Capsule()
                                .fill(.ultraThinMaterial)
                                .environment(\.colorScheme, .dark)
                                .overlay(Capsule().stroke(AuroraTheme.glassBorder, lineWidth: 1))
                        )
                    }
                    .buttonStyle(PlainButtonStyle())
                    
                    Spacer()
                    
                    Text(post.boardName)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(AuroraTheme.crimsonGlow)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(AuroraTheme.crimson.opacity(0.18)))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(AuroraTheme.surface)
                
                // 帖子正文
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 16) {
                        // 标题
                        Text(post.title)
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                        
                        // 楼主与时间
                        HStack(spacing: 8) {
                            Circle()
                                .fill(AuroraTheme.crimson.opacity(0.3))
                                .frame(width: 32, height: 32)
                                .overlay(
                                    Text(String(post.author.prefix(1)))
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(.white)
                                )
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text(post.author)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(.white)
                                HStack(spacing: 6) {
                                    Text(post.postTime)
                                    Text("·")
                                    Text("\(post.views) 次浏览")
                                    Text("·")
                                    Text("\(post.replies) 条回复")
                                }
                                .font(.system(size: 11))
                                .foregroundColor(AuroraTheme.textTertiary)
                            }
                        }
                        
                        Divider().background(Color.white.opacity(0.1))
                        
                        // 文字内容
                        Text(post.contentText)
                            .font(.system(size: 15))
                            .foregroundColor(AuroraTheme.textPrimary)
                            .lineSpacing(6)
                        
                        // 预览图片
                        if let imgUrl = post.previewImageUrl {
                            AsyncImage(url: URL(string: imgUrl)) { phase in
                                if let img = phase.image {
                                    img.resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .cornerRadius(12)
                                }
                            }
                        }
                        
                        Spacer().frame(height: 40)
                    }
                    .padding(16)
                }
            }
        }
    }
}
