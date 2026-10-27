# 验证记录 — 2026-09-24（最新补记 2026-10-27）

## 169 — XVideos 完整分类与 HornyStudents 直播修复 — 2026-10-27

- 构建命令：`./gradlew assembleRelease --console=plain --no-daemon`（JDK 21）；结果 `BUILD SUCCESSFUL`，耗时 1 分 46 秒。
- APK：`dist/AuroraShelf-2.15.0-169-xvideos-live-fix-LOCAL.apk`，4,418,213 bytes；AAPT2 `versionCode='169' versionName='2.15.0'`；SHA-256 `6f24aa433dffa405f25db1a1d0605547e2331c374f47157d9add87b06d1ba3c7`。
- JVM：`testDebugUnitTest` 实测 205 tests，201 passed，0 failed，0 errors，4 skipped；定向直播/缓存/XVideos 测试先行通过。
- 模拟器配置：`missavtest`，Android 14 / arm64，1080×2340，density 440；按规则卸载旧 168 后全新安装 169，`dumpsys package` 确认 versionCode 169。
- XVideos 运行时（E3）：首页 UI 树显示当前源 `XVideos`，分类顺序含「最新 / 热门 / 中文 / 自拍 / 亚洲 / 认证」，右侧继续存在「AI」；不再是旧 168 的五分类。截图：`captures/169_xvideos_41_categories_home.png`。
- HornyStudents 运行时（E3）：切换至 `HornyStudents` 后加载 `czamabech`、`kriskras__` 等真实主播；`kriskras__` 房间日志在约 1.76 秒返回 `ready:landscape`，UI 显示「暂停直播」、主播信息、HD、观看人数及公开聊天；截图：`captures/169_hornystudents_public_player_ready.png`。
- 旧问题复现对照：`czamabech` 本轮状态被站点判定为 `private_show`，应用显示「公开直播流暂未开放」，属于源站状态而非客户端误判；未把该房间计作播放失败。
- 代码防护：直播房间 slug 兼容尾部 `/` 与 query；`about:blank`/loading 阶段返回 `pending`；实际 `videoWidth/videoHeight` 与 `readyState >= 2` 才进入 READY；跨直播源缓存按 `sourceId + category` 隔离；XVideos kiosk 显式按语义 ID 映射，过期五 kiosk extractor 不再静默回退到 `latest`。
- 截图与 UI 流程仅覆盖上述 Android 14 仿真器配置；TalkBack、真实硬件真机、200% 字体与所有 41 个分类逐项联网加载仍未在本轮完成，不能据此宣称 E4 或全分类网络可用。


## Netgrey 品牌候选（137-netgrey）— 2026-10-24

- 品牌：应用名与首页标题改为 **Netgrey**（英文名，不使用中文名）；启动图标从旧拟物素材重绘为扁平矢量 N（冷灰 `#9AA3AE`，纯黑底），自适应图标前景/单色层改为 `drawable/ic_launcher_foreground.xml` + `drawable/ic_launcher_monochrome.xml`，删除旧 `launcher_foreground*` 资源与 PNG 中间产物。
- 图标规格：108 dp 画布，N 高 60.48 dp（56%），宽 33.246 dp，竖笔/斜笔等重 11.715 dp；冷灰对黑底对比度 8.22:1。
- 构建：`assembleDebug` PASS（16:03，增量 18 s）。最终 release 在独立 `git worktree`（`HEAD b8b36da` + 仅品牌改动）中构建：`assembleRelease` PASS（16:16，1m46s，R8 + shrinkResources），避免混入并行会话在工作树中的未提交改动；16:12 于工作树直接构建的首个产物已丢弃。
- Release：`dist/AuroraShelf-2.15.0-137-netgrey.apk`，3,407,533 bytes，versionCode **137** / versionName 2.15.0，label `Netgrey`；v2 签名（Android Debug 证书）通过，`zipalign -c -P 16` 通过；SHA-256 `eae016cfa75308a1df3577ad12e40898e6ec594a47629a875409a76e5f761f4a`；`dist/SHA256SUMS.txt` 已覆盖全部 10 个 APK 且全量校验通过。
- 安装：Android 14 / 1080×2340 模拟器 `adb install -r` 覆盖安装成功；冷启动 `am start -W` **TotalTime 1811–2226 ms**（3 次：2014 / 2226 / 1811，含首屏网络请求，环境同机），crash buffer 为空；启动器抽屉显示 Netgrey + 冷灰 N，首页标题 Netgrey 单行不截断。
- Lint：最终代码 **0 errors / 19 warnings / 3 hints**，无图标相关告警。
- 未覆盖：主题图标（monochrome）未在设备开启实测；JVM/设备测试未在本轮重跑；TalkBack 与真机走查沿用上一轮结论。

## 2.15.0 加载优化 + HIG 第二轮候选（137）— 2026-10-24

- 加载优化：首页首屏结果写入本地快照（按来源 + 分类，24 小时有效），冷启动先渲染缓存再静默刷新；封面增加 24 MB 内存 LRU + 96 MB 磁盘 LRU（`cacheDir/cover_cache`，SHA-256 文件名、原子写、后台淘汰）、同 URL 单航班合并、60 秒失败负缓存；封面与黄果配置抓取改用共享 OkHttp 连接池；播放解析缓存 2 分钟（播放地址）/10 分钟（选集）；首屏预热前 8 张封面。
- 首屏实测（同一 host、同一 Android 14 模拟器，`am start` 后经 uiautomator 轮询到 hero 与 ≥2 张网格卡片；指标含每次 dump 约 0.5–1 s 采样开销）：优化前 **4.79 s / 5.80 s**；优化后冷缓存 **4.50 s**、热缓存 **3.22 s / 4.04 s**。仅作同环境相对比较，不宣称普适提升百分比。
- HIG 第二轮：高对比（Increase Contrast）三态色板 + 动态色对比度守卫 + 系统检测（API ≥36 用官方 `isHighContrastTextEnabled`，低版本回退 `Settings` 键；API 34 模拟器实测 `high_text_contrast_enabled=1` 后 UI 切换为纯白底/纯黑字）；深色模式封面压暗 11%；200% 系统字体下直播/漫画降单列、首页双列改单列，并修复 hero 标题与收藏按钮重叠；15 处 heading 语义；可选文字轨自动优选中文/英文；新增 3 项 Compose 无障碍设备断言。
- JVM：**125 项 / 0 失败 / 1 跳过**（新增 26 项：封面缓存 8、封面 loader 4、播放解析 3、主题色板/守卫 7、偏好缓存 4）。
- 设备：全量 connected 套件本轮在 **3 台模拟器同时运行、host 高负载** 的环境下未通过（2 项 `AuroraFlowTest` 失败：1 项「启动瞬间收藏列表为空」为真实回归——本地收藏库异步化导致，已回退该改动；1 项为首页网络等待 20 s 超时）。修复后定向复跑：**`HigAccessibilityTest` 3/3 PASS**、**`AuroraFlowTest#favoriteAppearsInFavorites` PASS**。干净环境下的全量设备基线仍为 12:36 的 **26/26（136 代码）**；137 的全量设备覆盖记为 **blocked**，需在空闲 host 重跑。
- 回归审计：对全部改动 Kotlin 文件做了字符串级 diff，修复本轮引入的 1 处误改文案（首页标题一度被改成“网灰”，已按并发品牌改名恢复为“网灰”并保持 heading）。注意：本轮工作期间工作树出现**另一路并发工作**的品牌改名与启动图标替换（`values/strings.xml` app_name → 网灰、`mipmap-anydpi/ic_launcher.xml` 与新增 `drawable-*dpi` 前景图、删除旧 `launcher_foreground*` 资源，mtime 14:35–14:37）。按要求原样保留，未纳入本轮提交，也未在本文档中当作本轮成果。
- Release：`dist/AuroraShelf-2.15.0-137-loading-hig.apk`，versionCode **137** / versionName 2.15.0；v2 签名、`zipalign -P 16` 通过；最终 SHA-256 `722fbe9789986b5f657abef8a7b8ad2dc21f3e481164db315d303bf751dd565d`（`dist/SHA256SUMS.txt` 已覆盖全部 9 个 APK）。在 Android 14 模拟器安装并冷启动成功（`am start -W` TotalTime 2281 ms），首页标题与内容正常渲染（应用名受上述并发改名影响显示“网灰”）。
- 说明：带窗口的 `test` AVD 在三台模拟器并行压力下出现系统服务不稳定（`activity` 服务短暂不可用），最终安装验证改在稳定的 `missavtest` AVD 完成；窗口模拟器已重启。

## 2.15.0 + 代码优化 / HIG 候选（136）— 2026-10-24

- 代码结构：`AuroraShelfApp.kt` 从 2587 行拆分为根组合（596 行）+ 首页 / 搜索 / 播放器 / 个人中心 / 共用组件 5 个文件；拆分经字节级重组校验（还原后 md5 与拆分前一致），行为、UI 与 testTag 不变。
- 冷启动：漫画与直播改为首次进入对应栏目才加载；Media3 离线缓存初始化移到后台线程（监听回调仍在主线程）。Release 冷启动 `am start -W` TotalTime 1238 ms；Debug 1853 ms（优化前基线未采集，不做提升幅度结论）。
- 健壮性与隐私：离线缓存删除竞态、缓存索引与本地保存逐条容错、联网搜索二次过滤、AVIF/HEIC 封面识别、直播分页终止与后台轮询、论坛图片 Cookie 同源转发、封面失败占位与重试。
- Apple HIG 审计：`docs/ui/hig-audit-2026-10-24.md`。修复 3 个 P1（深色按钮文字对比度 3.15 → 5.45:1；固定黑底直播评论 3.30 → 6.66:1；200% 系统字体下底部浮栏标签溢出）及若干 P2；未达标项（Increase Contrast 变体、深色封面压暗、TalkBack 逐屏实测、外源字幕）在报告第 7 节显式声明。
- JVM：**99 项 / 0 失败 / 1 跳过**（跳过项为 `ForumSourcesTest` 联网用例，默认需 `RUN_LIVE_FORUM_TESTS=1`）。
- Lint：最终代码 `lintDebug` **0 errors / 21 warnings / 3 hints**（warnings 以依赖版本与既有资源建议为主）。
- 设备：`connectedDebugAndroidTest` **26 项 / 0 失败**（2026-10-24 12:36 重跑；首次运行有 1 次 `AuroraFlowTest.feedContinuesBehindFloatingNavigation` 偶发失败，单项与完整重跑均通过）。
- 联网冒烟：经 adb 直接执行 `LiveFeedTest`（`live=true`，黄果）**1/1 通过**，7.77 s，覆盖列表解析、翻页终止与封面 AES-CBC 解密。
- 全源巡检：`PreReleaseSourceAuditTest`（`sourceAudit=true`）两次均在巡检中段被 host 侧 `adb` SIGKILL 中止（非应用 crash，无 crash buffer 记录）。已取得部分结果：默认视频源列表/第二页/播放解析、黄果列表/第二页/播放解析、MissAV 列表 56 项/第二页 0/搜索 50 项/播放解析、KissJAV 列表 30 项/第二页 30 项/搜索/播放解析、爱看漫首章 13 页、拷贝漫画首章 20 页。**16 源全量与 11 源第二页证据本轮未完整重建**。
- Release：`dist/AuroraShelf-2.15.0-136-hig-optimized.apk`，versionCode **136** / versionName 2.15.0；v2 签名验证通过（Android Debug 证书）；`zipalign -c -P 16` 通过；SHA-256 `51c8f5b1027aa909dca58aa11497007f72d085a68e0a131553e622e41d8d11bc`。在 `missavtest`（Android 14 / 1080×2340 / 440）上从 135 覆盖安装到 136 成功，冷启动后首页正常渲染。
- 交付文档：`dist/SHA256SUMS.txt` 已补齐 dist 下全部 8 个 APK 的 SHA-256。

## 2.15.0 Stripchat 原生直播候选

> **勘误（2026-10-24）**：下一行记录的 SHA-256 `f84e1fa8…` 实际对应当时安装在模拟器上的旧构建（09-24 17:58 安装）；`dist/` 中同名文件在 22:05 被重建为 `e8195b4e…`，该新构建从未安装验证，且两个构建 versionCode 同为 135。现行交付包以本页顶部 136 候选为准。

- 直播页为原生 Compose 列表，真实加载 Stripchat 公开目录，女主播、情侣、男主播和跨性别分类可切换；详情页只保留视频、主播信息、原生播放控制和只读公开评论。
- Android 14 / arm64、1080 × 2340、density 440 模拟器实测列表、情侣分类、房间播放、公开评论、暂停、静音、全屏入口、退出全屏、返回详情和返回列表。主播下播或无评论时显示明确状态。
- 62 项 JVM 测试中 61 项通过、1 项联网用例按默认配置跳过；26 项 Android 设备测试全部通过。Android Lint 为 0 errors（12 warnings、2 hints）；R8、资源裁剪与 Release 构建成功。
- 候选包版本为 `135 / 2.15.0`，通过 v2 签名与 16 KB zipalign。黄果封面 CDN 轮换修复已编入最新 Release；文件为 `dist/AuroraShelf-2.15.0-stripchat-live-pre-release.apk`，SHA-256 为 `f84e1fa82cf044f618c6d49de3c0a5d22873cab850317562ba96388a54931ac9`。
- 直播仍依赖源站公开目录、房间 DOM 与实时网络；主播可随时下播。登录、付费、私信、打赏和发言不在接入范围。
- 黄果封面 CDN 轮换修复：强制联网设备测试 `LiveFeedTest` 在 `missavtest` 上 1/1 通过，断言包含当前 CDN 封面 AES-CBC 解密；最终 Release 覆盖安装后，推荐榜 hero 与双列封面在界面中显示正常。

## 2.14.3 上线前修复候选

- 漫画吸顶分类栏移除整块矩形背景和 2 dp 投影，只保留独立圆角分类按钮；横向滚动、点击和吸顶行为保持不变。Android 14 Release 实机画面见 `docs/screenshots/comic-category-floating-2.14.3.png`。
- 55 项 JVM 测试中 54 项通过、1 项联网用例按默认配置跳过；完整联网设备基线 25/25 通过，论坛分页栏和列表返回位置设备测试分别通过。
- 全源巡检确认 16/16 视频来源能取得首屏和可播放样本；11 个扩展来源第二页包含第一页没有的新条目。Komiic、包子、拷贝、E-Hentai、禁漫完成列表、分页、搜索、详情与首章线路。
- Android Lint 为 0 errors（10 warnings、1 hint，均为目标 API、依赖版本和既有资源/性能建议）；R8、资源裁剪和 lintVital Release 构建通过。
- 候选包版本为 `134 / 2.14.3`，通过 v2 签名和 16 KB zipalign，覆盖安装及冷启动成功。文件为 `dist/AuroraShelf-2.14.3-pre-release.apk`，SHA-256 为 `7c8f7a32ab495a79ffbd7f3eb334ca8292b39004a41cde48e4f20e6a8fc1d636`。
- 此包仍标记为 pre-release：爱看漫受当前出口地区/WAF 拒绝，哔咔需合法测试账号，直播尚无指定来源；这些外部门禁未满足前不能宣称所有源、所有功能均已上线验收。

## 2.14.2 论坛阅读器清理与 91 空屏修复

- 在 Android 14 / arm64、1080 × 2340、density 440 模拟器打开两个真实来源：草榴正文、延迟图片、作者/时间信息正常；91 论坛正文、作者/楼层信息和长文本换行正常。
- 实测发现并修复 91 旧页面不规范广告标签导致 `#wrap` 被浏览器嵌入广告容器、清理规则误隐藏整个正文的问题；现在先将论坛主体移回 `body`，再移除页眉、广告、控制栏和回复框。
- 48 项 JVM 单元测试无失败；Android Lint 为 0 errors；R8 与资源裁剪 release 构建成功。
- Release 候选包版本为 `133 / 2.14.2`，通过 v2 签名、16 KB zipalign、覆盖安装和冷启动，SHA-256 为 `c25f8756d6b8d90fd59380700d4bf14ddd1a2a87cedcf449fe0f4fb29f35870c`。

## 2.14.1 论坛阅读器移动端修复

- 48 项 JVM 单元测试无失败（默认跳过 1 项实时网络用例）；单独启用实时用例后，草榴社区与 91 自拍论坛的公开第一页解析通过，帖子标题和 HTTPS 阅读地址有效。
- Android Lint 为 0 errors；R8 与资源裁剪 release 构建成功。候选 APK 版本为 `132 / 2.14.1`，通过 v2 签名与 16 KB zipalign，SHA-256 为 `bd4eddb38f0c2473ec835d5e183b7226197430bceb8ab9fb5c53262fc40a1379`。
- 本轮构建主机没有可用的 Android Emulator 可执行组件，也没有连接真机；因此未执行帖子 WebView 的点击、正文重排、延迟图片和返回流程设备回归，不能把构建与实时列表解析视为阅读页验收通过。
- 阅读器仅允许 HTTPS 页面导航，继续禁用文件访问、内容 URI 与混合内容；页面完成后只短暂启用本地固定布局脚本，脚本回调完成即重新关闭 JavaScript。

## 2.14.0 合并论坛

- 48 项 JVM 单元测试无失败（默认跳过 1 项实时网络用例）；单独启用实时用例后，草榴社区与 91 自拍论坛的公开第一页解析通过，结果同时包含两个来源，标题和 HTTPS 主题地址有效。
- 新增解析、合并排序与单源失败隔离测试；论坛入口 Android 14 设备回归通过，确认原占位页已替换为统一信息流。
- Android 14 模拟器当前系统 DNS 同样无法解析论坛域名和默认视频源，因此未把模拟器远端加载计为通过；真实联网解析由 macOS 构建主机完成。
- 论坛只提供公开页面的匿名只读入口，不包含登录、发帖、回复、验证码处理或权限绕过。
- Android Lint 为 0 errors；R8 与资源裁剪 release 构建成功。版本 `131 / 2.14.0` 通过 v2 签名、16 KB zipalign、覆盖安装和冷启动；SHA-256 为 `878ebfb12ca259ea798df9113bbfddd7ef7d67ac2562f141efa0ce6c8e66b8a7`。

## 2.13.0 导航与“我的”整合

- 43 项 JVM 单元测试通过；Android Lint 无错误；R8 与资源裁剪 release 构建成功。
- 4 项本轮聚焦设备回归通过：直播/论坛/我的入口、收藏二级页、设置二级返回和液态底栏从首页连续拖动至“我的”。
- Android 14 / arm64、1080 × 2340、density 440 模拟器完成日间、夜间与横屏视觉复核；五项底栏文字无裁切，“我的”卡片未被底栏遮挡，横屏五个侧栏图标完整可见。
- 全量 18 项设备用例中 16 项通过；两项旧首页用例因模拟器无法解析默认视频源 `h1014.so148.com`，等待 20 秒后超时。失败发生在远端列表取得前，与本轮导航渲染和交互无关，未作为通过结果处理。
- 视觉记录：`docs/screenshots/navigation-personal-light.png`、`docs/screenshots/navigation-personal-landscape.png`。
- Release APK 为版本 `130 / 2.13.0`，通过 v2 签名与 16 KB zipalign，覆盖安装和冷启动通过；SHA-256 为 `0d1588a9b7be1ee4fda40741c66e11ae4173fbf539aaed7cd4935f88c4d2bd37`。

## 2.12.1 禁漫图片与 E-Hentai 加载修复

- 43 项 JVM 单元测试通过；Android Lint 无错误；R8 与资源裁剪 release 构建成功。
- 禁漫天堂实时链路测试通过；在 Android 14 / arm64、1080 × 2340、density 440 模拟器打开用户截图中的《她們的最愛.ZIP》第 1 话，确认人物与背景连续，原有横向切片错序不再出现。
- E-Hentai 实时列表、详情和图片页解析测试通过；前 10 个封面均解析为 HTTPS 图片并携带公共站 Referer。
- 清除调试应用数据后的界面复核中，E-Hentai 列表约 6 秒出现、封面约 9 秒可见；进入一部当前画廊后，前两页约 10–11 秒可见。该结果受源站、地区网络与设备状态影响，不作为固定性能承诺。
- 图片页解析加入 256 项内存缓存，预览分页并发限制为 4，首批预解析限制为 4，避免无界请求挤占列表和阅读线程。
- Release APK 为版本 `129 / 2.12.1`，通过 v2 签名与 16 KB zipalign，覆盖安装和冷启动通过；SHA-256 为 `8f5ad2ac94a1ee4d0545bd50bc5f7aa0a454617102f06885c40a3b358e082b6f`。

## 2.12.0 多漫画源与设置页优化

- 42 项 JVM 单元测试通过；Android Lint 无错误；R8 与资源裁剪 release 构建成功。
- Android 14 / arm64、1080 × 2340、density 440 模拟器全量 17 项设备测试通过：9 项主流程、1 项禁漫图片重组、3 项实时漫画、1 项实时列表和 3 项实时播放。
- Komiic、E-Hentai 公共站与禁漫天堂的列表、详情和章节实时链路通过；Release 中实际打开禁漫天堂列表、19 章详情和第一章图片成功，另以非整除高度位图验证分片重组没有丢行或越界。
- 设置首页、视频源二级页、漫画来源面板和漫画详情完成两轮日间/夜间截图复核；首轮发现的设置行重叠已修复，非法自定义地址、返回和来源切换设备回归通过。
- 哔咔登录界面在 Release 中打开正常；没有可用于验收的用户账号，因此未提交真实登录。令牌使用 Android Keystore AES-GCM 保存，密码不持久化。
- Release APK 为版本 `128 / 2.12.0`，通过 v2 签名与 16 KB zipalign，覆盖安装和冷启动通过；SHA-256 为 `59f4cf487204043c4b8067521884c9b4813b8b4ff8b359fdfeb3b132c5849436`。

## 2.11.0 漫画模块

- 38 项 JVM 单元测试通过；Android Lint 为 0 个问题；R8 release 构建成功。
- Android 14 / arm64、1080 × 2340、density 440 模拟器全量 14 项设备测试通过，包含漫画底栏、源与分类控件；原视频搜索、收藏、分页、播放全屏和设置流程继续通过。
- Komiic 实时端到端测试获取 24 条列表、124 个章节和首章 193 张图片；Release 冷启动后封面显示、详情、连续阅读、上下章状态和阅读时隐藏底栏均通过。
- 包子漫画桌面网络可取得列表、详情和章节图片，但当前 Android 14 模拟器拒绝其 ZeroSSL 证书链；没有绕过证书校验，默认源改为 Komiic。拷贝漫画在当前网络 DNS 不可达，爱看漫返回 HTTP 403。
- 日间与夜间截图位于 `docs/screenshots/comic-library-light.png`、`comic-library-dark.png`；详情和阅读器截图为 `comic-details-light.png`、`comic-reader-light.png`。
- Release APK 为版本 `127 / 2.11.0`，通过 v2 签名和 16 KB zipalign 检查；SHA-256 为 `5f67ff416dc6721ce1a3dda60b6e5e1a02e37e70d220ce08fcad32dc33c934af`。

## 已完成

- JDK 21 / Gradle 9.6.0 / AGP 9.4.0，debug、Android Lint 和 R8 release 构建成功。
- 32 项 JVM 单元测试通过，包含首页配对逻辑和日间/夜间回退色板的背景、正文亮度断言。
- 模拟器 Android 14 / arm64：11 项设备测试通过；新增用例按实际坐标确认前两张普通卡处于同一水平行且分占左右两列。
- 实时源站用例：默认源获取第一页 **37 条**、第二页 **35 条**且包含新增 ID；黄果获取 **20 条**并成功解密真实封面。源站数量会变化，测试不输出远端标题或地址。
- 2.9.1 release APK 覆盖安装、冷启动和黄果真实双列列表显示成功；日间/夜间切换前后进程 PID 保持一致，运行中的首页与设置页会随系统配置立即更新。
- 多源检查：MissAV 独立解析与其余 MISSPipe 服务注册表测试继续通过；未逐站执行网络播放，源站可用性仍取决于地区、Cloudflare 与站点维护状态。
- 交付 APK 已通过 v2 签名和 16 KB zipalign 检查，版本为 `125 / 2.9.1`，SHA-256 为 `f156ddcb4ffdceb8fe7b39182e14550f25e85cc82120997ebd580567a0036abb`。
- 本轮以同一黄果榜单、同一 1080 × 2340 视口对照日间与夜间：首页、设置、系统栏和播放器检查通过，记录见 `docs/design-audit/system-theme/audit.md`。

最新设备测试结果位于 `build.nosync/app/outputs/androidTest-results/connected/debug/`；解析/校验测试结果位于 `build.nosync/app/test-results/testDebugUnitTest/`。

## 警告与边界

Android Lint 为 0 errors；构建保留 Compose 测试 API 弃用和 Gradle 10 兼容性提示，均未导致编译、测试或安装失败。

以下仍未验收，不能宣称为正式商店发行：

- 用户真机的 120 Hz 长时间滚动、One UI 字体渲染、旋转和多窗口；本轮只在匹配 1080 × 2340 / density 440 的 Android 14 模拟器验证。
- Android 8、不同厂商设备的运行兼容性和性能。
- TalkBack 实机走查、完整颜色对比度审计、长时间滚动帧率/内存/电量基准。
- Stripchat 长时间播放、频繁切房、网络切换、源站 DOM 改版和所有主播状态；本轮仅对当前公开样本完成真实播放与评论验证。
- 站点全量搜索、登录、会员、支付、评论和原版私有数据迁移；这些模块按需求明确排除，离线缓存仍保留。
- 正式发行证书和应用商店发布；当前使用 Android 开发测试证书。

## 用户安装检查

1. 安装 `AuroraShelf-2.15.0-stripchat-live-pre-release.apk`；与上一版包名和测试签名一致，可直接覆盖更新。
2. 底栏第二项应为“漫画”，可从来源面板切换 Komiic、E-Hentai、禁漫天堂等七个来源；首次进入默认选择 Komiic。
3. 进入漫画详情可选择章节；阅读器应连续显示图片、隐藏底栏并提供上下章。系统浅色/深色切换后漫画列表同步主题。
4. 设置首页的视频源和离线缓存应分别进入独立二级页；视频源页支持搜索和自定义地址校验。
5. 成年测试用户进入“直播”后，应看到原生分类列表；打开公开房间，检查播放、暂停、静音、全屏、只读评论和返回。若主播已下播，返回列表刷新后换一个房间。
6. 进入“论坛”后应直接看到两个社区的合并列表，没有论坛源选择器；滑到底部会继续加载。打开两边各一篇帖子，确认底栏隐藏、作者栏不挤压正文、长文本与图片不横向溢出，并分别测试刷新和系统返回。
7. 如遇故障，记录手机型号、Android 版本、来源名称、具体操作和错误提示；不要发送账号密码或敏感内容截图。

这是使用开发证书签名的内部测试候选包，不应作为官方商店发行包。桌面交付文件 SHA-256：`f84e1fa82cf044f618c6d49de3c0a5d22873cab850317562ba96388a54931ac9`。
