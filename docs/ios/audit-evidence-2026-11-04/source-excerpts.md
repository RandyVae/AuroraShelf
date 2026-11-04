# Fixed commit source evidence: 3412839b29a01a8be111ef09c161b7255b46750a


## ios/AuroraShelf/UI/Components/HeroBanner.swift

```text
79:                 // 动作按钮栏（HIG 标准尺寸 ≥ 44pt）
80:                 HStack(spacing: AuroraMetrics.spacingL) {
81:                     // 主 CTA：立即播放（borderedProminent）
82:                     Button(action: onPlay) {
83:                         Label("立即播放", systemImage: "play.fill")
84:                             .font(.headline)
85:                             .frame(maxWidth: .infinity, minHeight: AuroraMetrics.minTouchTarget)
86:                     }
87:                     .buttonStyle(.borderedProminent)
88:                     .tint(AuroraColor.brand)
89:                     
90:                     // 次 CTA：我的片单（bordered）
91:                     Button {
92:                         withAnimation(AuroraMotion.quick) {
93:                             isBookmarked.toggle()
94:                         }
95:                     } label: {
96:                         Label(isBookmarked ? "已添加" : "我的片单", systemImage: isBookmarked ? "checkmark" : "plus")
97:                             .font(.headline)
98:                             .frame(minHeight: AuroraMetrics.minTouchTarget)
99:                     }
100:                     .buttonStyle(.bordered)
101:                     .tint(.secondary)
102:                 }
```


## ios/AuroraShelf/UI/Screens/PersonalScreen.swift

```text
128:                     // 3. 源管理与设置列表
129:                     VStack(spacing: 1) {
130:                         Button {
131:                             showVideoSourcePicker = true
132:                         } label: {
133:                             settingRow(
134:                                 icon: "server.rack",
135:                                 title: "当前视频源",
136:                                 value: "\(videoRepo.currentSource.name) (共 \(VideoSourceCatalog.allSources.count) 源)"
137:                             )
138:                         }
139:                         .buttonStyle(.plain)
140:                         
141:                         Button {
142:                             showComicSourcePicker = true
143:                         } label: {
144:                             settingRow(
145:                                 icon: "books.vertical",
146:                                 title: "当前漫画源",
147:                                 value: "\(comicRepo.currentSource.name) (共 \(ComicSourceCatalog.allSources.count) 源)"
148:                             )
149:                         }
150:                         .buttonStyle(.plain)
151:                         
152:                         settingRow(icon: "antenna.radiowaves.left.and.right", title: "直播源", value: "Chaturbate / LL-HLS")
153:                         settingRow(icon: "tv", title: "AirPlay 投屏", value: "就绪 (原生)")
154:                         settingRow(icon: "arrow.down.circle", title: "离线缓存管理", value: "741 KB 已用")
155:                         settingRow(icon: "info.circle", title: "版本基线", value: "2.16.0 (Build 173)")
```

```text
191:     private func settingRow(icon: String, title: String, value: String) -> some View {
192:         HStack(spacing: AuroraMetrics.spacingM) {
193:             Image(systemName: icon)
194:                 .font(.body)
195:                 .foregroundStyle(AuroraColor.brand)
196:                 .frame(width: 24)
197:             
198:             Text(title)
199:                 .font(.body)
200:                 .foregroundStyle(AuroraColor.textPrimary)
201:             
202:             Spacer()
203:             
204:             Text(value)
205:                 .font(.subheadline)
206:                 .foregroundStyle(AuroraColor.textSecondary)
207:             
208:             Image(systemName: "chevron.right")
209:                 .font(.caption2)
210:                 .foregroundStyle(AuroraColor.textTertiary)
211:         }
212:         .padding(.horizontal, AuroraMetrics.spacingL)
213:         .padding(.vertical, AuroraMetrics.spacingM)
214:     }
```


## ios/AuroraShelf/UI/Screens/NativePlayerView.swift

```text
52:                     .ignoresSafeArea()
53:                     .contentShape(Rectangle())
54:                     .onTapGesture(count: 2) {
55:                         toggleFillMode()
56:                     }
57:                     .onTapGesture(count: 1) {
58:                         withAnimation(AuroraMotion.quick) {
59:                             showControls.toggle()
60:                         }
61:                         if showControls {
62:                             scheduleAutoHideControls()
63:                         }
64:                     }
```

```text
129:                 if showControls && player != nil {
```

```text
154:                             PlayerOverlayBar(
155:                                 title: video.title,
156:                                 subtitle: "\(video.sourceName) · 1080P 超清直连",
```

```text
277:     private func scheduleAutoHideControls() {
278:         hideControlsTask?.cancel()
279:         let task = DispatchWorkItem {
280:             withAnimation(AuroraMotion.quick) {
281:                 if self.isPlaying {
282:                     self.showControls = false
283:                 }
284:             }
285:         }
286:         hideControlsTask = task
287:         DispatchQueue.main.asyncAfter(deadline: .now() + 3.0, execute: task)
288:     }
```


## ios/AuroraShelf/UI/Theme/AuroraDesignTokens.swift

```text
5: public enum AuroraColor {
6:     // 品牌
7:     public static let brand        = Color(rgb: 0xFF4F7B)   // AuroraCoral，1:1 对齐 Android
8:     public static let brandDeep    = Color(rgb: 0xB20A14)
9:     public static let accentRed    = Color(rgb: 0xE50914)   // 仅用于排行/徽章等标记
10: 
11:     // 表面（层级由低到高）
12:     public static let canvas       = Color(rgb: 0x080A0F)   // 页面底，全 App 唯一一处
13:     public static let surface      = Color(rgb: 0x141724)
14:     public static let surfaceCard  = Color(rgb: 0x1C2130)
15:     public static let surfaceHigh  = Color(rgb: 0x232838)
16: 
17:     // 文字（层级，遵循系统语义）
18:     public static let textPrimary   = Color.primary
19:     public static let textSecondary = Color.secondary
20:     public static let textTertiary  = Color(white: 0.45)
21: 
22:     // 语义状态
23:     public static let success     = Color.green
24:     public static let warning     = Color.orange
25:     public static let destructive = Color.red
26: 
27:     // 来源标识色（与 Android Theme.kt 对齐）
28:     public static let forestGreen  = Color(rgb: 0x2E7D32)   // 草榴社区标识绿
29: 
30:     // 描边 / 发丝线
31:     public static let hairline = Color.primary.opacity(0.12)
```

```text
53: public enum AuroraMotion {
54:     public static let quick  = Animation.spring(response: 0.24, dampingFraction: 0.82)
55:     public static let smooth = Animation.spring(response: 0.36, dampingFraction: 0.78)
56: }
```


## ios/AuroraShelf/Data/Video/VideoRepository.swift

```text
185:             videos.append(VideoItem(
186:                 id: "dm-\(animeId)",
187:                 title: title,
188:                 subtitle: "91动漫 · \(category) · 高清直连",
189:                 coverUrl: coverUrl,
190:                 playbackUrl: fullPageUrl,
191:                 duration: "1080P",
192:                 rating: String(format: "%.1f", Double.random(in: 9.5...9.9)),
193:                 tags: ["1080P", "91动漫", category],
194:                 category: category,
195:                 isTop10: index < 10,
```

```text
339:                 result.append(VideoItem(
340:                     id: "beeg-\(fileId)",
341:                     title: title,
342:                     subtitle: "Beeg · \(category) · 国际精选",
343:                     coverUrl: thumbUrl,
344:                     playbackUrl: "https://beeg.com/-0\(fileId)",
345:                     duration: durationStr,
346:                     rating: String(format: "%.1f", Double.random(in: 9.4...9.9)),
347:                     tags: ["1080P", "Beeg", category],
348:                     category: category,
```

```text
470:                 id: "hg-\(dramaId)",
471:                 title: title,
472:                 subtitle: "黄果短剧 · \(category) · 连载完结",
473:                 coverUrl: coverUrl,
474:                 playbackUrl: fullPlayUrl,
475:                 duration: "短剧全集",
476:                 rating: String(format: "%.1f", Double.random(in: 9.3...9.9)),
477:                 tags: ["短剧", "黄果", category],
478:                 category: category,
479:                 isTop10: index < 10,
```

```text
608:                 id: "91p-\(viewkey)",
609:                 title: title,
610:                 subtitle: "91视频 · \(category) · 热门经典",
611:                 coverUrl: coverUrl,
612:                 playbackUrl: fullPageUrl,
613:                 duration: duration,
614:                 rating: String(format: "%.1f", Double.random(in: 9.3...9.9)),
615:                 tags: ["91原创", "经典", category],
616:                 category: category,
617:                 isTop10: index < 10,
```

```text
717:                 id: "hua-\(vodId)",
718:                 title: title,
719:                 subtitle: "华视影院 · \(category) · 4K 超清",
720:                 coverUrl: coverUrl,
721:                 playbackUrl: playbackPageUrl,
722:                 duration: "4K HDR",
723:                 rating: String(format: "%.1f", Double.random(in: 9.3...9.9)),
724:                 tags: ["4K HDR", "华视影院", category],
725:                 category: category,
726:                 isTop10: index < 10,
```
