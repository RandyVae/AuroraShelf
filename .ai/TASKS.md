# Active

- [x] 新增无广告极速长视频源 134X（`https://134x.com`）全端原生接入、Release 模拟器验证与 `v2.16.29 (Build 255)` 交付：完成 TanStack Start `_serverFn` Seroval 协议逆向与双端适配器（`X134Repository.kt` / `X134SourceService.swift`）、6 并发详情水合与哈希自动刷新、Seroval 字符串反转义修复、20 源目录与搜索对齐、`probe_all_sources.mjs` / `diagnose_sources.swift` 同步扩展、Android R8 APK 与 iPhone 18 Pro / iPad Pro 13-inch Release 实测及 OTA 原子发布 — 2026-11-14。

- [x] 全项目 Bug 审查、多端 UI 防错位与双端模拟器 Release 级实测交付（v2.16.28 / Build 254）：Android 修复 ANR、日间首屏与播放页状态栏高对比背板、平板响应式 Hero 高度、`SettingsSubpageScaffold` 固定设置子页返回栏（`AuroraFlowTest` 10/10）；iOS 消除 `HeroBanner`/`LiveScreen` 在 Duo 下的左右白边并上调操作栏底部间距；Web 游戏 `.age-badge` 移至左上角避让浮动胶囊；Android `lintDebug`（0 errors）、`testDebugUnitTest`（245 项 0 失败 7 跳过）、非在线 UI/无障碍设备测试（20/20）与 iOS XCTest（169 项 0 失败 6 跳过）全部通过，Release OTA（`Netgrey.apk` / `Netgrey.ipa`）同步发布至 `https://bananarey.us` — 2026-11-14。

- [x] 播放器组件与 UI 对标 Apple TV 全方位审查与双端重构（v2.16.27 / Build 253）：iOS 合流双播放器、激活 `.playback` 音频会话、打通 Up Next 断点续播与自动存盘、补齐 PiP / 字幕音轨菜单 / InSight 规格抽屉 / 键盘快捷键、消除双 HUD 重叠与拖拽跳针；Android 重构为 Compose 原生 Apple TV OSD 控制层（`AppleTvScrubberBar` 悬浮时间气泡、剩余时间切换、倍速、画幅 Fit/Zoom、三区双击与左右滑调光调音 HUD、控制栏同步渐隐零遮挡） — 2026-11-14。

- [x] 逐源分类统一与 iPad 横屏 YouTube 双栏布局：Android 完整 kiosk 分类适配、iOS 41 个 XVideos 分类与国际源原站分类收敛、iPad 横屏播放器/推荐双栏、Android 243 项 JVM 单测与 iOS build-for-testing 通过 — 2026-11-14；JAVSB 外部 TLS 阻塞、iOS Simulator runtime 缺失和既有 Android lint 错误已记录。

- [x] 商业化第一阶段账号系统：邮箱密码注册/登录/退出、Android/iOS 会话接入、免费权益状态预留、PocketBase 服务器部署与 `/auth-api` 代理、双端 v2.16.22 / Build 248 Release OTA 发布 — 2026-11-13。
- [ ] 配置 PocketBase 首个管理员账号与 SMTP，并完成真实邮箱验证/找回密码 E2E。

- [x] 确立双端持续交付与服务器零老版本磁盘防爆铁律：在全局准则（`~/.gemini/config/plugins/agent-memory/rules/AGENTS.md` 2.5 节）与项目 `AGENTS.md` 写入核心强制规范，明确无论是 iOS 还是 Android 修改后必须打包上传最新包；服务器严禁保留老版本，执行同名原子覆盖并即时清理临时碎片，保障磁盘空间健康 — 2026-11-06。

- [x] 官方下载站全面升级为 iOS + Android 双平台无线发布中心：重新编译并上架 Android 2.16.0 正式版 APK（168.45 MB），落地 Segmented Control 智能平台自适应切换、直链秒级下载与 Nginx 专属 MIME 支持 — 2026-11-06。

- [x] 确立 iPhone 与 iPad 双端同步修复全局铁律：在全局准则（`~/.gemini/config/plugins/agent-memory/rules/AGENTS.md` 2.4 节）与项目 `AGENTS.md` 写入核心强制规范，修复 iPhone 软件缺陷时必须同步排查并修复 iPad 对应实现，严禁割裂交付，确保双端同权同质 — 2026-11-06。

- [x] 建立 iOS 持续交付与 OTA 自动发布规范（核心流程）：后续只要完成新的功能开发、UI 打磨或缺陷修复，必须立即执行 `./scripts/publish_ota.sh Release`，自动使用苹果开发者账号（Team ID: `U4MXHLA3GF`）进行签名归档，并上传同步至日本服务器 `/var/www/netgrey/Netgrey.ipa`，确保用户通过 `https://bananarey.us:8443` 下载到的永远是最新编译版本 — 2026-11-06。

- [x] iOS 全面接入 12 大語種多語言國際化架構與即時無縫切換閉環：在 `AuroraShelf/Resources/` 落地 12 套 `.lproj/Localizable.strings`（繁體中文、簡體中文、英、日、韓、泰、德、法、西、馬來、印尼、越），全面覆蓋底欄 Tab、主頁、漫畫、直播、論壇、播放器、片單、搜尋、資料庫及設定；封裝 `@unchecked Sendable` + `NSLock` 的 `LocalizationManager`，解決 Swift 6 併發隔離與 `String.localized` / `L10n` 調用問題；在 `PersonalScreen` 新增「語言」行與 `LanguagePickerSheet`；新增 `LocalizationManagerTests` 5 項單測，全量 106 項單元測試 100% 全部通過（0 failures, 5 skipped，耗時 4.999 秒）；iPhone 17 Pro 模擬器（PID 80501）實機驗收截屏存盤（`captures/screen_ios_tab_library_multilingual.png` 與 `captures/screen_ios_tab_home_multilingual.png`），`git diff --check` 0 警告 — 2026-11-06。

- [x] iOS 2.16.0 缺陷審計（三線並行只讀 + 主審實測）：合計 76 項技術缺陷（P0×1、P1×15、P2×43、P3×17）＋1 項流程風險。P0＝當前工作樹不可建置（`LocalizationManager` 以 `@MainActor` 單例被 `String.localized`／`L10n` 等 nonisolated 宣告同步呼叫，6 個 Swift 6 併發錯誤；15:41 與 15:47 兩次 `xcodebuild` 均 exit 65）。主審另實測：15:36 指紋下 101 項測試 96 passed／5 skipped／0 failed、Release 可建置（48 條 app-target 警告）；模擬器啟動出畫正常但運行時檢出 2 個無效 SF Symbol；全部 9 個測試檔與 29 個源碼檔未入 Git；`SourceSecrets.plist` 隨 bundle 分發；`dist/` IPA 為 build 173 且不含憑據與 12 套在地化資源，其 SHA-256 與 `PROJECT_STATE.md` 記載不符。報告：[`docs/ios/audit-2026-11-06/AUDIT-REPORT.md`](../docs/ios/audit-2026-11-06/AUDIT-REPORT.md)，三線片段同目錄。未修改任何源碼 — 2026-11-06。

- [x] iOS 全項目 12 項代碼缺陷與隱患地毯式排查與全量修復閉環：排查修復 4 項高危（`NativePlayerView` 跨線程競爭/退出刷盤、`PlaybackProgressStore` 5s防抖+10s節流、`VideoRepository` 升級 `@MainActor` 嚴格隔離）、5 項中危（Comic/Forum/XVideos/Live 4 源硬編碼截斷清零、`MainContainerView` 輪詢 Task 生命週期管控、`SearchSheetView` 單例語義）、3 項低危（`CoverImageLoader` 與 `ComicPageImageLoader` 升級 actor 任務去重防擊穿、`ForumScreen` 複合唯一鍵 `fid:id`、正則表達式預編譯優化）；全量 96 項單元測試 100% 全部通過（0 failures, 5 skipped），iPhone 17 Pro 模擬器部署運行截屏存盤（`captures/screen_ios_tab_home_after_bugfix.png`），`git diff --check` 0 警告 — 2026-11-06。

- [x] 全面打通漫畫板塊全源動態分頁與無限滾動加載：在 `ComicRepository.swift` 聲明 `currentPage: Int = 1`、`canLoadMore: Bool = true`、`isLoadingNextPage: Bool = false` 及 `loadNextPage() async` 異步追加與 `Set(comics.map(\.id))` 去重；打通包子漫畫（`amp_comic_list?page=\(p)&limit=36`）、拷貝漫畫（`offset=\((p-1)*24)`）、Komiic（GraphQL offset: `(p-1)*24`）、禁漫天堂（`filter?page=\(p)`）、嗶咔漫畫（`comics?page=\(p)`）、91動漫漫畫及 HTML 漫畫源全部分頁路由；在 `ComicsScreen.swift` 網格末端掛載 `onAppear` 觸底偵測與 Apple HIG 原生 `ProgressView("正在加載更多漫畫…")` / `Text("已加載全部內容")`；新增 2 項單元測試，全量 83 項測試 100% 通過（0 failures），iPhone 17 Pro 模擬器實機截屏存盤驗證（`captures/screen_ios_tab_comics_pagination_verified.png`），`git diff --check` 0 警告 — 2026-11-06。

- [x] 徹底根除全視頻源固定數量截斷缺陷、全量貫通觸底動態分頁加載與實機驗證：移除 `parseHuavodHtml`、`parseHuangguoHtml`、`parse91PornHtml`、`parse91DongmanHtml` 硬編碼 `count >= 20/24` 截斷；華視影院升級官方全量分頁路由 `/vodshow/\(typeId)/page/\(p).html`（每頁 76 卡片）；91 視頻透傳 `&page=\(p)`、Beeg 透傳 `offset=\((p-1)*20)`、HTML 適配器動態頁碼透傳；在 `VideoRepository.swift` 實現 `currentPage`、`canLoadMore`、`isLoadingNextPage` 與 `loadNextPage()` 去重追加；`HomeScreen.swift` 網格末端掛載 `onAppear` 觸底與 Apple HIG 原生 `ProgressView` / `Text("已加載全部內容")`；新增 3 項單測，`UniversalVideoEngineTests` 全量 39 項單測 100% 全部通過；iPhone 17 Pro 模擬器安裝實機截屏驗證通過，`git diff --check` 0 警告 — 2026-11-06。

- [x] 承接 Codex 遺留工作全量閉環、嗶咔協議邏輯漏洞修復、論壇實測容差加固與在線源實測：修復 `ComicProtocolSecrets.swift` 判定字面量空值 bug（`!value($0).isEmpty`）；`ComicsScreen.swift` 增加未配置協議時原生 `ContentUnavailableView` 空狀態指引；加固 `ForumRepositoryTests.swift` 短文字圖片貼斷言；在線實測驗證 5 核心視頻源（kissjav, eporner, huavod, 91dongman, beeg）、2 漫畫源（JM, 包子）、2 直播源（Chaturbate, Stripchat）與 6 論壇版塊雙頁；新增 1 項單測，全量 87 項單測 100% 全部通過（0 failures, 4 skipped），iPhone 17 Pro 模擬器截屏存盤 — 2026-11-06。

- [x] iOS 历史遗留三大源分页缺陷与全视频源第二页路由加固攻坚：根除 JAVSB 第 2 页为空问题（重构 MacCMS 路径分页与分类映射）；实测根治 91 动漫漫画分页 404 缺陷（`/manga/\(id)/\(page)/`）；打通包子漫画搜索 HTML 解析与 `ComicRepository.searchComics` 全源搜索分发；地毯式重构全视频源 Path/Query 分页路由与中文字幕匹配；新增 4 项单元测试，全量 85 项测试 100% 全部通过（0 failures），模拟器实机截图存盘验证 — 2026-11-06。

- [x] iOS 全项目地毯式缺陷排查与清零：清除 `ComicRepository.swift` 全部 14 处 `URL(string: ...)!` 强拆包隐患，长列表阅读器加入 80MB 限制、真实 Cost 上报、`autoreleasepool` 防爆内存与切章即时清空；论坛原帖扩展多协议泛域名并升级为应用内 `SourceWebsiteSheet`（`SFSafariViewController`）浏览；个人中心接入动态直播源与切换弹窗，片单增加 `dynamicTypeSize.isAccessibilitySize` 宽屏自适应；全量 81 项测试 100% 通过，iPhone 17 Pro 模拟器全 Tab 截屏验证闭环 — 2026-11-06。

- [x] 恢复 iOS 品牌为 Netgrey：移除不匹配的 `sparkles.tv.fill` 顶栏图标，将标题改用系统导航栏，更新显示名称并完成模拟器截图复核 — 2026-11-05。

- [x] 攻克 iOS 禁漫天堂（JM Comic）源全链路：解决参数探测盲区、修复 `id`/`sort` 数字类型解包吞数据严重缺陷、补齐 Referer，实测通过在线列表（80部）、封面、章节（6话）、正片（136页）及动态反混淆分段计算；79/79 单元测试全部通过 — 2026-11-05。

- [x] 攻克 iOS 19 视频源中 7 个失败源协议头、反爬策略与流解析链路（Pornhub/OHentai 封面解码、91视频 strencode2/CDN77 解密与死链绕过、EPORNER 动态重定向流、JAVSB XOR 签名换 m3u8、MissAV 镜像与 packed eval 解包、JAV-NONI 嵌入式流与 UA/Referer 校验）；76/76 单元测试全部通过 — 2026-11-05。

- [x] 攻克 iOS 包子漫画（Baozi）章节页图 404 与 CDN 阻断：锁定 `div#chapter-items` 正序完整目录避开 VIP 占位章节、URLComponents 完整保留 Query 与 Path 切换至 `cn.twbzmg.com`、支持 AMP 图床标签与 JSON 提取、`ComicPageImageLoader` 建立 `static-tw.baozimh.com` CDN 容灾自动回退，实测在线 1.48s 获取 36 本漫画、首部《剑尊归来》、第 1 话、50 页正片图片，第一页 690x690 UIImage 秒级解码成功 — 2026-11-05。

- [x] 直播弹幕、评论与年龄门禁端到端打通与横屏沉浸体验：在 `LiveChatSheet` 与 `StripchatLiveWebView` 注入条款 Cookie 与脚本穿透年龄门禁，轮询间隔调优至 3 秒；`LivePlayerView` 完整接入 90° 横屏沉浸旋转（`isLandscape`）与画幅切换；为漫画、直播卡片及播放器控件全量补齐语义化 VoiceOver 标签与提示；全量 81 项单元测试 100% 全部通过 — 2026-11-05。

- [ ] iOS 哔咔协议凭据接入、VoiceOver/多设备真机矩阵长效走查。Android 暂不处理。

- 整理并提交本地工作区 150～169 版本改动，确认是否推送远程分支与发布 GitHub Releases。

## 169 已完成（2026-10-27）

- [x] XVideos 41 項官方分類與 41 個 kiosk 顯式映射；extractor 分類不足時不再靜默回退 `latest`。
- [x] HornyStudents 房間尾部斜線 slug、WebView pending/mismatch 寬限、真實 video readyState 檢查、iframe HTTPS 放行。
- [x] 直播快取按 `sourceId + category` 隔離並升級 version 2。
- [x] JVM 205 項（201 passed, 0 failed, 0 errors, 4 skipped）；Release versionCode 169；missavtest 全新安裝與 XVideos/HornyStudents 實機走查。

# Blocked

- iOS：VoiceOver/Switch Control 逐屏人工走查；iPad、横屏、分屏适配验证；带字幕/多音轨播放器样片验证；真机证据。
- TalkBack / Switch Control 逐屏人工走查（需人工）。
- OEM 自有高对比文字开关无法被 AOSP Settings 键检测（平台限制，已声明）。

# Planned

- 200% 字体下搜索框标签“搜…”截断（P3 打磨）。
- 论坛 WebView 图片 / 漫画阅读页深色压暗（当前有意例外）。
- E4 真机证据（高刷、长列表内存、真实无障碍设置）。

# Completed

- [x] iOS 设计审计修复（Build 174 工作树）：真实内容/状态、持久化片单、列表错误恢复与请求竞态保护、论坛 push、对比度/Reduce Motion/动态字级、播放器 VoiceOver/字幕音轨、包子漫画章节详情链接；XcodeGen + 模拟器测试 9/9，首页/漫画最大辅助字号抽查 — 2026-11-04。未提交；VoiceOver、iPad/方向、真实字幕音轨及真机仍待验证。
- [x] iOS 封面加载稳健性与直播评论原生面板：来源相对 URL、图床重写、Referer、稳定缓存键、漫画页图解密尝试/重试、聊天室 DOM 轮询与原生状态视图 — 2026-11-05。Build PASS；封面 URL 单测 2/2；全量测试 22 passed、1 cancelled（runner 卡在 worker materialization）；聊天室及逐源在线加载仍待 E2E。
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
# 已完成（2026-11-05）

- [x] 修复华视影院播放解析：绕过失效 `channel_key`，使用实时 `mt` 令牌换取签名代理 M3U8，并验证清单/分片可达。
- [x] 修复视频卡片交互：封面整块点击播放，标题保留播放入口，叠加评分不拦截点击。
- [x] 增加华视响应形状回归测试；iOS 测试 51 项通过。
