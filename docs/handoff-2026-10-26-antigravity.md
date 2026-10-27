# Candidate 157 项目优化与 APK 打包交接文档（面向 Antigravity）

> **交接时间**：2026-10-26 16:30 HKT  
> **目标版本**：`2.15.0`（`versionCode = 157`，已在 `app/build.gradle.kts` 更新）  
> **目标产物**：`dist/AuroraShelf-2.15.0-157-cast-optimized-LOCAL.apk`  
> **接手 Agent**：Antigravity

## 1. 本轮已完成的优化与缺陷修复清单（Candidate 157）

### 1.1 投屏模块与本地流媒体代理（P0 / P1 核心修复）
1. **修复离线缓存视频投屏 404 缺陷（`CastManager.kt` / `LocalMediaProxy.kt` / `PlayerScreen.kt`）**：
   - **根因**：`OfflineVideo.mediaUrl` 保存的是 `DownloadRequest.uri`（远端 HTTP/HTTPS 链接），而实际视频分片存储在 Media3 `SimpleCache`（`filesDir/offline_media`）中。156 版本在 `PlayerScreen` 将 `cached.mediaUrl` 作为 `localFilePath` 传给 `LocalMediaProxy.handleFileProxy`，导致 `File("https://...")` 必定失败返回 `404 Not Found`。
   - **修复**：
     - 在 `LocalMediaProxy.kt` 新增 `OfflineCacheStreamOpener` 接口，并在 `CastManager.kt` 中桥接 `OfflineCacheManager.get(appContext).playbackDataSourceFactory(...)` 与 `DataSpec(uri, start, length, cacheKey)`，使电视请求 `/stream?token=...&url=...&key=offline-...` 时直接从手机本地 Media3 `SimpleCache` 读取字节流（完整支持 HTTP 206 Partial Content 与 Range 跳转，未命中分片自动带 `Referer` 回源）；
     - 在 `LocalMediaProxy.buildLocalFileProxyUrl()` 中增加远端 URL 守卫：若传入 `http://` 或 `https://` 地址，自动透明路由至 `/stream` 代理。
2. **修复 HLS (`.m3u8`) 投屏相对路径断裂与 MIME 漏判（`DlnaSoap.kt` / `LocalMediaProxy.kt`）**：
   - `DlnaSoap.isHlsUrl()` 同时识别原始 `.m3u8`、URL 编码后的 `%2Em3u8` 以及解码后的查询参数，并支持 `mimeTypeOverride` 显式传入 `application/vnd.apple.mpegurl`；
   - `LocalMediaProxy.rewriteHlsManifest()` 在代理 `.m3u8` 播放列表时，自动将清单内的相对/绝对分片路径（`.ts` / 子 `.m3u8`）以及 `#EXT-X-KEY` / `#EXT-X-MAP` 中的 `URI="..."` 重写为携带 `token` 与 `Referer` 的本地代理 URL，彻底解决电视拉取 HLS 分片报 404 或 403 的问题。
3. **加固 `LocalMediaProxy` 局域网安全边界（`LocalMediaProxy.kt`）**：
   - 每次实例启动生成随机 `sessionToken`（UUID），所有 `/stream` 与 `/file` 请求必须校验 `token == sessionToken`，否则立即返回 `403 Forbidden`；
   - 新增 `isAllowedLocalFile(file, allowedDirs)` 沙盒白名单校验，限制 `/file` 仅可访问应用缓存目录（`cacheDir`、`filesDir`、`externalCacheDir`），并显式拦截 `shared_prefs`、`databases` 目录及 `.xml`、`.db`、`.sqlite`、`.json`、`.properties` 等敏感后缀。
4. **SSDP 并发解析与 `SupervisorJob` 容错（`SsdpDiscovery.kt` / `CastManager.kt`）**：
   - `SsdpDiscovery.discover()` 在收到 UDP 响应包后，改为通过 `async(Dispatchers.IO)` 并发拉取各 `LOCATION` 的 XML 描述并支持 `onDeviceFound` 实时增量回调，避免局域网内慢速路由器/打印机阻塞 UDP 接收窗口；
   - `CastManager.scope` 由普通 `Job()` 升级为 `SupervisorJob()`，防止单次协程异常导致全局投屏作用域失效；
   - 投屏指令顺序优化为 `SetAVTransportURI -> Play -> delay(400ms) -> Seek`，兼容要求处于 Playing 状态才接受 `REL_TIME` Seek 的智能电视。

### 1.2 播放器协同、顶栏投屏交互与性能优化（P2）
1. **防止投屏期间切回前台手机误自动播放（`NativeSitePlayer.kt`）**：
   - 在 `Lifecycle.Event.ON_RESUME` 与 `player.prepare()` 处增加 `currentCastState !is CastState.Connected` 守卫，确保投屏连接状态下应用前后台切换或切换剧集时，手机本地 ExoPlayer 保持暂停，不会与电视同时出声。
2. **投屏起播进度同步与剧集连播（`NativeSitePlayer.kt` / `PlayerScreen.kt`）**：
   - `NativeSitePlayer` 新增 `onPositionChange` 回调向 `PlayerScreen` 同步当前播放毫秒数；点击投屏时将 `currentPositionMs` 作为 `initialPositionMs` 传给电视无缝续播；
   - 在投屏连接状态下切换剧集（`onEpisode`）时，自动向当前已连接的电视推送新剧集。
3. **顶栏投屏面板预选设备反馈（`CastDeviceSheet.kt` / `AuroraShelfApp.kt`）**：
   - 当用户在首页、漫画、直播、论坛顶栏打开 `CastDeviceSheet` 点击某台电视时，调用 `castManager.selectDevice(device)` 记录预选设备并展示「待投屏」高亮胶囊，消除此前点击无反应的空操作感；同时新增 `CastState.Error` 错误横幅展示。
4. **真正激活冷启动开屏期间的后台预热（`MainActivity.kt`）**：
   - 在 `MainActivity.onCreate` 中显式访问 `warmUpViewModel.uiState` 并将 `warmUpViewModel` 传入 `AuroraShelfApp(viewModel = warmUpViewModel)`，使首页网络与封面预热在 5.7 秒开屏短片播放期间立即启动。
5. **消除主线程重复 JSON 反序列化与 Compose 重组分配（`AppPreferences.kt` / `AuroraShelfApp.kt` / `HomeScreen.kt`）**：
   - `AppPreferences.savedVideos()` 增加基于原始 JSON 字符串的内存级缓存（`lastSavedVideosJson` / `lastSavedVideosList`），避免每次点击视频或收藏时在主线程重复解析整份历史/收藏 JSON；
   - `AuroraShelfApp.kt` 中使用 `remember` 缓存 `videosById`（$O(1)$ 哈希查找）、`favoriteVideos`、`historyVideos` 与 `currentRelatedVideos`；`HomeScreen.kt` 中使用 `remember(state.videos, gridColumns)` 缓存 `gridRows`。
6. **开屏视频无损瘦身（`app/src/main/res/raw/intro_film.mp4`）**：
   - 使用 `ffmpeg`（H.264 High Profile Level 5.1, `crf=24`, `-preset slow`, `-movflags +faststart`, 1080×2400 @ 60fps）重新编码开屏视频，文件体积从 **2.9 MB** 降至 **741 KB**，预计为 Release APK 直接缩减约 **2.15 MB** 体积。

---

## 2. Antigravity 接手打包与验证步骤（Standard SOP）

### 步骤 1：运行全量 JVM 单元测试与 Lint 检查
请使用 **JDK 21** 执行以下命令（切勿使用 Android Studio 自带的 JBR 25）：

```bash
cd /Users/itou/Documents/ChatGPT/91pron

JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
./gradlew testDebugUnitTest lintDebug --console=plain --no-daemon \
  -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```
- **预期结果**：
  - `testDebugUnitTest` 全量通过（包含 `DlnaSoapTest` 新增的 HLS 编码识别、M3U8 清单重写、沙盒权限校验、Media3 离线缓存分片 Range 流测试，共计 **171 项测试**）；
  - `lintDebug` **0 errors**。

### 步骤 2：构建 Release 候选包（versionCode 157）

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
./gradlew assembleRelease --rerun-tasks --console=plain --no-daemon \
  -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```
- 构建产物路径：`build.nosync/app/outputs/apk/release/app-release.apk`。

### 步骤 3：拷贝至 `dist/` 并执行签名、16 KB 页对齐与 SHA-256 校验

```bash
cp build.nosync/app/outputs/apk/release/app-release.apk \
   dist/AuroraShelf-2.15.0-157-cast-optimized-LOCAL.apk

# 1. 验证 versionCode=157 与 versionName=2.15.0
/opt/homebrew/share/android-commandlinetools/build-tools/37.0.0/aapt2 dump badging \
  dist/AuroraShelf-2.15.0-157-cast-optimized-LOCAL.apk | head -n 5

# 2. 验证 APK Signature Scheme v2+ 签名
/opt/homebrew/share/android-commandlinetools/build-tools/37.0.0/apksigner verify --print-certs \
  dist/AuroraShelf-2.15.0-157-cast-optimized-LOCAL.apk

# 3. 验证 16 KB ELF 页对齐
/opt/homebrew/share/android-commandlinetools/build-tools/37.0.0/zipalign -c -P 16 -v 4 \
  dist/AuroraShelf-2.15.0-157-cast-optimized-LOCAL.apk | tail -n 5

# 4. 计算 SHA-256 并追加至 dist/SHA256SUMS.txt
shasum -a 256 dist/AuroraShelf-2.15.0-157-cast-optimized-LOCAL.apk
```

### 步骤 4：模拟器安装与冒烟验证（可选）
若 `emulator-5554` 在线，可执行覆盖安装与冷启动验证：
```bash
adb install -r dist/AuroraShelf-2.15.0-157-cast-optimized-LOCAL.apk
adb shell am start -W -n com.aurorashelf.app/.MainActivity
```

### 步骤 5：收工记忆同步
打包验证完成后，将实际测得的 APK 字节数与 SHA-256 填入 `.ai/PROJECT_STATE.md`、`.ai/HANDOFF.md` 与 `MEMORY.md`，并运行：
```bash
python3 ~/.agent-memory/sync.py --project .
```
