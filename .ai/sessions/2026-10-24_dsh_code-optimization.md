# DSH Code Optimization Pass

Date: 2026-10-24 12:30 HKT

## Objective

在不改变功能行为与 UI 视觉的前提下，修复前置审计确认的 P1/P2 正确性、性能、隐私缺陷，清理死代码，补 JVM 回归测试，并完成构建验证。

## Method

1. 只读审计（并行 3 名审计员）：主代码 / 未提交改动 / docs 与 `.ai` 证据链。
2. 建立基线快照：`git status`、`git diff`、`app/src`、`.ai`、`docs` 复制到仓库外临时目录，用于事后区分本轮改动与用户既有未提交工作。
3. 按文件写作用域拆 4 个写任务交给 4 名执行者，Lead 负责 app 层、文档与统一构建验证；文件作用域互不重叠。
4. 统一编译 + 全量 JVM 单测 + Debug 组装；`git diff --check`；与基线做全树 diff 确认无越界改动。

## Changes

### 启动与网络时机

- `ComicViewModel`、`LiveViewModel`：删除 `init { refresh() }`，新增幂等 `ensureLoaded()`。
- `AuroraShelfApp`：COMICS / LIVE 分支 `LaunchedEffect(Unit)` 触发 `ensureLoaded()`；冷启动不再请求默认漫画源与 Stripchat。

### 搜索

- `AuroraViewModel`：新增 `isRemoteSearch`；网络结果 `distinctBy(VideoItem::id)`；`localSearchMatches()` 纯函数。
- `AuroraShelfApp.SearchScreen`：新增 `filterLocally`，网络结果不做二次子串过滤。

### 数据层

- `OfflineCacheManager`：`resolveJobs` 取消 + pending 身份校验 + `ensureActive`；`OfflineMetadataCodec`（失败返回 null）；索引逐条容错；`shouldAcceptResolvedDownload()` 纯谓词。
- `AppPreferences`：`decodeSavedVideos()` 逐条容错，保留 demo-/地址过滤语义。

### 网络与封面

- `data/TransientTransportFailure.kt`：统一 `SSLException`/`EOFException` cause 链判定；替换两处重复实现。
- `CoverImageLoader`：`looksLikeImage` 增加 ISO-BMFF `ftyp` + major brand 识别（AVIF/HEIC/HEIF），改为 internal 供测试。

### 论坛

- `ForumImageSaver.shouldForwardCookies()`：仅同 HTTPS host 转发 Cookie；`ForumScreen` 应用该判定，并为 WebView 增加 ON_PAUSE/ON_RESUME。

### 直播

- `LiveViewModel`：`nextLivePage()` 以新增条目数决定 `canLoadMore`；空页/全重复页终止。
- `LiveScreen`：触底 `snapshotFlow`；播放器探测在 ready/mismatch/超限后退出；探测与评论轮询 `repeatOnLifecycle(STARTED)`；`pageLoadToken` 支持重载后重新探测。

### 其他

- `ComicScreen`：触底 `snapshotFlow`。
- 删除无引用 `FeaturePlaceholderScreen`（`AuroraShelfApp.kt`）与 `WebPlayer.kt`（`SitePlayer`）。
- 新增 34 项 JVM 纯单测（65 → 99）。
- README 三处事实修正；CHANGELOG「未发布」补记。

## Commands and Results

```sh
# JDK 21（JBR 已升级到 25，不能满足 pipepipe Java 21 toolchain）
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
./gradlew testDebugUnitTest --console=plain --no-daemon \
  -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
# BUILD SUCCESSFUL — 99 tests / 0 failures / 1 skipped

JAVA_HOME=... ./gradlew assembleDebug ...
# BUILD SUCCESSFUL (1m12s)

JAVA_HOME=... ./gradlew lintDebug ...
# BUILD SUCCESSFUL — 0 errors / 20 warnings / 3 hints

JAVA_HOME=... ./gradlew connectedDebugAndroidTest ...
# BUILD SUCCESSFUL — 26 tests / 0 failures
# 首次完整运行有 1 次 AuroraFlowTest 偶发失败（仅执行 1 项后中止、无堆栈、crash buffer 为空）；
# 单项重跑与完整重跑均通过，按环境时序抖动记录。

git diff --check   # exit 0
```

## Files Touched

24 个文件 + `.ai`，完整清单见 `.ai/HANDOFF.md` 的 Files Changed。

## Unconfirmed / Follow-up

- 联网冒烟与人工观察未执行（黄果封面 `LiveFeedTest live=true`、冷启动网络行为、直播前后台切换、论坛图片保存）。
- 发布证据链（versionCode 135 的两个构建、SHA 不一致）未处理。
- 未做：`AuroraShelfApp.kt` 拆分、Application 主线程缓存初始化、封面失败占位、直播评论对比度、xHamster 猜测页码。
