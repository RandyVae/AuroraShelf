import SwiftUI

public struct ComicsScreen: View {
    @ObservedObject public var repo = ComicRepository.shared
    @State private var activeComicForReading: ComicItem? = nil
    
    public init() {}
    
    public var body: some View {
        ZStack {
            AuroraTheme.background.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // 1:1 对齐 Android 顶栏 NetflixTopBar
                NetflixTopBar(
                    title: "Netgrey",
                    searchPlaceholder: "搜索漫画"
                )
                
                // 10 大漫画源横向滑动条
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(ComicSourceCatalog.allSources) { source in
                            let isSelected = source.id == repo.currentSource.id
                            Button(action: {
                                let impact = UIImpactFeedbackGenerator(style: .light)
                                impact.impactOccurred()
                                withAnimation(AuroraTheme.appleSpring) {
                                    repo.switchSource(to: source)
                                }
                            }) {
                                Text(source.name)
                                    .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                                    .foregroundColor(isSelected ? .white : AuroraTheme.textSecondary)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(
                                        Capsule()
                                            .fill(isSelected ? AuroraTheme.crimson : AuroraTheme.surfaceCard)
                                            .overlay(Capsule().stroke(isSelected ? AuroraTheme.crimson : AuroraTheme.glassBorder, lineWidth: 0.8))
                                    )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)
                }
                
                // 漫画网格
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 18) {
                        ForEach(repo.comics) { comic in
                            Button(action: {
                                let impact = UIImpactFeedbackGenerator(style: .medium)
                                impact.impactOccurred()
                                activeComicForReading = comic
                            }) {
                                VStack(alignment: .leading, spacing: 6) {
                                    AsyncImage(url: URL(string: comic.coverUrl)) { phase in
                                        if let img = phase.image {
                                            img.resizable().aspectRatio(contentMode: .fill)
                                        } else {
                                            RoundedRectangle(cornerRadius: 10).fill(AuroraTheme.surfaceCard)
                                        }
                                    }
                                    .frame(height: 220)
                                    .cornerRadius(10)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                            .stroke(AuroraTheme.glassBorder, lineWidth: 0.8)
                                    )
                                    
                                    Text(comic.title)
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                    
                                    HStack {
                                        Text(comic.author)
                                            .font(.system(size: 11))
                                            .foregroundColor(AuroraTheme.textSecondary)
                                        Spacer()
                                        Text(comic.latestChapterTitle)
                                            .font(.system(size: 10, weight: .semibold))
                                            .foregroundColor(AuroraTheme.crimsonGlow)
                                    }
                                }
                            }
                            .buttonStyle(PlainButtonStyle())
                            .applePressEffect()
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 24) // 底部原生呼吸间隙（原生 TabBar 自动接管 SafeArea）
                }
            }
        }
        .fullScreenCover(item: $activeComicForReading) { comic in
            ComicReaderView(comic: comic) {
                activeComicForReading = nil
            }
        }
    }
}
