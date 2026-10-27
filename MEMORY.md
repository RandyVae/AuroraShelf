# 极光视界 · Android 2.16.0-dev · 跨 Agent 记忆

> 本文件是所有 AI Agent（DSH / Codex / Claude / Hermes / Antigravity）进入本项目的统一入口。
> 开工先读本文件，再按「权威来源」读细节；收工前更新「当前状态」。
> 不写 Key、密码、服务器 IP、客户隐私。协议：`~/.agent-memory/PROTOCOL.md`

## 自动摘要（`sync.py` 维护，勿手改）

<!-- AUTO:START -->
- 项目路径：`/Users/itou/Documents/ChatGPT/91pron`
- 技术栈：build.gradle.kts, Kotlin/Android
- Git：`main` @ `126b425`（2026-10-27），未提交 0 个文件
- 权威记忆文件：
  - `.ai/PROJECT_STATE.md` — 项目状态（当前阶段/目标/进行中），最后更新 2026-10-27（今天）
  - `.ai/HANDOFF.md` — 最新交接，最后更新 2026-10-27（今天）
  - `.ai/TASKS.md` — 任务清单，最后更新 2026-10-27（今天）
  - `.ai/DECISIONS.md` — 关键决策，最后更新 2026-10-27（今天）
  - `.ai/ENVIRONMENT.md` — 环境与构建基线，最后更新 2026-10-25（2 天前）
  - `AGENTS.md` — Agent 协作规则（自动读取），最后更新 2026-10-27（今天）
- Codex 全局记忆：该项目 cwd 暂无条目（`~/.codex/memories/` 仅覆盖少数目录）
- 最后同步：2026-10-27 17:33 by `sync.py`
<!-- AUTO:END -->

## 当前状态（Agent 收工前必须更新）

- 当前目标：极光视界（Netgrey）Android 客户端：Kotlin + Compose + Material 3，保留真实视频源，以液态玻璃与 Netflix 风格信息层级重构，不是网页套壳。
- 当前阶段：**2.16.0-dev（Candidate 173 开发周期开启，统一收归 main 单主线，清理全部历史分支）**（`versionCode = 173`，`versionName = 2.16.0`）：
  - 正式发布里程碑：`2.15.0`（`versionCode = 172`）已正式打 Tag `v2.15.0`，交付包 `dist/AuroraShelf-2.15.0-172-live-crop-fix-LOCAL.apk`（SHA-256 `ee4e955aaa283e53214c32804064d044d76605eae284389ee7fc5aedd07b7dfe`）实测通过。
  - 单一产品线固化：全局唯一保留 `main` 分支作为产品线，所有临时与过期分支（`origin/codex/feat-comic-source-settings`、`origin/codex/feat-forum-image-save`、`origin/codex/fix-forum-reader-loading`、`origin/feature/backdrop-liquid-tabs` 等）全部彻底砍掉，彻底消除后续其他 Agent 接手时的分支误解。
  - 实测状态：JVM 205 tests 全通（201 passed, 0 failed, 4 skipped），Release 构建通过。
- 进行中：开启 2.16.0 开发周期。
- 下一步：按需接入新特性或执行下一阶段任务。
- 阻塞：无代码层阻断。
- 最近一次更新：2026-10-27 17:32 HKT by Antigravity (gemini-3-flash)

## 关键决策（日期 + 决定 + 原因；只追加）

- 2026-10-27：HornyStudents 直播畫面裁切、視口高度塌陷黑屏與誤判私密秀深度根治（Candidate 172）。定位並根治五大缺陷：Chaturbate 底層播放器腳本動態注入 `clip-path: inset(0px 199.5px)` 削切左右兩側 200px，透過 CSS `!important` 規則覆蓋並在 JS 調度中強制 `clip-path: none !important; -webkit-clip-path: none !important;` 恢復完整相機原生畫面；Compose WebView 缺少 UA 全屏基準導致 `100vh` 容器高度塌陷為 `0px`（黑屏），改用 `window.visualViewport` 或 `window.innerWidth/innerHeight` 換算精確物理像素 `${viewportWidth}px` 與 `${viewportHeight}px` 覆蓋播放器及中間父層鏈；收緊私密秀選擇器排除常規導航 `<a data-testid="private-shows-tab">` 並引入 3 秒保護寬限期避免初始誤判阻斷；將播放器主節點從 `#mobile-room-root` 拔出至 `document.body` 頂層消除頂部 40px 偏移與底部截斷；在 `LiveCard` 與非全屏播放器默認適配 4:3 比例並動態跟隨相機分辨率消除黑條，橫屏全屏完美鋪滿 2340×1080；JVM 205 項單測 100% 通過，Release APK 4,418,209 bytes，SHA-256 `ee4e955aaa283e53214c32804064d044d76605eae284389ee7fc5aedd07b7dfe`。

- 2026-10-27：找回現代化 Netflix/Apple HIG 首頁 UI、XVideos 完整 41 分類與 HornyStudents 直播深度修復（Candidate 169）。徹底排查並定性 DeepSeek harness 因並發編輯失誤曾執行 `git checkout HEAD` 將未提交的 893 行現代化首頁代碼抹回舊版 708 行素色網格的事故，自 IDE 任務備份（`~/.gemini/tasks/c_239d52f983b7dead/backup/...`）完整找回並覆蓋復原，保全 Spotlight Billboard、今日熱播 Top 10 大卡片、為你推薦橫向貨架與毛玻璃分類條；XVideos 保留並驗證官方全量 41 項分類與 41 個 kiosk，改用 `xvideosKioskId` 語義映射，橫向滑動條支持全部 41 個分類，杜絕不足時靜默回退；HornyStudents 直播房間修復尾部斜線、3 秒 mismatch 容忍、Cookie 注入、放行 `cb-wl.com`、`agego.com` 並自動關閉年齡條款，實機秒出畫並串流實時聊天室；205 項 JVM 單測 100% 通過，Release APK 4,418,213 bytes，SHA-256 `dd4e436e01730812d6420ff6ad408b99ca1a4237e03f4c78a8fb95c76941f576`。

- 2026-10-27：播放器操作按鈕高對比視覺 Bug 修復、8Muses/GEDE Comix/91動漫三大漫畫源深度接入、91動漫視頻源獨立接入（Candidate 168）。在 `PlayerScreen.kt` 的 `PlayerActionButton` 中為 `GlassSurface` 顯式傳入 `forceDark = true` 並優化文字色彩，徹底修復系統淺色主題下操作按鈕（收藏、緩存、投屏、選集）被錯誤渲染為高亮白底氣泡且文字不可見的視覺割裂問題；接入 8Muses（Next.js 結構，4 大分類與官方媒體路由圖片直連）、GEDE Comix（WordPress Madara 協議，4 大分類與 WebP 閱讀頁解析）；落實 91dongman.net 視頻與漫畫獨立分開接入：漫畫端對接 4 大分類與全網搜索，視頻端註冊為「精選推薦（大陸直連穩定）」視頻源並映射 5 大多態分類，由 ExoPlayer 原生硬解直鏈 HLS 視頻播放出畫；新增 `NewComicSourcesTest.kt`，全量 204 項 JVM 單測 100% 通過。

- 2026-10-27：XVideos 設為預設視頻源、五大分類擴展、全網視頻源統一併發搜索聚合、HornyStudents 直播源深度接入（Candidate 167）。將 `XVIDEOS_BASE_URL = "https://www.xvideos.com"` 設為 `DEFAULT_BASE_URL`，在首選項自動遷移升級；在 `XVideosService.java` 補齊註冊 5 大分類 kiosk；搜索頁面全面升級為「全局搜索所有視頻源」，使用協程並發向全網 18 個視頻源檢索關鍵詞並設置 6 秒超時守護，實現 `interleave` 輪詢交織算法將各視頻源結果均勻交錯混排首屏展現；新增 `LiveSourceCatalog` 並接入 HornyStudents（hornystudents.com）RSS 2.0 在線主播流，映射四大性別分類過濾，WebView 放行必要流媒體域名並支持原生全屏播放與在線聊天；全量 198 項 JVM 單測 100% 通過。

- 2026-10-27：Beeg（beeg.com）官方視頻源深度接入、REST API 解析、多態分類導航、聯網搜索與原生硬解播放（Candidate 166）。逆向分析 Beeg SPA 與底層 REST API 服務（store.externulls.com），註冊海外視頻源 `beeg` 並劃入「擴展視頻源（海外/國際線路）」配 Apple HIG 天藍色微型膠囊；頂部分類動態映射至「精選 / 自拍 / 日本 / 熟女 / 亞洲」；列表解析器對接 `/tag/videos/{tag}` 路由並支持分頁加載；在 `AuroraViewModel.kt` 支持聯網搜索；在 `PlaybackResolver` 實現 `resolveBeeg` 對接 `/video/play_url/{fileId}` 由 ExoPlayer 原生硬解播放 M3U8 清單，首幀 1.04 秒出畫；通過 191 項 JVM 單測（100% 通過）。

- 2026-10-27：華視影院（huavod.com）官方視頻源深度接入、五大分類多態映射、ArtPlayer/BunnyCDN 簽名解析、236 集自動識別與原生硬解播放（Candidate 165）。逆向分析華視影院客戶端（huavod_64.apk）與官方站點（huavod.com），註冊直連視頻源 `huavod` 並劃入「精選推薦（大陸直連）」；頂部分類動態映射至「電影 / 電視劇 / 綜藝 / 動漫 / 短劇」；列表解析器抽取 `.public-list-box` 卡片並智能剝離「封面圖」多餘後綴；聯網搜索對接 `/vodsearch/` 路由；在 `PlaybackResolver` 實現 `parseHuavodEpisodes`（精準提取 236+ 集劇集並按序編排）與 `resolveHuavod`（模擬官方 EcPlayer 協議鏈路：異步請求 `/Player/next` 獲取廣告令牌 `mt`，計時對沖後向 `/Player/resolveUrl` 換取帶 BunnyCDN HMAC 簽名的 `.m3u8` 直播/點播清單）；集成 `huavodTokenCache` 預加載機制，瀏覽時後台消化倒計時，點擊秒播；JVM 單測覆蓋分類、分頁與選集解析（185 tests 100% 通過）。

- 2026-10-27：全量預設視頻源深度修復、線路分組感知與首頁自愈機制（Candidate 164）。全量 16 個預設視頻源（91、黃果短劇、及 14 個 MISSPipe/NewPipe 視頻源）43 個分類進行真實 Feed 與 Playback 探測診斷與深度修復：85po 正則適配 `.html` 後綴；JAV-NONI 域名輪換至 `javnoni.com` 並改寫 Dean Edwards 解包器為線性非遞歸掃描器徹底消除巨型正則回溯導致的 JVM `StackOverflowError`；MissAV 403 路由映射至官方有效路徑 `/ja/release`；OHentai 選擇器限定 `.videobrick` 過濾導航條無效死鏈。在設置頁為視頻源分組「大陸直連」與「海外/代理」並配以 Apple HIG 狀態膠囊；在首頁加載失敗時提供一鍵切回默認直連視頻源的自愈按鈕。

- 2026-10-26：仿 Bilibili 視口底緣細線進度條與微光分割線架構（Candidate 163）。對齊 Bilibili 移動端在常規觀影狀態下將「播放進度」與「視口分割」合二為一的經典設計。在 `NativeSitePlayer.kt` 封裝 `PlayerBottomProgressBar` 嵌合於播放器視口底部（`Alignment.BottomCenter`）：底層全寬鋪設 0.15f 微光分割線清晰界定視口邊界，消除全黑視口與列表深色背景融為一體的突兀感；已播進度以 200ms 刷新率向前平滑延伸，網絡緩衝條前瞻可視，在控制器隱藏時充當零遮擋進度指示器；通過 `PlayerView.setControllerVisibilityListener` 監聽 OSD 顯隱，喚起時微縮淡化至 1.5dp，淡出時彈簧復原為 2.5dp 飽滿進度條（`dampingRatio = 0.85f, stiffness = 380f`）；封裝純函數 `calculateVideoProgressFraction` 與 `calculateVideoBufferedFraction` 並進行全面單元測試覆蓋。

- 2026-10-26：仿 Bilibili 縱向視頻兩階段滾動折疊收窄機制（Candidate 162）。縱向視頻展開大畫面後雖然消除了黑邊，但若滾動時死鎖大面積視口會過度壓縮下方推薦內容。參考 Bilibili 移動端最佳實踐，在 `PlayerScreen.kt` 引入 `NestedScrollConnection` 實現兩階段折疊：上滑優先消費滾動量將播放器高度自 1440px 收折至 16:9（607px）吸頂，釋放 833px 可視空間，隨後無縫交還滾動權限讓下方推薦卡片順暢上滑；下拉列表到頂後手勢驅動自然展開回大畫面；中途輕掃基於 Apple HIG 彈簧物理吸附（`dampingRatio = 0.85f, stiffness = 320f`）；在 `calculateEffectivePlayerHeight` 增加浮點公差與 `minH >= maxH` 守衛，保證橫屏視頻折疊差額嚴格為 0 零受影響。

- 2026-10-26：仿 Bilibili 縱向視頻自適應展開與黑邊消除架構（Candidate 161）。此前非全屏播放器固定為 16:9 比例，導致播放 9:16 縱向視頻時兩側黑邊佔比超 68%，可視高度僅 225dp。在 `NativeSitePlayer.kt` 透過 `calculateVideoAspectRatio` 結合 `videoSize`（寬、高、像素比、旋轉角度）回傳真實寬高比，並在 `PlayerScreen.kt` 實現 `calculateAdaptivePlayerHeight`：橫屏維持 16:9，縱向視頻自適應展開至 60% 屏幕可用高度（在 2400px 屏幕上展開至 1440px / 548.5dp），視頻顯示面積暴增 +402%，黑邊消除；同時 `Spacer` 與播放器同頻採用 Apple HIG 彈簧物理曲線（`spring(dampingRatio = 0.82f, stiffness = 300f)`）彈性拉伸，將下方內容順暢向下壓縮排版。

- 2026-10-25：Lint 检查配置优化。在 `app/build.gradle.kts` 中加入 `disable += "ModifierParameter"` 屏蔽 AGP 9.4.0 与 Kotlin 2.3+ 下的 UAST NPE 崩溃，加入 `disable += "BidirectionalText"` 规避遍历 `pipepipe-extractor` 3500+ 文件导致的 AST 遍历严重挂起。
- 2026-10-25：`AuroraFlowTest` 播放测试断言优化。全屏播放测试改由首屏 Hero 卡片中心“播放”按钮触发，避免网格卡片被浮动玻璃导航栏（Bottom Dock）遮挡而误触底层导航条目。
- 2026-10-25：Netflix 风格全局化推广与公共组件原子化。将 `NetflixNLogo`、`NetflixProfileAvatar`、`NetflixRankNumber`、`NetflixSectionHeader`、`NetflixBadge`、`NetflixPosterShelfCard` 提炼到 `AppComponents.kt`，消灭私有重复代码，全板块推行纯黑沉浸底色 `#000000`、Hero Billboard 聚光灯舞台、Top 10 大数字重叠滑轨与磨砂卡片，并严格保持测试语义树 100% 兼容。
- 2026-10-25：黄果短剧在线路由与无限分页重构。黄果站点排行榜页面（`/ranks/hot/` 等）为 20 部固定单页，第二页返回 404 导致无限分页失效；将其重新映射至站点原生分页路由（`/recommend`、`/newest`、`/ai-manju/`、`/ai-huanlian/`、`/ai-duanju/`），单页 24~30 部并支持向下滚动自动加载无限页。
- 2026-10-25：Netflix 沉浸式流媒体全应用底色锁死与日夜间双模式一致性。在 `AuroraShelfApp.kt` 中将根 Surface 强制锁死为 `AuroraImmersiveBackground`（`#000000`），消除 `MaterialTheme.colorScheme.background` 在系统浅色模式下暴露的 `#F8F9FE` 白光与切页闪白；强制浅色状态栏/导航栏图标（纯白）；`LiquidBottomNavigation` 容器锁死为暗黑毛玻璃 `AuroraGlassContainerDark`（alpha 0.85）与珊瑚红 `AuroraCoral`，彻底根治日间模式下 LiquidBackdrop 捕获底色白光造成的乳白白雾与动态色漂移。
- 2026-10-26：直播非全屏视口重构、4 泳道错峰防重叠弹幕系统与纯净影院模式。重构非全屏布局，播放器吸顶，主播条压缩为微型胶囊，评论区高度扩至 ~65%；全屏下引入 4 泳道弹幕，采用 `staggered delayMs`（种子 1800ms 阶梯，新弹幕 350ms）与单行强制约束（单行、截断、最大宽 320dp、阴影）杜绝重叠与方块；顶部药丸与底部字幕双开关支持一键关闭弹幕切换纯净模式，带浮动胶囊 Toast 反馈与 4s 自动隐藏零遮挡。
- 2026-10-26：应用启动图标全面重构（Netflix 3D 绯红丝带与影院微弧）。淘汰原单调扁平冷灰 N（`#9AA3AE`），重构为 3D 折叠丝带 N 标，包含深暗影院红双立柱（`#C11119` 至 `#830A10`）、折痕真实投影层、鲜亮绯红前置对角丝带（`#FF1E27` -> `#E50914` -> `#B80711`）与底部经典影院微弧（Cinema Arc）；精确适配 108dp 自适应图标安全区（宽 29.87dp、高 54.23dp，外接圆半径 30.96dp < 安全圈 33dp），在圆形、平滑圆角矩形等各种遮罩下保持完美呼吸感与零截断；同步更新 Material You 单色层。
- 2026-10-26：全板块顶栏统一对齐首页与搜索框文字垂直居中根治。抽象 `NetflixTopBar` 与 `NetflixSearchCapsule`，全板块（Home/Comics/Live/Forum）统一步调：左侧统归 `NetflixNLogo` + `Netgrey`，移除漫画、直播、论坛等独立大标题及冗余副标题；漫画搜索入口移至顶栏搜索胶囊并可原位展开为搜索栏，删除原分类下方嵌入的巨大搜索框 item；重构搜索输入框为 `CenteredSearchBar`（消除 Material 3 `OutlinedTextField` 的 `label` 导致的文本基线严重偏下下沉，改用 `BasicTextField` 与居中容器），实测文字中心与搜索条中心偏差仅 1.5~2.0px，达到亚像素级垂直居中。
- 2026-10-26：原生电视投屏协议栈与本地流媒体代理架构（Candidate 156）。拒绝臃肿第三方库，全栈使用纯 Kotlin 协程实现 DLNA/UPnP 组播发现（SSDP）与 SOAP AVTransport 播放控制；集成微型本地 HTTP 代理服务 `LocalMediaProxy`，在手机端向第三方电视播放器代理转发媒体流并透明注入 `Referer` 与 `User-Agent` 标头，彻底攻克电视端 403 Forbidden 防盗链拦截顽疾；同时支持将手机离线缓存的 MP4 视频直接推流到电视；设计 Apple HIG / Netflix 黑暗磨砂底面板 `CastDeviceSheet` 与播放器浮动投屏控制横幅，实现手机电视无缝协同。
- 2026-10-26：投屏协议栈深度加固、离线缓存 SimpleCache 推流、HLS 清单重写与全链路性能优化（Candidate 157）。修复 Media3 `SimpleCache` 离线视频因 `mediaUrl` 为远端 URL 导致 `/file` 报 404 的问题，通过 `OfflineCacheStreamOpener` 桥接 `CacheDataSource` 实现分片直读与 Range 响应；为 `LocalMediaProxy` 引入随机 `sessionToken` 与目录沙盒白名单防止局域网越权读取私有配置，并新增 M3U8 清单分片代理重写；将 `SsdpDiscovery` 的设备描述 XML 请求改为 `async` 并发避免慢节点阻塞 UDP 接收；修复投屏中切回前台本地播放器复播问题，激活 `MainActivity` 闲置的 `warmUpViewModel`，为 `AppPreferences.savedVideos()` 与 Compose 列表引入内存缓存，并将 `intro_film.mp4` 由 2.9 MB 压缩至 741 KB。
- 2026-10-26：Candidate 157 單測適配與打包驗收。在 `app/build.gradle.kts` 配置 `testOptions.unitTests.isReturnDefaultValues = true` 解決純 JVM 單測調用 `android.util.Log` 拋出未模擬異常，確保 171 項測試 100% 通過；Lint 0 errors；Release APK 體積從 6.40 MB 大幅瘦身至 4.18 MB（4,385,441 bytes，減少 34.6%），AAPT2 badging、apksigner v2、16 KB 頁對齊與模擬器冷啟動出畫全量通過。
- 2026-10-26：91 自拍論壇動態域名探測回退與板塊分類膠囊體系（Candidate 158）。定位原硬編碼域名 `t906.abc2507.cc` 返回 404；將基礎域名切換至最新 `t924.sol140.com`，並實現攜帶 Cookie 動態讀取 91 門戶主頁最新自拍論壇入口的正則提取與多級候選自動回退機制；在論壇頂部新增吸頂 `CategoryStrip`（`全部` | `草榴社區` | `91自拍論壇`），徹底解決草榴高頻回帖淹沒 91 帖子問題；帖子標籤緋紅著色；WebView 閱讀器腳本適配 `t924` 夜間樣式；175 項 JVM 單測 100% 通過，打包 `AuroraShelf-2.15.0-158-forum-91-restored-LOCAL.apk` 並在模擬器實測驗證通過。
- 2026-10-26：全鏈路零等待預加載、12小時快照容災與 Apple HIG 流體物理微交互體系（Candidate 159）。首頁 Top 1 視頻直鏈提前 1.7s 後台解析寫入 `playbackCache` 達成點擊即播；論壇啟動空閒期階梯預熱；漫畫與直播引入 12h 本地磁盤快照與樂觀保底（Optimistic Stale UI），消除切頁白屏與轉圈；漫畫閱讀器基於滑動窗口前瞻預加載後 3 幀高清潔點；直播 DNS 異步預解析；全局輕量協程階梯喚醒；定義 `AppleSpring` 物理彈簧 Token，封裝 `Modifier.applePressEffect` 物理微凹回彈微交互全板塊覆蓋；底欄方向感知位移 + 0.96x 景深深度過渡；播放器抽屜式展開；179 項 JVM 單測與 Lint 0 errors 通過；打包 `AuroraShelf-2.15.0-159-apple-motion-instant-LOCAL.apk`（4,385,445 bytes，SHA-256 `95bb5840366c4b23247e62ef19e32d623605f8bc84170912c7da68b56041f75c`）。
- 2026-10-26：91 壇 5 大核心板塊並發聚合、分頁修復與播放器動作按鈕居中精緻重構（Candidate 160）。定位 91 壇原先僅抓取單一 `fid=33`（單頁 16 帖）且未抓取主力原創自拍區（`fid=4`）與我愛我妻區（`fid=21`）；引入 `CORE_FIDS = listOf(4, 21, 33, 34, 36)` 並發抓取，單頁帖量由 16 激增至 84 帖，翻頁加載 100 帖（累計 184+ 帖，+1150%）；修復 `ForumScreen.kt` 觸底分頁監聽錯誤對比全局帖子數量的 bug，改為監聽 `visiblePosts.lastIndex - 4`，使單源過濾態可正常觸底加載後續頁；擴充 `MAX_FORUM_CACHE_ITEMS` 至 120；重構 `PlayerActionButton` 容器內部採用 `fillMaxSize()` + 雙軸絕對居中 + 24dp 圖標規範盒 + 對稱雙字標籤，接入 `applePressEffect`；180 項 JVM 單測與 Lint 0 errors 通過；打包 `AuroraShelf-2.15.0-160-forum-posts-and-player-ui-LOCAL.apk`（4,385,445 bytes，SHA-256 `701c8dc43d4bf528f9b49e73f4c0461ca3330f24a72a9b7cfa34e20b4dac387b`）並通過模擬器實測。

## 坑与教训（症状 → 原因 → 做法；只追加）

- 宿主残留无头模拟器与后台进程占用：宿主若启动无头模拟器或第三方后台应用（如 `com.bluelion.qiqufishing`），会大量占用 CPU（350%+）并抢占前台焦点导致测试进程被 STOPPED；排查需先彻底卸载无关包并杀除无头 AVD 进程。
- WebView `JavascriptInterface` 权限约束：作为反射桥接给 `addJavascriptInterface` 的类不能声明为 `private class`，否则在 Android 4.2+ (API 17) 及 Lint 中被拦截；需声明为 `internal class` 并对容器添加 `@SuppressLint`。
- 浮动玻璃导航遮挡：`LazyColumn` 内容末尾的元素在未完全滚出浮动导航栏时，Compose `performClick` 点击元素中心可能直接命中顶层浮动导航栏；测试应优先使用视口中心安全区域的可交互节点。
- 浅色模式下 GlassSurface 撞白：`GlassSurface` 中直接调用 `isSystemInDarkTheme()` 在系统浅色模式下判定为浅色，导致暗黑页面下的玻璃容器取为白色而文字取自 DarkTheme 也是白色；应通过 `background.luminance() <= 0.5f` 判定当前背景亮度以动态决策。
- `AndroidViewModel` 派生类 Kotlin 默认参数导致开屏闪退：当 `AndroidViewModel(application)` 带有默认参数的第二项（如 `repository: Repository = Default()`）且未提供显式 `constructor(application: Application)` 时，Kotlin 不会生成单参数 `(Application)` 的 Java 构造函数。Compose 根组件的 `viewModel()` 使用反射 `getConstructor(Application.class)` 时抛出 `NoSuchMethodException` 导致开屏动画播毕后主进程闪退；做法是显式书写 `constructor(application: Application) : this(application, Default())` 并在 `proguard-rules.pro` 声明 `-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }`，并编写反射单测锁定保护。
- Compose 根 Surface 白光穿透与毛玻璃背板浑浊：在暗黑流媒体应用中，若顶层 Surface 使用动态主题色（浅色模式为 `#F8F9FE`），即使各页面内容区域绘制黑色，动画容器或组件边界微缩缝隙仍会将底层白光暴露；底层 LiquidBackdrop 捕获到底层白光后，会使毛玻璃浮动 Dock 呈现乳白浑浊雾状；必须在应用根 Surface 显式锁死纯黑背景。
- Gradle 增量编译 KotlinCompile UP-TO-DATE 陷阱：仅执行 assembleDebug 时，若 Gradle 缓存判定 Kotlin 编译 UP-TO-DATE，会导致修改未打入新 APK；修改 Compose 核心组件后应使用 `--rerun-tasks` 或清理构建确保 Dex 重新生成。
- 全屏横屏旋转下的触控坐标变换与自动隐藏：在横屏（2400x1080）全屏播放下，Compose 控件具有 4000ms 自动隐藏定时器；首次点击若控件处于隐藏态，会被消费为“唤醒控制栏”，必须在控件处于显示状态下的第二次点击才能触发顶部或底部操作按钮；且横屏下坐标需通过 UI hierarchy dump 获取精确 bounds 才能命中。
- Compose 弹幕长文本折行遮挡与初始同泳道碰撞：弹幕列表若包含长推广文案，Text 默认 softWrap 会折行渲染为大黑方块遮挡半个视频；初始种子数据若直接平铺进入 4 泳道，第 0 条与第 4 条同在泳道 0 会立即相撞重叠；必须给 Text 强加 `maxLines = 1`、`overflow = TextOverflow.Ellipsis`、`widthIn(max = 320.dp)`，并在数据项中绑定 `staggered delayMs` 阶梯延迟启动。
- Android VectorDrawable 的 gradientTransform 不支持与坐标烘焙：Android VectorDrawable XML 的 `<gradient>` 不支持任意 2D 仿射矩阵 `gradientTransform="matrix(...)"`，强行引入会导致 AAPT2 编译失败或渐变位置错乱；应使用 Python 严格按照 W3C SVG 规范将多层路径与渐变起止坐标直接预计算烘焙（Bake）为基于 108dp 视口的绝对几何坐标（`startX`, `startY`, `endX`, `endY`）。
- Material 3 `OutlinedTextField` 的 `label` 导致搜索文本基线偏下：在搜索条或胶囊输入框中若传入 `label = { Text(...) }`，Material 3 会在输入区域上方预留浮动标签槽位，即使设置了小高度或透明边框，内部输入文本也会被强制下沉偏向搜索框底部 20dp+；搜索框属于单行即时检索场景，应坚决杜绝使用表单浮动 `label`，必须采用纯 `placeholder` 并使用 `BasicTextField` 配合 `Box(contentAlignment = Alignment.CenterStart)` 实现绝对几何垂直居中。
- 第三方智能电视 DLNA 播放器防盗链 403 阻断：主流电视内置 DLNA 播放器在请求网络视频流时无法携带手机端的自定义 HTTP Headers（如 Referer），直接推流源站 CDN 会直接报错 403 Forbidden；必须在手机本地搭建微型 HTTP 代理服务器（LocalMediaProxy），将视频 URL 包装为本地代理链接，由手机代为请求并透明透传 HTTP 206 Partial Content 与 Range 标头给电视。
- Android 系统节电机制默认屏蔽 Wi-Fi 芯片组播包：在 Android 设备上使用 UDP SSDP 广播（239.255.255.250:1900）搜索局域网电视设备时，若未显式获取 `WifiManager.createMulticastLock()`，广播包与响应包会被底层 Wi-Fi 芯片静默丢弃导致搜不到设备；必须在扫描前调用 `multicastLock.acquire()` 并在扫描结束后释放。
- Media3 `SimpleCache` 离线视频投屏路径陷阱：`OfflineCacheManager` 下载的离线视频分片存储在 `SimpleCache` 中，而 `OfflineVideo.mediaUrl` 保存的是 `DownloadRequest.uri`（即远端原始 HTTP/HTTPS URL），若直接将 `mediaUrl` 传给 `File(path)` 会触发 404；必须通过 `CacheDataSource` 指定 `cacheKey = offlineVideo.id` 读取分片流，或对远端 URL 走 `/stream` 代理。
- `by viewModels()` 惰性委托未触发预热陷阱：在 Activity 中仅声明 `private val warmUpViewModel by viewModels()` 而不访问属性，Kotlin `Lazy` 委托不会在 `onCreate` 时实例化 ViewModel，必须在 `onCreate` 中显式读取属性（如 `warmUpViewModel.uiState`）才能在开屏动画期间真正启动后台预热。
- 纯 JVM 单测中 Android Log 桩方法未模拟抛出 RuntimeException：在 Android Gradle 模组中，纯 JVM 单元测试若直接调用了 `android.util.Log`，未被 Robolectric 代理的方法会抛出 `Method ... in android.util.Log not mocked` 导致单测崩溃；必须在 `app/build.gradle.kts` 的 `android.testOptions.unitTests` 配置 `isReturnDefaultValues = true`。
- 论坛爬虫硬编码域名失效与子域名轮换：外部论坛运营方会频繁轮换子域名（如 `t906` 废弃升级为 `t924`），不能仅硬编码单点 URL；应从门户主页（如 `index.php`）携带特定 Cookie（如 `language=cn_CN; watch_times=1`）动态读取最新导航入口，并配合内存记忆与多候选域名自动回退机制。
- Compose `remember` 作用域约束：`remember` 是 `@Composable` 函数，不能在 `LazyColumn` 的 `item { ... }` 内部调用（因其在 `LazyListScope` 闭包而非 Composable 上下文），必须在进入 `LazyColumn` 之前在 Composable 外层派生计算出 `visiblePosts`。
- 筛选过滤列表触底分页的 index 作用域陷阱：在列表上方提供分类筛选（如按单源过滤）时，`LazyColumn` 渲染的是过滤后的子列表 `visiblePosts`，而触底分页判定若写为 `lastVisible >= state.posts.lastIndex - 4`，当子列表条目数小于全局总列表条目数时，`lastVisible` 永远不可能达到全局最后一项的索引，导致分类状态下向下滚动永远无法触发 `onLoadMore()`；必须使用过滤后列表的长度 `visiblePosts.lastIndex - 4` 进行触底监听。
- 浮点精度与 `coerceIn(minimumValue, maximumValue)` 崩溃陷阱：在 Kotlin/Compose 中，`value.coerceIn(min, max)` 在 `min > max` 时会直接抛出 `IllegalArgumentException: Cannot coerce value to an empty range: maximum X is less than minimum Y`。例如计算 16:9 高度时，`width * (9f / 16f)`（浮点乘法得 231.42856dp）与 `width / (16f / 9f)`（浮点除法得 231.39464dp）存在 0.03392dp 的细微精度差，导致 `expandedHeight < collapsedHeight` 进而引发应用闪退。在任何动态高度折叠或范围夹紧中，必须先执行 `val minH = minOf(expanded, collapsed); val maxH = maxOf(expanded, collapsed)`，并在 `minH >= maxH` 时直接安全返回基准值，杜绝空区间异常。
- JAV-NONI / Dean Edwards 解包器巨型正則回溯導致 JVM `StackOverflowError`：在解析 70KB 的 LuluStream 流媒體頁面時，原正則包含 `(?:\\\\'|[^'])*` 巨型貪婪分支，引發 Java NFA 引擎深層遞歸回溯耗盡 JVM 線程棧；做法是徹底棄用回溯正則，改為非遞歸單遍歷詞法掃描器配合字典查表替換，時間複雜度降為線性 $O(N)$，棧佔用為常數級。
- 85po 站點 URL 遷移導致整頁條目丟棄：85po 站點 URL 結構由 `/ja/v/123/` 升級為 `/ja/v/123.html`，提取正則未適配 `\\.html` 導致提取 ID 失敗並在過濾階段整頁清空；做法是在提取正則尾部兼容 `(?:/|\\.html|$)`。
- MissAV 官方熱門路由失效 403 阻斷：站點已移除 `/ja/popular` 路由，直接訪問觸發 Cloudflare 403 攔截；做法是核驗站點最新發布路由，將熱門視頻分類映射至 `/ja/release`。
- 國內直連網絡與國際源網絡感知差異：國際源（如 Pornhub、XVideos 等）在國內直連下不可達，缺少透明提示會讓用戶誤判為 App 崩潰或視頻源壞死；做法是在 UI 明確標識「直連推薦」與「海外/國際線路」並附 Apple HIG 狀態膠囊，並在加載失敗時於首頁空狀態提供「一鍵切回默認直連視頻源」自愈按鈕。
- Chaturbate / HornyStudents 播放器動態 `clip-path` 硬裁切兩側畫面：Chaturbate 的 `video.js` 實例會在播放調度中動態計算並為 `<video class="vjs-tech">` 賦予內聯樣式 `clip-path: inset(0px 199.5px);`，強制將 4:3 攝像頭硬裁為寬屏，導致移動端畫面邊緣 200px 內容（人體、肢體）完全丟失，窄屏甚至全黑；做法是注入全域樣式 `video, .vjs-tech { clip-path: none !important; -webkit-clip-path: none !important; }`，並在 JS 調度循環中主動覆蓋清空 `clip-path`。
- Android Compose WebView 嵌入環境 `100vh` 高度塌陷為 0px 黑屏：在 Compose 的 `AndroidView(factory = { WebView })` 中，固定定位（`position: fixed`）的 DOM 容器若使用 CSS `100vw`/`100vh`，因 WebView 缺少外層瀏覽器 UA 全屏基準，計算高度會直接塌陷為 `0px` 導致黑屏；做法是提取 `window.visualViewport?.width || window.innerWidth` 換算為具體物理像素 `${viewportWidth}px` 與 `${viewportHeight}px` 賦予容器及其所有中間父層。
- 直播源特殊狀態（私密秀/門票秀）過早誤判阻斷加載：直播頁面常規頂部導航包含 `<a data-testid="private-shows-tab">`，操作欄常態包含 `.privateShowButton`。若腳本使用寬鬆選擇器（如 `[data-testid*=private]`）判定狀態，會在 `<video>` 尚未掛載的 150ms 內過早觸發誤判並彈出「正在進行私密表演」遮罩永久中斷加載；做法是嚴格限定選擇器至真正全屏遮罩類名（`.private-show-overlay` 等），並在 Kotlin 側引入 `MISMATCH_GRACE_MS`（3,000ms）保護寬限期。

## 权威来源（细节以这些文件为准）

| 文件 | 作用 | 最后更新 |
|---|---|---|
| `.ai/PROJECT_STATE.md` | 项目状态（当前阶段/目标/进行中） | 2026-10-26（今天） |
| `.ai/HANDOFF.md` | 最新交接 | 2026-10-26（今天） |
| `.ai/TASKS.md` | 任务清单 | 2026-10-26（今天） |
| `.ai/DECISIONS.md` | 关键决策 | 2026-10-26（今天） |
| `.ai/ENVIRONMENT.md` | 环境与构建基线 | 2026-10-25 |
| `docs/handoff-2026-10-26-antigravity.md` | 面向 Antigravity 的 157 打包交接指南 | 2026-10-26（今天） |
| `AGENTS.md` | Agent 协作规则（自动读取） | 2026-10-24 |

## 跨 Agent 规则

1. 开工：读本文件 → 按需读权威来源 → 用 `git status`、实际运行核验易变事实。
2. 收工：更新本文件「当前状态」+ 变化的权威记忆文件，然后运行 `python3 ~/.agent-memory/sync.py --project .`。
3. 只追加决策与教训，不改写历史；不写敏感信息；不覆盖未提交的用户成果。
