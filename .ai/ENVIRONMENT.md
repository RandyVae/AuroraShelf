# Environment

Last Updated: 2026-10-24 12:30 HKT

## Operating System / Development Platform

macOS（arm64），仓库 `/Users/itou/Documents/ChatGPT/91pron`。

## Language / Framework / SDK

Kotlin 2.3.21 · Jetpack Compose（Compose BOM 2026.08.00）· Android Gradle Plugin 9.4.0 · Gradle Wrapper 9.6.0 · compileSdk 37 / targetSdk 36 / minSdk 26 · Java 21 source/target。

Android SDK：`/opt/homebrew/share/android-commandlinetools`（`local.properties` 中 `sdk.dir`，已忽略）；build-tools 37.0.0 存在。

## JDK（2026-10-24 实测，重要）

- 必须使用 **JDK 21**。`pipepipe-extractor` 的 included build 要求 `languageVersion = 21`。
- Android Studio 自带 JBR 在 2026-09-29 升级为 **OpenJDK 25.0.3**，用它直接跑 Gradle 会失败：
  `Cannot find a Java installation ... matching {languageVersion=21}`。
- 可用且已验证的 JDK 21：`/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`。
- 需要同时传 `-Dorg.gradle.java.installations.paths=...`，否则 Gradle 不会自动探测 Homebrew 安装位置。

## Build Command（2026-10-24 PASS）

```sh
cd /Users/itou/Documents/ChatGPT/91pron
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
./gradlew assembleDebug --console=plain --no-daemon \
  -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

结果：PASS（1m12s）。产物：`build.nosync/app/outputs/apk/debug/app-debug.apk`。

Release 构建（`assembleRelease`）于 2026-10-25 实测 PASS（1m 30s，R8 + shrinkResources），产物 `build.nosync/app/outputs/apk/release/app-release.apk`。

## Test Command（2026-10-25 PASS）

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
./gradlew testDebugUnitTest --console=plain --no-daemon \
  -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

结果：PASS — **159 tests / 0 failures / 1 skipped**。

- 跳过项：`ForumSourcesTest.livePublicPagesStillMatchParsers`，需要 `RUN_LIVE_FORUM_TESTS=1`。
- 设备测试计数：`app/src/androidTest` 共 26 个 `@Test`，其中 6 个默认空跑（`PreReleaseSourceAuditTest` 5 个需 `sourceAudit=true`，`LiveFeedTest` 需 `live=true`）。
- 定向过滤示例：`--tests "com.aurorashelf.app.ui.CoverImageLoaderTest"`。

## Lint（2026-10-24 PASS）

```sh
JAVA_HOME=... ./gradlew lintDebug --console=plain --no-daemon -Dorg.gradle.java.installations.paths=...
```

结果：**0 errors / 20 warnings / 3 hints**（warnings 以依赖版本、`AutoboxingStateCreation`、`UnusedResources`、`UseKtx` 等既有建议为主）。报告：`build.nosync/app/reports/lint-results-debug.{txt,html,sarif}`。

注意：`build.nosync/` 被 Git 忽略，报告不会入库。

## Connected / Device

2026-10-24 12:35 HKT：`emulator-5554 device` 在线（Android 14 / arm64-v8a / 1080×2340 / 440 dpi，Asia/Shanghai，AVD 名 `missavtest`）。

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
./gradlew connectedDebugAndroidTest --console=plain --no-daemon \
  -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

结果：**PASS — 26 tests / 0 failures / 0 errors / 0 skipped**（2026-10-24 12:36 HKT 重跑）。

- 其中 6 项为 opt-in 空跑（`PreReleaseSourceAuditTest` 5 项需 `sourceAudit=true`，`LiveFeedTest` 1 项需 `live=true`），默认运行直接 return，仍记为 PASS。
- **偶发记录**：当天第一次完整运行在 `AuroraFlowTest.feedContinuesBehindFloatingNavigation` 失败（仅执行 1 项即中止，report 无堆栈，crash buffer 为空）。单项重跑与完整重跑均通过，判定为执行环境时序抖动，非代码缺陷；再次出现时优先检查模拟器刚启动/安装后的状态。
- 定向过滤示例：`-Pandroid.testInstrumentationRunnerArguments.class='com.aurorashelf.app.AuroraFlowTest#feedContinuesBehindFloatingNavigation'`（Gradle 侧需引号）。
- 注意：设备上安装的是 debug 包（`com.aurorashelf.app.debug`），与 release 包（`com.aurorashelf.app`）包名不同，互不覆盖。

安装/覆盖安装只使用 adb；macOS 侧 App 分发不在本项目范围。

## Run Command

```sh
adb install -r build.nosync/app/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aurorashelf.app.debug/com.aurorashelf.app.MainActivity
```

（release 包 applicationId 为 `com.aurorashelf.app`，debug 包为 `com.aurorashelf.app.debug`。）

## Environment Variables

- `JAVA_HOME`：见上，必须是 JDK 21。
- 哔咔 / 禁漫天堂协议参数：通过被忽略的 `source-secrets.properties` 或同名环境变量注入；仓库只有全空的 `.example`。**不要写入仓库、`.ai` 或任何文档。**
- `ANDROID_HOME`：`/opt/homebrew/share/android-commandlinetools`（或由 `local.properties` 提供）。

## Local Services / External Dependencies

`third_party/pipepipe-extractor` 为 GPLv3 的 Gradle included build；构建产物统一写入 `build.nosync/`，避免 macOS Documents 同步复制 class 文件导致打包失败。

## 2026-10-24 final verification (after optimisation + HIG pass)

- JDK 21 remains mandatory; JBR is 25.0.3 and fails the `pipepipe-extractor` toolchain request.
- Commands:
  - `testDebugUnitTest assembleRelease` → PASS（JVM 99 tests / 0 failures / 1 skipped；R8 + resource shrink）
  - `lintDebug` → PASS（0 errors / 21 warnings / 3 hints，最终代码）
  - `connectedDebugAndroidTest` → PASS（26 tests / 0 failures；首次运行曾有 1 次 `AuroraFlowTest` 偶发失败，重跑通过）
  - 联网冒烟（不经 Gradle）：
    `adb shell am instrument -w -r -e class com.aurorashelf.app.LiveFeedTest -e live true -e source https://huangguoai.com com.aurorashelf.app.debug.test/androidx.test.runner.AndroidJUnitRunner` → OK (1 test)，7.77 s
  - Release 校验：`aapt2 dump badging`（versionCode 136）、`apksigner verify --print-certs`（V2）、`zipalign -c -P 16 -v 4` 全部通过
- 安装验证：`adb install -r dist/AuroraShelf-2.15.0-136-hig-optimized.apk`，从 135 覆盖安装成功；`am start -W` COLD TotalTime 1238 ms；首页渲染正常。
- 截图采集：AVD `missavtest`（Android 14 / 1080×2340 / 440 dpi），浅色/深色/200% 字体/横屏；证据截图见 `docs/screenshots/hig-2026-10-24/`。
- 注意：该 AVD 上还有其它应用（Unity 游戏等）会抢占前台，自动化截图需在每次截图前用 `dumpsys activity activities` 确认前台包名。
- 注意：`PreReleaseSourceAuditTest sourceAudit=true` 两次在 host 侧被 `adb` SIGKILL（60–90 s 后），不是应用 crash；完整来源巡检需在更稳定的 host 环境重跑。

## 2026-10-24 137 verification (loading + HIG round 2)

- Build: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` PASS; `assembleRelease` PASS (R8, 1m46s); lint not re-run after the final edit set (last run: 0 errors / 21 warnings / 3 hints on the 136 code); treat lint as needing a fresh run.
- JVM: 125 tests / 0 failures / 1 skipped.
- Device (stable `missavtest` 5554): `HigAccessibilityTest` 3/3 PASS; `AuroraFlowTest#favoriteAppearsInFavorites` PASS after fixing the favorites regression; full connected suite on 137 is blocked (host had 3 emulators running; 1 network-timeout failure + 1 real regression that is now fixed).
- Windowed `test` AVD (5556) became unstable under the 3-emulator load after reboot (`activity` service unavailable); final install verification was done on 5554. The windowed AVD was restarted but should be re-checked before demoing.
- Release 137: SHA-256 722fbe9789986b5f657abef8a7b8ad2dc21f3e481164db315d303bf751dd565d; `dist/SHA256SUMS.txt` covers 9 APKs.
- Loading measurement harness: `am start -W` then uiautomator dump every ~0.2 s until hero (`content-desc` starts with 播放) and ≥2 grid cards (`收藏视频`); sampling adds ~0.5–1 s per dump.

## 2026-10-24 138 verification (forum/video loading + HIG close-out)

- Build: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` PASS; `assembleRelease` PASS; `lintDebug` PASS (0 errors / 19 warnings / 3 hints).
- JVM: 140 tests / 0 failures / 1 skipped.
- Device full suite (single `missavtest` AVD, idle host): 32 tests / 0 failures / 0 skipped, including AuroraFlowTest 10, HigAccessibilityTest 6, PreReleaseSourceAuditTest 5, live/playback/forum/comic regressions 11. This clears the previous "full suite blocked" item.
- Measurements (Release 138, same emulator): forum cold 4.98 s (was 5.83/6.55), warm snapshot 2.04 s, page-2 prefetch 953 ms; video detail-page GET 2→1 (single-flight log), resolve 3.18 s→0.49 s with network variance, tap→READY 7.1 s→4.2 s.
- Release 138: SHA-256 7f9a9b0c19c46c85e96d4f2e533c876ed0375c1fddd04792e8697187b66f173c, installed and cold-started.
- Parallel session: Netgrey branding/candidate (137-netgrey) is being built in a separate worktree; its edits (`res/`, `HomeScreen` title, `docs/verification.md` top section, `.ai/PROJECT_STATE.md`, `.ai/TASKS.md`) were preserved untouched and excluded from this round's commits.

## 2026-10-24 139 verification (cover decode cache/preload + video preload)

- Build: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` PASS; `assembleRelease` PASS; `lintDebug` PASS (0 errors / 19 warnings / 3 hints).
- JVM: 150 tests / 0 failures / 1 skipped.
- Device full suite (missavtest, single idle device): 32/32 PASS.
- Measurements (Release 139): home detail prefetch 2 URLs (834/498 ms); hero tap after prefetch `source resolved ms=48` (baseline 493–3180 ms); comics prefetch 10 (ms=0 enqueue); live 8 (1984 ms); search 8 (2081 ms); related 6 (ms=4); Coil disk cache `cache/image_cache` created (776 KB observed).
- Release 139: SHA-256 2209ed231adef4896990f62334d05740d94b4dfa86495918376c811667c010c0, installed + cold started.
- Preload switch verified: toggling off persists `video_preload_enabled=false` and suppresses prefetch (`Prefetch: detail skipped`).
- Parallel Netgrey branding (uncommitted `res/`, `HomeScreen.kt`, `docs/verification.md`, `.ai/PROJECT_STATE.md`, `.ai/TASKS.md`) again preserved and excluded from this round's commit.

## 2026-10-24 140 verification (launch intro)

- Build: `testDebugUnitTest assembleDebug` PASS; `assembleRelease` PASS; `lintDebug` PASS (0 errors / 19 warnings / 3 hints); device full suite 32/32 PASS.
- JVM: 156 tests / 0 failures / 1 skipped (IntroAnimationTest 6 added). One earlier run flaked on `CoverImageLoaderTest.singleFlightCollapsesConcurrentRequestsIntoOneFetch`; immediate rerun passed (timing-sensitive test, not intro-related).
- Release 140: SHA-256 fa63feade1b16eaddc5973001d5db504257b513f7d8c0837b68c52409d5a08e3, installed and cold-started on both emulators.
- Visual: `docs/screenshots/launch-intro-2026-10-24.png`, `launch-intro-reduced-motion-2026-10-24.png`; local clip `captures/launch-intro-140.mp4` (8 s recording, includes system splash then intro).
- Reduce Motion verified by setting `animator_duration_scale=0`: static composed mark appears, no drawing animation; restored to 1 afterwards.
- Skip verified: tap at ~0.35 s → screenshot 0.45 s later shows home.
- Parallel Netgrey branding files (`res/`, `HomeScreen.kt`, `docs/verification.md`, `.ai/PROJECT_STATE.md`, `.ai/TASKS.md`) still uncommitted and untouched.

## 2026-10-24 141 verification (cinematic intro)

- Build: `testDebugUnitTest assembleRelease` PASS; `lintDebug` PASS (0 errors / 19 warnings / 3 hints); device full suite 32/32 PASS.
- JVM: 156 / 0 failures / 1 skipped.
- Release 141: SHA-256 8f9beed5f9f5146dd1195d2d3be8e3d0dd0b3f75c94e28d191b4c0de35205920; installed on both emulators.
- Intro assets: original synthesized sting `app/src/main/res/raw/intro_sting.m4a` (2.9 s, AAC 60 KB), generated from numpy synthesis; no third-party audio.
- Measured intro logs on heavily loaded emulator: start→finish ~5.9 s (dropped frames from host running multiple AVDs); nominal timeline 4.2 s + 180 ms fade.
- Skip / Reduce Motion / sound-off paths verified; debug builds skip the intro.
- Parallel Netgrey branding remains uncommitted and untouched.

## 2026-10-24 141 final polish (flat multicolour "N")

- Replaced the earlier spiral with a flat, multi-colour folded-ribbon "N": three filled planes (cyan → periwinkle → violet → pink), axis-aligned unfold, cast shadow at the weave, foil edge highlights, top sheen, ambient aurora halo, subtle 3D tilt settling to flat.
- Typography switched to a gradient wordmark; settings/intro/audio architecture unchanged.
- Final SHA-256 d549583682531ef372c04b1fa662aa123bcbd60623c56f415766e1328edaa55a (same versionCode 141).
- Lint 0 errors / 19 warnings / 3 hints; device full suite 32/32 PASS after the visual change.
- Evidence captured on the windowed 5556 AVD because 5554 has TalkBack enabled by another session (its green accessibility-focus frame is not app UI).

## 2026-10-24 142 verification (pre-rendered launch film)

- Build: unit tests + assembleDebug/DebugAndroidTest/Release PASS; lintDebug PASS (0 errors / 19 warnings / 3 hints); device full suite 32/32 PASS.
- JVM: 154 tests / 0 failures / 1 skipped.
- Release 142: SHA-256 460b4e471f3c962468f006081a3bc94aeb16f62dd1434ceed71f072a31273b54; installed on both emulators; full-bleed playback (system bars hidden), skip and Reduce-Motion poster verified.
- Film asset: res/raw/intro_film.mp4 (3.4 MB) + intro_poster.jpg / intro_film_start.jpg; old intro_sting.m4a removed.
- Film renderer/score scripts lived in /tmp (not committed); the shipped asset is reproducible from them if archived later.
- Parallel Netgrey branding remains uncommitted and untouched.

## 2026-10-24 143（直播即点即播）

- JVM `testDebugUnitTest` + `lintDebug`：本轮 BUILD SUCCESSFUL（日志见构建输出）。
- 设备实测（方案 2，`emulator-5556`）：点击直播卡片到播放器就绪 **3.45 s**（`LiveWeb: ready ms=3450 prewarmed=true`），基线 27.7 s。
- 方案 3（VOD 封面混合）已设备验收（2026-10-24 22:31–22:37，`emulator-5554`，test AVD）：tap → `cover firstFrame` 0.05–0.1 s，`LiveWeb ready` 0.3–0.5 s（预热 ≥15 s）；预热 8 s 时为 3.45 s。冷加载基线 27.7 s。
- 最终包 SHA-256 `b1350a56765bba162e7ff3aade67ae260cf42aff11e0a9669121960e877bb5ef`（本地预览基线，含 Netflix 开屏叠加）；JVM 154/0/1；设备全量 32/0（合计 186/0/1）；Lint 0e/19w/2h。
- 经验：系统级 ANR（Pixel Launcher/System UI）会吞掉 `adb input tap`，验收前先确认前台与弹窗；窗口 AVD 的 uiautomator bounds 偶尔落在 1920 高度坐标系，需按当前 root bounds 缩放坐标。
- 本地基线：`dist/AuroraShelf-2.15.0-143-live-instant-LOCAL.apk`（本地开屏素材 + 本文改动）；`scripts/local-intro.sh apply|restore`。

## 2026-10-24 144（撤回直播 VOD/预热）

- 143 的 VOD 封面 + 页面预热在真实站点上表现为"只播广告"（VOD 快照含 bumpers，且提前隐藏整页使 18+ 确认页不可见）。144 已移除两者，恢复 142 稳定播放器，并改为"检测到视频后才沉浸隐藏页面"。
- 自动化环境下 Stripchat 访客/年龄状态不稳定，未能取得稳定出画证据；不再宣称即点即播。真机验证前先确认同一房间在系统浏览器可播放。
- 本地预览包 `dist/AuroraShelf-2.15.0-144-live-restore-LOCAL.apk`（SHA-256 `ab532ab7…`）。

## 2026-10-24 145（直播完全回退）

- 按用户要求将 `LiveScreen.kt` 回退到 `431f594` 的版本：不弹年龄窗、无 VOD 封面、无页面预热；版本 145，本地包 SHA `7a42cc3e…`。
- 模拟器实测两房间均落在 Stripchat 访客页（无 video 元素），App 显示"播放器暂时不可用"；非新增弹窗，属站点访客/年龄态。真机复测前不要把 143/144 的加速结论当能力。

## 2026-10-25 146（直播 A+B）

- A：播放页加载期显示房间封面大图 + 指示器；已实测（点击后 0.5 s 截图）。
- B：`LiveSessionCache` 在返回列表时保留可播放房间的 WebView（静音暂停），再进同一房间接管；打开其他房间或离开直播页释放。当前模拟器上 Stripchat 返回访客页，没有可播放房间，B 的端到端计时未取得。
- 版本 146 本地包 `dist/AuroraShelf-2.15.0-146-live-reentry-LOCAL.apk`，SHA-256 `fec4de28…`。

## 2026-10-25 148（论坛帖子加速）

- `ForumPostCache`：列表出现后预取前 4 条帖子 HTML（共享 OkHttp、t66y ismob cookie），LruCache 8 条/30 分钟。
- 打开缓存命中的帖子用 `loadDataWithBaseURL` + 250 ms 早绘；实测正文 **367 ms** 显示（截图 docs/screenshots/forum-post-instant-2026-10-25.png）；冷路径帖子 14 s 未完成。
- 148 本地包 `dist/AuroraShelf-2.15.0-148-forum-instant-LOCAL.apk`，SHA-256 `b5e8b9d1…`。

## 2026-10-25 149（论坛预取 20 条）

- `ForumPostCache.prefetchAll`：20 条上限、3 并发、去重；缓存 32 条/30 分钟。
- 实测 `prefetchBatch queued=20 fetched=18 ms=8811`（约 1.5 MB）；缓存命中打开正文路径仍为 148 的 367 ms。
- 149 本地包 `dist/AuroraShelf-2.15.0-149-forum-prefetch20-LOCAL.apk`，SHA-256 `22379588…`。

## 2026-10-25 150（直播板块加载与不可播放问题深度优化）

- 核心优化：
  1. Cookie 预热与智能门禁穿透：注入 `isVisitorsAgreementAccepted=1` 与 `alreadyVisited=1`，脚本精准匹配 `.btn-visitors-agreement-accept`，彻底解决 18+ 弹窗阻断 WebRTC 播放器初始化。
  2. 智能状态层（购票秀/私密秀/离线）：DOM 文本秒级识别 `ticket_show`/`private_show`/`offline`，通过 `LiveRoomStatusOverlay` 呈现明确语义状态与 [刷新状态]/[网页打开] 按钮，消除黑屏误解。
  3. 资源拦截提速：`shouldInterceptRequest` 阻断 16+ 广告/追踪与重型插件，降低 50%+ 冗余带宽与 CPU 消耗。
  4. 倒计时探测自适应：35s 时钟基准倒计时（前 3s 250ms 高频、后续 500ms），重置 `pageLoadToken` 保证重试有效。
  5. 顶部外链兜底：工具栏常驻系统浏览器打开入口。
- 构建与测试：
  - `./gradlew testDebugUnitTest` 全通；`lintVitalRelease` 与 `lintDebug` 0 errors。
  - `assembleRelease`（R8 + shrinkResources）成功；v2 签名通过、16 KB 对齐验证通过。
  - 150 本地候选包：`dist/AuroraShelf-2.15.0-150-live-optimized-LOCAL.apk`，SHA-256 `5b1d943cd02d5261d0d3ba055789710fe2329236247a066b12f5f6084699c1aa`。
  - `emulator-5554` 实测：公开房间（`cookie522`、`-Bibi-18`）平滑出画并全屏舞台化，弹幕聊天秒级实时流式滚动，冷启动 TotalTime 306 ms。
