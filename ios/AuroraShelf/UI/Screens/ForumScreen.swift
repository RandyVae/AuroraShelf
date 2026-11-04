import SwiftUI

public struct ForumScreen: View {
    @ObservedObject public var repo = ForumRepository.shared
    @State private var activePost: ForumPostItem? = nil
    @State private var selectedSegment: Int = 0
    
    public init() {}
    
    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()
            
            VStack(spacing: AuroraMetrics.spacingM) {
                // 板块分段选择器（Apple 原生 Segmented Picker）
                Picker("板块", selection: $selectedSegment) {
                    Text("全部").tag(0)
                    Text("91自拍").tag(4)
                    Text("草榴社区").tag(100)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, AuroraMetrics.contentInset)
                .padding(.top, AuroraMetrics.spacingS)
                
                // 帖子列表（原生卡片流）
                let filtered = repo.selectedBoardFid == nil ? repo.posts : repo.posts.filter { $0.fid == repo.selectedBoardFid }
                
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(spacing: AuroraMetrics.spacingM) {
                        ForEach(filtered) { post in
                            Button {
                                activePost = post
                            } label: {
                                VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                                    // 标题（语义样式 .headline，最多 2 行）
                                    Text(post.title)
                                        .font(.headline)
                                        .foregroundStyle(AuroraColor.textPrimary)
                                        .lineLimit(2)
                                        .multilineTextAlignment(.leading)
                                    
                                    // 底部元信息行
                                    HStack(spacing: AuroraMetrics.spacingS) {
                                        // 来源小徽章（91 亮红，草榴 墨绿）
                                        let is1024 = post.boardName.contains("草榴") || post.fid == 100
                                        Text(is1024 ? "草榴社区" : "91自拍")
                                            .font(.caption2)
                                            .fontWeight(.heavy)
                                            .foregroundStyle(.white)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(
                                                RoundedRectangle(cornerRadius: 4, style: .continuous)
                                                    .fill(is1024 ? AuroraColor.forestGreen : AuroraColor.accentRed)
                                            )
                                        
                                        // 作者名
                                        Text(post.author)
                                            .font(.caption)
                                            .foregroundStyle(AuroraColor.textSecondary)
                                            .lineLimit(1)
                                        
                                        Spacer()
                                        
                                        // 气泡图标与回复数
                                        HStack(spacing: 4) {
                                            Image(systemName: "bubble.left")
                                                .font(.caption2)
                                                .foregroundStyle(AuroraColor.textTertiary)
                                            Text("\(post.replies)")
                                                .font(.caption)
                                                .fontWeight(.semibold)
                                                .foregroundStyle(Color(rgb: 0xD6DCE5))
                                        }
                                    }
                                }
                                .padding(AuroraMetrics.spacingL)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(
                                    RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                        .fill(AuroraColor.surface)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                                .stroke(AuroraColor.hairline, lineWidth: 1)
                                        )
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                    .padding(.bottom, AuroraMetrics.spacingXXL)
                }
            }
        }
        .navigationTitle("论坛")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                AirPlayRoutePicker()
                    .frame(width: 32, height: 32)
            }
        }
        .onChange(of: selectedSegment) { _, newTag in
            repo.selectedBoardFid = newTag == 0 ? nil : newTag
            repo.loadPosts()
        }
        .fullScreenCover(item: $activePost) { post in
            ForumPostDetailView(post: post) {
                activePost = nil
            }
        }
    }
}
