# Apple UI / UX Design System

本项目所有界面以 Apple Human Interface Guidelines（HIG）为首要质量标准，并结合目标平台的原生规范。Apple HIG 与官方 Developer Documentation 是动态真源；本文件是项目实施基线，不替代最新官方文档。

## 适用范围
适用于 SwiftUI、UIKit、AppKit、macOS、iOS、iPadOS、watchOS、visionOS、Android 与 Web。Android 保留 Material 3、Compose、Predictive Back、edge-to-edge、TalkBack 和 48dp 触控目标；Web 保留语义 HTML、键盘操作、WCAG 2.1 AA 与浏览器历史行为。

## 设计原则
Purpose、Clarity、Consistency、Familiarity、Agency、Accessibility、Privacy、Craft。先保证任务完成、可理解、可恢复，再考虑装饰。不得把“能编译/能运行”当作 UI 完成。

## 页面与信息架构
重要页面编码前先记录用户、核心任务、页面结构、信息层级、组件层级、状态（空、加载、成功、错误、离线、权限、破坏性操作）和响应式行为。每个状态有清晰主操作、可逆路径和一致导航。优先使用平台原生 NavigationStack/NavigationSplitView/TabView/Toolbar/Sidebar/List/Table/Sheet/Popover/Menu/Button/Picker 等，不模拟系统控件。

## 视觉令牌
- 字体：系统字体与语义文本样式；支持 Dynamic Type/系统缩放、长文本和本地化。
- 间距：优先 4/8/12/16/20/24/32/40/48/64，避免随机值。
- 颜色：语义颜色、足够对比度、Light/Dark 变体；不要把颜色硬编码到组件。
- 图标：SF Symbols（Apple 平台）或目标平台原生图标；图标需有语义标签。
- 材料与圆角：优先原生 Material；Liquid Glass 仅在目标 SDK 支持时使用原生 API。禁止伪造 blur/opacity/gradient/translucency、嵌套 Glass 或全页面 Glass 化。
- 组件：Button、Input、Card、Sidebar、Toolbar、Navigation、Modal、Popover、List、Table 统一尺寸、状态和可访问性。重复结构出现两次以上应抽象。

## 布局与交互
优先 VStack/HStack/ZStack/Grid/Lazy*、List、ScrollView、safeAreaInset、containerRelativeFrame、layoutPriority、ViewThatFits 与自适应约束。避免硬编码 x/y、offset、position、negative padding 和无解释 magic numbers；谨慎使用 GeometryReader。控件必须支持 hover/pressed/selected/disabled/focus，触控目标符合平台要求，键盘、VoiceOver/TalkBack、Reduced Motion、深色模式和安全区从首版纳入。

## 平台映射
Apple 平台优先 SwiftUI/AppKit/UIKit 原生模式；Android 使用 Compose + Material 3；Web 使用语义 HTML/CSS Grid/Flexbox、原生表单、可见焦点与响应式布局。不得把 Apple 专属控件直接移植到其他平台；迁移的是清晰、克制、可访问的体验原则。

## 交付门槛
按 Screen-by-Screen 开发。每个独立页面完成 Implement → Build → Run → Screenshot → Visual inspection → Fix → 再运行/截图。发现不一致、Glass 错误、导航异常、缩放溢出或状态缺失时先判断是组件、布局、Design System、状态、响应式或导航架构问题，优先修复根因。验收流程见 [visual-qa.md](./visual-qa.md)。

