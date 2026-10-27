# Active

- 整理并提交本地工作区 150～169 版本改动，确认是否推送远程分支与发布 GitHub Releases。

## 169 已完成（2026-10-27）

- [x] XVideos 41 項官方分類與 41 個 kiosk 顯式映射；extractor 分類不足時不再靜默回退 `latest`。
- [x] HornyStudents 房間尾部斜線 slug、WebView pending/mismatch 寬限、真實 video readyState 檢查、iframe HTTPS 放行。
- [x] 直播快取按 `sourceId + category` 隔離並升級 version 2。
- [x] JVM 205 項（201 passed, 0 failed, 0 errors, 4 skipped）；Release versionCode 169；missavtest 全新安裝與 XVideos/HornyStudents 實機走查。

# Blocked

- TalkBack / Switch Control 逐屏人工走查（需人工）。
- OEM 自有高对比文字开关无法被 AOSP Settings 键检测（平台限制，已声明）。
- 字幕 UI 切换器（当前仅自动优选文本轨）。

# Planned

- 200% 字体下搜索框标签“搜…”截断（P3 打磨）。
- 论坛 WebView 图片 / 漫画阅读页深色压暗（当前有意例外）。
- E4 真机证据（高刷、长列表内存、真实无障碍设置）。

# Completed

- [x] 168 播放器詳情頁操作按鈕視覺 Bug 修復（`forceDark = true` 磨砂暗黑高對比）、8Muses/GEDE Comix/91動漫三大漫畫源深度接入、91動漫視頻源獨立接入（5 大分類映射與 HLS 硬解出畫）— 2026-10-27。
- [x] 167 XVideos 視頻源接入並設為全應用預設源（5 大分類擴展最新/熱門/自拍/亞洲/認證）、全網視頻源統一併發搜索與輪詢交織算法混排、HornyStudents 直播源深度接入（RSS 2.0 在線主播解析、四大性別分類過濾、雙直播源切換）— 2026-10-27。
- [x] 166 Beeg（beeg.com）官方視頻源深度接入、REST API 解析、多態分類導航、聯網搜索與 ExoPlayer 原生硬解播放 — 2026-10-27。
- [x] 165 華視影院（huavod.com）官方視頻源深度接入、五大分類多態映射、ArtPlayer/BunnyCDN 簽名解析、236 集自動識別與原生硬解播放 — 2026-10-27。

- [x] 加载优化（首屏快照、封面内存/磁盘 LRU、单航班、连接复用、播放解析缓存、首屏预热）— 2026-10-24。
- [x] HIG 第二轮：高对比三态 token、检测与动态色守卫；深色封面压暗；200% 字体降列与 hero 修复；15 处 heading；字幕轨优选；3 项无障碍设备断言 — 2026-10-24。
- [x] 回归修复：收藏库异步化回退、首页文案按并发改名恢复、无障碍测试去品牌耦合 — 2026-10-24。
- [x] JVM 125/0/1；定向设备测试 PASS；Release 137 构建/验签/安装 — 2026-10-24。
- [x] Netgrey 品牌 release 137 重建：英文名 + 矢量冷灰 N；v2 签名、16 KB zipalign — 2026-10-24。
- [x] 直播板块体验优化：注入 `isVisitorsAgreementAccepted=1` 及 `.btn-visitors-agreement-accept` 智能穿透年龄确认门禁；拦截 16+ 广告/追踪与重型非核心插件；探测循环重构为 35s 真实时间倒计时自适应轮询，秒级识别购票秀/私密秀/离线状态并提供语义状态层与系统浏览器外链兜底 — 2026-10-25。
- [x] 缺陷修复与体验优化：论坛刷新失败/翻页加载失败状态解耦（添加顶部错误横条与正确定向重试）、直播播放器选择器全流程统一（`LIVE_VIDEO_SELECTOR`）；编写 `ForumUiStateTest` 单元测试 — 2026-10-25。
- [x] 开屏动画 4K 60fps 竖屏 9:20 居中自适应裁剪：重构为 1080×2400 60fps H.264 High 5.1，更新 `IntroTiming`（5.7s）、`intro_film_start.jpg` 及 `intro_poster.jpg` — 2026-10-25。
- [x] 全局液态玻璃（Liquid Glass）视觉与结构统一：注入 `LocalBackdrop`；重构 `GlassSurface` 支持 HIG 磨砂透光材质与 1dp 微光边缘 — 2026-10-25。
- [x] 直播全屏方向自适应与交互升级：通过 `AuroraLiveBridge` 实时回传源站视频真实宽高，动态决策竖屏流使用 `SCREEN_ORIENTATION_SENSOR_PORTRAIT` 铺满手机屏幕，横屏流自适应横屏 — 2026-10-25。
- [x] 主页头部移除冗余视频源下拉框与吸顶分类栏 Netflix 胶囊化（Candidate 150/151/152）— 2026-10-25。
- [x] 153 直播间非全屏视口空间扩充、4 泳道防重叠错峰弹幕浮层与一键纯净影院观影模式 — 2026-10-26。
- [x] 154 应用启动图标全面重构（Netflix 3D 绯红丝带与影院微弧）— 2026-10-26。
- [x] 155 全板块顶栏统一对齐首页与搜索框文字垂直居中根治 — 2026-10-26。
- [x] 156 原生电视投屏能力：DLNA/UPnP 协议栈、本地流媒体代理与 Apple HIG 设备控制体系 — 2026-10-26。
- [x] 157 投屏协议栈深度修复与全项目性能优化：修复离线缓存视频投屏 404（接入 Media3 `SimpleCache` 分片流桥接与远端 URL 代理回退）、新增 HLS `.m3u8` 清单分片代理重写与 `%2Em3u8` MIME 识别、加固 `LocalMediaProxy` 会话 Token 与文件沙盒白名单、升级 `SsdpDiscovery` 并发 XML 解析与 `CastManager` `SupervisorJob`、修复投屏期间切回前台本地播放器误自动播放、支持投屏起播进度同步与切集联动、激活 `MainActivity` `warmUpViewModel` 开屏预热、优化 `AppPreferences.savedVideos()` 内存缓存与 Compose 列表 `remember` 记忆化、将 `intro_film.mp4` 从 2.9 MB 压缩至 741 KB，并完成面向 Antigravity 的打包交接文档 `docs/handoff-2026-10-26-antigravity.md` — 2026-10-26。
- [x] 158 91 自拍论坛域名轮换修复、门户页动态发现回退、板块分类胶囊直达与 Release 打包验收：定位硬编码域名 `t906.abc2507.cc` 失效（HTTP 404），更新最新基础域名为 `https://t924.sol140.com`，新增带 Cookie 门户页动态正则提取与候选域名自动回退机制；新增论坛顶部吸顶分类胶囊（`全部` | `草榴社区` | `91自拍论坛`）并为 91 帖子着色绯红标签；扩展 WebView 注入脚本适配 `t924` 域名夜间样式；通过 175 项 JVM 单测（100% 通过）与 Lint 检查；打包 `dist/AuroraShelf-2.15.0-158-forum-91-restored-LOCAL.apk` 并通过模拟器端到端验证 — 2026-10-26。
- [x] 159 全链路零等待预加载、12 小时磁盘快照容灾与 Apple HIG 流体物理微交互：视频板块新增 `prefetchLeadingStream` 提前 1.7s 后台解析 Top 1 视频直链写入 `playbackCache` 达成即点即播；论坛板块启动空闲期阶梯式静默预热；漫画板块新增 `CachedComic` 磁盘快照与 12 小时 TTL，`ComicViewModel` 采用乐观保底（Optimistic Stale UI）杜绝切页白屏，`ComicReader` 实现滑动窗口前瞻预加载后 3 帧高清单页；直播板块新增 `CachedLive` 在线主播快照与 `StripchatLiveRepository.prefetchDns()` 消除 TLS 握手延时；全局阶梯式轻量后台调度；封装 `AppleSpring` 物理弹簧 Token 与 `Modifier.applePressEffect` 按压微凹回弹微交互并全量应用；底栏 Tab 切换方向感知位移 + 0.96x 空间景深过渡；`PlayerScreen` 抽屉式自底向上滑入展开与滑出；通过 180 项 JVM 单测（100% 通过）与 `lintDebug`（0 errors）；修复 `LiveViewModel` 构造函数缺失导致的开屏闪退；构建 Release 候选包 `dist/AuroraShelf-2.15.0-159-apple-motion-instant-LOCAL.apk`（4,385,445 bytes，SHA-256 `95bb5840366c4b23247e62ef19e32d623605f8bc84170912c7da68b56041f75c`）— 2026-10-26。
- [x] 160 91 论坛 5 大核心板块并发聚合（帖量激增 1150%）、筛选触底分页修复与播放器动作按钮居中精致重构：91 论坛引入 `CORE_FIDS = listOf(4, 21, 33, 34, 36)` 涵盖原创自拍、我爱我妻等核心主力板块，单页抓取量由 16 帖提升至 84 帖，翻页加载 100 帖（累计 184+ 帖）；修正 `ForumScreen.kt` 触底分页监听为 `visiblePosts.lastIndex - 4`，根除单源筛选无法触底分页的缺陷；将 `MAX_FORUM_CACHE_ITEMS` 扩容至 120；重构 `PlayerScreen.kt` 动作按钮（收藏、缓存、投屏、选集）容器为 `fillMaxSize()` + 绝对居中 + 24dp 规范图标盒 + 对称双字标签，接入 `applePressEffect`；通过 180 项 JVM 单测与 `lintDebug` 0 errors；构建 Release 候选包 `dist/AuroraShelf-2.15.0-160-forum-posts-and-player-ui-LOCAL.apk`（4,385,445 bytes，SHA-256 `701c8dc43d4bf528f9b49e73f4c0461ca3330f24a72a9b7cfa34e20b4dac387b`）并完成模拟器实测 — 2026-10-26。
- [x] 161 仿 Bilibili 纵向视频自适应展开、黑边消除与 Apple HIG 流体联动动效：在 `NativeSitePlayer.kt` 增加 `calculateVideoAspectRatio` 提取真实宽高比并增加 `onVideoAspectRatioChange` 回调；在 `PlayerScreen.kt` 引入 `calculateAdaptivePlayerHeight` 算法，横屏维持 16:9 607px，竖屏自适应展开至 1440px / 60% 屏幕可用高度，视频显示面积暴增 +402%，黑边消除；`Spacer` 与播放器容器同频采用 Apple HIG 弹簧物理曲线（`spring(dampingRatio = 0.82f, stiffness = 300f)`）弹性平滑拉伸，下方内容顺畅向下压缩排版；通过 182 项 JVM 单测与 `lintDebug` 0 errors；构建 Release 正式包 `dist/AuroraShelf-2.15.0-161-vertical-video-adaptive-LOCAL.apk`（4,386,076 bytes，SHA-256 `06a57db7dec98bebebf8da9ba82e093cb8df36044109a569b2655c5193fd0658`）并完成模拟器端到端实测验证 — 2026-10-26。
- [x] 162 仿 Bilibili 纵向视频两阶段滚动收窄折叠机制（视口自 1440px 收折至 16:9 607px 吸顶释放 833px 内容空间、Apple HIG 弹簧物理吸附、浮点公差与超宽屏数学健壮性防御）：在 `PlayerScreen.kt` 引入 `NestedScrollConnection` 拦截列表滚动，上滑优先消费滑动量平滑收折播放器至 16:9 吸顶，下方内容紧贴上移，释放 833px 可视空间，推荐卡片一屏容纳 4+ 项；下拉到顶后手势驱动自然展开回大视口；轻扫基于 `spring(dampingRatio = 0.85f, stiffness = 320f)` 智能吸附；修复浮点精度公差导致的 `IllegalArgumentException`；通过 183 项 JVM 单测（100% 通过）与 `lintDebug` 0 errors；构建 Release 正式包 `dist/AuroraShelf-2.15.0-162-bilibili-scroll-collapse-LOCAL.apk`（4,385,445 bytes，SHA-256 `7f64bff358076221e806586ee4738072f3f943f2d1f87ee3461a76a3481a70cc`）并完成模拟器实测 — 2026-10-26。
- [x] 163 仿 Bilibili 视口底缘细线进度条、常驻微光分割线与 Apple HIG 流体微交互：在 `NativeSitePlayer.kt` 封装 `PlayerBottomProgressBar` 嵌合于播放器视口底缘，底层 100% 铺设 0.15f 微光分割线消除与下方列表割裂感，已播部分采用 Netgrey 经典绯红渐变 `AuroraCoral` 高刷（200ms）平滑推进，缓冲部分采用 0.32f 白轨；接入 `PlayerView.setControllerVisibilityListener` 监听控制器显隐，唤起时平滑收窄至 1.5dp，淡出时弹簧复原为 2.5dp 饱满进度条；封装 `calculateVideoProgressFraction` 与 `calculateVideoBufferedFraction`；通过 184 项 JVM 单测（100% 通过）与 `lintDebug` 0 errors；构建 Release 正式包 `dist/AuroraShelf-2.15.0-163-bottom-progress-divider-LOCAL.apk`（4,385,445 bytes，SHA-256 `dc9076c3e8d99585ed2b652b15061d59148f56b4c2961164dd6862535593b74d`）并完成模拟器实测 — 2026-10-26。
- [x] 164 全量预设视频源深度修复、43 分类 100% Playback 探针通过、线路属性感知分组、Apple HIG 状态胶囊与首页自愈回退：排查修复 85po（正則適配 `.html` 後綴）、JAV-NONI（域名輪換至 `javnoni.com`，防回溯棧溢出改寫，解包器線性化，外鏈白名單補齊 `luluvido.com`）、MissAV（失效 403 路由 `/ja/popular` 映射至有效發布路徑 `/ja/release`）、OHentai（卡片選擇器限定 `.videobrick` 過濾無效死鏈），全量 16 源 43 分類 100% 通過 Playback 探針；視頻源設置頁按 `isDirectDomestic` 劃分為「精選推薦（大陸直連穩定）」與「擴展視頻源（海外/國際線路）」，卡片標題右側標註綠色「直連」與天藍「海外」Apple HIG 微型膠囊；首頁加載失敗空狀態提供「一鍵切回默認直連視頻源」自愈按鈕；通過 185 項 JVM 單測（100% 通過）；構建 Release 正式包 `dist/AuroraShelf-2.15.0-164-video-sources-repaired-LOCAL.apk`（4,396,412 bytes，SHA-256 `7128fe104fa4cadf99e6640fcc34d079fde11c2a49c90159bbea665947b8881c`）並完成模擬器端到端實測 — 2026-10-27。
- [x] 165 華視影院（huavod.com）官方視頻源深度接入、五大分類多態映射、ArtPlayer/BunnyCDN 簽名解析、236 集自動識別、ExoPlayer 原生硬解播放與 Release 打包驗收：逆向分析華視影院客戶端（huavod_64.apk）與官方站點（huavod.com），註冊直連視頻源 `huavod` 並劃入「精選推薦（大陸直連）」；頂部分類動態映射至「電影 / 電視劇 / 綜藝 / 動漫 / 短劇」；列表解析器抽取 `.public-list-box` 卡片並智能剝離「封面圖」多餘後綴；聯網搜索對接 `/vodsearch/` 路由；在 `PlaybackResolver` 實現 `parseHuavodEpisodes`（精準提取 236+ 集劇集並按序編排）與 `resolveHuavod`（模擬官方 EcPlayer 協議鏈路：異步請求 `/Player/next` 獲取廣告令牌 `mt`，計時對沖後向 `/Player/resolveUrl` 換取帶 BunnyCDN HMAC 簽名的 `.m3u8` 直播/點播清單）；集成 `huavodTokenCache` 預加載機制，瀏覽時後台消化倒計時，點擊秒播；通過 185 項 JVM 單測（100% 通過）；構建 Release 正式包 `dist/AuroraShelf-2.15.0-165-huavod-source-LOCAL.apk`（4,401,829 bytes，SHA-256 `3ffd93f8da424a11154fd3e92facf101366da6a67b989864fd43969fc554b10f`）並完成模擬器端到端實測（136+ 影片渲染、236 集識別、ExoPlayer 播放 1h06m 喜劇之王單口季出畫）— 2026-10-27。
- [x] 166 Beeg（beeg.com）官方視頻源深度接入、REST API 解析、多態分類導航、聯網搜索與 ExoPlayer 原生硬解播放：逆向分析 Beeg SPA 與底層 REST API 服務（store.externulls.com），註冊海外視頻源 `beeg` 並劃入「擴展視頻源（海外/國際線路）」配 Apple HIG 天藍色微型膠囊；頂部分類動態映射至「精選 / 自拍 / 日本 / 熟女 / 亞洲」；列表解析器對接 `/tag/videos/{tag}` 路由並支持分頁加載；在 `AuroraViewModel.kt` 支持聯網搜索；在 `PlaybackResolver` 實現 `resolveBeeg` 對接 `/video/play_url/{fileId}` 由 ExoPlayer 原生硬解播放 M3U8 清單，首幀 1.04 秒出畫；通過 191 項 JVM 單測（100% 通過）；構建 Release 正式包 `dist/AuroraShelf-2.15.0-166-beeg-source-LOCAL.apk`（4,401,829 bytes，SHA-256 `c560c1f4191c6a5b6f0651e835f976d7a654dc188d10ec6cde5bc3994ae5f816`）並完成模擬器端到端實測 — 2026-10-27。
