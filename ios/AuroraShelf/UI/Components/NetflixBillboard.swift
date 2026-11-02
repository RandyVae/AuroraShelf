import SwiftUI

public struct NetflixBillboard: View {
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
            // 背景封面图与渐变层
            GeometryReader { geo in
                AsyncImage(url: URL(string: video.coverUrl)) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: geo.size.width, height: geo.size.height)
                            .clipped()
                    default:
                        ZStack {
                            LinearGradient(
                                colors: [Color(red: 0.12, green: 0.14, blue: 0.22), Color(red: 0.05, green: 0.06, blue: 0.10)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                            Image(systemName: "film.stack")
                                .font(.system(size: 64))
                                .foregroundColor(.white.opacity(0.15))
                        }
                    }
                }
            }
            .frame(height: 530)
            
            // 顶层微弱暗度
            LinearGradient(
                colors: [Color.black.opacity(0.45), Color.clear],
                startPoint: .top,
                endPoint: .center
            )
            .frame(height: 160)
            .frame(maxHeight: .infinity, alignment: .top)
            
            // 底部深黑沉浸渐变遮罩（无缝过渡至纯黑列表背景）
            LinearGradient(
                stops: [
                    .init(color: .clear, location: 0.0),
                    .init(color: AuroraTheme.background.opacity(0.5), location: 0.55),
                    .init(color: AuroraTheme.background.opacity(0.92), location: 0.85),
                    .init(color: AuroraTheme.background, location: 1.0)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 320)
            
            // 内容信息区域
            VStack(spacing: 12) {
                // 顶部品牌标识
                HStack(spacing: 6) {
                    Text("N")
                        .font(.system(size: 22, weight: .black, design: .rounded))
                        .foregroundColor(AuroraTheme.crimson)
                    
                    Text("FILM · 极光精选")
                        .font(.system(size: 11, weight: .bold))
                        .tracking(2.5)
                        .foregroundColor(.white.opacity(0.85))
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(
                    Capsule()
                        .fill(Color.black.opacity(0.45))
                )
                
                // 大标题
                Text(video.title)
                    .font(.system(size: 28, weight: .heavy))
                    .foregroundColor(AuroraTheme.textPrimary)
                    .multilineTextAlignment(.center)
                    .shadow(color: .black.opacity(0.8), radius: 8, x: 0, y: 3)
                
                // 标签栏（自动去重来源名称，避免重复显示）
                HStack(spacing: 8) {
                    ForEach(video.tags.filter { $0 != video.sourceName }, id: \.self) { tag in
                        Text(tag)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.white.opacity(0.9))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .fill(Color.white.opacity(0.16))
                            )
                    }
                    
                    Text(video.sourceName)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(AuroraTheme.crimsonGlow)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(
                            RoundedRectangle(cornerRadius: 6, style: .continuous)
                                .fill(AuroraTheme.crimson.opacity(0.2))
                        )
                }
                
                // 动作按钮栏
                HStack(spacing: 14) {
                    // 我的收藏
                    Button(action: {
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                        withAnimation(AuroraTheme.quickSpring) {
                            isBookmarked.toggle()
                        }
                    }) {
                        VStack(spacing: 3) {
                            Image(systemName: isBookmarked ? "checkmark" : "plus")
                                .font(.system(size: 17, weight: .bold))
                                .foregroundColor(.white)
                            Text(isBookmarked ? "已收藏" : "我的片单")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.white.opacity(0.9))
                        }
                        .frame(width: 72, height: 44)
                    }
                    .buttonStyle(PlainButtonStyle())
                    .applePressEffect()
                    
                    // 立即播放（高亮主按钮）
                    Button(action: {
                        let impact = UIImpactFeedbackGenerator(style: .heavy)
                        impact.impactOccurred()
                        onPlay()
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "play.fill")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.black)
                            Text("立即播放")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(.black)
                        }
                        .padding(.horizontal, 28)
                        .frame(height: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color.white)
                                .shadow(color: .white.opacity(0.25), radius: 10, x: 0, y: 4)
                        )
                    }
                    .buttonStyle(PlainButtonStyle())
                    .applePressEffect(scale: 0.95)
                    
                    // 详情信息
                    Button(action: {
                        let impact = UIImpactFeedbackGenerator(style: .light)
                        impact.impactOccurred()
                        onDetail()
                    }) {
                        VStack(spacing: 3) {
                            Image(systemName: "info.circle")
                                .font(.system(size: 18, weight: .regular))
                                .foregroundColor(.white)
                            Text("详细信息")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.white.opacity(0.9))
                        }
                        .frame(width: 72, height: 44)
                    }
                    .buttonStyle(PlainButtonStyle())
                    .applePressEffect()
                }
                .padding(.top, 4)
            }
            .padding(.bottom, 20)
            .padding(.horizontal, 16)
        }
        .frame(height: 530)
    }
}
