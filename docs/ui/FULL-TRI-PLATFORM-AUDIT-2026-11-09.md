# Netgrey 三端 UI / UX / 代码全量复审

日期：2026-11-09  
范围：Android 手机、Android 平板、iPhone、iPadOS 主 App 与游戏模块  
目标：以 Apple TV 的 iPad 信息层级和导航原则为参考，检查视觉、交互和代码实现。

## 结论

项目的 iPad 主导航已经使用 SwiftUI `.sidebarAdaptable`，Home 也具备 Hero、Up Next、频道和分类货架的流媒体信息架构。播放器已实现 iPad 右侧选集抽屉、宽幅播放和 hover 反馈。当前不能宣称“Apple TV 1:1 像素一致”，因为仓库没有 Apple TV 对照截图、设计稿或同一内容数据集，且现有 UI 使用 Netgrey 自有颜色、品牌和信息结构。

本轮确认的主要问题是窗口尺寸来源、固定尺寸、三端承载差异、游戏层与主 App 的视觉割裂、iPad 分屏下的布局推断，以及真实设备证据不足。

## 已确认问题

| ID | 优先级 | 平台 | 问题 | 代码证据 | 影响 |
| --- | --- | --- | --- | --- | --- |
| UI-01 | P1 | iPadOS | 多个组件用 `UIScreen.main.bounds` 计算卡片和网格，忽略当前 Split View/Stage Manager 窗口尺寸 | `HeroBanner.swift`、`CategoryShelfView.swift`、`CategoryDetailScreen.swift`、`LiveScreen.swift`、`ComicsScreen.swift`、`ForumScreen.swift`、`Top10Card.swift`、`WatchlistScreen.swift`、`VideoDetailSheet.swift` | 分屏时可能仍按整屏尺寸计算，造成海报过小、列数过多或内容溢出 |
| UI-02 | P1 | iPadOS | `DeviceLayout.resolve` 虽支持宽度断点，但调用者传入的常常是物理屏幕尺寸而非容器尺寸 | `DeviceLayoutProfile.swift` 与上述组件组合 | Apple TV 式响应式布局在窗口变化时不稳定 |
| UI-03 | P1 | iPadOS | 游戏 Web 容器与主 App 侧边栏体系分离，游戏仍使用自定义底部导航和 Web CSS | `GameContainerView.swift`、`app.css` | 游戏进入后无法保持主 App 的 Apple TV 式导航连续性 |
| UI-04 | P1 | 三端 | Android、iPhone、iPad 共享游戏 Web 层，但原生返回、方向、生命周期和安全区由不同容器处理 | `GameActivity.kt`、`GameContainerView.swift`、`app.js` | 相同流程在三端可能产生不同的中断、恢复和遮挡行为 |
| UI-05 | P1 | iPadOS | Home 的宽屏 Hero/货架缺少真实 iPad 分屏和 Stage Manager 截图证据 | `HomeScreen.swift`、`HeroBanner.swift`、`CategoryShelfView.swift` | 不能证明 Apple TV 式大画幅和横向货架在全部窗口状态下成立 |
| UI-06 | P2 | iPadOS | 多处固定宽高仍用于详情、预览、播放抽屉和直播卡片 | 例如 `NativePlayerView.swift` 的 360pt 抽屉、`PosterCard.swift` 的 220×330 预览、直播与详情的固定卡片 | 小窗口或大字体状态可能出现截断和比例失衡 |
| UI-07 | P2 | 三端 | 游戏 Canvas/Spine 内容缺少完整系统可读语义 | Web 游戏动态 DOM 和 Canvas | VoiceOver/TalkBack 无法可靠读取战斗状态、Slot 结果和奖励 |
| UI-08 | P2 | iPadOS | iPad Pointer/hover 已覆盖主要海报和按钮，但未证明焦点环、键盘导航和外接指针与侧边栏选中状态一致 | 多数组件有 `hoverEffect`，缺少端到端输入证据 | Apple TV 式连续焦点体验未关闭 |
| UI-09 | P2 | 三端 | 游戏仍有大量延迟任务、动画帧和字符串模板 DOM 重绘 | `app.js`、`battle.js`、`slots.js`、`vfx.js` | 页面离开、中断、快速点击和自动旋转时存在状态残留风险 |
| UI-10 | P3 | iPhone/Android | 游戏和主 App 的顶部控制、底部导航、品牌色和字体 token 不完全一致 | 原生容器与 Web CSS 分开维护 | 一体化程度低于 Apple TV 的统一系统 Chrome |

## Apple TV / iPadOS 对齐评估

已满足的方向：

- 通过 `.sidebarAdaptable` 提供 iPad 侧边栏与紧凑 Tab 形态。
- Home 使用 Hero、Up Next、频道和分类货架的流媒体层级。
- 海报卡片支持宽屏尺寸阶梯、hover lift 和上下文详情。
- 播放器在 iPad 使用宽幅内容与右侧选集抽屉。
- 主要按钮和可操作卡片具备 44pt 以上触控目标。

未达到像素级一致的原因：

- 没有 Apple TV iPad 目标截图、标注尺寸、字体、内容顺序和状态清单。
- Apple TV 的内容、品牌和素材不能直接复制到 Netgrey。
- 当前组件仍有 `UIScreen.main` 和固定尺寸依赖，无法保证分屏窗口一致。
- 没有 iPad 横屏、竖屏、1/2 Split View、1/3 Split View、Stage Manager 和 Dynamic Type 截图矩阵。

## 三端功能与交互缺陷

### Android

- 已改为跟随窗口方向，但需要验证手势返回、系统返回、后台恢复、锁屏、低内存重建和 WebView 销毁。
- `textZoom=100` 会阻断系统字体放大传递到游戏 Web 层。
- Release WebView 控制台错误没有用户可见诊断通道。

### iPhone

- 原生游戏控制胶囊覆盖 WKWebView，需要验证刘海、安全区、动态字体和边缘返回手势。
- Home 和详情组件大量依赖屏幕尺寸，横屏和窄窗口需要运行截图。
- 播放器手势、画中画、音量/亮度调节需要在真实设备确认。

### iPadOS

- 主导航架构方向正确，但内容布局尚未全面改为基于容器几何尺寸。
- iPad 游戏已解除 560px 宽度限制，但与主 App 的 Apple TV 侧边栏和焦点模型仍然分离。
- 详情、直播、漫画、论坛和播放抽屉需要用同一套窗口宽度输入重新校准。

## 本轮验证

- Android `./gradlew lintVitalRelease --no-daemon`：通过。
- Android Release 构建：此前通过。
- iOS iPad Pro 11-inch Simulator Debug 构建：此前通过。
- iPad 游戏截图：已取得，确认宽屏游戏可加载，但曾出现一次冷启动白屏，资源加载错误态仍需补测。
- 主 App 干净 Home 截图：未完成，模拟器被持久化游戏路由占用。

## 后续修复顺序

1. 将 UI 尺寸计算改为容器宽度优先，移除业务组件对 `UIScreen.main.bounds` 的依赖。
2. 为 iPad 建立 Home、分类、详情、播放器、搜索、直播、漫画、论坛和个人页的截图基线。
3. 统一 iPad 主 App 与游戏容器的导航、返回、焦点、顶部控制和安全区策略。
4. 补充 VoiceOver、TalkBack、键盘、指针、动态字体、减少动态效果和分屏测试。
5. 只有取得目标对照稿和逐状态截图后，才能作出像素级差异结论。
