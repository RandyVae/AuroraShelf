# Project

Netgrey · Android & iOS 2.16.1-dev

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

## Apple UI / UX 持久规范

所有 UI 任务必须遵循本项目的 Apple HIG 产品设计基线，并完成构建、运行和视觉验收。具体规则：[docs/ui/design-system.md](docs/ui/design-system.md)；验收流程：[docs/ui/visual-qa.md](docs/ui/visual-qa.md)。现有项目规则继续有效；平台差异以目标平台原生规范和当前官方文档为准。
