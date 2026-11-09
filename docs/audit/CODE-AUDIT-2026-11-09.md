# 三端代码审查报告

日期：2026-11-09  
范围：Android、iPhone、iPadOS 主 App 与游戏模块  
方法：静态代码审查、构建/lint 证据、现有测试与近期模拟器运行记录。

## 结论

代码可以构建，但当前工作树包含大量未提交改动，不能把历史构建结果视为当前完整发布证据。三端最大的风险集中在强制解包、容器尺寸来源、WebView 生命周期、异步任务取消、空 catch 和游戏资源错误态。

## 已确认问题

| ID | 严重度 | 平台 | 文件 | 问题 | 影响 |
| --- | --- | --- | --- | --- | --- |
| CODE-01 | P1 | Android | `app/src/main/java/com/aurorashelf/app/ui/CoverPrefetch.kt` | `comic.coverUrl!!` 强制解包 | 旧数据或缺失封面可能导致预取流程崩溃 |
| CODE-02 | P1 | Android | `app/src/main/java/com/aurorashelf/app/data/comic/IkmmhComicSource.kt` | `chapterArray!!` 强制解包 | 来源返回缺字段时可能直接崩溃，且属于网络输入边界 |
| CODE-03 | P1 | Android | `app/src/main/java/com/aurorashelf/app/game/GameAssetServer.kt` | `serverSocket!!` 在启动/停止竞态中被强制使用 | 资产服务器异常或重复启动时可能崩溃游戏 Activity |
| CODE-04 | P1 | iPadOS | `ios/AuroraShelf/UI/Screens/WatchlistScreen.swift`、`LiveScreen.swift`、`ComicsScreen.swift`、`ForumScreen.swift`、`HeroBanner.swift`、`CategoryShelfView.swift` 等 | 多处用 `UIScreen.main.bounds` 代替当前容器尺寸 | Split View、Slide Over、Stage Manager 下布局可能选错尺寸阶梯 |
| CODE-05 | P1 | 三端游戏 | `app.js`、`battle.js`、`slots.js`、`vfx.js` | 大量延迟任务和 RAF 与页面生命周期分散管理 | 中断、快速退出、恢复后可能残留动画、音频、自动旋转或回调 |
| CODE-06 | P1 | iOS | `GameContainerView.swift` | WKWebView 游戏桥接已接入，但原生 SwiftUI 消失/后台路径没有与 JS destroy 协议形成同等闭环 | 返回、后台、系统回收后游戏资源和状态可能残留 |
| CODE-07 | P2 | iOS | `PosterCard.swift` | `catch {}` 吞掉片单持久化错误 | 用户无法知道写入失败，数据问题不可诊断 |
| CODE-08 | P2 | Android | `GameActivity.kt` | `textZoom = 100` 固定字体缩放 | 系统字体放大无法传递到游戏，辅助阅读和长文案可能失败 |
| CODE-09 | P2 | iOS | `LiveDetailView.swift`、多个播放器/直播页面 | `DispatchQueue.main.asyncAfter` 与 Task 分散，部分路径依赖视图仍存活 | 页面离开后可能更新已失效状态或重复显示提示 |
| CODE-10 | P2 | 三端 | Web 游戏动态 `innerHTML` 模板 | 数据和视图层边界不统一 | 资源损坏、长文本或未来远程字段进入时可能破坏结构，调试困难 |
| CODE-11 | P2 | iPadOS | `DeviceLayoutProfile.swift` 及其调用方 | compact iPad 已加入降级，但 regular Split View 仍有调用方使用物理屏幕尺寸 | 半屏宽度下仍可能出现过大的卡片或错误列数 |
| CODE-12 | P3 | iOS | 多个 SwiftUI 视图仍使用 `UIScreen.main` | Xcode 已报告 iOS 26 deprecated warning | 未来 SDK 行为变化时布局回归风险增加 |

## 平台结论

### Android

- 业务数据层整体使用协程和仓库抽象，主要风险是来源解析、强制解包和 WebView 游戏容器边界。
- 游戏 Activity 的方向已改为跟随窗口，需补旋转、后台、低内存重建和返回手势测试。
- Release lint 已通过，但 lint 不能覆盖网络字段缺失和 WebView 运行时崩溃。

### iPhone

- SwiftUI 导航和原生控制组件较完整。
- 播放器、直播和游戏中存在多个延迟任务，视图消失后的取消策略需要统一。
- WKWebView 相关组件有 dismantle 路径，但游戏容器需要补原生生命周期调用验证。

### iPadOS

- `.sidebarAdaptable` 与宽屏货架方向正确。
- `UIScreen.main` 仍是最明显的 iPad 适配缺陷来源。
- 当前不能把 Apple TV 视觉或交互称为 1:1，因为缺少目标标注、窗口矩阵和焦点测试。

## 已有验证

- Android `lintVitalRelease`：通过。
- Android Release 构建：通过。
- iOS iPad Simulator Debug 构建：通过。
- Slot 固定种子逻辑回归：通过。

## 建议修复顺序

1. 消除 Android 强制解包并为网络字段缺失补测试。
2. 为游戏和播放器建立统一的 `pause/resume/destroy` 协议，原生生命周期和 SwiftUI `onDisappear` 同步调用。
3. 用容器几何尺寸替代 iPad UI 的 `UIScreen.main`，先覆盖 Home、货架、Top10、Watchlist、直播和漫画。
4. 将空 catch 改为可观察错误或最小日志。
5. 补充四端设备矩阵和 iPad 分屏/Stage Manager 截图，之后再做像素级差异判断。
