# Visual QA / UI 验收流程

## 验收门槛
UI 只有同时满足 Functionally Correct、Visually Correct、Consistent、Responsive、Native、Maintainable、Validated 才能标记完成。编译成功、单张截图或源码检查均不足以通过。

## 流程
1. 识别目标平台、设备/窗口类、输入方式、部署版本和代表性用户任务。
2. Build 并 Run；记录真实数据约束和所有空/加载/成功/错误/离线/权限/破坏性状态。
3. 在最小、标准、大窗口或代表性设备上截图；检查 Light/Dark、系统缩放/Dynamic Type、长短文本、本地化、键盘/触控/鼠标和安全区。
4. 逐屏检查并记录问题：对齐、间距、层级、字体大小/粗细/行高、图标语义、组件尺寸、圆角、颜色对比度、Material/Glass/阴影、截断/换行、滚动、Sidebar/Inspector 展开收起、hover/pressed/selected/disabled/focus。
5. 修复根因后重复 Build → Run → Screenshot → Recheck。不要用局部补丁掩盖组件、布局、Design System、状态或导航架构问题。

## 平台检查
- macOS：最小/标准/大窗口、全屏、拖拽缩放、窗口恢复、多窗口、Toolbar、Sidebar、Inspector、菜单与键盘快捷键。
- iOS/iPadOS：安全区、横竖屏、NavigationStack/TabView/Sheet、动态字体、VoiceOver、手势返回。
- Android：Material 3、edge-to-edge、Predictive Back、48dp 触控目标、TalkBack、系统深浅色。
- Web：语义元素、键盘顺序、可见焦点、WCAG 2.1 AA、响应式断点、prefers-color-scheme/reduced-motion、Core Web Vitals 相关布局稳定性。

## 记录格式
每个问题记录：页面/状态、复现配置、现象、根因分类、严重度（阻断/高/中/低）、截图或日志证据、修复与复验结果。未能运行或缺少设备时标记 Blocked，不得宣称已通过。

## 发布前清单
- 信息架构与导航可预测，主操作明确且可恢复。
- 无重叠、截断、错位、异常空白、浮层遮挡和布局跳动。
- 令牌、组件、间距、字体、颜色、图标和状态跨页面一致。
- Light/Dark、缩放、长文本、空/大量数据、错误和权限状态已验证。
- 无自制系统 Toolbar/Sidebar/Button/Glass 替代品；无无理由渐变、阴影、透明卡片或动画。
- 无障碍、隐私、本地化、Reduced Motion 与目标平台原生行为已验证。

