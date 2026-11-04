import SwiftUI

/// 原生 Hero 聚焦横幅：固定 4:5 竖版黄金比例，消除魔数尺寸与伪玻璃阴影
public struct HeroBanner: View {
    public let video: VideoItem
    public let onPlay: () -> Void
    public let onDetail: () -> Void
    
    @State private var isBookmarked: Bool = false
    
    public init(
        video: VideoItem,
        onPlay: @escaping () -> Void,
        onDetail: @escaping () -> Void
    ) {
        self.video = video
        self.onPlay = onPlay
        self.onDetail = onDetail
    }
    
    public var body: some View {
        ZStack(alignment: .bottom) {
            // 背景封面海报，4:5 竖版黄金比例，自适应延伸
            AsyncImage(url: URL(string: video.coverUrl)) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                default:
                    ZStack {
                        AuroraColor.surfaceCard
                        Image(systemName: "film")
                            .font(.largeTitle)
                            .foregroundStyle(.tertiary)
                    }
                }
            }
            .aspectRatio(AuroraMetrics.heroAspect, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .clipped()
            
            // 单一平滑渐变可读性遮罩
            LinearGradient(
                stops: [
                    .init(color: .clear, location: 0.0),
                    .init(color: AuroraColor.canvas.opacity(0.4), location: 0.45),
                    .init(color: AuroraColor.canvas.opacity(0.88), location: 0.8),
                    .init(color: AuroraColor.canvas, location: 1.0)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(maxWidth: .infinity)
            
            // 底部内容信息与操作区
            VStack(spacing: AuroraMetrics.spacingM) {
                // 标题（语义样式 .title + .bold，最多 2 行）
                Text(video.title)
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundStyle(AuroraColor.textPrimary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .padding(.horizontal, AuroraMetrics.contentInset)
                
                // 元信息行（评分 · 标签 · 来源，单行紧凑）
                HStack(spacing: AuroraMetrics.spacingS) {
                    if !video.rating.isEmpty {
                        Label(video.rating, systemImage: "star.fill")
                            .labelStyle(.titleAndIcon)
                            .foregroundStyle(AuroraColor.brand)
                    }
                    
                    if let tag = video.tags.first(where: { $0 != video.sourceName }) {
                        Text("•")
                            .foregroundStyle(AuroraColor.textSecondary)
                        Text(tag)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                    
                    Text("•")
                        .foregroundStyle(AuroraColor.textSecondary)
                    Text(video.sourceName)
                        .foregroundStyle(AuroraColor.brand)
                }
                .font(.callout)
                .lineLimit(1)
                
                // 动作按钮栏（HIG 标准尺寸 ≥ 44pt）
                HStack(spacing: AuroraMetrics.spacingL) {
                    // 主 CTA：立即播放（borderedProminent）
                    Button(action: onPlay) {
                        Label("立即播放", systemImage: "play.fill")
                            .font(.headline)
                            .frame(maxWidth: .infinity, minHeight: AuroraMetrics.minTouchTarget)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AuroraColor.brand)
                    
                    // 次 CTA：我的片单（bordered）
                    Button {
                        withAnimation(AuroraMotion.quick) {
                            isBookmarked.toggle()
                        }
                    } label: {
                        Label(isBookmarked ? "已添加" : "我的片单", systemImage: isBookmarked ? "checkmark" : "plus")
                            .font(.headline)
                            .frame(minHeight: AuroraMetrics.minTouchTarget)
                    }
                    .buttonStyle(.bordered)
                    .tint(.secondary)
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
                .padding(.bottom, AuroraMetrics.spacingL)
            }
        }
    }
}
