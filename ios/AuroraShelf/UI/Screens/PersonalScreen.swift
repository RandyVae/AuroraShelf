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
            AuroraTheme.background.ignoresSafeArea()
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 20) {
                    // 系统状态栏顶部安全间隙（避开 8:20 及灵动岛）
                    Color.clear.frame(height: 44)
                    
                    // 1. 用户头像与会员卡
                    HStack(spacing: 16) {
                        ZStack {
                            Circle()
                                .fill(AuroraTheme.crimson)
                                .frame(width: 56, height: 56)
                            Text("R")
                                .font(.system(size: 24, weight: .black, design: .rounded))
                                .foregroundColor(.white)
                        }
                        
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 6) {
                                Text("Randy")
                                    .font(.system(size: 19, weight: .bold))
                                    .foregroundColor(AuroraTheme.textPrimary)
                                
                                Text("PRO")
                                    .font(.system(size: 10, weight: .black))
                                    .foregroundColor(.black)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Capsule().fill(AuroraTheme.gold))
                            }
                            
                            Text("已同步香港节点 · 全量 19 视频源 + 10 漫画源就绪")
                                .font(.system(size: 12))
                                .foregroundColor(AuroraTheme.textSecondary)
                        }
                        
                        Spacer()
                    }
                    .padding(16)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(AuroraTheme.surface)
                            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(AuroraTheme.glassBorder, lineWidth: 1))
                    )
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    
                    // 2. 娱乐板块（战术 RPG 离线单机游戏接入）
                    VStack(alignment: .leading, spacing: 12) {
                        Text("娱乐 · 游戏")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(AuroraTheme.textSecondary)
                            .padding(.horizontal, 4)
                        
                        Button(action: {
                            let impact = UIImpactFeedbackGenerator(style: .heavy)
                            impact.impactOccurred()
                            showGame = true
                        }) {
                            HStack(spacing: 14) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(AuroraTheme.crimson.opacity(0.2))
                                        .frame(width: 44, height: 44)
                                    Image(systemName: "gamecontroller.fill")
                                        .font(.system(size: 22))
                                        .foregroundColor(AuroraTheme.crimsonGlow)
                                }
                                
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 6) {
                                        Text("欲望女神 · 战术 RPG")
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundColor(.white)
                                        Text("v2.16.0")
                                            .font(.system(size: 10, weight: .bold))
                                            .foregroundColor(AuroraTheme.crimsonGlow)
                                    }
                                    Text("策略战棋 · 原生硬件加速与沉浸视口")
                                        .font(.system(size: 11))
                                        .foregroundColor(AuroraTheme.textTertiary)
                                }
                                
                                Spacer()
                                
                                HStack(spacing: 4) {
                                    Text("启动")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(AuroraTheme.crimsonGlow)
                                    Image(systemName: "play.circle.fill")
                                        .font(.system(size: 16))
                                        .foregroundColor(AuroraTheme.crimsonGlow)
                                }
                            }
                            .padding(12)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(AuroraTheme.surface)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                                            .stroke(AuroraTheme.crimson.opacity(0.35), lineWidth: 1)
                                    )
                            )
                        }
                        .buttonStyle(PlainButtonStyle())
                        .applePressEffect()
                    }
                    .padding(.horizontal, 16)
                    
                    // 3. 源管理与设置列表
                    VStack(spacing: 1) {
                        Button(action: { showVideoSourcePicker = true }) {
                            settingRow(icon: "server.rack", title: "当前视频源", value: "\(videoRepo.currentSource.name) (共 19 源)")
                        }
                        .buttonStyle(PlainButtonStyle())
                        
                        Button(action: { showComicSourcePicker = true }) {
                            settingRow(icon: "books.vertical", title: "当前漫画源", value: "\(comicRepo.currentSource.name) (共 10 源)")
                        }
                        .buttonStyle(PlainButtonStyle())
                        
                        settingRow(icon: "antenna.radiowaves.left.and.right", title: "直播源", value: "HornyStudents / Stripchat")
                        settingRow(icon: "tv", title: "AirPlay 投屏", value: "就绪 (原生)")
                        settingRow(icon: "arrow.down.circle", title: "离线缓存管理", value: "741 KB 已用")
                        settingRow(icon: "info.circle", title: "版本基线", value: "2.16.0 (Build 173)")
                    }
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(AuroraTheme.surface)
                            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(AuroraTheme.glassBorder, lineWidth: 1))
                    )
                    .padding(.horizontal, 16)
                    
                    Spacer().frame(height: 24) // 底部原生呼吸间隙（原生 TabBar 自动接管 SafeArea）
                }
            }
        }
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
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(AuroraTheme.crimsonGlow)
                .frame(width: 24)
            
            Text(title)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(AuroraTheme.textPrimary)
            
            Spacer()
            
            Text(value)
                .font(.system(size: 13))
                .foregroundColor(AuroraTheme.textSecondary)
            
            Image(systemName: "chevron.right")
                .font(.system(size: 12))
                .foregroundColor(AuroraTheme.textTertiary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
    }
}

// 10 大漫画源切换面板
struct ComicSourcePickerSheet: View {
    let selectedSource: ComicSourceDefinition
    let onSelect: (ComicSourceDefinition) -> Void
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        ZStack {
            AuroraTheme.background.ignoresSafeArea()
            
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text("10 大漫画源切换")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    Spacer()
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.white.opacity(0.4))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)
                
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 10) {
                        ForEach(ComicSourceCatalog.allSources) { src in
                            let isSelected = src.id == selectedSource.id
                            Button(action: { onSelect(src) }) {
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(src.name)
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundColor(isSelected ? AuroraTheme.crimsonGlow : .white)
                                        Text(src.description)
                                            .font(.system(size: 11))
                                            .foregroundColor(AuroraTheme.textTertiary)
                                    }
                                    Spacer()
                                    if isSelected {
                                        Image(systemName: "checkmark")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(AuroraTheme.crimson)
                                    }
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(isSelected ? AuroraTheme.crimson.opacity(0.12) : AuroraTheme.surface)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .stroke(isSelected ? AuroraTheme.crimson.opacity(0.4) : AuroraTheme.glassBorder, lineWidth: 1)
                                        )
                                )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
        }
    }
}
