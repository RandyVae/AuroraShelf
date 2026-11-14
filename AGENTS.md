# Project

Netgrey · Android & iOS 2.16.29

# Objective

按用户选定的「方案 1」重新实现的 Android 客户端。采用 Kotlin、Jetpack Compose、Material 3、Haze 与 AndroidLiquidGlass/Backdrop；Material You 界面保留真实视频源能力，并以浮动玻璃导航、吸顶分类和弹性选中动效重构视觉层级，不是网页套壳首页。2.9.0 保留首个大幅推荐视频并将后续内容改为 YouTube 式双列信息层级；2.9.1 新增完整日间与夜间主题，默认跟随系统即时切换。当前交付包可从 [GitHub Releases](https://github.com/RandyVae/AuroraShelf/releases/latest) 下载。

# Tech Stack

build.gradle.kts, Kotlin/Android

# Architecture

See the repository source tree and `.ai/DECISIONS.md`; architectural facts are recorded there only when evidenced.

# Repository Structure

app/, build.nosync/, captures/, docs/, gradle/, third_party/

# Development Rules

Preserve existing project conventions. Do not discard uncommitted work or change business code without an explicit task.

# Build

See `.ai/ENVIRONMENT.md`; only record PASS after actually running the command.

# Test

See `.ai/ENVIRONMENT.md`; only record PASS after actually running the command.

# AI Project Memory

Before work, read `.ai/PROJECT_STATE.md`, `.ai/HANDOFF.md`, `.ai/TASKS.md`, `.ai/DECISIONS.md`, and `.ai/ENVIRONMENT.md`.

# Agent Workflow

Check `git status` before editing. Update `.ai` after each meaningful stage. Record architecture changes in `DECISIONS.md`. Never delete tests to bypass errors and never overwrite uncommitted user work.

## iOS 与 Android 双端持续交付、版本发布与远端磁盘防爆规范（核心强制）

- **版本号显式更新（强制）**：完成任何新的实质性功能开发、UI 调整或缺陷修复时，**必须显式递增版本号**（包含代码层语义化版本 `versionName` / `MARKETING_VERSION` 与构建号 `versionCode` / `CURRENT_PROJECT_VERSION`），并同步更新客户端内部版本显示、分发网页（`index.html`）与安装清单（`manifest.plist`），严禁静默覆盖或版本号停滞。
- **Git 远程仓库全量同步与发布（核心强制）**：每次任务完成并验证通过后，**必须立即将所有修改与新增代码创建规范 Commit 并同步推送到 Git 远程仓库（`git push origin <branch>`）**。严禁出现「本地代码已修改或已发布安装包，但未同步推送到 Git 远程仓库」的脱节状态。发布正式版本时，必须同步创建对应版本的 Git 标签（`git tag -a vX.Y.Z -m "..."` 并 `git push origin vX.Y.Z`），并在 GitHub 对应发布 Release 与挂载交付包。
- **双端同权同发**：后续只要完成任何新的实质性功能开发、UI 调整或缺陷修复（不论是 iOS 还是 Android），必须立即执行对应及双端自动化构建与签名打包，并上传发布至 OTA 服务器：
  ```sh
  ./scripts/publish_ota.sh Release
  ```
  确保用户通过 `https://bananarey.us` 网页端下载到的永远是最新编译的完整安装包（`Netgrey.ipa` 与 `Netgrey.apk`）。严禁出现「本地代码已提交或修改但未同步发布给用户」的脱节状态。
- **服务器单版本覆盖与磁盘防爆机制**：
  1. **零历史包堆积**：服务器上严禁保留任何老版本安装包，每次发布必须执行同名原子覆盖（仅保留唯一的 `Netgrey.ipa` 与 `Netgrey.apk`）；
  2. **自动清理残留**：上传发布后必须即刻清理任何远端临时碎片、旧版本归档与备份文件（`*.tmp`、`*.bak`、`*.old` 等），防止版本迭代逐渐挤爆服务器磁盘空间；
  3. **容量水位监控**：发布流程必须包含远端磁盘容量健康核验（`df -h /`），确保可用空间维持在安全水位（>5GB）。

## iPhone 与 iPad 双端同步维护与缺陷修复规范（核心强制）

- **双端同权同质**：本项目是覆盖 iPhone 与 iPad 的 Universal 通用工程。在排查或修复任何缺陷（Bug）、调整业务逻辑或优化视觉体验时，**必须两端同步排查并同时修复**。
- **严禁割裂交付**：严禁出现「iPhone 软件修复好了、iPad 软件仍残留 bug」的脱节状态。
- **排查与修复基线**：
  1. 涉及 UI 布局与导航：同步检查 iPhone（竖屏/浮动胶囊）与 iPad（侧边栏/4~6 列自适应网格/横竖屏全向翻转/大屏黄金画幅）；
  2. 涉及数据源、播放器控制器与核心逻辑：确保核心组件在不同尺寸与设备形态（`DeviceFormFactor`）下均无异常；
  3. 验收标准：必须明确在 iPhone 与 iPad 双端形态下均通过构建与逻辑核验后，方可完成任务并执行 OTA 发布。

## 缺陷修复后全源连通性与 UI 防错位全量核验规范（核心强制）

- **全源连通性全量核验（核心强制）**：
  任何缺陷修复（Bug Fix）、数据源网络策略调整或数据模型重构后，**严禁仅测试当前改动的单个源即草率交付**。必须对工程内支持的全部源进行一次全量连通性与解析可用性检查（涵盖 19 个视频流媒体源、黄果短剧源、漫画源、直播源、社区论坛源、图床 CDN 等）：
  1. 验证 Feed 列表数据解析返回有效项目；
  2. 验证封面与切片图片通过防盗链/代理加载正常解码；
  3. 验证播放直链或切片解析成功且协议有效（无 SSL/TLS 证书握手阻断、无 403 跨域拦截、无协程/Task 自身死锁）；
  4. 可直接执行全源探针自动化测试：`RUN_IOS_LIVE_SOURCE_AUDIT=1 xcodebuild test -project ios/AuroraShelf.xcodeproj -scheme AuroraShelf -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -only-testing:AuroraShelfTests/LiveVideoSourceAuditTests`。
- **UI 多形态防错位与防截断核验（核心强制）**：
  任何缺陷修复或界面调整后，**必须对全链路 UI 进行系统性防错位与防溢出核验**：
  1. 严禁出现布局错位、元素腰斩、文字省略截断（如“...”）、图文重叠、边距塌陷或超出视口等视觉回归问题；
  2. 必须覆盖多端与不同设备视口断点（iPhone 标准窄屏 393pt、Duo 外屏折叠态 466×678pt、内屏展开态 669×951pt、iPad Pro 宽屏与横竖屏翻转）；
  3. 必须核验核心控制组（如 Slots 下注辅助条、播放器悬浮按键、TabBar 胶囊、设置页列表）的光学居中与自适应排布，确保原生质感与零错位呈现。

## 模拟器核验必须与交付包一致规范（核心强制）

- **构建一致性（核心强制）**：
  在模拟器中进行全源连通性检查、UI 走查或功能验收时，**严禁使用独立无混淆的 Debug 内部工程或未签名的临时缓存进行检验**。必须采用**与最终推送给用户、发布到 OTA 服务器与 GitHub Releases 相同构建级别、相同混淆配置（Release 模式、开启 R8/ProGuard/代码优化与签名规则）的正式交付包产物**（或直接将打包生成的 Release APK / 签名 App 安装至模拟器中进行实测走查）。
- **杜绝环境脱节**：
  严禁出现「模拟器跑 Debug 模式显示正常，但推送给用户的 Release 包因 R8 混淆/裁剪、资源优化或配置差异导致源打不开、UI 崩溃或白屏」的脱节状态。模拟器检验结论必须 100% 代表交付给用户的真实运行体验。

## Apple UI / UX 持久规范

所有 UI 任务必须遵循本项目的 Apple HIG 产品设计基线，并完成构建、运行和视觉验收。具体规则：[docs/ui/design-system.md](docs/ui/design-system.md)；验收流程：[docs/ui/visual-qa.md](docs/ui/visual-qa.md)。现有项目规则继续有效；平台差异以目标平台原生规范和当前官方文档为准。
