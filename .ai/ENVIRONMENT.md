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

Release 构建（`assembleRelease`）本轮未重跑；上一次成功记录为 2026-09-24，产物 `build.nosync/app/outputs/apk/release/app-release.apk`（SHA-256 `e8195b4e…`）。

## Test Command（2026-10-24 PASS）

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
./gradlew testDebugUnitTest --console=plain --no-daemon \
  -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

结果：PASS — **99 tests / 0 failures / 1 skipped**。

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
