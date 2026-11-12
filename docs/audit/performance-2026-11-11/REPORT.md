# Netgrey v2.16.16 三端性能与代码质量审查

审查日期为 2026-11-11。重点覆盖启动、首屏、搜索、图片、点播、直播、漫画、离线缓存、论坛预取、游戏入口与设备布局。

## 基线与结论

- 本地及远端 main 均为 `4e6783d0ad489e44078063e5a6dc0e62f4064b53`，功能交付提交为 `40a437c`，版本为 **2.16.16 / Build 242**。审查开始工作树干净。
- 交接来源为 MEMORY.md、.ai/PROJECT_STATE.md、.ai/HANDOFF.md，并核对任务、决策和环境文档。两份本地交付包 SHA-256 与最新交接一致。
- Android 为独立 Kotlin/Compose 工程。iPhone 与 iPad 使用同一 Universal IPA，Info.plist 的 UIDeviceFamily 为 `[1, 2]`，最低 iOS 26.4。报告对共享问题同时标注两种设备。
- 发现 **21 项代码问题或可执行优化项，其中 P1 9 项、P2 10 项、P3 2 项**。P1 表示优先修复的明显正确性、安全或高影响资源问题。优先顺序以影响和触发范围评估，性能增益尚未通过真机 A/B 定量。
- 最值得先处理的性能路径为网络会话复用、MainActor 解析/图片 I/O、封面下采样与缓存预算、整本下载并发、启动预热与搜索渐进呈现。错误集缓存及离线不完整问题也需在提速前解决。

## 方法与覆盖范围

原生代码清单包含 Android 82 个 Kotlin 文件、25,059 行，以及 Apple 70 个 Swift 文件、23,594 行。执行了模块级检索，并沿关键入口深入检查调用链、线程隔离、并发、缓存键、失效策略和生命周期。该清单数表示纳入搜索范围，逐行深审集中在下表路径。

| 模块 | Android 主要路径 | iPhone / iPad 主要路径 |
|---|---|---|
| 启动与首屏 | Application、MainActivity、AuroraViewModel、HomeScreen | App、MainContainer、HomeScreen、GlobalStartupPrefetchEngine |
| 网络与搜索 | VideoRepository、PlaybackResolver、LiveStreamResolver | VideoRepository、ComicRepository、SearchSheetView、URLSession delegate |
| 图片与缓存 | CoverImageLoader、CoverCache、Coil 配置、预取 | CoverImageLoader、ComicPageImageLoader、AuroraCoverImage、DoubanPosterResolver |
| 点播与离线 | PlayerScreen、OfflineCacheManager | VideoDetailPlaybackScreen、NativePlayerView、VideoPreloadManager、OfflineDownloadManager |
| 直播 | LiveViewModel、LiveScreen、两个直播 repository、会话缓存 | LiveDetailView、LivePlayerView、LiveInlineCommentsView、WebView 包装 |
| 漫画与论坛 | ComicRepository/Screen/ViewModel、ForumRepository/ViewModel/Screen | ComicReader、ComicDownloadManager、ComicRepository、ForumRepository |
| 设备与游戏 | GameActivity、资源包 | DeviceLayoutProfile、iPad 双页/播放器布局、GameContainer；共用 JS 与资源 |

本轮执行代码审查、包体分析、独立函数复现与现有单元测试。测试使用 Debug 可测试构建，应用性能结论来自代码路径分析与包体事实。真机 Release 的 CPU、峰值内存、首帧时间、耗电、完整网络源 E2E、Stage Manager 视觉验收仍待专项采样。交接中的“0.3 秒起播”和“全量闭环”没有作为本轮实测数据使用。

## 测试与证据

| 目标 | 本轮结果 | 备注 |
|---|---|---|
| Android JVM | 241 总数，234 通过，7 跳过，0 失败 | `testDebugUnitTest --rerun` 确实重跑测试任务 |
| iPhone 17 Pro / iOS 26.4 | 163 总数，157 通过，6 跳过，0 失败 | xcodebuild test，Debug |
| iPad Pro 13-inch M5 / iOS 26.4 | 163 总数，157 通过，6 跳过，0 失败 | 同一测试产物 test-without-building |
| 缓存键独立复现 | 第一集与第二集共享两个缓存键 | 从当前源码原样抽取 canonicalKeys 执行 |
| 包体完整性 | APK、IPA 哈希匹配交接 | 见 evidence.json |
| 资源重复分析 | 两包各 19 组重复 | 按文件内容 SHA-256 对比 |

交接文件把 Android 的 241 写成通过数，并另加 7 skipped。本轮 XML 实际为 241 总数，其中 7 skipped。iPhone 与 iPad 跑的是同一套共享逻辑测试，不能将两次运行当作 326 项独立业务场景。现有下载测试主要覆盖状态模型、列表管理，未覆盖 HLS 完整依赖图、错误响应和章节别名冲突。

- Android APK SHA-256 为 `0904f08976feb394e13a558148e2de3851c88dac6f7f318c04af30e9f3b4b70a`。
- Universal IPA SHA-256 为 `49714df662a32e5cda1583dc34b03630bacc8e4bb6d4a635cab7de88210ac901`。

## 发现汇总

| 编号 | 优先级 | 问题 | 平台 |
|---|---|---|---|
| F01 | P1 | 视频下载逐请求创建 URLSession，连接复用与会话生命周期失控 | iPhone、iPad |
| F02 | P1 | 视频与漫画 HTML/JSON 解析仍在 MainActor 同步执行 | iPhone、iPad |
| F03 | P1 | 封面按原尺寸加载，内存和自建磁盘缓存缺少字节预算 | iPhone、iPad，iPad 大网格更明显 |
| F04 | P1 | 漫画离线读图、JPEG 转码与写盘阻塞主线程 | iPhone、iPad，双页阅读增加同时处理量 |
| F05 | P1 | 整本漫画下载一次启动全部章节，缺少并发上限 | iPhone、iPad |
| F06 | P1 | 黄果不同集数共用播放缓存别名，预热会返回错误集 | iPhone、iPad |
| F07 | P1 | HLS 离线缓存会截断长视频，且未本地化关键 URI | iPhone、iPad |
| F08 | P1 | 离线下载未校验 HTTP 状态，可把错误页面当成功媒体 | iPhone、iPad |
| F09 | P1 | 全局关闭服务器身份验证，把网络错误掩盖成成功 | Android、iPhone、iPad |
| F10 | P2 | Android 开场结束后才开始组合主界面，形成可避免的启动等待 | Android |
| F11 | P2 | iOS 全局预热重复请求首页，论坛 await 也没有等待实际结果 | iPhone、iPad |
| F12 | P2 | Android 全局预热丢弃论坛与漫画列表，之后进入页面还要重拉 | Android |
| F13 | P2 | 全源搜索等待最慢来源，快来源的结果迟迟不可见 | Android、iPhone、iPad |
| F14 | P2 | Android 同步网络阻塞不响应协程取消，6 秒搜索期限不构成硬截止 | Android |
| F15 | P2 | Android 直播自动降级实现与 v242 交接不一致 | Android |
| F16 | P2 | 直播评论使用完整隐藏网页，全屏往返会重载且 DOM 扫描过密 | iPhone、iPad |
| F17 | P2 | 播放缓存的有效期和回收策略不完整，重试可能持续命中过期流 | iPhone、iPad |
| F18 | P2 | Android 漫画章节防重复请求存在竞争窗口 | Android |
| F19 | P2 | 两个交付包各有约 30 MB 完全重复游戏资源 | Android、iPhone、iPad |
| F20 | P3 | 播放实现重复且主文件过大，旧实现容易被误修 | iPhone、iPad、Android |
| F21 | P3 | 游戏启动串行读取全部配置，Slots 配置又重复获取 | Android、iPhone、iPad 的 Web 游戏 |

## 详细问题与修复验收

### F01 · P1 · 视频下载逐请求创建 URLSession，连接复用与会话生命周期失控

**影响平台为 iPhone、iPad。**

permissive() 每次返回带 delegate 的新 URLSession。HLS 主列表、变体、每一个切片以及 MP4 下载直接调用临时会话，没有结束会话的代码。300 个切片会创建约 302 个会话，串行切片无法复用同一会话的连接池，额外 TLS 建连和会话资源显著拖累下载。长时间资源增长幅度需 Instruments 定量。直播解析也存在相同临时会话调用。

代码证据见 [PermissiveURLSessionDelegate.swift L79](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/PermissiveURLSessionDelegate.swift:79)、[OfflineDownloadManager.swift L271](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/OfflineDownloadManager.swift:271)、[OfflineDownloadManager.swift L341](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/OfflineDownloadManager.swift:341)、[LivePlayerView.swift L465](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/UI/Live/LivePlayerView.swift:465)。

建议修复为让下载管理器持有并复用有明确生命周期的 URLSession。需要后台下载时使用受系统管理的下载会话。临时会话完成后显式失效。

验收方式为本地服务器提供 300 个切片，统计会话创建数、TCP/TLS 连接数、下载时间及重复下载后的存活会话。

### F02 · P1 · 视频与漫画 HTML/JSON 解析仍在 MainActor 同步执行

**影响平台为 iPhone、iPad。**

VideoRepository 整类隔离于 MainActor，fetch91DongmanFeed/fetchRealHuavodFeed 在 await 网络返回后直接同步调用解析器。解析器标记 nonisolated 只解除隔离限制，同步调用仍在调用者执行线程上运行。字符串转换、正则匹配、HTML 遍历和部分 JSON 反序列化因此占用 UI 执行时间。搜索同时返回多源结果和大页列表时更容易集中抢占主线程。ComicRepository 同样在 MainActor 方法中解析响应。

代码证据见 [VideoRepository.swift L52](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Data/Video/VideoRepository.swift:52)、[VideoRepository.swift L347](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Data/Video/VideoRepository.swift:347)、[VideoRepository.swift L1201](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Data/Video/VideoRepository.swift:1201)、[ComicRepository.swift L262](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Data/Comic/ComicRepository.swift:262)。

建议修复为把获取与解析封装为独立 worker actor 或明确离开 MainActor 的异步处理，返回 Sendable 模型后只在主线程更新状态。

验收方式为使用保存的真实 HTML/JSON fixture 批量解析，同时采集主线程 Time Profiler 与滚动 hitch。确认解析栈离开主线程。

### F03 · P1 · 封面按原尺寸加载，内存和自建磁盘缓存缺少字节预算

**影响平台为 iPhone、iPad，iPad 大网格更明显。**

CoverImageLoader 仅设置 countLimit=120，网络与磁盘路径均使用 UIImage(data:) 原尺寸图片，没有目标显示尺寸、下采样和 cost。120 张 2000×3000 RGBA 位图的理论像素量约 2.88 GB，该数字是容量示例，实际峰值取决于解码与缓存淘汰。AuroraCovers 自建目录持续写入，只有手工全清，URLCache 的 256 MB 限额不控制该目录。

代码证据见 [CoverImageLoader.swift L20](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/CoverImageLoader.swift:20)、[CoverImageLoader.swift L31](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/CoverImageLoader.swift:31)、[CoverImageLoader.swift L72](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/CoverImageLoader.swift:72)、[CoverImageLoader.swift L113](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/CoverImageLoader.swift:113)、[CoverImageLoader.swift L128](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/CoverImageLoader.swift:128)。

建议修复为以显示尺寸与 scale 生成下采样图片，缓存键包含尺寸档位，按真实解码字节设置 totalCostLimit。为自建磁盘缓存增加总量、TTL 和 LRU 清理。

验收方式为手机与 iPad 各连续浏览至少 300 张高分辨率封面，记录峰值、稳定态内存、缓存目录大小和滚动掉帧。

### F04 · P1 · 漫画离线读图、JPEG 转码与写盘阻塞主线程

**影响平台为 iPhone、iPad，双页阅读增加同时处理量。**

ComicDownloadManager 为 MainActor 类。localImage 同步 Data(contentsOf:) 读取图片，阅读器直接调用。下载每页在 await 完成后执行 image.jpegData 与 Data.write，同样回到主线程。视频 HLS 切片写盘也位于 MainActor 下载方法。后台下载与前台浏览同时进行时会争抢 UI 时间。

代码证据见 [ComicDownloadManager.swift L61](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/ComicDownloadManager.swift:61)、[ComicDownloadManager.swift L113](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/ComicDownloadManager.swift:113)、[ComicDownloadManager.swift L224](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/ComicDownloadManager.swift:224)、[ComicReaderView.swift L46](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/UI/Comic/ComicReaderView.swift:46)、[OfflineDownloadManager.swift L342](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/OfflineDownloadManager.swift:342)。

建议修复为文件读写、解码和 JPEG 编码移入专用后台执行器，UI 只接收结果与节流后的进度。离线阅读采用按尺寸解码和邻页窗口。

验收方式为下载一章高分辨率漫画时持续滚动首页，再离线双页翻阅同章，检查主线程文件 I/O 与 JPEG 编码调用。

### F05 · P1 · 整本漫画下载一次启动全部章节，缺少并发上限

**影响平台为 iPhone、iPad。**

startDownloadAll 遍历全部章节，每章立刻创建 Task。没有下载队列或 permit 限制。每章先解析页面再加载图片，共享 actor 在网络 await 时可重入，不能限制同时在途的章节和请求。长篇漫画会同时排入大量请求、状态更新与转码，影响前台播放与加载。

代码证据见 [ComicDownloadManager.swift L123](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/ComicDownloadManager.swift:123)、[ComicDownloadManager.swift L151](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/ComicDownloadManager.swift:151)、[ComicDownloadManager.swift L158](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/ComicDownloadManager.swift:158)。

建议修复为建立有限并发章节队列及全局图片下载预算，支持取消、暂停、网络策略和前台任务优先级。

验收方式为使用 200 章 fixture 点整本下载，断言活跃章节数和网络数有界，退出后可停止，前台首屏加载不会被下载占满。

### F06 · P1 · 黄果不同集数共用播放缓存别名，预热会返回错误集

**影响平台为 iPhone、iPad。**

canonicalKeys 对 /video/123/ep-2/ 也追加 /detail/123/ 和 /video/123/。先缓存第一集后解析第二集，会通过别名命中第一集，跳过真正解析；第二集先写入时也会污染第一集别名。已从当前源码原样抽取函数执行 Swift 探针，确认两集共享两个键。预热和滑动窗口扩大了触发机会。

代码证据见 [VideoPreloadManager.swift L42](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/VideoPreloadManager.swift:42)、[VideoPreloadManager.swift L62](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/VideoPreloadManager.swift:62)、[VideoPreloadManager.swift L94](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/VideoPreloadManager.swift:94)。

建议修复为缓存键必须包含源、剧目、线路和集数。只有明确代表第一集的详情页可建立第一集别名。清除旧的错误缓存。

验收方式为先缓存第 1 集再解析第 2/3 集，并反向执行；三个 URL 和播放内容必须保持独立。运行本目录 canonical-key-probe.swift 可重现当前冲突。

### F07 · P1 · HLS 离线缓存会截断长视频，且未本地化关键 URI

**影响平台为 iPhone、iPad。**

下载循环只处理 segmentOriginalURLs.prefix(360)，但 rewrittenLines 保留所有切片引用，最终无条件设置 completed。超过 360 片的视频留下不存在的文件。以 # 开头的标签直接保留，因此 EXT-X-KEY、EXT-X-MAP 与外置音轨的 URI 也没有完整本地化。加密 HLS、fMP4 或多音轨内容可能联网时看似成功，断网后失败。

代码证据见 [OfflineDownloadManager.swift L304](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/OfflineDownloadManager.swift:304)、[OfflineDownloadManager.swift L328](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/OfflineDownloadManager.swift:328)、[OfflineDownloadManager.swift L357](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/OfflineDownloadManager.swift:357)。

建议修复为采用系统支持的离线 HLS 机制，或实现完整播放列表依赖解析。按 VOD/直播语义判断，完成前校验所有依赖，不以固定片数替代完整性。

验收方式为本地提供 361/600 片 VOD、AES-128、EXT-X-MAP、外置音轨四类样本，断网完整播放并检查最终状态。

### F08 · P1 · 离线下载未校验 HTTP 状态，可把错误页面当成功媒体

**影响平台为 iPhone、iPad。**

HLS playlist/variant/segment 请求忽略 HTTP response，MP4 download(for:) 取得 response 后只使用长度。URLSession 收到 403/404 HTML 通常正常返回数据，因此错误内容可能写为 .ts 或 .mp4 并标记完成。重试看到文件存在又直接跳过，坏缓存会持续阻碍播放。

代码证据见 [OfflineDownloadManager.swift L271](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/OfflineDownloadManager.swift:271)、[OfflineDownloadManager.swift L294](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/OfflineDownloadManager.swift:294)、[OfflineDownloadManager.swift L341](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/OfflineDownloadManager.swift:341)、[OfflineDownloadManager.swift L376](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/OfflineDownloadManager.swift:376)。

建议修复为统一校验 2xx、合理媒体类型和非空负载。使用临时文件校验后原子落盘，失败清理不完整片段，重试验证已存文件。

验收方式为让第 3 个切片或 MP4 URL 返回 403 HTML，确认下载失败且不进入 completed，恢复服务器后可成功重试。

### F09 · P1 · 全局关闭服务器身份验证，把网络错误掩盖成成功

**影响平台为 Android、iPhone、iPad。**

Apple permissive delegate 替换主机名策略、把服务器自带证书作为根，最终始终 useCredential。Android 直播共享客户端采用空 checkServerTrusted 和恒 true hostnameVerifier。这会接受不可信服务器，涉及接口、媒体和用户数据完整性。它也使连通性与性能报告失去正常 TLS 环境的参考价值。

代码证据见 [PermissiveURLSessionDelegate.swift L37](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/PermissiveURLSessionDelegate.swift:37)、[PermissiveURLSessionDelegate.swift L72](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/PermissiveURLSessionDelegate.swift:72)、[StripchatLiveRepository.kt L103](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/StripchatLiveRepository.kt:103)。

建议修复为恢复系统证书和主机名验证，修复具体源的证书链或可信代理配置。确需私有 CA 时明确限定可信材料和目标域名。

验收方式为正常证书成功，错误主机名、自签名和过期证书均拒绝；在正常信任策略下重新测首帧与列表耗时。

### F10 · P2 · Android 开场结束后才开始组合主界面，形成可避免的启动等待

**影响平台为 Android。**

Release 默认开启 intro。appVisible 初值为 !introVisible，只有 onFinished 才设 true，AuroraShelfApp 因此在开场期间根本没有组合。ViewModel 虽已拉数据，界面组合、Coil 绑定和首次布局仍排在视频与 220 ms 淡出之后。源码声明片长 5.7 秒、兜底 8.5 秒；这两个是配置值，当前未重新测量视频实际时长或真机 TTID。

代码证据见 [MainActivity.kt L48](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/MainActivity.kt:48)、[MainActivity.kt L84](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/MainActivity.kt:84)、[IntroAnimation.kt L58](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/ui/IntroAnimation.kt:58)、[AppPreferences.kt L153](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/AppPreferences.kt:153)。

建议修复为让主界面尽早组合，按首个可交互帧决定开场收尾。保持用户现有跳过与开场设置，单独统计数据就绪和可交互时间。

验收方式为在正式 Release 包分别测默认开场、跳过、关闭开场、Reduce Motion 的 TTID/TTFD，检查首次安装与暖启动。

### F11 · P2 · iOS 全局预热重复请求首页，论坛 await 也没有等待实际结果

**影响平台为 iPhone、iPad。**

App.init 启动全局预热。读取 VideoRepository.shared 已通过 init→switchSource→loadFeed 发起首屏请求，预热又调用 fetchVideosForSource 请求首页，结果没有作为首页 snapshot 复用。随后 ForumRepository.init 启动 loadPosts，预热再次调用 loadPosts。该方法返回 Void 并内部创建 Task，外层 await 不等待帖子完成，紧接着读取 posts 可能为空。整个流程无网络计费、低电量或当前可见页预算。

代码证据见 [AuroraShelfApp.swift L15](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/App/AuroraShelfApp.swift:15)、[GlobalStartupPrefetchEngine.swift L45](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/GlobalStartupPrefetchEngine.swift:45)、[GlobalStartupPrefetchEngine.swift L82](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/GlobalStartupPrefetchEngine.swift:82)、[VideoRepository.swift L80](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Data/Video/VideoRepository.swift:80)、[ForumSourceCatalog.swift L422](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Data/Forum/ForumSourceCatalog.swift:422)。

建议修复为让首页请求有唯一持有者并复用结果。预热显式返回可 await 的任务，按用户访问概率、网络和前台负载调度，避免调用会取消当前列表加载的刷新入口。

验收方式为冷启动录制请求时间线，当前源首页只发一次；预热不取消用户正在浏览的论坛请求，论坛计数对应实际完成结果。

### F12 · P2 · Android 全局预热丢弃论坛与漫画列表，之后进入页面还要重拉

**影响平台为 Android。**

startGlobalWarmup 在 1.2 秒后创建独立 ForumRepository，load(1) 的结果被丢弃。漫画也创建独立 repository，列表只用于前 6 张封面；没有调用 AppPreferences 的论坛/漫画 snapshot 保存接口。两个 repository 的列表入口没有对应的共享结果缓存。这部分额外请求不能提供交接描述的列表即点即看，而且绕过现有 isPrefetchEnabled 策略。

代码证据见 [AuroraViewModel.kt L373](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/ui/AuroraViewModel.kt:373)、[ForumRepository.kt L27](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/forum/ForumRepository.kt:27)、[ComicRepository.kt L38](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/comic/ComicRepository.kt:38)、[AppPreferences.kt L40](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/AppPreferences.kt:40)。

建议修复为复用真正页面使用的缓存与 repository，或取消无消费方的预取。统一服从预加载设置、计费网络策略及优先级。

验收方式为关闭预加载后冷启动不产生额外论坛/漫画预取；开启后进入对应页面命中已完成快照并避免重复列表请求。

### F13 · P2 · 全源搜索等待最慢来源，快来源的结果迟迟不可见

**影响平台为 Android、iPhone、iPad。**

Android searchAll 对所有 source async 后 awaitAll，iOS searchAllSources 收齐 task group 后才赋值 searchResults。即使某源 200 ms 已返回，也要等全部来源成功或失败。UI 只有整批结果，慢源直接决定首个结果时间，联网源越多影响越明显。

代码证据见 [VideoRepository.kt L156](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/VideoRepository.kt:156)、[AuroraViewModel.kt L220](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/ui/AuroraViewModel.kt:220)、[VideoRepository.swift L1702](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Data/Video/VideoRepository.swift:1702)、[VideoRepository.swift L1722](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Data/Video/VideoRepository.swift:1722)。

建议修复为按来源渐进合并结果、去重并保留稳定排序，设置来源预算与总预算，取消旧查询，显示已完成来源数。

验收方式为注入 200 ms、1 秒和 15 秒三个来源，第一批结果在快源完成后可见，慢源更新不清空已有内容。

### F14 · P2 · Android 同步网络阻塞不响应协程取消，6 秒搜索期限不构成硬截止

**影响平台为 Android。**

搜索用 withTimeout(6000) 包住最终执行 Jsoup 同步请求的代码，底层可阻塞 15000 ms，取消无法自动中断 socket。直播解析同样调用 OkHttp Call.execute，API 失败后继续两个 HTML 候选，客户端 connect/read 均为 25 秒且无总 callTimeout。最坏等待会随多个串行失败累加，用户离开后旧 I/O 仍占用线程与网络。

代码证据见 [VideoRepository.kt L164](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/VideoRepository.kt:164)、[VideoRepository.kt L615](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/VideoRepository.kt:615)、[LiveStreamResolver.kt L55](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/LiveStreamResolver.kt:55)、[LiveStreamResolver.kt L78](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/LiveStreamResolver.kt:78)、[StripchatLiveRepository.kt L114](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/StripchatLiveRepository.kt:114)。

建议修复为网络调用接入 cancellable suspend 适配器并在取消时 Call.cancel，设置单请求与整个解析流程的总预算。不要吞 CancellationException 后进入下一候选。

验收方式为本地服务器延迟响应，搜索 6 秒截止确实停止底层请求；退出直播页后确认旧请求关闭，新的主播请求可立即开始。

### F15 · P2 · Android 直播自动降级实现与 v242 交接不一致

**影响平台为 Android。**

NativeLivePlayer.onPlayerError 只设置 errorMessage/isLoading/isPlaying，没有调用 onFallbackToWeb。传入的回调仅挂在错误层的“备用视口”按钮。当前行为需要用户手动操作，原生 403/解码失败时不会按交接描述自动切换。

代码证据见 [LiveScreen.kt L1194](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/ui/LiveScreen.kt:1194)、[LiveScreen.kt L1348](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/ui/LiveScreen.kt:1348)、[LiveScreen.kt L1465](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/ui/LiveScreen.kt:1465)。

建议修复为区分可降级错误和真实离线/私密状态，为可降级错误执行一次受控切换，避免循环回退和重复播放器同时运行。

验收方式为让 HLS 返回 403 或不支持的内容，确认自动释放原生播放器并进入备用页，正常 HLS 不创建额外 WebView。

### F16 · P2 · 直播评论使用完整隐藏网页，全屏往返会重载且 DOM 扫描过密

**影响平台为 iPhone、iPad。**

非 Stripchat 评论通过 1×1 WKWebView 加载主播完整网页。MutationObserver 观察整个 document 子树，每次变动都 querySelectorAll，另有 2.5 秒定时扫描；sent Set 无上限。LiveDetailView 把整个评论区置于 if !isFullScreen，切全屏移除，再退出创建新网页。原生视频播放仍伴随独立网页脚本、资源请求和聊天重连；隐藏网页是否额外播放媒体需实测。

代码证据见 [LiveInlineCommentsView.swift L83](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/UI/Live/LiveInlineCommentsView.swift:83)、[LiveInlineCommentsView.swift L109](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/UI/Live/LiveInlineCommentsView.swift:109)、[LiveInlineCommentsView.swift L125](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/UI/Live/LiveInlineCommentsView.swift:125)、[LiveDetailView.swift L54](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/UI/Live/LiveDetailView.swift:54)。

建议修复为优先使用独立聊天数据源；保留 WebView 时将生命周期绑定房间，避免随布局重建，限定观察容器、节流扫描并限制去重集合大小，卸载时停止监听。

验收方式为同一直播全屏往返十次，统计网页导航次数、WebContent 进程内存和网络流量；大消息量运行 30 分钟检查 CPU。

### F17 · P2 · 播放缓存的有效期和回收策略不完整，重试可能持续命中过期流

**影响平台为 iPhone、iPad。**

VideoPreloadManager 固定缓存两小时，仅访问同键时移除过期项，没有容量淘汰。Beeg 的独立 StreamCache 无 TTL，也没有失效入口；即使外层缓存被清空，解析仍可命中内部旧 URL。播放器错误重试调用相同 resolve 路径，缺少对失败直链的定向失效。长时间浏览会积累条目，签名流过期后可能重复失败。

代码证据见 [VideoPreloadManager.swift L20](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/VideoPreloadManager.swift:20)、[VideoPreloadManager.swift L62](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Services/VideoPreloadManager.swift:62)、[VideoRepository.swift L26](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Data/Video/VideoRepository.swift:26)、[VideoRepository.swift L552](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/Data/Video/VideoRepository.swift:552)、[VideoDetailPlaybackScreen.swift L1412](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/UI/Screens/VideoDetailPlaybackScreen.swift:1412)。

建议修复为合并明确的缓存所有权，按来源/签名过期时间制定 TTL，加容量限制，并在授权过期等错误后失效该条目再解析。

验收方式为控制时钟和签名有效期，过期后重试必须产生新解析；浏览数千条后缓存数量保持预算内。

### F18 · P2 · Android 漫画章节防重复请求存在竞争窗口

**影响平台为 Android。**

prewarmChapter 使用 containsKey 后 async 再赋值，检查和注册不原子。普通 chapter 冷 miss 只读取已有 inFlightChapters，自己发起的请求未登记。预取与点击、两个并发调用均可能请求同章两次；快速完成的 async 还可能先执行 finally.remove，随后父协程把已完成 deferred 重新放回。

代码证据见 [ComicRepository.kt L51](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/comic/ComicRepository.kt:51)、[ComicRepository.kt L66](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/comic/ComicRepository.kt:66)、[ComicRepository.kt L72](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/data/comic/ComicRepository.kt:72)。

建议修复为统一预取和前台加载入口，用 Mutex 或原子注册保证任务先登记后启动，完成时按任务身份移除。

验收方式为用计数 fake source 并发调用同一章节 20 次，断言真实 fetch 为一次，成功和失败后均没有残留 in-flight 条目。

### F19 · P2 · 两个交付包各有约 30 MB 完全重复游戏资源

**影响平台为 Android、iPhone、iPad。**

逐个读取 APK/IPA 内 www 文件并按内容 SHA-256 分组，各发现 19 组重复。Android 多余压缩数据 30,510,191 bytes，iOS 30,077,597 bytes。例 cg_ch3.mp4 与 v_comics_maze_3_14.mp4 内容一致，各约 5 MB。游戏资源占两包 ZIP 压缩负载约 97.8%/98.7%。这直接增加下载、安装和更新成本；不会仅因打包存在就全部加载到 RAM。

代码证据见 [cg_ch3.mp4](/Users/itou/Documents/ChatGPT/91pron/app/src/main/assets/www/assets/videos/cg_ch3.mp4)、[v_comics_maze_3_14.mp4](/Users/itou/Documents/ChatGPT/91pron/app/src/main/assets/www/assets/videos/v_comics_maze_3_14.mp4)。

建议修复为先汇总资源别名与所有引用，保留一个实体文件并统一 manifest 映射，确认两个原生资源打包流程一致。按需资源分发属于额外产品决策。

验收方式为按资源清单全量打开 CG/剧情，重新计算包内重复量、包体与下载时间，避免删除仍有路径引用的文件。

### F20 · P3 · 播放实现重复且主文件过大，旧实现容易被误修

**影响平台为 iPhone、iPad、Android。**

当前 iOS 实际详情入口创建 VideoDetailPlaybackScreen（1701 行）。NativePlayerView 仍有 1036 行完整播放器代码，生产源码未找到构造调用。二者都持有 AVPlayer、KVO、播放解析和清理代码，容易把修复落到未使用实现。Android LiveScreen 达 3007 行，混合解析调度、原生/Web 播放、DOM 脚本、评论与错误层，F15 正是回调接线容易遗漏的例子。行数本身不能推出运行性能差。

代码证据见 [HomeScreen.swift L356](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/UI/Screens/HomeScreen.swift:356)、[PersonalScreen.swift L274](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/UI/Screens/PersonalScreen.swift:274)、[NativePlayerView.swift L1](/Users/itou/Documents/ChatGPT/91pron/ios/AuroraShelf/UI/Screens/NativePlayerView.swift:1)、[LiveScreen.kt L1276](/Users/itou/Documents/ChatGPT/91pron/app/src/main/java/com/aurorashelf/app/ui/LiveScreen.kt:1276)。

建议修复为先确认实际入口和是否有外部引用，再处理旧播放器。按生命周期与职责提取播放器状态/网络解析，保留已有 UI 行为及测试。

验收方式为从首页、搜索、收藏、历史、离线分别进入播放，验证每条入口都覆盖同一套修复，检查退出释放与全屏切换。

### F21 · P3 · 游戏启动串行读取全部配置，Slots 配置又重复获取

**影响平台为 Android、iPhone、iPad 的 Web 游戏。**

loadData 逐个 await 14 份独立 JSON，再读取 manifest，所有文件使用 no-store。Slots 初始化另行 fetch slots.json。文件位于本地包内，主要成本是 WebView/资源桥往返与串行 JSON 处理，实际节省量需要测量，不应当作外网下载瓶颈。

代码证据见 [app.js L200](/Users/itou/Documents/ChatGPT/91pron/app/src/main/assets/www/js/app.js:200)、[slots.js L718](/Users/itou/Documents/ChatGPT/91pron/app/src/main/assets/www/js/slots.js:718)。

建议修复为复用已加载 Slots 配置，独立配置采用受控并行读取，首次进入只加载当前入口必要数据，按资源版本使用缓存。

验收方式为在两端 Release WebView 记录游戏入口到可交互时间与资源请求数，覆盖首次进入、返回重进及存档恢复。

## 需要进一步采样的设计风险

以下两项已有明确代码位置，实际卡顿幅度或布局失效还需要场景验证，未计入上面 21 项。

### iPad 窄窗口仍进入硬件 pad 分支

VideoDetailPlaybackScreen 在 213、338、495 行等位置通过硬件 idiom 决定播放器高度、工具栏文字和尺寸，未统一使用现有 DeviceLayout 的紧凑窗口分类。DeviceLayout.currentWindowSize 与 safeArea 又从 connectedScenes 选择第一个活动窗口。需在 393pt、半屏与 Stage Manager 缩放下检查工具组宽度、播放器比例及窗口归属。现有 DeviceLayout 单元测试覆盖分类函数，不能证明所有消费者使用了该分类。

### Android 收藏越多，启动同步 JSON 成本越大

AuroraViewModel 初始化在主线程读取 cachedFeed、favoriteIds、historyIds 和 savedVideos，再 distinctBy。AppPreferences.savedVideos 初次解码同步执行。当前缓存页数有界，但收藏可持续增长，需用 100/1000/5000 条收藏数据测启动主线程耗时。若出现明显阻塞，再改成异步恢复与稳定占位，保留资料库状态一致性。

## 已核对的保护措施

- Android 自建 CoverCache 具有内存和磁盘字节上限，不能套用 iOS 封面缓存缺少字节预算的结论。
- Android OfflineCacheManager 采用 Media3 持久下载设施，并把索引读取放到 IO 调度器，未发现 iOS 的 360 片硬截断实现。
- iOS ComicPageImageLoader 已有 80 MB cost 限额和请求去重。F03 指向另一个 CoverImageLoader，F04 指向 MainActor 离线读写调用者。
- 原生直播与游戏具有部分退出清理代码。本报告未把所有播放器都判定为泄漏。
- iPhone/iPad 主列表使用 LazyVGrid，漫画使用 LazyVStack。仍需评估单张图片和同时可见内容的成本。

## 建议执行顺序

1. 先修 F06–F09，建立正确的缓存键、离线完整性、HTTP 校验和 TLS 信任基线。
2. 处理 F01–F05，将网络会话、解析、读写、解码与下载并发放到可控预算内。
3. 处理 F10–F18，减少启动无效工作，让搜索先显示快结果，补全取消、降级与缓存失效。
4. 清理资源重复与失去入口的实现，最后优化游戏本地配置读取。

每个修复应沿现有业务入口验证，增加能捕获该缺陷的定向回归测试。性能验收建议同时记录冷启动可交互时间、列表首批结果、首张封面、点击到首帧的 p50/p95，以及 10 分钟滚动/播放后的内存与网络量。使用同机、同包配置、相同 fixture 和网络条件比较。iPad 增加双页漫画、宽网格和窄窗口场景。所有指标在此报告中保留为待测项。

## 官方依据

主线程上的同步解析、图片处理与 I/O 会抢占界面响应时间，await 之后继续执行 MainActor 方法仍受主线程约束。相关处理原则见 [Apple Improving app responsiveness](https://developer.apple.com/documentation/xcode/improving-app-responsiveness)。

会话失效会解除 delegate 与回调对象引用，临时 URLSession 应有明确终结流程。相关行为见 [Apple invalidateAndCancel](https://developer.apple.com/documentation/foundation/urlsession/invalidateandcancel())。

## 本轮交付状态

新增审查报告、结构化证据与缓存键复现脚本，并更新项目交接记忆。本轮业务源码差异为空。以上问题尚待修复，性能优化收益尚待 Release 真机量化。测试运行覆盖了两种 Apple 模拟器的共享逻辑，未执行本轮发布或远程安装包替换。
