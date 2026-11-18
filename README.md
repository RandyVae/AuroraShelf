# Netgrey (`AuroraShelf`) · iOS / iPadOS & Android v2.17.0

Netgrey（工程代号 `AuroraShelf`，包名 `com.aurorashelf.app`）是一套面向 **iOS / iPadOS Universal** 与 **Android（手机 / 折叠屏 / 平板）** 的双端原生多源流媒体、漫画、直播、社区阅读与离线单机游戏客户端。当前正式版本为 **v2.17.0 (Build 267)**，交付包通过 [GitHub Releases](https://github.com/RandyVae/AuroraShelf/releases/latest)、官方无线 OTA 与 Apple TestFlight 同步分发。

- **iOS / iPadOS 原生端**：基于 **Swift 6 + SwiftUI**（最低部署目标 **iOS 26.0+**），全面遵循 Apple Human Interface Guidelines 与 Liquid Glass 视觉规范；iPhone 采用浮动玻璃胶囊导航，iPad 自动切换为原生侧边栏、4~6 列自适应海报网格与宽屏影院画幅。
- **Android 原生端**：基于 **Kotlin + Jetpack Compose + Material 3 + Haze + AndroidLiquidGlass / Backdrop**（最低 **Android 8.0 / API 26**，目标 **API 36**，编译 SDK **37**），支持 Material You 动态取色、明暗双态即时切换、竖屏液态折射底栏与横屏/大屏自适应侧栏。

---

## 核心功能矩阵（v2.17.0）

### 1. 多源长视频与短剧（18 个视频源）
- 内置 **18 个视频与短剧源**：`huavod`（华视影院）、`huangguo`（黄果短剧）、`91porn`（91视频）、`91dongman`（91动漫）、`beeg`、`134x`、`xvideos`、`missav`、`kissjav`、`85po`、`pornhub`、`tokyomotion`、`spankbang`、`xhamster`、`eporner`、`mrdouga`、`ohentai`、`xnxx`。
- 双端均支持分类浏览、全站搜索、自动翻页去重、选集切换、硬件解码播放（iOS `AVPlayer` + 本地 `HLSLoopbackRelayServer` 防盗链中继；Android `Media3 / ExoPlayer`）与应用私有目录 MP4/HLS 离线缓存。

### 2. 原生在线漫画与分片解扰（10 个漫画源）
- 内置 **10 个原生漫画源**：`komiic`、`baozi`（包子漫画）、`copy`（拷贝漫画）、`ikmmh`（爱看漫画）、`picacg`（哔咔漫画）、`ehentai`、`jm`（禁漫天堂）、`8muses`、`gedecomix`、`dongman91_comic`（91动漫漫画）。
- 支持分类、搜索、章节目录、纵向连续阅读、JM 分片图像实时解扰还原、章节离线下载，以及哔咔最小内容访问登录（令牌经系统硬件密钥库加密存储）。

### 3. 公开直播聚合（2 个直播源）
- 原生接入 **Stripchat** 与 **HornyStudents** 两个公开直播源，支持全部来源混合流或按源筛选、五大频道分类、实时在线人数、HLS 原生拉流播放与只读公开互动展示，不介入充值打赏或权限绕过。

### 4. 多版块社区阅读（7 个论坛版块）
- 聚合 **7 个公开社区版块**：91 社区（`原创自拍区`、`我爱我妻区`、`性趣闲谈`、`两性健康`、`海外专区`）与草榴社区（`草榴技术区`、`达盖尔的旗帜`）。
- 支持多版块并发拉取、移动端正文重排、广告噪声剥离、高清组图懒加载、帖子翻页与单图保存，保持纯匿名只读浏览。

### 5. 内置离线单机游戏
- 双端离线内置基于 HTML5 Canvas + **Spine WebGL** 骨骼动画的回合战斗与确定性随机 **Slots** 单机游戏，所有脚本、纹理与音频均随包内置并运行在受限本地沙箱中，无外网远程代码加载。

### 6. `AuroraResilience` 五层网络自愈引擎
双端对齐实现五层高可用网络防失联架构（Android `data/resilience/*` 与 iOS `Services/Resilience/*`）：
1. **抗污染智能 DNS（`ResilientDns`）**：并发竞速 DoH（DNS-over-HTTPS）与系统解析，自动识别并透传 VPN / TUN Fake-IP 段（`198.18.0.0/15` 等），持久化缓存健康 IP。
2. **源健康度与动态镜像路由（`SourceHealthRegistry`）**：按延迟与成功率实时评分，遇到 403/5xx/超时自动冷却降权并切换备用镜像，支持重定向新域名自学习。
3. **共享连接池与阶梯对冲竞速（`AuroraHttpClient`）**：复用 HTTP/2 连接池，遇到 `StreamResetException` / `EOF` 自动降级 HTTP/1.1 重试；主镜像超过 `1.1s` 未响应时自动拉起次优镜像并发竞速（Staggered Hedged Racing）。
4. **快照秒开与自适应 DOM 兜底（`SourceSnapshotStore` + `AdaptiveHtmlFallbackParser`）**：支持 Stale-While-Revalidate 本地快照秒开；源站改版导致主选择器失效时启用启发式 DOM 结构提取兜底。
5. **零单点故障远端镜像热更新（`RemoteSourceManifest`）**：后台非阻塞拉取 `source-manifest.json` 动态补充最新镜像域名与选择器，远端不可达时 100% 依靠本地内置池独立运行。

### 7. PocketBase 独立账号服务（可选登录）
- 后端采用独立部署的 **PocketBase** 服务（经 Nginx 显式白名单反向代理于 `/auth-api`），仅暴露健康检查与 `users` 集合的注册、登录、会话刷新、邮箱验证与密码重置接口，阻断管理后台 `/_/` 与内部管理路由。
- 客户端会话令牌在 Android 端使用 **Android Keystore AES-GCM** 加密，在 iOS 端存储于系统 **Keychain**。账号服务与匿名内容浏览完全解耦，未登录状态下所有视频、漫画、直播、论坛与单机游戏均可正常使用。

---

## 隐私与传输安全边界

- **本地数据隔离**：收藏、观看历史、阅读进度与离线缓存均保存在应用私有沙箱目录，Android 端显式关闭系统云备份迁移（`allowBackup="false"`）。
- **严格的按域名限定 TLS 降级边界（Domain-Scoped TLS Fallback）**：
  - 账号认证接口（`/auth-api`）、OTA 与 TestFlight 接口及常规站点全程强制执行**系统标准 CA 证书链与主机名校验**，严禁全局忽略证书错误。
  - 针对部分历史源站或镜像 CDN 存在的自签/过期证书问题，宽松 TLS 回退**严格限制在已登记的特定内容源/镜像域名白名单内按需触发**，绝不作用于认证、账户或控制面流量。
- **WebView 安全收口**：内置游戏与必要的网页回退层严格禁用文件协议越权访问、混合内容与非 HTTP(S) 外部 Scheme 跳转。

---

## 构建与验证指南

### 1. 环境要求
- **Android**：JDK 21、Android SDK Platform 37 / Build-Tools 37.0.0、Gradle Wrapper 9.6.0。
- **iOS / iPadOS**：macOS + Xcode（支持 iOS 26.0 SDK 与 Swift 6.0）、XcodeGen（可选，用于从 `ios/project.yml` 同步工程）。
- **本地协议密钥注入（可选）**：复制 `source-secrets.properties.example` 为 Git 忽略的 `source-secrets.properties` 并填入协议参数（iOS 构建前由预构建脚本 `scripts/generate_ios_source_secrets.py` 自动生成未追踪的本地常量文件），严禁提交真实密钥。

### 2. 本地测试与构建命令

```sh
# 跨平台版本号一致性检查 + 内置游戏逻辑回归
bash scripts/publish_ota.sh --check-versions
node scripts/verify-game-logic.js
node scripts/verify-fixes.js

# Android 单元测试、Lint 静态检查与 R8 Release 构建
./gradlew testDebugUnitTest lintDebug
./gradlew assembleRelease

# iOS 模拟器构建与测试（示例：iPhone 17 Pro）
xcodebuild test \
  -project ios/AuroraShelf.xcodeproj \
  -scheme AuroraShelf \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro'

# 全源连通性探测（自动区分 PASS / WARN 受限挑战 / FAIL）
node scripts/probe_all_sources.mjs
```

### 3. 双通道持续交付（OTA + TestFlight）
在双端模拟器 Release 包全规模检查通过后，通过项目脚本完成自动化构建与发布（服务器地址与凭据从本地未追踪的 `.server_credentials` / `.appstore_credentials` 或环境变量读取）：

```sh
# 双端 Release 构建并单事务原子发布至 OTA 服务器（含 >=5GB 磁盘水位门禁与 UDID 签名清单同步）
./scripts/publish_ota.sh Release

# iOS 归档合规预检并上传至 App Store Connect / TestFlight Internal
./scripts/publish_testflight.sh --check
./scripts/publish_testflight.sh
```

---

## 仓库目录结构

| 路径 | 职责说明 |
| --- | --- |
| `app/` | Android 客户端工程（Kotlin、Jetpack Compose、Media3、`AuroraResilience`、单元与仪器测试） |
| `ios/` | iOS / iPadOS Universal 客户端工程（Swift 6、SwiftUI、AVKit、HLS 防盗链中继、XCTest） |
| `third_party/pipepipe-extractor/` | Android 视频多源解析依赖（GPLv3 源码子模块） |
| `server/pocketbase/` | PocketBase 独立账号服务 systemd 沙箱配置、Nginx 白名单配置与集合权限迁移脚本 |
| `scripts/` | 双端签名构建、OTA 原子发布、TestFlight 上传、全源探针与游戏回归校验脚本 |
| `scripts/ota_web/` | OTA 无线分发前端页面（`index.html`）、iOS 安装清单（`manifest.plist`）与远端自愈清单（`source-manifest.json`） |
| `docs/` | Apple HIG / Material 3 视觉规范（`docs/ui/design-system.md`）与视觉验收流程（`docs/ui/visual-qa.md`） |

---

## 开源许可与第三方声明

Android 客户端集成了 GPLv3 许可的 `PipePipeExtractor` 源码组件；内置单机游戏集成了 `Spine Runtimes License` 许可的 Spine WebGL 运行时；其余网络、媒体与 UI 组件遵循 `Apache-2.0` 或 `MIT` 许可。完整归属说明与许可证路径请查阅 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
