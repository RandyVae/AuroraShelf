import SwiftUI

public struct PersonalScreen: View {
    @ObservedObject public var videoRepo = VideoRepository.shared
    @ObservedObject public var comicRepo = ComicRepository.shared
    
    @State private var showGame: Bool = false
    @State private var showVideoSourcePicker: Bool = false
    @State private var showComicSourcePicker: Bool = false
    
    public init() {}
    
    public var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: AuroraMetrics.spacingXL) {
                    // 1. 用户头像与会员卡
                    HStack(spacing: AuroraMetrics.spacingL) {
                        ZStack {
                            Circle()
                                .fill(AuroraColor.brand)
                                .frame(width: 56, height: 56)
                            // 头像单字母（§3.2 白名单允许 .black）
                            Text("R")
                                .font(.system(size: 24, weight: .black, design: .rounded))
                                .foregroundStyle(.white)
                        }
                        
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                Text("Randy")
                                    .font(.title2)
                                    .fontWeight(.bold)
                                    .foregroundStyle(AuroraColor.textPrimary)
                                
                                // PRO 徽章（§3.2 白名单允许 .black）
                                Text("PRO")
                                    .font(.caption2)
                                    .fontWeight(.black)
                                    .foregroundStyle(.black)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Capsule().fill(Color.yellow))
                            }
                            
                            Text("已同步香港节点 · 全量 \(VideoSourceCatalog.allSources.count) 视频源 + \(ComicSourceCatalog.allSources.count) 漫画源就绪")
                                .font(.footnote)
                                .foregroundStyle(AuroraColor.textSecondary)
                        }
                        
                        Spacer()
                    }
                    .padding(AuroraMetrics.spacingL)
                    .background(
                        RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                            .fill(AuroraColor.surface)
                            .overlay(
                                RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                    .stroke(AuroraColor.hairline, lineWidth: 1)
                            )
                    )
                    .padding(.horizontal, AuroraMetrics.contentInset)
                    .padding(.top, AuroraMetrics.spacingS)
                    
                    // 2. 娱乐板块（战术 RPG 离线单机游戏接入）
                    VStack(alignment: .leading, spacing: AuroraMetrics.spacingM) {
                        Text("娱乐 · 游戏")
                            .font(.headline)
                            .foregroundStyle(AuroraColor.textSecondary)
                            .padding(.horizontal, AuroraMetrics.spacingXS)
                        
                        Button {
                            showGame = true
                        } label: {
                            HStack(spacing: AuroraMetrics.spacingL) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: AuroraMetrics.controlRadius, style: .continuous)
                                        .fill(AuroraColor.brand.opacity(0.18))
                                        .frame(width: 44, height: 44)
                                    Image(systemName: "gamecontroller.fill")
                                        .font(.title3)
                                        .foregroundStyle(AuroraColor.brand)
                                }
                                
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 6) {
                                        Text("欲望女神 · 战术 RPG")
                                            .font(.headline)
                                            .foregroundStyle(AuroraColor.textPrimary)
                                        Text("v2.16.0")
                                            .font(.caption2)
                                            .fontWeight(.bold)
                                            .foregroundStyle(AuroraColor.brand)
                                    }
                                    Text("策略战棋 · 原生硬件加速与沉浸视口")
                                        .font(.caption)
                                        .foregroundStyle(AuroraColor.textSecondary)
                                }
                                
                                Spacer()
                                
                                HStack(spacing: 4) {
                                    Text("启动")
                                        .font(.subheadline)
                                        .fontWeight(.bold)
                                        .foregroundStyle(AuroraColor.brand)
                                    Image(systemName: "play.circle.fill")
                                        .font(.body)
                                        .foregroundStyle(AuroraColor.brand)
                                }
                            }
                            .padding(AuroraMetrics.spacingM)
                            .background(
                                RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                    .fill(AuroraColor.surface)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                            .stroke(AuroraColor.brand.opacity(0.35), lineWidth: 1)
                                    )
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, AuroraMetrics.contentInset)
                    
                    // 3. 源管理与设置列表
                    VStack(spacing: 1) {
                        Button {
                            showVideoSourcePicker = true
                        } label: {
                            settingRow(
                                icon: "server.rack",
                                title: "当前视频源",
                                value: "\(videoRepo.currentSource.name) (共 \(VideoSourceCatalog.allSources.count) 源)"
                            )
                        }
                        .buttonStyle(.plain)
                        
                        Button {
                            showComicSourcePicker = true
                        } label: {
                            settingRow(
                                icon: "books.vertical",
                                title: "当前漫画源",
                                value: "\(comicRepo.currentSource.name) (共 \(ComicSourceCatalog.allSources.count) 源)"
                            )
                        }
                        .buttonStyle(.plain)
                        
                        settingRow(icon: "antenna.radiowaves.left.and.right", title: "直播源", value: "Chaturbate / LL-HLS")
                        settingRow(icon: "tv", title: "AirPlay 投屏", value: "就绪 (原生)")
                        settingRow(icon: "arrow.down.circle", title: "离线缓存管理", value: "741 KB 已用")
                        settingRow(icon: "info.circle", title: "版本基线", value: "2.16.0 (Build 173)")
                    }
                    .background(
                        RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                            .fill(AuroraColor.surface)
                            .overlay(
                                RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                    .stroke(AuroraColor.hairline, lineWidth: 1)
                            )
                    )
                    .padding(.horizontal, AuroraMetrics.contentInset)
                    
                    Spacer().frame(height: AuroraMetrics.spacingXXL)
                }
            }
        }
        .navigationTitle("我的")
        .fullScreenCover(isPresented: $showGame) {
            GameContainerView {
                showGame = false
            }
        }
        .sheet(isPresented: $showVideoSourcePicker) {
            VideoSourcePickerSheet(selectedSource: videoRepo.currentSource) { src in
                videoRepo.switchSource(to: src)
                showVideoSourcePicker = false
            }
        }
        .sheet(isPresented: $showComicSourcePicker) {
            ComicSourcePickerSheet(selectedSource: comicRepo.currentSource) { src in
                comicRepo.switchSource(to: src)
                showComicSourcePicker = false
            }
        }
    }
    
    private func settingRow(icon: String, title: String, value: String) -> some View {
        HStack(spacing: AuroraMetrics.spacingM) {
            Image(systemName: icon)
                .font(.body)
                .foregroundStyle(AuroraColor.brand)
                .frame(width: 24)
            
            Text(title)
                .font(.body)
                .foregroundStyle(AuroraColor.textPrimary)
            
            Spacer()
            
            Text(value)
                .font(.subheadline)
                .foregroundStyle(AuroraColor.textSecondary)
            
            Image(systemName: "chevron.right")
                .font(.caption2)
                .foregroundStyle(AuroraColor.textTertiary)
        }
        .padding(.horizontal, AuroraMetrics.spacingL)
        .padding(.vertical, AuroraMetrics.spacingM)
    }
}

// 漫画源切换面板
struct ComicSourcePickerSheet: View {
    let selectedSource: ComicSourceDefinition
    let onSelect: (ComicSourceDefinition) -> Void
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        ZStack {
            AuroraColor.canvas.ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: AuroraMetrics.spacingL) {
                HStack {
                    Text("全网 \(ComicSourceCatalog.allSources.count) 大漫画源切换")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundStyle(AuroraColor.textPrimary)
                    Spacer()
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title3)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                }
                .padding(.horizontal, AuroraMetrics.contentInset)
                .padding(.top, AuroraMetrics.spacingL)
                
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: AuroraMetrics.spacingM) {
                        ForEach(ComicSourceCatalog.allSources) { src in
                            let isSelected = src.id == selectedSource.id
                            Button(action: { onSelect(src) }) {
                                HStack(spacing: AuroraMetrics.spacingM) {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(src.name)
                                            .font(.body)
                                            .fontWeight(.semibold)
                                            .foregroundStyle(isSelected ? AuroraColor.brand : AuroraColor.textPrimary)
                                        Text(src.description)
                                            .font(.caption)
                                            .foregroundStyle(AuroraColor.textSecondary)
                                    }
                                    Spacer()
                                    if isSelected {
                                        Image(systemName: "checkmark")
                                            .font(.headline)
                                            .fontWeight(.bold)
                                            .foregroundStyle(AuroraColor.brand)
                                    }
                                }
                                .padding(.horizontal, AuroraMetrics.spacingL)
                                .padding(.vertical, AuroraMetrics.spacingM)
                                .background(
                                    RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                        .fill(isSelected ? AuroraColor.brand.opacity(0.12) : AuroraColor.surface)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous)
                                                .stroke(isSelected ? AuroraColor.brand.opacity(0.4) : AuroraColor.hairline, lineWidth: 1)
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
    }
}
