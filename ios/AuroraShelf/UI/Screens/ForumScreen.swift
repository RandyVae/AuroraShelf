import SwiftUI

public struct ForumScreen: View {
    @ObservedObject public var repo = ForumRepository.shared
    @State private var activePost: ForumPostItem? = nil
    
    public init() {}
    
    public var body: some View {
        ZStack {
            // 背景纯黑
            Color.black.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // 1:1 对齐 Android 端 NetflixTopBar
                NetflixTopBar(
                    title: "Netgrey",
                    searchPlaceholder: "搜索论坛",
                    onSearch: {
                        // 预留搜索
                    },
                    onCast: {
                        // 投屏
                    }
                )
                
                // 论坛板块分类横向胶囊条（1:1 对齐 Android CategoryStrip）
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        // 全部
                        let isAllSelected = repo.selectedBoardFid == nil
                        Button(action: {
                            withAnimation(AuroraTheme.appleSpring) {
                                repo.selectedBoardFid = nil
                                repo.loadPosts()
                            }
                        }) {
                            Text("全部")
                                .font(.system(size: 13, weight: isAllSelected ? .bold : .medium))
                                .foregroundColor(isAllSelected ? .white : AuroraTheme.auroraDarkMuted)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(
                                    Capsule()
                                        .fill(isAllSelected ? AuroraTheme.crimson : AuroraTheme.auroraMediaSurface)
                                        .overlay(Capsule().stroke(isAllSelected ? AuroraTheme.crimson : AuroraTheme.auroraMediaBorder, lineWidth: 1))
                                )
                        }
                        .buttonStyle(PlainButtonStyle())
                        
                        // 各大核心板块
                        ForEach(ForumSourceCatalog.allBoards) { board in
                            let isSelected = board.fid == repo.selectedBoardFid
                            Button(action: {
                                withAnimation(AuroraTheme.appleSpring) {
                                    repo.selectedBoardFid = board.fid
                                    repo.loadPosts()
                                }
                            }) {
                                Text(board.name)
                                    .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                                    .foregroundColor(isSelected ? .white : AuroraTheme.auroraDarkMuted)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(
                                        Capsule()
                                            .fill(isSelected ? AuroraTheme.crimson : AuroraTheme.auroraMediaSurface)
                                            .overlay(Capsule().stroke(isSelected ? AuroraTheme.crimson : AuroraTheme.auroraMediaBorder, lineWidth: 1))
                                    )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }
                
                // 1:1 对齐 Android 端 ForumPostCard 列表（无任何假头像，纯正 BBS 极简质感）
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(spacing: 10) {
                        let filtered = repo.selectedBoardFid == nil ? repo.posts : repo.posts.filter { $0.fid == repo.selectedBoardFid }
                        ForEach(filtered) { post in
                            Button(action: {
                                let impact = UIImpactFeedbackGenerator(style: .light)
                                impact.impactOccurred()
                                activePost = post
                            }) {
                                VStack(alignment: .leading, spacing: 10) {
                                    // 标题
                                    Text(post.title)
                                        .font(.system(size: 15, weight: .bold))
                                        .foregroundColor(.white)
                                        .lineLimit(2)
                                        .lineSpacing(3)
                                        .multilineTextAlignment(.leading)
                                    
                                    // 底部元信息行（对齐 Android 端 Row）
                                    HStack(spacing: 8) {
                                        // 来源小徽章（91 红色，草榴 绿色）
                                        let is1024 = post.boardName.contains("草榴") || post.fid == 100
                                        Text(is1024 ? "草榴社区" : "91自拍")
                                            .font(.system(size: 10, weight: .heavy))
                                            .foregroundColor(.white)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(
                                                RoundedRectangle(cornerRadius: 3)
                                                    .fill(is1024 ? AuroraTheme.forestGreen : AuroraTheme.crimson)
                                            )
                                        
                                        // 作者名
                                        Text(post.author)
                                            .font(.system(size: 12))
                                            .foregroundColor(AuroraTheme.auroraDarkMuted)
                                            .lineLimit(1)
                                        
                                        Spacer()
                                        
                                        // 气泡图标与回复数
                                        HStack(spacing: 4) {
                                            Image(systemName: "bubble.left")
                                                .font(.system(size: 11))
                                                .foregroundColor(AuroraTheme.textTertiary)
                                            Text("\(post.replies)")
                                                .font(.system(size: 11, weight: .semibold))
                                                .foregroundColor(Color(hex: 0xFFD6DCE5))
                                        }
                                    }
                                }
                                .padding(16)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(AuroraTheme.auroraMediaSurface)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .stroke(AuroraTheme.auroraMediaBorder, lineWidth: 1)
                                        )
                                )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 24) // 底部原生呼吸间隙（原生 TabBar 自动接管 SafeArea）
                }
            }
        }
        .fullScreenCover(item: $activePost) { post in
            ForumPostDetailView(post: post) {
                activePost = nil
            }
        }
    }
}
