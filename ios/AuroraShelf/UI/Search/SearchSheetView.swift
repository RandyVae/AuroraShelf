import SwiftUI

public struct SearchSheetView: View {
    public let onPlayVideo: (VideoItem) -> Void
    public let onDismiss: () -> Void
    
    @StateObject private var repo = VideoRepository.shared
    @State private var query: String = ""
    @FocusState private var isFocused: Bool
    
    public init(onPlayVideo: @escaping (VideoItem) -> Void, onDismiss: @escaping () -> Void) {
        self.onPlayVideo = onPlayVideo
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        ZStack {
            AuroraTheme.background.ignoresSafeArea()
            
            VStack(spacing: 16) {
                // 搜索顶栏
                HStack(spacing: 12) {
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 16))
                            .foregroundColor(AuroraTheme.textTertiary)
                        
                        TextField("全局并发搜索全网 19 个视频源...", text: $query)
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                            .focused($isFocused)
                            .submitLabel(.search)
                            .onSubmit {
                                Task {
                                    await repo.searchAllSources(query: query)
                                }
                            }
                        
                        if !query.isEmpty {
                            Button(action: { query = "" }) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 14))
                                    .foregroundColor(.white.opacity(0.4))
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .frame(height: 42)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(AuroraTheme.surfaceCard)
                            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(AuroraTheme.glassBorder, lineWidth: 1))
                    )
                    
                    Button(action: {
                        onDismiss()
                    }) {
                        Text("取消")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(AuroraTheme.crimsonGlow)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)
                
                // 搜索状态与结果
                if repo.isSearching {
                    VStack(spacing: 12) {
                        Spacer()
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: AuroraTheme.crimson))
                        Text("正在向全网 19 个视频源并发检索...")
                            .font(.system(size: 13))
                            .foregroundColor(AuroraTheme.textSecondary)
                        Spacer()
                    }
                } else if !repo.searchResults.isEmpty {
                    ScrollView(.vertical, showsIndicators: false) {
                        LazyVStack(spacing: 14) {
                            ForEach(repo.searchResults) { item in
                                Button(action: {
                                    onPlayVideo(item)
                                    onDismiss()
                                }) {
                                    HStack(spacing: 12) {
                                        AsyncImage(url: URL(string: item.coverUrl)) { phase in
                                            if let img = phase.image {
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } else {
                                                RoundedRectangle(cornerRadius: 8).fill(AuroraTheme.surfaceCard)
                                            }
                                        }
                                        .frame(width: 120, height: 75)
                                        .cornerRadius(8)
                                        
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(item.title)
                                                .font(.system(size: 14, weight: .bold))
                                                .foregroundColor(.white)
                                                .lineLimit(1)
                                            Text(item.subtitle)
                                                .font(.system(size: 12))
                                                .foregroundColor(AuroraTheme.textSecondary)
                                                .lineLimit(1)
                                            
                                            HStack(spacing: 6) {
                                                Text(item.sourceName)
                                                    .font(.system(size: 10, weight: .bold))
                                                    .foregroundColor(AuroraTheme.crimsonGlow)
                                                    .padding(.horizontal, 6)
                                                    .padding(.vertical, 2)
                                                    .background(Capsule().fill(AuroraTheme.crimson.opacity(0.2)))
                                                
                                                Text(item.duration)
                                                    .font(.system(size: 10))
                                                    .foregroundColor(AuroraTheme.textTertiary)
                                            }
                                        }
                                        
                                        Spacer()
                                    }
                                    .padding(8)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .fill(AuroraTheme.surface)
                                    )
                                }
                                .buttonStyle(PlainButtonStyle())
                                .applePressEffect()
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                } else {
                    VStack(spacing: 16) {
                        Spacer()
                        Image(systemName: "sparkle.magnifyingglass")
                            .font(.system(size: 48))
                            .foregroundColor(AuroraTheme.crimson.opacity(0.6))
                        Text("输入关键词，回车搜索全网 19 个视频源")
                            .font(.system(size: 14))
                            .foregroundColor(AuroraTheme.textTertiary)
                        Spacer()
                    }
                }
            }
        }
        .onAppear {
            isFocused = true
        }
    }
}
