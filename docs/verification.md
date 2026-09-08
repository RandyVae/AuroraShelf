# 验证记录 — 2026-09-09

## 2.11.0 漫画模块

- 38 项 JVM 单元测试通过；Android Lint 为 0 个问题；R8 release 构建成功。
- Android 14 / arm64、1080 × 2340、density 440 模拟器全量 14 项设备测试通过，包含漫画底栏、源与分类控件；原视频搜索、收藏、分页、播放全屏和设置流程继续通过。
- Komiic 实时端到端测试获取 24 条列表、124 个章节和首章 193 张图片；Release 冷启动后封面显示、详情、连续阅读、上下章状态和阅读时隐藏底栏均通过。
- 包子漫画桌面网络可取得列表、详情和章节图片，但当前 Android 14 模拟器拒绝其 ZeroSSL 证书链；没有绕过证书校验，默认源改为 Komiic。拷贝漫画在当前网络 DNS 不可达，爱看漫返回 HTTP 403。
- 日间与夜间截图位于 `docs/screenshots/comic-library-light.png`、`comic-library-dark.png`；详情和阅读器截图为 `comic-details-light.png`、`comic-reader-light.png`。
- Release APK 为版本 `127 / 2.11.0`，通过 v2 签名和 16 KB zipalign 检查；SHA-256 为 `5f67ff416dc6721ce1a3dda60b6e5e1a02e37e70d220ce08fcad32dc33c934af`。

## 已完成

- JDK 21 / Gradle 9.6.0 / AGP 9.4.0，debug、Android Lint 和 R8 release 构建成功。
- 32 项 JVM 单元测试通过，包含首页配对逻辑和日间/夜间回退色板的背景、正文亮度断言。
- 模拟器 Android 14 / arm64：11 项设备测试通过；新增用例按实际坐标确认前两张普通卡处于同一水平行且分占左右两列。
- 实时源站用例：默认源获取第一页 **37 条**、第二页 **35 条**且包含新增 ID；黄果获取 **20 条**并成功解密真实封面。源站数量会变化，测试不输出远端标题或地址。
- 2.9.1 release APK 覆盖安装、冷启动和黄果真实双列列表显示成功；日间/夜间切换前后进程 PID 保持一致，运行中的首页与设置页会随系统配置立即更新。
- 多源检查：MissAV 独立解析与其余 MISSPipe 服务注册表测试继续通过；未逐站执行网络播放，源站可用性仍取决于地区、Cloudflare 与站点维护状态。
- 交付 APK 已通过 v2 签名和 16 KB zipalign 检查，版本为 `125 / 2.9.1`，SHA-256 为 `f156ddcb4ffdceb8fe7b39182e14550f25e85cc82120997ebd580567a0036abb`。
- 本轮以同一黄果榜单、同一 1080 × 2340 视口对照日间与夜间：首页、设置、系统栏和播放器检查通过，记录见 `docs/design-audit/system-theme/audit.md`。

最新设备测试结果位于 `build.nosync/app/outputs/androidTest-results/connected/debug/`；解析/校验测试结果位于 `build.nosync/app/test-results/testDebugUnitTest/`。

## 警告与边界

Android Lint 为 0 errors；构建保留 Compose 测试 API 弃用和 Gradle 10 兼容性提示，均未导致编译、测试或安装失败。

以下仍未验收，不能宣称为正式商店发行：

- 用户真机的 120 Hz 长时间滚动、One UI 字体渲染、旋转和多窗口；本轮只在匹配 1080 × 2340 / density 440 的 Android 14 模拟器验证。
- Android 8、不同厂商设备的运行兼容性和性能。
- TalkBack 实机走查、完整颜色对比度审计、长时间滚动帧率/内存/电量基准。
- 站点全量搜索、登录、会员、支付、评论和原版私有数据迁移；这些模块按需求明确排除，离线缓存仍保留。
- 正式发行证书和应用商店发布；当前使用 Android 开发测试证书。

## 用户安装检查

1. 安装 `AuroraShelf-2.11.0-comic-reader.apk`；与上一版包名和测试签名一致，可直接覆盖更新。
2. 底栏第二项应为“漫画”，首次进入默认选择 Komiic，并显示来源、分类、搜索和双列漫画封面。
3. 进入漫画详情可选择章节；阅读器应连续显示图片、隐藏底栏并提供上下章。系统浅色/深色切换后漫画列表同步主题。
4. 首次启动默认使用已确认的视频源；远端列表仍取决于手机网络与源站状态。
5. 如遇故障，记录手机型号、Android 版本、具体操作和错误提示；不要发送账号密码或敏感内容截图。

这是使用开发证书签名的内部测试候选包，不应作为官方商店发行包。桌面交付文件 SHA-256：`5f67ff416dc6721ce1a3dda60b6e5e1a02e37e70d220ce08fcad32dc33c934af`。
