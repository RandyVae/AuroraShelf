# iOS 端原生化重构说明书（交给 Antigravity 执行）

> 文档版本：**v1.1**（v1.0 为 Codex 原始审查；v1.1 为补丁修订，修订记录见 §11.1）
> 日期：2026-11-04
> 目标工程：`ios/AuroraShelf.xcodeproj`（Swift 6.4.2 / SwiftUI + AVKit / Xcode 27.2 / iOS 27.2 SDK / 部署目标 iOS 26.4）
> 基线版本：AuroraShelf 2.16.0 (Build 173)，与 Android 端 versionCode 173 同基线
> 权威规范：[docs/ui/design-system.md](../ui/design-system.md)（Apple HIG 基线）、[docs/ui/visual-qa.md](../ui/visual-qa.md)（验收流程）
> 状态记忆：`.ai/PROJECT_STATE.md`、`.ai/HANDOFF.md`、`.ai/DECISIONS.md`

---

## 0. 一句话任务

**保持「极光视界」的内容能力、品牌气质与信息架构不变，把 iOS 端的界面实现从「Android 视觉的 SwiftUI 复刻」改造成「用 iOS 27 原生组件与原生 Liquid Glass API 构建的原生 iOS 应用」。**

不做的事：不改数据层、不改播放解析、不改视频源能力、不做 UIKit 重写、不引入第三方 UI 库。

---

## 1. 为什么现在必须重构（7 条根因与对应改造项）

下面是 2026-11-04 代码级审查得出的 7 条根因。每条都给出代码证据、改造方向和验收方式。**改造必须逐条闭环，不允许只调颜色和圆角。**

### 根因 1：视觉体系互相冲突（Android 液态玻璃 vs iOS 系统 TabBar vs Netflix 仿制风三套并存）

**证据**
- iOS 底栏使用系统 `TabView`（`ios/AuroraShelf/UI/Screens/MainContainerView.swift:12`），是 iOS 原生形态。
- 但顶栏、卡片、按钮全部是 Android 的视觉复刻：`NetflixTopBar.swift:16` 手绘 3D 丝带 N 标、`:43` 固定 `frame(width: 22, height: 34)`、`:139` 自制 `Capsule().fill(Color(red:0.12,0.14,0.20))` + 自绘描边。
- Android 端底栏是自研液态玻璃 `LiquidBottomNavigation`（Backdrop/Haze/自定义选中透镜），iOS 端的 `LiquidGlassDock.swift` 已被清空只剩一行注释「已被原生 TabView 替代」——两端的「导航语言」不同，但同一个 App 名字、同一个品牌。

**结果**：底部是 iOS 系统 TabBar，顶部是 Android/Netflix 仿制风，卡片又是第三套自定义体系。用户在同屏内感知到三种设计语言，这就是「很烂」的直接来源。

**改造方向**
1. **确立单一视觉语言：原生 iOS 26/27（含系统 Liquid Glass）**。不再追求与 Android 像素级一致，改为「品牌色 + 内容排布一致，控件由系统提供」。
2. 全局删除手绘控件：`NetflixNLogo` 的 Canvas 绘制改为**用系统图标 + 品牌色**表达品牌（或从 Assets 引入一张静态图片作为 AppIcon 同源资产，不要在运行时用 Canvas 画）。
3. 系统 TabBar 保留为底部导航（这是正确的原生选择），但要通过 `.tabViewStyle`、材质与 tint 与顶部工具栏统一观感；**不要**在 iOS 上复刻 Android 的浮动玻璃 Dock。
4. 组件库只允许两类：①系统原生控件（`Button`、`Label`、`List`、`NavigationStack`、`.searchable`、`.toolbar`、`.glassEffect`、`.picker`、`.menu`）；②本项目薄封装（PosterCard、SectionHeader、StatusChip），只做内容排布，不自绘系统控件。

**验收**
- 全屏截图逐个比对：同屏内不再出现两种圆角语言、两种材质、两种按钮形态。
- 底栏、顶栏、列表行、卡片内的按钮全部使用同一套系统材质与 tint。

### 根因 2：把「视觉复刻」误当成了「产品迁移」

**证据**
- 大量魔数尺寸：`.frame(width: 135, height: 195)`（`HomeScreen.swift:96`）、`frame(width: 72, height: 44)`（`NetflixBillboard.swift:140`）、`.frame(width: 125, height: 180)`（`NetflixPosterCard.swift:163`）、`.frame(height: 530)`（`NetflixBillboard.swift:46,194`）。
- 手绘渐变与阴影：`.shadow(color: .black.opacity(0.8), radius: 8)`（`NetflixBillboard.swift:95`）、`.shadow(color: .white.opacity(0.25), radius: 10)`（`:164`）。
- 组件命名仍是 `Netflix*`（`NetflixTopBar` / `NetflixBillboard` / `NetflixPosterCard`），说明实现目标仍是「长得像 Netflix」而不是「像 iOS 应用」。

**改造方向**
1. **重命名并重构**：删除 `Netflix*` 前缀的组件，改名 `AuroraTopBar`、`HeroBanner`、`PosterCard`、`Top10Row` 等，命名反映产品语义而非模仿对象。
2. 卡片尺寸改为**自适应 + 语义化**：宽度由 `containerRelativeFrame`/几何驱动或使用 `ViewThatFits`；比例固定 2:3 但由常量 `AuroraMetrics.posterAspect` 统一管理，不在调用点散落数字。
3. 阴影只保留一处：内容层级由**背景色阶 + 材质**表达（`surface` → `surfaceCard` → `elevated`），不再叠加多层阴影。
4. 组件内部不写死具体文案/来源名，由调用方传入。

**验收**
- `grep` 全工程不得再出现 `Netflix` 命名的组件（文件名、类型名）。
- 尺寸魔数集中在 `AuroraMetrics` 一个文件，调用点只引用具名常量。

### 根因 3：布局依赖硬编码，未按安全区与自适应设计

**证据**
- `HomeScreen.swift:122` 硬传 `topSafeAreaHeight: 52`，`NetflixTopBar.swift:78-80` 用 `Color.clear.frame(height: topSafeAreaHeight)` 手动垫出状态栏高度。
- `HomeScreen.swift:132-139` 顶栏浮层用 `LinearGradient` + `.ignoresSafeArea(edges: .top)`，让顶栏盖在海报上，形成「网页 Banner」观感。
- 顶栏内容区从 `y=0` 起画，`HomeScreen.swift:110` 注释自称「彻底避让系统状态栏」，但机制是猜的 —— 灵动岛机型（Dynamic Island 高度 ≠ 52pt）、横屏、分屏、iPad 全部会错位。

**改造方向**
1. **删除所有 `topSafeAreaHeight` 参数**和任何手写状态栏垫高。改用系统提供的安全区：顶层 `NavigationStack` + `.navigationTitle` / `.toolbar`，或 `.safeAreaInset(edge: .top)`。
2. Hero 区域需要「图片延伸到屏幕顶部、标题浮在图片上」时，用 `.ignoresSafeArea(edges: .top)` 只作用在**图片层**，工具栏用 `.toolbarBackground(.hidden, for: .navigationBar)` + `.toolbar` 让系统负责安全区。不要用自绘渐变盖层模拟。
3. 所有页面移除根部 `AuroraTheme.background.ignoresSafeArea()`（这是给全黑底色用的补丁），改为把深色背景放进 `ZStack` 的**最底层并用 `.background()` 扩展**，或者在 `App` 层统一设置窗口底色，避免每个页面各自为战。
4. 竖屏/横屏/分屏/iPad 都要有明确布局：横屏下 Hero 与列表并列、底栏转为 `NavigationSplitView` 的 sidebar（iPad 必需）。

**验收**
- iPhone 17 Pro、SE (3rd gen, 有刘海)、iPhone 16 Pro Max、iPad Pro 11" 四种尺寸截图：顶栏内容不与灵动岛/状态栏重叠；横屏下底栏不压扁内容。
- `grep` 全工程不得出现 `topSafeAreaHeight`、手写状态栏高度常量。

### 根因 4：首页信息密度失控，主次层级不清

**证据**
`HomeScreen.swift:23-106` 一屏内同时出现：Billboard（`NetflixBillboard.swift` 内部再含品牌胶囊、大标题、标签栏、三个动作按钮）、Top 10 大数字排行、分类胶囊条、推荐海报流、视频源切换胶囊、搜索胶囊、AirPlay、底部 TabBar。Billboard 内 `VStack` 从「N FILM · 极光精选」胶囊 → 28pt 大标题 → 标签栏 → 三按钮，共 4 层信息堆在一张图上。

**改造方向**
1. **定层级规则**：每屏只允许 **1 个主操作**（Filled/强调按钮）、**2 个次操作**（Plain/胶囊）、其余信息降级为文本或元信息。
2. Hero 区重构为：
   - 大标题（语义字体 `.largeTitle` 或 `.title`）
   - 一行元信息（评分 · 年份 · 类型 · 来源，单行可截断）
   - **两个**动作：`播放`（Filled，主）+ `片单`（Plain，次）。「详细信息」移除或收进标题点击进入的详情页。
   - 删除「N FILM · 极光精选」胶囊（品牌表达交给顶栏）。
3. 视频源切换**从顶栏移出**：iOS 上正确的位置是顶栏右侧的 `Menu`/工具栏按钮，或个人/设置页。顶栏只保留：品牌标识（导航标题）、搜索（`.searchable`）、投屏（`AirPlayRoutePicker`）。
4. 分类条从 Hero 下方移到「为你推荐」section 头部，或作为 Hero 之下的吸顶 segmented 控件（用系统 `Picker(.segmented)` 或 `ScrollView` + 系统 chip），不再与顶栏并列争夺注意力。
5. Top 10 与推荐流**不要在同屏上下堆叠两套横向滑动**——横向滑动的两个轨相邻会让用户分不清。改为：Top 10 保留（大数字是识别度最高的资产），推荐流改为**竖向两列网格**（与 Android 端 YouTube 式双列层级一致，见 `.ai/DECISIONS.md`），滚动方向单一。

**验收**
- 首页截图中，视觉最重的元素只有一个（主 CTA）。
- 首屏可见元素总数 ≤ 8 类；不再出现两处横向滑动轨相邻。

### 根因 5：没有使用语义字体与动态排版

**证据**
全工程 **139 处** `.font(.system(size: N))`（`grep -rn "\.system(size:" ios/AuroraShelf | wc -l` 实测于 2026-11-04），典型：`HomeScreen.swift:39` `size: 19`、`:43` `size: 15`、`:84` `size: 12`；`NetflixTopBar.swift:88` `size: 22 weight: .heavy` + `.tracking(-0.5)`；`NetflixPosterCard.swift:75` `size: 13`；`NetflixBillboard.swift:92` `size: 28 weight: .heavy`。所有字号都是写死的，系统字号放大后完全不变。

**改造方向**
1. 建立**语义字体映射表**（第 3 节给出），全部替换为 `.font(.headline)` / `.subheadline` / `.body` / `.caption` / `.footnote` / `.title2` / `.largeTitle` 等。组件内部**不得**再出现 `.system(size:)`。
2. 数字展示型文本（Top 10 排名、时长、评分）例外：排名号允许固定 `size:` 但需用 `@ScaledMetric` 缩放；时长/评分改语义字体。
3. 删除 `.tracking(-0.5)` 这类手工字距微调；如确需紧排，用 `.tracking()` 配合语义样式而非像素级 hack。
4. 中文长文本：所有标题 `lineLimit(2)` + `.minimumScaleFactor(0.8)` 或 `ViewThatFits`；禁止 `lineLimit(1)` 截断关键信息（时长、来源除外）。
5. 引入 `RelativeFont`/`@ScaledMetric` 处理固定尺寸控件（图标的 frame 用 `@ScaledMetric` 或 SF Symbols 的 `.imageScale`）。

**验收**
- 系统字号设为最大档（Accessibility XXXL）后：首页/详情/我的/设置全部可读，无截断、无重叠、无溢出。
- `grep -rn "\.system(size:" ios/AuroraShelf` 仅允许出现在一个白名单文件（数字展示组件）内。

### 根因 6：玻璃效果是伪造的，且与项目设计规范冲突

**证据**
- `ComicReaderView.swift:136,169`、`LivePlayerView.swift:152,210,236`、`NativePlayerView.swift:142` 全部使用 `.fill(.ultraThinMaterial)` 叠加 `Color.black.opacity(0.65)` 胶囊与自制描边。
- `HomeScreen.swift:133-138` 顶栏用 `LinearGradient([black 0.96, black 0.75, clear])` 模拟磨砂。
- 项目自有规范 `docs/ui/design-system.md` 明确写着：**「禁止伪造 blur/opacity/gradient/translucency、嵌套 Glass 或全页面 Glass 化」「Liquid Glass 仅在目标 SDK 支持时使用原生 API」**。当前实现直接违反自家规范。

**改造方向**
1. **弃用手工玻璃**。SDK 为 iOS 26.4 / 27.2，已具备原生 Liquid Glass API，统一改用：
   - 导航栏/底栏：系统 `TabView` / `NavigationStack` 自带材质，配合 `.toolbarBackground(.visible, for: .navigationBar)`；
   - 浮层按钮/胶囊：`.glassEffect(.regular.interactive(), in: .capsule)`（iOS 26+ 原生 API，**以当前 SDK 实际签名为准**，先在 Xcode 中确认可用性再落地）；
   - 需要成组玻璃元素时用 `GlassEffectContainer` 统一管理，避免嵌套玻璃。
2. 播放器的浮层控制条（返回、投屏、弹幕开关）是**视频内容之上**，不是页面材质：应使用系统 `AVKit` 播放控件 / `AVPlayerViewController` 的原生控制，或自定义控件配 `.glassEffect`，**不要**用 `Color.black.opacity` 胶囊假装。
3. 深色沉浸底色保留（`AuroraTheme.background` #080A0F 是产品身份，不是「玻璃」），但要**只保留一处**，放在 App 根或 TabView 根部，不要每个页面各自 `ignoresSafeArea()`。

**验收**
- `grep` 不得再出现 `.fill(.ultraThinMaterial)` + 手工描边组合；出现 `glassEffect` 的地方必须是原生 API。
- 浅色/深色两种外观下截图均无「半透明深色塑料」感。

### 根因 7：验收只到「能编译、能出画」，没有真正的视觉与可访问性验收

**证据**
- 现有验收证据集中在「xcodebuild BUILD SUCCEEDED」「模拟器截图存在」「直播能出画」「播放能走秒」——全部是功能证据，不是视觉证据。
- 无 Dynamic Type、无 VoiceOver、无横竖屏、无 iPad、无浅色模式、无减弱动效（Reduce Motion）验证记录。
- `design-qa.md`（2026-09-06，Android 阶段 2.3.0）头部已自我声明「不再作为当前 UI 门禁」，当前门禁是 `docs/ui/design-system.md` + `docs/ui/visual-qa.md` —— 但**这两份文档没有被执行**。

**改造方向**
1. 严格按 `docs/ui/visual-qa.md` 执行 Screen-by-Screen：Implement → Build → Run → Screenshot → Visual inspection → Fix → 再截图。**每个页面独立走完一轮**，不允许批量改完再统一看。
2. 每轮必须覆盖矩阵（见第 8 节）。
3. 修复根因优先于打补丁：发现问题时先判断是「组件 / 布局 / Design System / 状态 / 响应式 / 导航架构」哪一层的问题，修那一层。

**验收**
- `docs/verification-ios-*.md` 记录每屏每矩阵项的结果与截图路径；`docs/ui/visual-qa.md` 流程被完整走通至少一轮。

---

## 2. 目标架构：iOS 原生信息架构

### 2.1 导航模型

```
TabView (系统, .tint(AuroraTheme.brand))
├── NavigationStack
│   ├── HomeScreen        (精选)  — Hero + Top10 + 推荐双列
│   ├── ComicsScreen      (漫画)  — 源选择 + 双列网格 → push ComicReaderView
│   ├── LiveScreen        (直播)  — 主播网格 → push LivePlayerView (fullScreen)
│   └── ForumScreen       (论坛)  — 板块筛选 + 帖子列表 → push ForumPostDetailView
└── NavigationStack
    └── PersonalScreen    (我的)  — 账户卡片 + 设置列表 → push 设置子页
```

规则：
- 每个 Tab 是独立 `NavigationStack`，**不要**把 `NavigationStack` 放在 TabView 外层。
- 详情页一律 push；播放器、漫画阅读器、直播全屏播放用 `.fullScreenCover`。
- 搜索用系统 `.searchable` 修饰在各自的 NavigationStack 上；搜索范围限定当前模块时，用 `navigationBarDrawer` 之外的局部搜索（不要用全局 sheet 承载带输入的搜索，除非是「全网搜索」这类跨模块场景 —— 全网搜索保留 `.sheet`，但输入框改用系统 `TextField` + `.searchable` 风格）。
- 全局播放入口（点任意海报）保持 `fullScreenCover`，但**统一由一个 `PlayerCoordinator`（`@Observable`）持有当前播放项**，避免每个页面各自维护 `activeVideoForPlayback`（当前 `MainContainerView` 持有、其它页面各自持有，状态分散）。

### 2.2 工具栏分工（顶栏）

| 位置 | 元素 | 实现 |
|---|---|---|
| 导航栏左侧 | 返回 / 模块标题 | 系统 `navigationTitle` |
| 导航栏右侧 | 搜索、投屏、更多 | `.toolbar { ToolbarItem(placement: .topBarTrailing) }`；投屏用现有 `AirPlayRoutePicker`（`AVRoutePickerView`）但套进工具栏 |
| 导航栏标题 | 品牌标识 | 标题用 `.navigationTitle("Netgrey")` + 系统字体加粗，或放一枚静态图片资源；**删掉 Canvas 手绘 N 标** |
| 视频源切换 | —— | 移到首页「为你推荐」section 右上角 `Menu`，或个人页设置列表 |

### 2.3 状态模型

每个数据驱动的页面必须显式覆盖 6 态，且状态视图用系统语义：

| 状态 | 表现 |
|---|---|
| 加载中 | `ProgressView()`（系统样式），不要自绘转圈 |
| 空 | SF Symbol 图标 + 一句说明 + 一个恢复动作（`ContentUnavailableView` for iOS 17+，本工程部署目标 26.4，直接用） |
| 成功 | 正常内容 |
| 错误 | `ContentUnavailableView` + 「重试」按钮 |
| 离线 | 同错误，但文案说明网络原因 |
| 权限/破坏性 | 系统 `Alert` / `confirmationDialog` |

---

## 3. 设计令牌（Design Tokens）

新建 `ios/AuroraShelf/UI/Theme/AuroraDesignTokens.swift`（保留 `AuroraTheme.swift` 作为颜色别名以免破坏调用点，逐步收敛）。

### 3.1 颜色

```swift
public enum AuroraColor {
    // 品牌
    static let brand        = Color(hex: 0xFF4F7B)   // AuroraCoral，1:1 对齐 Android
    static let brandDeep    = Color(hex: 0xB20A14)
    static let accentRed    = Color(hex: 0xE50914)   // 仅用于排行/徽章等标记

    // 表面（层级由低到高）
    static let canvas       = Color(hex: 0x080A0F)   // 页面底，全 App 唯一一处
    static let surface      = Color(hex: 0x141724)
    static let surfaceCard  = Color(hex: 0x1C2130)
    static let surfaceHigh  = Color(hex: 0x232838)

    // 文字（层级）
    static let textPrimary   = .primary
    static let textSecondary = .secondary
    static let textTertiary  = Color(white: 0.45)

    // 语义状态
    static let success = .green
    static let warning = .orange
    static let destructive = .red

    // 来源标识色（与 Android Theme.kt 对齐）
    static let forestGreen  = Color(hex: 0x2E7D32)   // 草榴社区标识绿

    // 描边 / 发丝线
    static let hairline = Color.primary.opacity(0.12)
}
```

规则：
- **禁止**在组件里写 `Color(red:0.12,green:0.14,blue:0.20)` 这类裸 RGB；一律引用令牌。
- 前景色优先用 `.primary` / `.secondary` 语义色，以自动适配 Light/Dark；深色画布下 canvas 保持不变。
- 描边统一 `Color.primary.opacity(0.12)` 级别，不再有 `glassBorder` 独立色。

#### 3.1.1 令牌收敛顺序（**禁止中间态**）

> 本节为补丁。原文要求「保留 `AuroraTheme.swift` 作为颜色别名以免破坏调用点」，但若只加别名而不删除旧定义，
> 工程将同时存在 `AuroraColor` / `AuroraTheme` **两个枚举、两套文字色、两套表面色**，
> 比重构前更混乱。必须按下列 3 步**一次性收敛**，不允许停在「别名」阶段。

**Step 1 — 建立单一真源（阶段 0）**

新建 `UI/Theme/AuroraDesignTokens.swift`，内含 `AuroraColor` / `AuroraMetrics` / `AuroraMotion`。
`AuroraColor` 是**唯一真源**。

**Step 2 — 让 `AuroraTheme` 变成纯转发层（阶段 0，与 Step 1 同一提交）**

```swift
public enum AuroraTheme {
    public static let background      = AuroraColor.canvas
    public static let surface         = AuroraColor.surface
    public static let surfaceCard     = AuroraColor.surfaceCard
    public static let auroraCoral     = AuroraColor.brand
    public static let crimson         = AuroraColor.accentRed
    public static let forestGreen     = AuroraColor.forestGreen
    public static let textPrimary     = AuroraColor.textPrimary   // ← 注意此处
    public static let textSecondary   = AuroraColor.textSecondary
    public static let textTertiary    = AuroraColor.textTertiary
    public static let glassBorder     = AuroraColor.hairline
    public static let appleSpring     = AuroraMotion.smooth
    public static let quickSpring     = AuroraMotion.quick
}
```

**⚠️ 必须修正的既有缺陷**：`AuroraTheme.textPrimary` 当前是 `Color.white`（硬编码纯白），
`textSecondary` 是 `Color(white: 0.72)`。在浅色模式下必然不可读。
按 §3.1 规则必须改为 `.primary` / `.secondary` 语义色。

**Step 3 — 逐文件迁移调用点，最后删除旧定义（阶段 1–2）**

- 迁移范围：`grep -rln "AuroraTheme\." ios/AuroraShelf`
- 全部改为直接引用 `AuroraColor.*` / `AuroraMetrics.*`
- **当 `grep -rn "AuroraTheme\." ios/AuroraShelf` 返回空后**，删除 `AuroraTheme.swift` 整个文件
- 同步删除 `AuroraTheme.swift` 内的 `ApplePressEffect`（自制按压效果，与系统按钮反馈重复）

**硬性要求**

- Step 1 与 Step 2 必须在**同一个提交**完成，不允许只建 `AuroraColor` 而 `AuroraTheme` 仍持有裸值
- Step 3 的删除动作必须与迁移在**同一阶段收尾**，不允许「迁了 80% 就停」
- 全工程禁止出现裸 `Color(red:green:blue:)` 与裸 `Color(hex:)`（令牌外），已发现例外见 §5.1

**验收**

- `grep -rn "AuroraTheme\." ios/AuroraShelf` → 0（阶段 3 完成后）
- `grep -rn "Color(red:" ios/AuroraShelf` → 0
- `grep -rn "Color(hex:" ios/AuroraShelf` → 0（令牌定义文件除外）
- 浅色模式下全工程无纯白文字、无限白背景

### 3.2 字体映射表（替换所有 `.system(size:)`）

| 语义样式 | 用途 | 对应旧硬编码 |
|---|---|---|
| `.largeTitle` | 页面大标题 | size 28 heavy（Hero 标题） |
| `.title` | Hero 标题 | size 28 → 改 `.title` + `.bold` |
| `.title2` | Section 标题 | size 19 bold |
| `.title3` | 卡片标题 | size 15~16 semibold |
| `.headline` | 列表行主文本 / 按钮标题 | size 15~16 semibold |
| `.subheadline` | 次要信息（作者、来源） | size 13 |
| `.body` | 正文 / 描述 | size 14~15 |
| `.callout` | 元信息行（评分·年份） | size 13~14 |
| `.caption` | 标签、徽章 | size 11~12 |
| `.footnote` | 时长、计数 | size 10~11 |

规则：
- 数字展示（Top 10 排名）用 `.system(size: 96, weight: .black, design: .rounded)` + `@ScaledMetric`，且集中在单一组件内。
- 字体权重只用 `.regular / .medium / .semibold / .bold / .heavy`。
- **`.black` 白名单**（仅这四类可用，其余禁止）：
  1. Top 10 排名数字（`Top10Card`）
  2. 展示型徽章文字（`PRO` 会员标、`LIVE` 直播标、`TOP 10` 角标）
  3. 头像内的单字字母（`PersonalScreen` 的 "R"）
  4. `AuroraMetrics` 之外的品牌图形标记
- 理由：`.black` 在上述场景用于「贴纸感」的强调，与系统动态字体混用无碍；
  而正文/标题使用 `.black` 会在浅色模式下压垮字重层次。

### 3.3 间距与尺寸

```swift
public enum AuroraMetrics {
    static let spacingXS: CGFloat = 4
    static let spacingS:  CGFloat = 8
    static let spacingM:  CGFloat = 12
    static let spacingL:  CGFloat = 16
    static let spacingXL: CGFloat = 24
    static let spacingXXL: CGFloat = 32
    static let spacingXXXL: CGFloat = 48

    static let cardRadius: CGFloat = 12
    static let controlRadius: CGFloat = 10
    static let minTouchTarget: CGFloat = 44   // HIG 最小触控

    static let posterAspect: CGFloat = 2.0 / 3.0
    static let heroAspect:  CGFloat = 4.0 / 5.0   // 竖版 Hero，见 §4.1

    static let contentInset: CGFloat = 16
}
```

规则：
- 只允许 4/8/12/16/24/32/48 档；出现其他值必须在本文件登记理由。
- 所有可点元素最小命中区域 ≥ 44×44pt。
- 圆角统一：卡片 12、控件 10、胶囊 `Capsule()`。

### 3.4 动画

```swift
public enum AuroraMotion {
    static let quick  = Animation.spring(response: 0.24, dampingFraction: 0.82)
    static let smooth = Animation.spring(response: 0.36, dampingFraction: 0.78)
}
```
- 全局响应「减弱动效」：所有自定义动画包一层 `@Environment(\.accessibilityReduceMotion)`，为真时改为 `withAnimation(nil)` 或淡入淡出。

### 3.5 浮层材质降级方案（**执行前必读，禁止跳过**）

> 本节为补丁。原始说明书 §6 禁用了 `.ultraThinMaterial`，§4.8 又要求 `.glassEffect`，但两者之间存在执行真空：
> 当前工程 **部署目标是 iOS 26.4**（见 `ios/project.yml`），而构建 SDK 为 **iOS 27.2**；
> `.glassEffect` 的实际签名与可用版本必须**在 Xcode 内实测确认**，不得凭记忆书写。
> 若删掉伪玻璃后无处安放 8 处浮层，实现者极易回退到手搓 `Color.black.opacity` —— 根因 6 复发。

**SDK / 部署目标统一口径（以工程文件为准，勿口头转述）**

| 项 | 权威值 | 来源 |
|---|---|---|
| 部署目标 | iOS 26.4 | `ios/project.yml` |
| 构建 SDK | iOS 27.2 | 本机 Xcode 27.2 |
| `.glassEffect` 可用性 | **待 Xcode 实测确认** | — |

**浮层材质三档策略（按顺序尝试，命中即停）**

1. **第一档 —— 原生 Liquid Glass（首选）**
   ```swift
   .glassEffect(.regular.interactive(), in: .capsule)
   ```
   仅在 Xcode 确认当前 SDK 存在该签名**且**部署目标可覆盖时使用。

2. **第二档 —— 系统单层 Material（降级主路径）**
   ```swift
   .background(.regularMaterial, in: .capsule)
   ```
   这是**唯一合法的 fallback**。单层、无手工描边、无 `Color.black.opacity` 叠加。

3. **第三档 —— 纯色（仅限极端场景）**
   `.background(AuroraColor.surfaceHigh, in: .capsule)`，用于确实不适合半透明的场合。

**绝对禁止（降级也不许）**

- ❌ `.fill(.ultraThinMaterial)` + `.overlay(...stroke(...))` 组合（现有 8 处伪玻璃的典型形态）
- ❌ `Color.black.opacity(0.6~0.7)` / `Color.white.opacity(0.2~0.4)` 叠加模拟玻璃
- ❌ 在 `.material` / `.glassEffect` 之上再叠一层半透明色（= 手工玻璃）
- ❌ 嵌套玻璃（玻璃元素内再放玻璃元素）—— 违反 `docs/ui/design-system.md:19`

**落地动作（阶段 0 必须先做，Phase 1 之前）**

- [ ] 在 Xcode 中实测 `.glassEffect` 是否可用、签名为何、**最低可用版本是否 ≤ 26.4**
- [ ] 在 `.ai/DECISIONS.md` 记录实测结论（可用 / 不可用 / 最低版本）
- [ ] 若不可用或最低版本 > 26.4 → 全工程统一采用第二档 `.regularMaterial`，并在本节标注「已降级」及日期
- [ ] 输出一份「浮层材质对照表」：`浮层名 → 采用档位 → 实现代码`，供 §4.8 的 `PlayerOverlayBar` 统一落地

**验收**

- `grep -rn "ultraThinMaterial" ios/AuroraShelf` 结果为 0
- `grep -rn "glassEffect" ios/AuroraShelf` 每处都能对应到对照表中的档位
- 深色 / 浅色两种外观下截图均无「半透明深色塑料」感、无多层叠加的浑浊雾感

---

## 4. 逐页规格

### 4.1 HomeScreen（重点）

**结构**
```
ScrollView
├── Hero（图片全宽延伸到屏幕顶，**比例固定 4:5（竖版）**）
│   ├── 底部信息区：标题(.title, 2行) / 元信息行(.callout) / [播放 Filled] [片单 Plain]
│   └── 顶部叠放系统工具栏（.toolbar, hidden background）
├── Top 10 横向轨（大数字 + 海报）
├── 「为你推荐 · 源名」section 头（右侧 Menu: 切换视频源）
└── 推荐双列竖向网格（LazyVGrid 2 列，异步加载更多）
```

**硬性要求**
- 顶栏：`.toolbar` + `.toolbarBackground(.hidden, for: .navigationBar)`，**不手写状态栏高度**。
- Hero 图片层可 `.ignoresSafeArea(edges: .top)`，工具栏不跟随。
- **Hero 比例固定 4:5**（竖版海报，Netflix/Disney+ 通用做法）。
  - 理由：iPhone 17 Pro 宽 402pt → 4:5 高约 502pt，可完整露出标题 + 元信息 + 2 个 CTA；
    若用 16:9（高约 226pt），标题与按钮会被挤到屏幕外，信息量不足。
  - 实现：`AspectRatio(4.0/5.0, contentMode: .fit)` + `.frame(maxWidth: .infinity)`，高度随宽度自适应，**不写死像素**。
  - 比例常量统一由 `AuroraMetrics.heroAspect` 提供（§3.3 已定义 `2.0/3.0` 为 posterAspect；heroAspect 需补为 `4.0/5.0`）。
- 元信息行只放一行：`评分 · 年份 · 类型`，`lineLimit(1)`。
- 主 CTA 用系统 `Button` + `.buttonStyle(.borderedProminent)`，品牌 tint；次 CTA 用 `.bordered`。
- 「我的片单」为本地状态，保留现有 `isBookmarked` 行为，但改为 `.bordered` 且不用自制 `applePressEffect`（系统按钮自带按压反馈）。
- 推荐网格：`LazyVGrid(columns: 2, spacing: 12)`，卡片宽 = `(containerWidth - 16*2 - 12) / 2`，海报高度按 `posterAspect` 计算，**不写 135/195**。
- 触底加载：`.onAppear` 于最后一个卡片时触发分页（当前 iOS 端无分页，保持现状但留出接口）。

**移除**
- `topSafeAreaHeight: 52` 参数
- 顶部 `LinearGradient` 盖层
- 「N FILM · 极光精选」胶囊
- 顶栏的「切换视频源 (19源)」胶囊 → 移到 section 头 Menu

### 4.2 分类条

- 组件从 `CategoryPillBar` 重构为 `SourceFilterBar`，使用系统 `Picker(.segmented)` 不合适（分类数量多、名字长），用**系统 chip 风格**：`ScrollView(.horizontal)` + `Button` + `.buttonStyle(.bordered)` + `.tint`，选中态用 `.borderedProminent`。
- 分类名过长用 `.lineLimit(1)` + `minimumScaleFactor(0.85)`。

### 4.3 ComicsScreen

- 顶栏：`.navigationTitle("漫画")` + 工具栏搜索（`ComicRepository` 若有搜索能力则接 `.searchable`，否则移除假按钮；当前 `onSearch: {}` 是空实现，**必须删掉或实现**）。
- 源选择行：同 `SourceFilterBar`，同样系统 chip。
- 网格：`LazyVGrid` 2 列，卡片标题 `.subheadline`，作者/最新章节 `.caption`。
- `ComicReaderView`：
  - 章节切换时 `LaunchedEffect(reader.chapterIndex) { listState.scrollToItem(0) }`（**已有教训，必须保留**）。
  - 阅读器浮层（`.ultraThinMaterial` 胶囊）→ 改用 `.glassEffect` 或系统 `Material` 单层，去掉自制描边。
  - 顶部标题 28pt → `.title2`。

### 4.4 LiveScreen

- 源选择行同上。
- 主播卡片：头像 + 名称 `.headline` + 在线人数 `.caption` + 直播徽章（红点 + 「LIVE」）。红点保留，但在线人数徽章的 `Color.black.opacity(0.7)` 胶囊改用系统材质或直接文本。
- 网格间距统一 12/16。
- `LivePlayerView`：
  - 保留原生 AVPlayer + Chaturbate API 直签 LL-HLS 的**功能实现不动**（这是已验收成果）。
  - 4 泳道弹幕保留；弹幕 `Text` 必须 `maxLines = 1` + `lineLimit(1)` + `widthIn(max: 320)`（**已有教训**）。
  - 顶部返回胶囊、投屏按钮改 `.glassEffect`。
  - 直播源的特殊状态判断保留 `MISMATCH_GRACE_MS` 保护。

### 4.5 ForumScreen

- 板块筛选：把 `全部 | 草榴社区 | 91自拍论坛` 做成系统 `Picker(.segmented)`（3 项，分段控件合适）。
- 帖子列表：改用系统 `List`（`.listStyle(.plain)`，行内分隔线用系统样式），保留 Android 端对齐的颜色语义：
  - `91自拍` 亮红 `#E50914` / `草榴社区` 墨绿 `#2E7D32` 徽章
  - 卡片底 `#161922`、描边 `#262626`、12pt 连续圆角
  - 右侧回帖计数胶囊
- 帖子标题 `.headline`（允许 2 行），元信息 `.caption`。
- 保留「触底分页监听过滤后列表长度」的修正（**已有教训**：`visiblePosts.lastIndex - 4`，不是全局 `lastIndex`）。

### 4.6 PersonalScreen（我的）

- 顶部头像区：去掉 `Color.clear.frame(height: 44)` 这种手写占位，改用 `.safeAreaInset` 或 `List` 的 `Section`。
- 头像 56×56、名字 `.title2`、账号 `.subheadline`、会员徽章 `.caption`。
- 设置项列表：改用系统 `List` + `Section` + `NavigationLink`，**不要**逐行手写 HStack + 44×44 frame。
- 「游戏」入口保留为独立 Section 行（Android 端已并入「我的」页设置列表上方，iOS 保持一致），点击 `fullScreenCover` 打开 `GameContainerView`。
- 删除底部手写 `Spacer().frame(height: 24)`（系统 List 会自动处理安全区）。

### 4.7 SearchSheetView（全网搜索）

- 保留 sheet 形态（跨模块搜索），但输入框改为 `.searchable` 风格：`TextField` + `Image(systemName:"magnifyingglass")`，去掉自制 `RoundedRectangle + stroke` 背景与「取消」文字按钮（改用系统 `ToolbarItem(placement: .cancellationAction)` 的「取消」）。
- 结果行：系统 `List`，左侧缩略图 120×75 → 用 `containerRelativeFrame` 或固定比例 + `.aspectRatio`，标题 `.headline`，元信息 `.caption`。
- 加载态用系统 `ProgressView()` + `.searchable` 的 spinner。
- 空态用 `ContentUnavailableView`。

### 4.8 播放器（NativePlayerView / LivePlayerView / ComicReaderView / GameContainerView）

- 三个全屏播放器统一一个浮层工具条组件 `PlayerOverlayBar`：
  - 返回：系统 `Button` + `.glassEffect(.regular.interactive(), in: .circle)` 或系统 `Label`；
  - 标题：`.headline`；
  - 投屏：`AirPlayRoutePicker`；
  - 弹幕开关：系统 `Toggle` 或 `.buttonStyle(.bordered)`。
- 保留现有已验收能力，不做功能改动：AVPlayer 直链播放、`x-cdn-auth` 头注入、`crypt.key` AES-128 解密路径、Chaturbate LL-HLS 直签、4 泳道弹幕、离线缓存与本地代理推流（若 iOS 端有对应实现则一并统一视觉）。

---

## 5. 组件重构清单

| 现有文件 | 处置 | 说明 |
|---|---|---|
| `Components/NetflixTopBar.swift` | **删除**，拆分为 `AuroraToolbar`（系统 toolbar 封装） | 移除 Canvas N 标、移除 `topSafeAreaHeight` |
| `Components/NetflixBillboard.swift` | 重写为 `HeroBanner.swift` | 语义字体、2 个动作、去掉品牌胶囊与多重阴影 |
| `Components/NetflixPosterCard.swift` | 重写为 `PosterCard.swift` + `Top10Card.swift` | 自适应宽度、语义字体 |
| `Components/CategoryPillBar.swift` | 重写为 `SourceFilterBar.swift` | 系统 chip |
| `Components/LiquidGlassDock.swift` | 删除（保留系统 TabView） | 文件已空 |
| `Components/AirPlayRoutePicker.swift` | 保留，补 `.tint` 与尺寸适配 | 原生 AVRoutePickerView 封装，正确 |
| `Theme/AuroraTheme.swift` | 收敛为颜色别名 + 新增 `AuroraDesignTokens.swift` / `AuroraMetrics` / `AuroraMotion` | 单一真源 |
| `Screens/MainContainerView.swift` | 重构：每 Tab 独立 NavigationStack + PlayerCoordinator | |
| `Screens/HomeScreen.swift` | 按 4.1 重写 | |
| `Screens/ComicsScreen.swift` `LiveScreen.swift` `ForumScreen.swift` `PersonalScreen.swift` | 按 4.3–4.6 重写 | |
| `Search/SearchSheetView.swift` | 按 4.7 重写 | |
| `Comic/ComicReaderView.swift` `Live/LivePlayerView.swift` `Screens/NativePlayerView.swift` | 视觉层按 4.8，数据与播放逻辑不动 | |
| `Services/VideoSourceService.swift` | **删除（死代码）** | 全工程零引用；内含 unsplash mock 图与 googleapis 示例视频 URL，留在工程里会被后续开发者误接为真实数据源。首页实际数据走 `VideoRepository`。删除前先 `grep -rn "VideoSourceService" ios/` 确认为 0 |

### 5.1 已核实的额外清理项（**Codex 审查补丁，实测于 2026-11-04**）

> 以下为原始说明书未覆盖、但经代码实测确认存在的问题。数量均为 `grep` 实测值，非估计。

#### 5.1.1 假按钮：7 处空回调（0 实际行为）

| 文件 | 回调 | 现状 | 处置 |
|---|---|---|---|
| `Screens/ComicsScreen.swift:18` | `onSearch` | **空闭包** | 删除该入口，或接入 `.searchable` |
| `Screens/LiveScreen.swift:18` | `onSearch` | **空闭包** | 同上 |
| `Screens/ForumScreen.swift:19` | `onSearch` | **空闭包** | 同上 |
| `Screens/HomeScreen.swift:118` | `onCast` | 仅 `UIImpactFeedbackGenerator` 震动，**无实际行为** | 删除；投屏走 `AirPlayRoutePicker`（见下） |
| `Screens/ComicsScreen.swift:21` | `onCast` | 同上 | 删除 |
| `Screens/LiveScreen.swift:21` | `onCast` | 同上 | 删除 |
| `Screens/ForumScreen.swift:22` | `onCast` | 同上 | 删除 |

**说明**：投屏的真实实现是 `Components/AirPlayRoutePicker.swift`（`AVRoutePickerView`），
已正确挂载在顶栏与播放器控制栏；`onCast` 回调是一条**从未接线的死路径**。
这些「点了没反应」的按钮正是「体验很烂」的高频来源，必须清除或接线，**不允许保留空壳**。

#### 5.1.2 硬编码业务文案（数量应动态生成）

| 位置 | 当前文案 | 处置 |
|---|---|---|
| `Screens/PersonalScreen.swift:47` | 「已同步香港节点 · 全量 19 视频源 + 10 漫画源就绪」 | 数量改为从 `VideoSourceCatalog.allSources.count` / `ComicSourceCatalog` 动态生成 |
| `Search/SearchSheetView.swift:28` | 「全局并发搜索全网 19 个视频源...」 | 同上，改为动态数量 |

违反 §6 Don't #8「不要在组件里写死业务文案、来源名、数量」。

#### 5.1.3 令牌外的裸颜色（实测 10 处）

- `Screens/ForumScreen.swift:126` — `Color(hex: 0xFFD6DCE5)` 正文色裸写，未走令牌
- `Components/NetflixTopBar.swift:5,6,9,10,89,105` — 6 处裸 `Color(hex:)`（`NetflixTopBar` 本就要删除，随之消失）
- `UI/Theme/AuroraTheme.swift` 内全部裸值 → 由 §3.1.1 Step 2 收敛

**注意 §3.1.1 验收项**：`grep -rn "Color(hex:" ios/AuroraShelf` → 0（令牌定义文件除外）。
`Color(hex:)` 扩展本身**保留**（令牌文件仍需使用），只是不允许在业务代码里裸写。

#### 5.1.4 ⚠️ `Color(hex:)` 扩展存在 8 位/6 位语义歧义（**潜在陷阱**）

现有扩展 `Color.init(hex: UInt32)` 按 **RGB 24 位** 解析（取 `>>16`、`>>8`、`&0xff`）。
因此 **8 位写法的 `0xFF` 前缀会被当作红色分量**，而非 alpha：

| 写法 | 实际渲染 | 注释声称 | 是否正确 |
|---|---|---|---|
| `Color(hex: 0xFFFF4F7B)` | `#FF4F7B` | `#FF4F7B` | ✅（前缀恰好等于 R=0xFF） |
| `Color(hex: 0xFFB20A14)` | `#B20A14` | `#B20A14` | ✅（同上） |
| `Color(hex: 0xFFE50914)` | `#E50914` | `#E50914` | ✅ |
| `Color(hex: 0xFF161922)` | `#161922` | `#161922` | ✅ |
| `Color(hex: 0xFF2E7D32)` | `#2E7D32` | `#2E7D32` | ✅ |
| `Color(hex: 0xFFB6BFCC)` | `#B6BFCC` | `#B6BFCC` | ✅ |
| `Color(hex: 0xFFD6DCE5)` | `#D6DCE5` | — | ✅ |

**结论：现有 8 处 8 位写法恰好全部正确**（因为这些颜色的 R 分量都是 `0xFF`），**当前无渲染 bug**。
但这是**极其脆弱的巧合**：任何人写 `Color(hex: 0xFF123456)`（R≠0xFF）都会得到完全错误的颜色且无任何报错。

**处置（阶段 0 一并完成）**：

1. 将扩展改为**显式区分**两种语义，避免歧义：
   ```swift
   public extension Color {
       init(rgb: UInt32) { /* 24 位 RGB，不透明 */ }
       init(rgba: UInt32) { /* 32 位 RGBA */ }
   }
   ```
2. 迁移全部调用点：8 位写法 → `init(rgb:)`（去掉 `0xFF` 前缀），6 位写法 → `init(rgb:)`（补 `0x` 前缀）
3. 删除 `init(hex:)` 或保留为 `@available(*, deprecated, message: "使用 init(rgb:)")` 并附编译期警告
4. 在 §3.1 令牌文件中统一使用 `init(rgb:)`

**验收**：全工程无 `Color(hex:` 调用；`grep -rn "init(hex:" ios/AuroraShelf` 仅剩弃用声明。

#### 5.1.5 已核实无误项（无需处理，供交叉验证）

- `Components/LiquidGlassDock.swift` — 确认仅剩 1 行注释，删除无副作用
- `Services/VideoSourceService.swift` — 确认零引用
- `VideoItem` 模型字段完整（`id/title/subtitle/coverUrl/playbackUrl/duration/rating/tags/category/isTop10/rankNumber/sourceName`），§4 规格无需额外字段

新增：
- `UI/Theme/AuroraDesignTokens.swift`
- `UI/Theme/AuroraMetrics.swift`（可与上文件合并）
- `UI/Components/AuroraToolbar.swift`
- `UI/Components/HeroBanner.swift`
- `UI/Components/PosterCard.swift`
- `UI/Components/Top10Card.swift`
- `UI/Components/SourceFilterBar.swift`
- `UI/Components/SectionHeader.swift`
- `UI/Components/StatusChip.swift`
- `UI/Components/PlayerOverlayBar.swift`
- `UI/Components/EmptyStateView.swift`（包装 `ContentUnavailableView`）
- `App/PlayerCoordinator.swift`

---

## 6. 强制约束（Do / Don't）

**Don't**
1. 不要新增任何 `.system(size:)` 字体（白名单：`Top10Card` 排名数字 + §3.2 列出的 `.black` 四类徽章/图形，见 §3.2 白名单）。
2. 不要新增 `Color.black.opacity(...)` / `Color.white.opacity(...)` 模拟玻璃的胶囊与按钮。
3. 不要新增 `LinearGradient` 作为 UI 层级工具（只允许用于图片上的可读性遮罩，且必须单一）。
4. 不要手写状态栏/灵动岛高度；不要用 `topSafeAreaHeight`。
5. 不要用 `GeometryReader` 包裹整页布局；优先 `safeAreaInset` / `containerRelativeFrame` / `ViewThatFits`。
6. 不要自制系统控件的替代品（自绘 tab bar、自绘 search bar、自绘 switch、自绘 back button）。
7. 不要在一个页面里放两个相邻的横向滑动轨。
8. 不要在组件里写死业务文案、来源名、数量（`"全网 19 个视频源"` 改为从目录动态生成）。

**Do**
1. 优先 `NavigationStack` / `Toolbar` / `searchable` / `ContentUnavailableView` / `Menu` / `Picker` / `Form` / `List` / `.glassEffect`。
2. 语义字体 + 语义颜色 + `@ScaledMetric`。
3. 每个可点元素 ≥ 44×44pt，且有 `accessibilityLabel`。
4. 支持 Dynamic Type、VoiceOver、Reduce Motion、Light/Dark、iPad 分屏。
5. 每次改动后跑一次真机/模拟器截图，不靠想象判断。

---

## 7. 执行顺序（建议分阶段，每阶段独立可交付）

**阶段 0 — 令牌与基础设施（不改视觉）**

| # | 动作 | 依据 | 验收 |
|---|---|---|---|
| 0.1 | 建立 `AuroraDesignTokens` / `AuroraMetrics` / `AuroraMotion` | §3 | 文件存在，编译通过 |
| 0.2 | **同一提交内**把 `AuroraTheme` 改成纯转发层（指向 `AuroraColor`），并修正 `textPrimary` 硬编码纯白 | §3.1.1 Step 1–2 | `AuroraTheme` 无裸值；浅色模式文字可读 |
| 0.3 | 新建 `PlayerCoordinator` | §2.1 | 播放态唯一持有者 |
| 0.4 | `MainContainerView` 拆成每 Tab 独立 `NavigationStack` | §2.1 | 5 个 Tab 各自可 push |
| 0.5 | **Xcode 实测 `.glassEffect`** 可用性/签名/最低版本，写入 `.ai/DECISIONS.md`；不可用则全工程统一降级 `.regularMaterial` | §3.5 | DECISIONS 有记录 + 浮层材质对照表 |
| 0.6 | `Color(hex:)` 扩展改为 `init(rgb:)` / `init(rgba:)`，迁移全部调用点 | §5.1.4 | `grep -rn "Color(hex:" ios/AuroraShelf` → 0 |
| 0.7 | 删除死代码 `Services/VideoSourceService.swift` | §5 表格 | `grep -rn "VideoSourceService" ios/` → 0 |
| 0.8 | 清除 7 处空回调（3× `onSearch` + 4× `onCast`） | §5.1.1 | 无空闭包；投屏走 `AirPlayRoutePicker` |

**阶段 0 整体验收**：`xcodebuild BUILD SUCCEEDED`，行为与重构前一致（不改变任何视觉输出），§9 回归红线逐条自检通过。

**阶段 1 — 首页（最高优先级）**
按 4.1 重写 `HomeScreen` + `HeroBanner` + `PosterCard` + `Top10Card` + `SourceFilterBar` + `AuroraToolbar`，删除 `NetflixTopBar`。验收：首页截图对比 + 字号最大档 + 横屏 + iPad。

**阶段 2 — 列表型页面**
`ComicsScreen` / `LiveScreen` / `ForumScreen` / `PersonalScreen` 全部改系统 `List`/`LazyVGrid` + 语义字体。验收：四页各自截图与状态矩阵。

**阶段 3 — 沉浸与浮层**
播放器、阅读器、直播、游戏容器：统一 `PlayerOverlayBar`，原生材质替换伪玻璃。验收：全屏播放无遮挡、弹幕不重叠、返回手势正常。

**阶段 4 — 搜索与细节**
`SearchSheetView`、帖子详情、设置子页。验收：键盘/焦点/VoiceOver。

**阶段 5 — 全矩阵验收与收尾**
执行第 8 节完整矩阵，产出 `docs/verification-ios-native-refactor.md`，更新 `CHANGELOG.md`、`.ai/HANDOFF.md`、`.ai/PROJECT_STATE.md`、`MEMORY.md`。

---

## 8. 验收矩阵（每页必过）

| 维度 | 取值 |
|---|---|
| 设备 | iPhone 17 Pro、iPhone SE (3rd gen)、iPad Pro 11" |
| 方向 | 竖屏、横屏 |
| 字号 | 默认、Large、Accessibility XXXL |
| 外观 | 深色、浅色 |
| 状态 | 加载、空、成功、错误、离线 |
| 辅助 | VoiceOver 全元素可朗读、Reduce Motion 生效、键盘可完成搜索 |

产出要求：
- 截图存 `captures/ios-native-refactor/`，命名 `<page>_<device>_<variant>.png`。
- 每个发现的问题在 `docs/verification-ios-native-refactor.md` 记录：现象 → 根因层级 → 修复 → 复验截图。

---

## 9. 禁止破坏的既有成果（回归红线）

以下为已验收的功能成果，本轮**不得回归**：

1. **真实视频源直链播放**：`VideoRepository.resolveHuavodStream` 的 `/Player/next` → 20s 倒计时 → `/Player/resolveUrl` 签名链；预热 `prewarmHuavod`。
2. **91动漫 CDN 鉴权头**：`data-cdn-auth` / `data-cdn-app` 注入 `AVURLAssetHTTPHeaderFieldsKey`，否则 EdgeOne 403 且 `crypt.key` 拉不到（00:00 卡死）。
3. **直播直签 LL-HLS**：Chaturbate `chatvideocontext` API → `hls_source` → 原生 AVPlayer 硬解，**不要再退回 WKWebView 方案**。
4. **删除本地 `intro_film.mp4` 劫持**：任何情况下不得重新引入本地片源回退。
5. **`java.util.Base64` 优先**：minSdk ≥ 26，JVM 单测环境下 `android.util.Base64` 会返回 null 导致单测失败。
6. **漫画阅读器切章归顶**：`LaunchedEffect(reader.chapterIndex) { listState.scrollToItem(0) }`。
7. **弹幕单行约束与错峰延迟**：单行、截断、`widthIn(max:320)`、阶梯 `delayMs`。
8. **论坛触底分页用过滤后长度**：`visiblePosts.lastIndex - 4`。
9. **Swift 6 并发安全**：`actor StreamCache` 等既有并发模型不要退化为非线程安全共享状态。
10. **构建产物路径**：必须显式 `-derivedDataPath ios/build`，避免安装旧缓存包（**已有教训**）。

---

## 10. 快速自查清单（提交前逐条打勾）

**视觉与布局**
- [ ] 页面里没有 `.system(size:)`（白名单见 §3.2）
- [ ] 页面里没有 `topSafeAreaHeight` / 手写状态栏高度
- [ ] 页面里没有自绘玻璃（`ultraThinMaterial` + 手工描边组合）→ `grep -rn "ultraThinMaterial" ios/AuroraShelf` 为 0
- [ ] 每个浮层的材质都能对应到 §3.5 对照表中的档位
- [ ] 页面里没有自绘系统控件（tab bar / search bar / switch / back）
- [ ] 每个屏幕有一个明确主操作
- [ ] 所有可点元素 ≥ 44pt 且有 accessibilityLabel

**令牌与代码卫生**
- [ ] `grep -rn "AuroraTheme\." ios/AuroraShelf` → 0（§3.1.1 Step 3 完成后）
- [ ] `grep -rn "Color(red:" ios/AuroraShelf` → 0
- [ ] `grep -rn "Color(hex:" ios/AuroraShelf` → 0（令牌定义文件除外）
- [ ] 浅色模式下无纯白文字、无限白背景

**功能与死代码**
- [ ] 无空回调（3× `onSearch` + 4× `onCast` 已清除或接线）
- [ ] 业务数量文案从 `VideoSourceCatalog` / `ComicSourceCatalog` 动态生成，无写死「19 源」
- [ ] `grep -rn "VideoSourceService" ios/` → 0（死代码已删）

**验收证据**
- [ ] 语义字体 + 语义颜色已使用
- [ ] 深色/浅色、字号最大档、横屏、iPad 各有截图
- [ ] Reduce Motion 下无强制动画
- [ ] 第 9 节回归红线逐条自检通过

---

## 11. 参考

- Apple HIG：https://developer.apple.com/design/human-interface-guidelines/ios
- SwiftUI `glassEffect`：**部署目标为 iOS 26.4（`ios/project.yml`），构建 SDK 为 iOS 27.2**；
  `.glassEffect` 的实际签名与最低可用版本以当前 Xcode 实测为准，不要凭记忆写。
  不可用时的降级路径见 §3.5（三档策略）。
- 本项目规范：[docs/ui/design-system.md](../ui/design-system.md)（第 19 行：禁止伪造 blur/opacity/gradient、Liquid Glass 仅在 SDK 支持时用原生 API）、[docs/ui/visual-qa.md](../ui/visual-qa.md)
- Android 对齐基线：[.ai/DECISIONS.md](../../.ai/DECISIONS.md)（液态玻璃导航、双列信息层级、19 视频源分组等决策）
- 既往教训：项目根 `../MEMORY.md`「坑与教训」区（含 SwiftUI ignoresSafeArea 越界、coerceIn 空区间、Base64 null、GraphQL 字段废弃、章节滚动残留等）

### 11.1 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-11-04 | Codex 原始审查：7 条根因 + 目标架构 + 令牌 + 逐页规格 + 组件清单 + 约束 + 阶段 + 验收矩阵 + 回归红线 |
| **v1.1** | **2026-11-04** | **补丁（本次修订）**：新增 §3.5 浮层材质三档降级方案；新增 §3.1.1 令牌收敛顺序（禁止中间态）；新增 §5.1 已核实额外清理项（7 处空回调、硬编码文案、裸颜色、`Color(hex:)` 8/6 位歧义、死代码）；阶段 0 扩为 8 项；修正 `.system(size:)` 计数 150+ → 139；`.black` 白名单补徽章类；Hero 比例定死 4:5；§10 自查清单按 4 类分组 |

**v1.1 修订未改动任何 iOS 源码**，仅补充说明书本身的执行空白与实测数据。
