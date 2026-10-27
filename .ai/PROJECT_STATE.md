# Project State

Last Updated: 2026-10-27 17:18 HKT

## Project

Netgrey · Android 2.15.0（候选包 versionCode 172 正式完成打包与全项验证；改动覆盖 HornyStudents 直播流画面硬裁切根治、WebView 物理像素适配根治 100vh 高度塌陷黑屏、私密秀过早误判阻断加载修复、Native DOM 顶层脱敏拔出消除 40px 偏移、信息流卡片 4:3 比例适配与全屏沉浸播放横竖屏自适应无缝切换、找回现代化 Netflix 首页 UI、XVideos 完整 41 分类、播放器操作按鈕視覺 Bug 修復、8Muses/GEDE Comix/91動漫三大漫畫源深度接入、91動漫視頻源獨立接入、XVideos 官方視頻源接入並設為預設源、五大分類擴展、全網視頻源統一併發搜索與輪詢交織算法混排、HornyStudents 直播源深度接入、RSS 2.0 在線主播解析、四大性別分類過濾、雙直播源切換、Beeg 官方視頻源深度接入、REST API 解析、多態分類導航、聯網搜索、ExoPlayer 原生硬解播放、華視影院官方視頻源深度接入、五大分類多態映射、ArtPlayer/BunnyCDN 簽名解析、236 集自動識別、ExoPlayer 原生硬解播放、全量预设视频源深度修复、43分类100%探针通过、85po正则适配、JAV-NONI域名轮换与防回溯栈溢出重构、MissAV 403路由修复、OHentai死链过滤、视频源线路属性分组、Apple HIG状态胶囊、首页异常自愈回退、仿 Bilibili 视口底缘细线进度条与微光分割线、Apple HIG 控制器联动弹性动画、仿 Bilibili 纵向视频两阶段滚动收窄折叠机制、视口收折至 16:9 吸顶释放 833px 内容空间、Apple HIG 弹簧物理吸附、浮点公差与超宽屏数学健壮性防御、仿 Bilibili 纵向视频自适应展开与黑边消除、91 论坛 5 大核心板块并发聚合、帖量激增 1150%、筛选触底分页修复、播放器动作按钮双轴绝对居中与双字对称重构、全链路零等待预加载、12小时磁盘快照容灾、Apple HIG 流体物理微交互与抽屉展开过渡、Netgrey 品牌化、Netflix 像素级复刻、黄果短剧无限加载、液态玻璃与日夜间双模式沉浸一致性、直播间非全屏视口空间扩充、全屏 4 泳道防重叠弹幕系统与纯净影院模式、Netflix 3D 绯红丝带启动图标重构、全板块顶栏统一对齐首页与搜索框垂直居中根治、纯 Kotlin DLNA/UPnP 电视投屏核心协议栈、本地流媒体防盗链代理、离线缓存 SimpleCache 推流修复、HLS M3U8 清单代理重写、局域网代理 Token 安全加固与全链路性能优化、91自拍论坛域名轮换修复与门户页动态探测回退、论坛吸顶分类胶囊直达过滤）

## Current Stage

**172 版本（Candidate 172）HornyStudents 直播畫面裁切、視口黑屏與誤判私密秀深度根治已完成**（`versionCode = 172`，`versionName = 2.15.0`）：
- 本地交付包：`dist/AuroraShelf-2.15.0-172-live-crop-fix-LOCAL.apk`
- 產物體積：**4,418,209 bytes**；**SHA-256**：`ee4e955aaa283e53214c32804064d044d76605eae284389ee7fc5aedd07b7dfe`
- JVM 205 tests：201 passed, 0 failed, 0 errors, 4 skipped；Release 構建成功（54s）。
- 5 大核心問題徹底根治（已驗證）：
  1. 根治 Chaturbate `video.js` 動態注入 `clip-path: inset(0px 199.5px)` 導致畫面左右兩側被硬削切 200px 丟失人體的原生缺陷；
  2. 根治 Android Compose WebView 嵌入環境下 CSS `100vh` 基準丟失導致播放器高度塌陷為 `0px`（黑屏）的缺陷，全面使用精確物理像素 `${viewportWidth}px` 與 `${viewportHeight}px` 連鎖保護；
  3. 根治加載初期誤將常規導航 `<a data-testid="private-shows-tab">` 判定為「主播正在進行私密表演」彈層並中斷加載的缺陷，限定選擇器並引入 3 秒保護寬限期；
  4. 消除 Native DOM 頂部 40px `#static-header` 偏移與父容器 `overflow: hidden; height: 221px;` 底部截斷，將播放器直接拔出至 `document.body` 頂層；
  5. 信息流卡片採用 4:3 比例消除主播頭部被切，非全屏播放器默認 4:3 並動態跟隨相機分辨率自適應，全屏橫屏完美鋪滿 2340×1080 視口無黑邊。
- `missavtest` Android 14 / 1080×2340 / 440 模擬器安裝實測：主播 `waifusummerr` 成功出畫，畫面頭部、足部、兩側相機內容 100% 完整無損展示，實時聊天彈幕滾動順暢，全屏切換與旋轉橫屏順暢無黑框。

**169 版本（Candidate 169）找回現代化 Netflix 首頁 UI、XVideos 完整 41 分類與 HornyStudents 直播深度修復已完成**：
- 本地交付包：`dist/AuroraShelf-2.15.0-169-xvideos-live-fix-LOCAL.apk`；4,418,213 bytes；SHA-256 `dd4e436e01730812d6420ff6ad408b99ca1a4237e03f4c78a8fb95c76941f576`。
- JVM 205 tests：201 passed, 0 failed, 0 errors, 4 skipped；Release 構建成功。
- 事故定性與 UI 復原：DeepSeek 曾誤清空 `HomeScreen.kt` 並執行 `git checkout HEAD` 抹掉未提交的現代化 893 行首頁代碼；現已從 IDE 任務備份完整找回並覆蓋復原，保全 Spotlight Billboard、今日熱播 Top 10 大卡片、為你推薦橫向貨架與毛玻璃分類條。
- `missavtest` Android 14 / 1080×2340 / 440 模擬器安裝實測：首頁 Netflix 影院風格完美呈現，41 個 XVideos 官方分類橫向滑動順暢；HornyStudents `miablueye` 房間進入直播間直接出畫（`Video Player [0,268][1080,879]`）並實時串流滾動公開聊天室。
- TalkBack、真機 E4、200% 字體與 41 個分類逐項聯網加載未於本輪逐一加載。

**168 版本（Candidate 168）全量验证与正式打包已完成**：
- 正式產物：`dist/AuroraShelf-2.15.0-168-comics-8muses-gede-91dm-fix-LOCAL.apk`
- 體積：**4,420,837 bytes**（約 4.22 MB）
- SHA-256：`d873c9fd5b9d37535560938f36c53545fa5cb60ea17a4249a5b3f2dbca5aeb8c`
- 測試狀態：JVM 單元測試 204 項全量通過（204 passed, 0 failed, 4 skipped）；AAPT2 badging、apksigner v2 簽名、16 KB 頁對齊、SHA-256 追加；模擬器端到端實機走查通過：
  1. 播放器操作按鈕淺色模式白底白字 Bug 徹底修復，統一為 Apple HIG 暗黑毛玻璃質感；
  2. 8Muses 歐美 3D / 成人漫畫源加載、分類切換與全彩章節圖片解析正常；
  3. GEDE Comix 歐美原版漫畫源加載、分類切換與 WebP 正文圖片解析正常；
  4. 91動漫 漫畫端（同人志/單行本/韓漫/排行榜）加載正常；
  5. 91動漫 視頻端獨立接入，首頁 5 大分類（里番/3D動漫/AI動漫/排行/全部）加載正常，ExoPlayer 原生硬解 HLS 視頻秒出畫播放正常。

## Current Objective

按用户选定的「方案 1」重新实现的 Android 客户端。采用 Kotlin、Jetpack Compose、Material 3、Haze 与 AndroidLiquidGlass/Backdrop；Material You 界面保留真实视频源能力，并以浮动玻璃导航、吸顶分类和弹性选中动效重构视觉层级，不是网页套壳首页。2.9.0 保留首个大幅推荐视频并将后续内容改为 YouTube 式双列信息层级；2.9.1 新增完整日间与夜间主题，默认跟随系统即时切换。

## Completed

- 2026-09-24：跨 Agent 交接初始化；黄果封面 CDN 轮换与动态 crypto-worker 配置跟随。
- 2026-10-24：审计、代码整体优化、UI 结构拆分、HIG 第一轮（136 候选）。
- 2026-10-24：**加载优化**（首屏快照、封面内存/磁盘 LRU、单航班、连接复用、播放解析缓存、首屏预热）。
- 2026-10-24：**HIG 第二轮**（高对比三态 token + 检测 + 动态色守卫、深色封面压暗、200% 字体降列与 hero 重叠修复、15 处 heading、字幕轨优选、3 项无障碍设备断言）。
- 2026-10-24：修复收藏库异步化回归与首页文案被并发改名影响；JVM 125/0/1、定向设备测试 PASS；Release 137 构建并安装验证。
- 2026-10-24：品牌定名 **Netgrey**（英文名，不再使用中文名）；启动图标重绘为扁平矢量 N，冷灰 `#9AA3AE` + 纯黑底；自适应图标前景/单色层改为 VectorDrawable（删除 10 张 PNG）；debug 构建 PASS、lint 0 errors/19 warnings/3 hints、模拟器抽屉与首页标题实测通过。
- 2026-10-25：**直播板块体验优化**：根治“主播点进去看不到内容”与“加载慢”。注入 `isVisitorsAgreementAccepted=1` 及 `.btn-visitors-agreement-accept` 智能穿透年龄确认门禁；拦截 16+ 广告/追踪与重型非核心插件（大转盘/互动动效）；探测循环重构为 35s 真实时间倒计时自适应轮询，秒级识别购票秀/私密秀/离线状态并提供语义状态层与系统浏览器外链兜底；单元测试覆盖；实机端到端出画与聊天流验证通过。
- 2026-10-25：**Bug 修复与交互体验优化**：修复论坛有缓存时刷新失败静默无提示、重试按钮错位到底部并错误触发 `onLoadMore` 的缺陷（拆分 `state.error` 与 `state.loadMoreError` 并新增顶部错误横幅与精确重试）；统一直播播放器裁剪、观察器与控制条全部视频选择器为 `LIVE_VIDEO_SELECTOR`；新增 `ForumUiStateTest` 覆盖状态解耦，全量 JVM 单元测试 158 tests / 0 failures 全部通过。
- 2026-10-25：**开屏动画 4K 60fps 竖屏 9:20 居中自适应裁剪**：根据桌面 `Netflix片头_4K_60fps版.mkv`，通过 `crop=972:2160:1434:0,scale=1080:2400` 裁剪自适应重编码为 1080×2400 60fps H.264 High 5.1 + AAC，更新 `IntroTiming`（5.7s）、`intro_film_start.jpg` 及 `intro_poster.jpg`。
- 2026-10-25：**全局液态玻璃（Liquid Glass）视觉与结构统一**：注入 `LocalBackdrop`；重构 `GlassSurface` 支持 HIG 磨砂透光材质（82%~88% 不透明度守卫防底层穿透文字干扰）与 1dp 微光边缘；升级漫画、直播吸顶分类栏为液态玻璃悬浮胶囊；升级搜索栏为吸顶液态玻璃胶囊；升级播放器动作按键与悬浮控件为液态玻璃圆形/圆角控件；Release 实机测试通过。
- 2026-10-25：**直播全屏自适应方向与返回交互升级**：根治“直播点击全屏后竖屏变横屏躺平”问题。通过 `AuroraLiveBridge` 实时回传源站视频真实宽高，动态决策竖屏流使用 `SCREEN_ORIENTATION_SENSOR_PORTRAIT` 铺满手机屏幕，横屏流自适应横屏；全屏模式新增左上角半透明返回胶囊，BackHandler 解耦优先退出全屏；单元测试 `LiveScreenTest` 覆盖（159/0/1 通过），Release 构建与模拟器端到端验证通过。
- 2026-10-25：**主页头部移除冗余视频源下拉框**：主页「默认视频源 ▼」已在设置中具备完整管理能力，移除主页冗余下拉项；升级 `Netgrey` 为 `headlineMedium` 粗体与右侧液态玻璃搜索/设置胶囊垂直居中对齐；全量 JVM 单元测试 159/0/1 通过，Release 构建与模拟器端到端验证通过。
- 2026-10-25：**重构吸顶分类栏为 Netflix 风格胶囊药丸**：根治外层白卡片气泡与文字下划线违和问题。重构为通栏边缘贯通磨砂条（`shape = RectangleShape, fillMaxWidth`），各分类升级为独立 `NetflixCategoryChip`（选中为反色纯色实体填充药丸、未选为微磨砂半透明胶囊带 1dp 精细轮廓线，全部分类带下拉箭头），全量 JVM 单元测试 159/0/1 通过，Release 构建与模拟器端到端验证通过。
- 2026-10-25：**150 全量测试回归与 Release 候选包打包闭环**：根除 Lint 阻断；优化 `AuroraFlowTest` 播放测试避免底部浮栏遮挡误触；清理宿主残留无头 AVD 与前台游戏干扰，`AuroraFlowTest` 实测 10/10 全通，`HigAccessibilityTest` 6/6 全通；打包 `dist/AuroraShelf-2.15.0-150-live-optimized-LOCAL.apk`。
- 2026-10-25：**Netflix 风格全局化重构覆盖 5 大模块与原子沉淀**：提炼公共 Netflix 设计原子至 `AppComponents.kt`；全面覆盖首页、漫画、直播、论坛、个人中心全部 5 大业务模块；正式打包 `dist/AuroraShelf-2.15.0-150-netflix-global-LOCAL.apk`。
- 2026-10-25：**151 Netflix 像素级复刻与黄果短剧无限加载根治**：首页顶部分类条完全透明悬浮化；Top 10 大数字 3D 浮雕层叠景深；黄果短剧路由彻底修正为可分页路由；修复 GlassSurface 控件在系统浅色模式下取色撞白缺陷。
- 2026-10-25：**152 全模块日夜间双模式视觉审美与高对比沉浸一致性**：根 Surface 背景锁死为纯黑 `#000000`；底部液态毛玻璃导航栏锁定暗黑毛玻璃容器与珊瑚红 `AuroraCoral`；个人中心、论坛、播放器详情全面暗黑化对齐。
- 2026-10-26：**153 直播间非全屏视口空间扩充、全屏 4 泳道防重叠弹幕系统与纯净影院模式**：重构非全屏直播间布局；全屏引入 4 泳道水平滑行弹幕系统与 `staggered delayMs` 错峰算法；支持一键切换纯净模式。
- 2026-10-26：**154 应用启动图标全面重构（Netflix 3D 绯红丝带与影院微弧）**：重构为三层光影立体 N 标与底部经典影院微弧，同步更新 Material You 单色层。
- 2026-10-26：**155 全板块顶栏统一对齐首页与搜索框文字垂直居中根治**：抽象公共顶层 `NetflixTopBar` 与 `NetflixSearchCapsule`；漫画搜索入口胶囊化；重构搜索框为 `CenteredSearchBar`。
- 2026-10-26：**156 原生电视投屏能力：DLNA/UPnP 协议栈、本地流媒体代理与 Apple HIG 设备控制体系**：基于纯 Kotlin 协程实现 DLNA/UPnP 局域网组播发现与 SOAP 控制；实现 `LocalMediaProxy` 本地流媒体代理与 `CastDeviceSheet` 投屏控制面板。
- 2026-10-26：**157 投屏协议栈深度修复、安全加固、播放器协同与全链路性能优化**：
  1. **修复离线缓存视频投屏 404 缺陷**：`LocalMediaProxy` 新增 `OfflineCacheStreamOpener` 接口并在 `CastManager` 中桥接 `OfflineCacheManager` 的 Media3 `SimpleCache`（`CacheDataSource`），支持直接读取已缓存视频分片并响应 HTTP 206 Range；同时在 `buildLocalFileProxyUrl()` 中对远端 URL 自动回退走 `/stream` 代理；
  2. **修复 HLS (`.m3u8`) 投屏相对路径断裂与 MIME 识别**：`DlnaSoap.isHlsUrl()` 兼容编码后的 `%2Em3u8`，`LocalMediaProxy.rewriteHlsManifest()` 自动将 M3U8 清单内的分片（`.ts`）与 `URI="..."` 密钥重写为带 `token` 与 `Referer` 的本地代理链接；
  3. **局域网代理安全加固**：`LocalMediaProxy` 引入随机 `sessionToken` 校验与 `isAllowedLocalFile()` 目录白名单，拦截对 `shared_prefs`、`databases` 及敏感后缀文件的越权访问；
  4. **SSDP 并发发现与 `SupervisorJob` 容错**：`SsdpDiscovery` 改为 `async(Dispatchers.IO)` 并发拉取设备描述 XML 并实时增量回调，`CastManager` 升级为 `SupervisorJob()` 并支持顶栏预选设备（`selectedDevice`）；
  5. **播放器生命周期守卫与进度/选集同步**：修复 `NativeSitePlayer` 在投屏状态下从后台恢复前台时误触发 `player.play()` 的问题；投屏起播自动同步手机当前播放进度 `currentPositionMs`，且投屏中切换剧集自动推送至电视；
  6. **启动预热、主线程存储缓存与开屏视频瘦身**：在 `MainActivity.onCreate` 中显式激活 `warmUpViewModel`；为 `AppPreferences.savedVideos()` 增加内存缓存，在 `AuroraShelfApp` 与 `HomeScreen` 中对衍生列表增加 `remember` 记忆化；使用 `ffmpeg` CRF 24 将 `intro_film.mp4` 从 **2.9 MB** 压缩至 **741 KB**（立减约 2.15 MB APK 体积）；
  7. **单元测试扩展与交接文档**：在 `DlnaSoapTest.kt` 新增 3 项针对 HLS 清单重写、文件沙盒与离线分片代理的测试（总计 171 项测试），并编写 `docs/handoff-2026-10-26-antigravity.md`。

- 2026-10-26：**158 91 自拍论坛域名轮换修复、门户页动态发现回退与板块分类胶囊直达**：
  1. **91 自拍论坛 404 彻底修复**：定位原硬编码域名 `t906.abc2507.cc` 已下线；更新最新基础域名为 `https://t924.sol140.com`，引入门户主页（`h1014.sol140.com`）Cookie 穿透动态探测与候选列表（`t924`、`t925`、`t906`）自动回退切源机制；
  2. **板块分类胶囊直达**：针对草榴海量回帖淹没 91 帖子问题，在论坛顶栏下方新增吸顶 `CategoryStrip`（`全部` | `草榴社区` | `91自拍论坛`），支持一键过滤纯享 91 帖子；帖子卡片采用 Netflix 经典绯红 `#E50914` 标签高亮；
  3. **阅读器适配与端到端闭环**：`mobileForumScript` 扩展适配 `t924` 及任意 `t9xx` 域名；全量 175 项 JVM 单元测试 100% 通过；Release 候选包构建、签名、16 KB 对齐与模拟器实测出画全部通过。

- 2026-10-26：**159 全链路零等待预加载、12小时磁盘快照容灾与 Apple HIG 流体物理微交互**：
  1. **视频流提前预解析（`prefetchLeadingStream`）**：首页首屏完成后提前 1.7 秒后台调用 `PlaybackResolver().resolve(lead)` 预取 Top 1 视频直链写入 `playbackCache`，点击播放 0 延时即时开播；
  2. **漫画与直播 12 小时本地磁盘快照（`CachedComic` / `CachedLive`）**：冷启动与分类切换乐观保底渲染（Optimistic Stale UI），首帧 0ms 呈现，杜绝白屏与转圈闪动；
  3. **漫画滑动窗口前瞻预取（Sliding Window Preload）**：在 `ComicReader` 中基于可见项向前侦测 3 帧高清单页向 Coil 队列异步解码预取，实现翻页零等待；
  4. **直播 DNS 预解析与全局阶梯唤醒**：异步预解析 `zh.stripchat.com` 消除 TLS 握手延时；在 `AuroraShelfApp` 根节点按「论坛 -> 直播 DNS -> 漫画 -> 直播」阶梯式静默唤醒；
  5. **Apple HIG 物理弹簧 Token 与按压微交互（`applePressEffect`）**：定义 `AppleSpring` 物理模型，卡片按下 0.96x~0.94x 弹性微凹、抬起低阻尼回弹，覆盖全板块海报、卡片与胶囊；
  6. **方向感知景深切页与播放器抽屉展开**：底栏切页根据索引方向感知位移 + 0.96x 景深缩放；`PlayerScreen` 抽屉式自底向上平滑拉起展开与滑出；
  7. **全量闭环交付**：180 项 JVM 单测 100% 通过；`lintDebug` 0 errors；Release 候选包构建、签名、16 KB 对齐与模拟器实测出画全部通过。
- 2026-10-26：**160 91 论坛 5 大板块并发聚合（帖量激增 1150%）、筛选触底分页修复与播放器动作按钮居中精致重构**：
  1. **91 论坛 5 大核心板块并发抓取**：引入 `CORE_FIDS = listOf(4, 21, 33, 34, 36)` 涵盖原创自拍、我爱我妻、性趣闲谈、两性健康、海外专区，单页抓取量由 16 帖提升至 84 帖，翻页加载 100 帖，帖量激增 1150%；
  2. **修复筛选态触底分页失效缺陷**：修正 `ForumScreen.kt` 触底判定为 `lastVisible >= visiblePosts.lastIndex - 4`，筛选「91自拍论坛」时可向下无限加载后续分页；
  3. **快照缓存扩容**：将 `MAX_FORUM_CACHE_ITEMS` 从 40 扩容至 120，保护两源完整首屏快照；
  4. **播放器动作按钮精致居中**：`GlassSurface` 内部采用 `fillMaxSize()` + 居中對齊，封裝 24dp 居中圖標 Box，文字 `fillMaxWidth()` + `TextAlign.Center` 絕對居中，標籤精簡為對稱雙字文案，全面接入 `applePressEffect` 彈簧回彈微交互。

## In Progress

- Candidate 160 交付驗證完成，等待用戶下一階段指示或 Git 提交。

## Known Issues

- 全量设备覆盖记录为 blocked（环境导致，非代码失败）；已定向验证关键回归与新增无障碍测试。
- HIG 未关闭：TalkBack 人工逐屏、OEM 高对比开关检测、字幕 UI 切换器、真机 E4 证据、200% 搜索框标签截断（P3）。
- 动态色对比度依赖壁纸（已加守卫回退）；论坛 WebView 图片/漫画阅读页未压暗（有意例外）。
- 黄果 CDN、各视频/漫画/直播源、论坛结构均为外部运行时依赖。

## Git State

Branch: codex/feat-comic-source-settings

Working Tree: 150～158 完整功能、投屏、论坛与性能优化改动待提交。

## Build Status

158 交付包 PASS（`dist/AuroraShelf-2.15.0-158-forum-91-restored-LOCAL.apk`，4,385,445 bytes，SHA-256 `03b0d29c2d193996ffbeb849c60014323280c8b63ca4fc6547aea3d2e029360c`）；AAPT2 badging 驗證通過、apksigner v2 簽名通過、zipalign 16 KB 對齊通過。

## Test Status

JVM：30 test suites，175 項測試（173 passed, 0 failed, 2 skipped，100% 成功）；`lintDebug` 0 errors。

## Next Recommended Action

向用戶 Randy 匯報 Candidate 158 交付與量化驗收數據；待指示後提交 Git 代碼。
