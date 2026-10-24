# Project

极光视界 · Android 2.15.0

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

## Apple UI / UX 持久规范

所有 UI 任务必须遵循本项目的 Apple HIG 产品设计基线，并完成构建、运行和视觉验收。具体规则：[docs/ui/design-system.md](docs/ui/design-system.md)；验收流程：[docs/ui/visual-qa.md](docs/ui/visual-qa.md)。现有项目规则继续有效；平台差异以目标平台原生规范和当前官方文档为准。
